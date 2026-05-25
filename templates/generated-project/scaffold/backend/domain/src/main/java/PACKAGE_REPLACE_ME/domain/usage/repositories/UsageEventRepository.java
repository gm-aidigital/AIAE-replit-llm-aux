package PACKAGE_REPLACE_ME.domain.usage.repositories;

import PACKAGE_REPLACE_ME.domain.usage.entities.UsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for persisted usage logging events.
 */
public interface UsageEventRepository extends JpaRepository<UsageEventEntity, Long> {
}
