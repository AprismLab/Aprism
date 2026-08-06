package com.aprism.manifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a parsed {@link AprismManifest} against the Aprism manifest schema.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ManifestValidator {

    /** Minimum and maximum length of a mod id (inclusive). */
    private static final int ID_MIN = 2;
    private static final int ID_MAX = 64;

    /** A mod id must be lowercase, start with a letter, and use only [a-z0-9_-]. */
    private static final String ID_PATTERN = "[a-z][a-z0-9_-]{" + (ID_MIN - 1) + "," + (ID_MAX - 1) + "}";

    /** Simplified SemVer pattern: MAJOR.MINOR.PATCH with optional pre-release/build. */
    private static final String SEMVER_PATTERN = "\\d+\\.\\d+\\.\\d+([+-][0-9A-Za-z.-]+)?";

    /**
     * Result of validating a manifest.
     *
     * @param valid    whether the manifest is valid
     * @param errors   the list of error messages
     * @param warnings the list of warning messages
     */
    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
    }

    /**
     * Validates the given manifest.
     *
     * @param manifest the manifest to validate
     * @return the validation result
     */
    public ValidationResult validate(AprismManifest manifest) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (manifest == null) {
            errors.add("manifest is null");
            return new ValidationResult(false, errors, warnings);
        }

        if (manifest.id() == null || manifest.id().isEmpty()) {
            errors.add("id is required");
        } else if (!manifest.id().matches(ID_PATTERN)) {
            errors.add("id must be lowercase, " + ID_MIN + "-" + ID_MAX
                    + " chars, and start with a letter");
        }

        if (manifest.version() == null || manifest.version().isEmpty()) {
            errors.add("version is required");
        } else if (!manifest.version().matches(SEMVER_PATTERN)) {
            errors.add("version must be SemVer (MAJOR.MINOR.PATCH)");
        }

        if (manifest.displayName() == null || manifest.displayName().isEmpty()) {
            warnings.add("displayName is recommended");
        }

        if (manifest.schemaVersion() <= 0) {
            errors.add("schemaVersion must be positive");
        }

        if (manifest.environment() == null || manifest.environment().isEmpty()) {
            warnings.add("environment is empty; defaulting to '*'");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
}
