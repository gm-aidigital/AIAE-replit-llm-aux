package PACKAGE_REPLACE_ME.external.googleworkspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime-tunable properties for the Google Workspace adapter pack.
 *
 * <p>All fields bind from the {@code app.external.google-workspace.*} namespace.
 * Individual Google APIs are enabled independently so an app can install the
 * pack and activate only the services it needs.
 *
 * <p>Typical {@code application.yml} stubs:
 * <pre>
 * app:
 *   external:
 *     google-workspace:
 *       credentials-location: ${GOOGLE_WORKSPACE_CREDENTIALS_LOCATION:}
 *       stub-enabled: ${GOOGLE_WORKSPACE_STUB_ENABLED:false}
 *       docs-enabled: ${GOOGLE_WORKSPACE_DOCS_ENABLED:false}
 *       drive-enabled: ${GOOGLE_WORKSPACE_DRIVE_ENABLED:false}
 *       sheets-enabled: ${GOOGLE_WORKSPACE_SHEETS_ENABLED:false}
 *       slides-enabled: ${GOOGLE_WORKSPACE_SLIDES_ENABLED:false}
 * </pre>
 *
 * <p>Security: {@code credentialsLocation} must be a file-system path to a
 * mounted service-account JSON file. Never commit credentials JSON.
 */
@ConfigurationProperties(prefix = "app.external.google-workspace")
@Validated
public class GoogleWorkspaceProperties {

    private String credentialsLocation = "";
    private boolean stubEnabled = false;
    private boolean docsEnabled = false;
    private boolean driveEnabled = false;
    private boolean sheetsEnabled = false;
    private boolean slidesEnabled = false;

    /**
     * Returns the file-system path to the service-account credentials JSON.
     *
     * @return credentials file path; empty when not configured
     */
    public String getCredentialsLocation() {
        return credentialsLocation;
    }

    /**
     * Sets the file-system path to the service-account credentials JSON.
     *
     * @param credentialsLocation absolute path to the mounted credentials file
     */
    public void setCredentialsLocation(String credentialsLocation) {
        this.credentialsLocation = credentialsLocation;
    }

    /**
     * Returns whether deterministic local stub clients are enabled.
     *
     * @return {@code true} if stub clients should be used
     */
    public boolean isStubEnabled() {
        return stubEnabled;
    }

    /**
     * Sets whether deterministic local stub clients are enabled.
     *
     * @param stubEnabled {@code true} to use in-memory stub clients
     */
    public void setStubEnabled(boolean stubEnabled) {
        this.stubEnabled = stubEnabled;
    }

    /**
     * Returns whether the Google Docs adapter is enabled.
     *
     * @return {@code true} if Docs is active
     */
    public boolean isDocsEnabled() {
        return docsEnabled;
    }

    /**
     * Sets whether the Google Docs adapter is enabled.
     *
     * @param docsEnabled {@code true} to enable
     */
    public void setDocsEnabled(boolean docsEnabled) {
        this.docsEnabled = docsEnabled;
    }

    /**
     * Returns whether the Google Drive adapter is enabled.
     *
     * @return {@code true} if Drive is active
     */
    public boolean isDriveEnabled() {
        return driveEnabled;
    }

    /**
     * Sets whether the Google Drive adapter is enabled.
     *
     * @param driveEnabled {@code true} to enable
     */
    public void setDriveEnabled(boolean driveEnabled) {
        this.driveEnabled = driveEnabled;
    }

    /**
     * Returns whether the Google Sheets adapter is enabled.
     *
     * @return {@code true} if Sheets is active
     */
    public boolean isSheetsEnabled() {
        return sheetsEnabled;
    }

    /**
     * Sets whether the Google Sheets adapter is enabled.
     *
     * @param sheetsEnabled {@code true} to enable
     */
    public void setSheetsEnabled(boolean sheetsEnabled) {
        this.sheetsEnabled = sheetsEnabled;
    }

    /**
     * Returns whether the Google Slides adapter is enabled.
     *
     * @return {@code true} if Slides is active
     */
    public boolean isSlidesEnabled() {
        return slidesEnabled;
    }

    /**
     * Sets whether the Google Slides adapter is enabled.
     *
     * @param slidesEnabled {@code true} to enable
     */
    public void setSlidesEnabled(boolean slidesEnabled) {
        this.slidesEnabled = slidesEnabled;
    }

    /**
     * Returns {@code true} when any production adapter (docs, drive, sheets, or slides)
     * is enabled.
     *
     * @return whether any production API is active
     */
    public boolean isAnyProductionEnabled() {
        return docsEnabled || driveEnabled || sheetsEnabled || slidesEnabled;
    }
}
