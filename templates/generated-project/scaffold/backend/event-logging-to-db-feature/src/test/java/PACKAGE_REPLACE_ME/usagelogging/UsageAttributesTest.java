package PACKAGE_REPLACE_ME.usagelogging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// In the usagelogging package so it can exercise the package-private
// snapshot()/clear() the aspect relies on.
class UsageAttributesTest {

    @AfterEach
    void clearBag() {
        UsageAttributes.clear();
    }

    @Test
    void shouldAccumulateAndSnapshotAttributesTest() {
        // When:
        UsageAttributes.put("geo", "US");
        UsageAttributes.put("count", 3);

        // Then:
        Map<String, Object> snapshot = UsageAttributes.snapshot();
        assertThat(snapshot).containsEntry("geo", "US").containsEntry("count", 3);
    }

    @Test
    void shouldIgnoreNullKeyOrValueTest() {
        // When:
        UsageAttributes.put(null, "x");
        UsageAttributes.put("k", null);

        // Then: nothing was recorded
        assertThat(UsageAttributes.snapshot()).isNull();
    }

    @Test
    void shouldReturnNullSnapshotWhenEmptyAndAfterClearTest() {
        // Then: empty bag snapshots null
        assertThat(UsageAttributes.snapshot()).isNull();

        // When: a value is recorded then cleared
        UsageAttributes.put("a", "b");
        UsageAttributes.clear();

        // Then:
        assertThat(UsageAttributes.snapshot()).isNull();
    }
}
