# Multi-Node Cache Management Rules

Cached data on one node goes stale on the others when a write happens, because the Hibernate
second-level cache and Spring caches are node-local. The `cache-management` feature module propagates
invalidation across nodes **without** Kafka/Redis: a write publishes an event row to a shared table;
every node polls that table on a fixed delay and clears the cache regions registered for the changed
class. Clearing is idempotent, so re-observing an event is harmless.

This module is the **generic mechanism only**. Each project must supply four app-specific pieces
(registry, event store, cache-manager bridge, publish calls). Implement all four or invalidation is a
silent no-op.

## What the module already provides (do not reimplement)

Package `PACKAGE_REPLACE_ME.cachemanagement`:

- `registry.CacheNamesByClassRegistry` — interface the app implements (class → cache region names).
- `registry.CacheNamesByClassService(+Impl)` — flattens the registry by simple class name.
- `cache.CacheService(+Impl)` — injects **every** Spring `CacheManager` bean and resolves a region by
  name across all of them. This is why a region is only evictable if some `CacheManager` exposes it.
- `updater.CacheUpdaterService(+Impl)` — clears the regions registered for a class.
- `updater.ScheduledCacheUpdater` — `@Scheduled` poller; advances a UTC cursor.
- `updater.CacheRegistryVerifier` — optional startup check (off by default).
- `event.CacheInvalidationEvent` (record) + `event.CacheInvalidationEventService` (interface).

## Required: the application must implement all four

### 1. `CacheNamesByClassRegistry` bean

Map every cached entity to the regions cleared when it changes. Region names must **exactly** match
the cache names registered in the managers — for Hibernate L2 those are the `ehcache.xml` aliases,
typically `hibernate-cache.<query-region>` and `hibernate-cache.<fully.qualified.EntityClass>`. Include
any Spring cache names (e.g. a derived per-user cache) whose inputs the class affects.

```java
@Component
public class AppCacheNamesByClassRegistry implements CacheNamesByClassRegistry {
    @Override public Map<Class<?>, List<String>> cacheNamesByClassMap() {
        return Map.of(
            Foo.class, List.of("hibernate-cache.findAllFoo",
                                "hibernate-cache.com.example.app.domain.entity.Foo"));
    }
}
```

### 2. DB-backed `CacheInvalidationEventService` (the shared store)

- JPA `@Entity` (e.g. `CacheUpdateEvent`) with `id`, `trackedClass` (text), `updatedAt` (timestamp) —
  in the `domain` module so the existing `@EntityScan`/`@EnableJpaRepositories` pick it up.
- Spring Data repository: `findByUpdatedAtAfterOrderByUpdatedAtAsc(LocalDateTime)` and
  `deleteByUpdatedAtBefore(LocalDateTime)`.
- `@Service @Transactional` impl: inject `CurrentTime`; `publishUpdateEvent`
  saves a row with `updatedAt = currentTime.nowLocalDateTime()`;
  `updatesAfter` maps rows to `CacheInvalidationEvent`; a `@Scheduled` daily cleanup prunes old rows
  (only recent rows are ever polled).
- Liquibase migration for the table goes in the **`db` module** (single-sourced), never in a feature module.

### 3. Expose every evictable region through a Spring `CacheManager`

`CacheService` only reaches regions that some `CacheManager` bean knows. Spring caches you declare are
already reachable. **Hibernate's L2 cache is the trap:** you must bridge the *exact* `javax.cache.CacheManager`
instance Hibernate uses — a second manager built from the same `ehcache.xml` is a *different* set of
caches, so clearing it is a no-op. Share one instance:

```java
@Bean(destroyMethod = "close")
javax.cache.CacheManager hibernateJCacheManager() {
    URI cfg = getClass().getClassLoader().getResource("ehcache.xml").toURI();
    return Caching.getCachingProvider().getCacheManager(cfg, getClass().getClassLoader());
}
@Bean // hand the SAME instance to Hibernate instead of letting it build its own
HibernatePropertiesCustomizer sharedJCacheManagerCustomizer(javax.cache.CacheManager hibernateJCacheManager) {
    return props -> props.put("hibernate.javax.cache.cache_manager", hibernateJCacheManager);
}
@Bean // bridge so CacheService can clear hibernate-cache.* regions by name
CacheManager hibernateL2CacheManager(javax.cache.CacheManager hibernateJCacheManager) {
    return new JCacheCacheManager(hibernateJCacheManager);
}
```

If the app has more than one `CacheManager` bean, mark the one used by `@Cacheable`/`@CacheEvict`
`@Primary`.

### 4. Publish on every write + enable scheduling

Inject `CacheInvalidationEventService` into the mutation services and call
`publishUpdateEvent(Entity.class)` after each create/update/delete and after batch jobs that touch the
entity. Keep any local `@CacheEvict` for same-node immediacy — the event is what reaches the *other*
nodes. Put `@EnableScheduling` on the application.

```java
fooRepository.save(foo);
cacheInvalidationEventService.publishUpdateEvent(Foo.class);
```

## Configuration baseline

```yaml
app:
  cache-management:
    poll-interval-ms: 15000     # fixed delay between polls
    initial-delay-ms: 15000     # delay before the first poll (after warm-up)
    verify-registry: false      # true in non-prod: fail fast on a region name that no manager knows
    cleanup-cron: "0 30 1 * * *" # daily prune of old event rows
```

## Critical correctness rules

- **Same JCache instance** for the Hibernate L2 bridge (see #3) — otherwise clears silently no-op.
- **UTC everywhere** — both the event `updatedAt` and the poller cursor use
  the generated `CurrentTime` boundary, so nodes in any timezone agree.
- **No implicit Hibernate regions** — every `@org.hibernate.annotations.Cache` usage must set an
  explicit `region = "..."`, and `ehcache.xml` must declare the matching alias after applying
  `hibernate.cache.region_prefix` (for example `hibernate-cache.com.example.Entity`).
- **No implicit query cache regions** — every `@QueryHint(name = HINT_CACHEABLE, value = "true")`
  must also set `HINT_CACHE_REGION`, and `ehcache.xml` must declare the matching prefixed alias
  (for example `hibernate-cache.com.example.Repository.findByCode`).
- **Region names must match exactly** — a typo means the region is never cleared. Turn on
  `verify-registry` in CI/dev to assert at startup that every registered name resolves to a region.
- **Bound the event table** — keep the scheduled retention cleanup; the poll only needs recent rows.
- **Idempotent clear** — never make eviction depend on processing an event exactly once.

## Acceptance checks

- Updating an entity on node A clears the entity's registered regions on node B within one poll interval.
- A row appears in the event table on each tracked write; old rows are pruned by the cleanup job.
- With `verify-registry: true`, a deliberately wrong region name fails application startup.
- Clearing a Hibernate L2 region via the mechanism actually forces a DB re-read on the next query
  (proves the bridge shares Hibernate's manager instance).
