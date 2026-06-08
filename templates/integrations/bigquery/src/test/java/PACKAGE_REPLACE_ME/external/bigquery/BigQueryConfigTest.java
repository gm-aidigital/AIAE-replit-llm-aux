package PACKAGE_REPLACE_ME.external.bigquery;

import PACKAGE_REPLACE_ME.external.bigquery.config.BigQueryConfig;
import PACKAGE_REPLACE_ME.external.bigquery.config.BigQueryProperties;
import PACKAGE_REPLACE_ME.external.bigquery.impl.BigQueryClientImpl;
import PACKAGE_REPLACE_ME.external.bigquery.impl.BigQueryStubClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BigQueryConfigTest {

    private final BigQueryConfig config = new BigQueryConfig();

    @Test
    void shouldProvideStubClientWhenStubEnabledTest() {
        BigQueryProperties props = new BigQueryProperties();
        props.setStubEnabled(true);
        props.setProjectId("demo-project");
        props.setDataset("analytics");

        assertThat(config.bigQueryStubClient(props)).isInstanceOf(BigQueryStubClient.class);
    }

    @Test
    void shouldFailFastWhenCredentialsMissingTest() {
        BigQueryProperties props = new BigQueryProperties();
        props.setEnabled(true);
        props.setCredentialsLocation("");
        props.setProjectId("my-project");

        assertThatThrownBy(() -> config.bigQueryClient(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("credentials-location");
    }

    @Test
    void shouldFailFastWhenProjectIdMissingTest() {
        BigQueryProperties props = new BigQueryProperties();
        props.setEnabled(true);
        props.setCredentialsLocation("/run/secrets/bq.json");
        props.setProjectId("");

        assertThatThrownBy(() -> config.bigQueryClient(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("project-id");
    }

    @Test
    void shouldNotLeakCredentialPathInFailFastMessageTest() {
        BigQueryProperties props = new BigQueryProperties();
        props.setEnabled(true);
        props.setCredentialsLocation("/run/secrets/super-secret-bq.json");
        props.setProjectId("");

        assertThatThrownBy(() -> config.bigQueryClient(props))
            .isInstanceOf(IllegalStateException.class)
            .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("super-secret-bq.json"));
    }

    @Test
    void shouldCreateProductionClientWhenConfiguredTest() {
        BigQueryProperties props = new BigQueryProperties();
        props.setEnabled(true);
        props.setCredentialsLocation("/run/secrets/bq.json");
        props.setProjectId("my-project");
        props.setDataset("analytics");

        assertThat(config.bigQueryClient(props)).isInstanceOf(BigQueryClientImpl.class);
    }
}
