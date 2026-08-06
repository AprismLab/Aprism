package com.aprism.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses and stores Fabric-style access widener rules, then applies them to
 * class bytecode via ASM during the {@link AprismClassTransformer} pipeline.
 *
 * <p>The access widener format is a text file with a header line
 * {@code accessWidener v1 named} followed by rules:
 * <pre>
 * accessWidener v1 named
 * accessible class net/example/SomeClass
 * accessible method net/example/SomeClass someMethod ()V
 * accessible field net/example/SomeClass someField I
 * extendable class net/example/SomeClass
 * extendable method net/example/SomeClass someMethod ()V
 * mutable field net/example/SomeClass someField I
 * </pre>
 *
 * <p>Access operations:
 * <ul>
 *   <li>{@code accessible} - makes the target public (classes, methods, fields)</li>
 *   <li>{@code extendable} - makes the target protected (classes, methods) so
 *       subclasses can override</li>
 *   <li>{@code mutable} - removes the {@code final} flag from fields</li>
 * </ul>
 *
 * <p>Rules are keyed by slashed class name for fast lookup during
 * transformation. Multiple widener files can be merged into a single
 * {@link AccessWidener} instance via {@link #merge}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AccessWidener {

    private static final Logger LOG = Logger.getLogger(AccessWidener.class.getName());

    /** The kind of access widening to apply. */
    public enum AccessType {
        /** Make the target public. */
        ACCESSIBLE,
        /** Make the target protected (classes, methods only). */
        EXTENDABLE,
        /** Remove the final flag (fields only). */
        MUTABLE
    }

    /** A single access widener rule targeting a class, method, or field. */
    public record WidenerRule(
            AccessType accessType,
            String className,
            String memberName,
            String descriptor) {

        /**
         * @return whether this rule targets a class (no member name)
         */
        public boolean isClassRule() {
            return memberName == null || memberName.isEmpty();
        }

        /**
         * @return whether this rule targets a method (has a descriptor
         *         starting with {@code (})
         */
        public boolean isMethodRule() {
            return !isClassRule() && descriptor != null && descriptor.startsWith("(");
        }

        /**
         * @return whether this rule targets a field (has a descriptor that
         *         is not a method descriptor)
         */
        public boolean isFieldRule() {
            return !isClassRule() && !isMethodRule();
        }
    }

    private final List<WidenerRule> rules = new ArrayList<>();
    private final Map<String, List<WidenerRule>> rulesByClass = new HashMap<>();

    /**
     * Parses an access widener file from a jar/zip entry and merges the rules
     * into this instance.
     *
     * @param archiveFile the jar or .aje archive containing the widener file
     * @param entryPath   the path to the widener file inside the archive
     *                    (e.g. {@code "mymod.accesswidener"})
     */
    public void parseFromArchive(Path archiveFile, String entryPath) {
        try (FileSystem fs = FileSystems.newFileSystem(archiveFile, (ClassLoader) null)) {
            Path entry = fs.getPath(entryPath);
            if (Files.exists(entry)) {
                try (InputStream is = Files.newInputStream(entry)) {
                    parse(is);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to read access widener from " + archiveFile + ":" + entryPath
                    + ": " + e.getMessage());
        }
    }

    /**
     * Parses an access widener file from an input stream and merges the rules
     * into this instance.
     *
     * @param stream the input stream containing the widener file content
     * @throws IOException if the stream cannot be read
     */
    public void parse(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean headerSeen = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    if (!trimmed.startsWith("accessWidener")) {
                        throw new IllegalArgumentException(
                                "Invalid access widener header: " + trimmed);
                    }
                    headerSeen = true;
                    continue;
                }
                parseRule(trimmed);
            }
        }
    }

    /**
     * Parses a single rule line and adds it to the rule set.
     *
     * @param line the rule line (e.g. {@code "accessible method net/Example foo ()V"})
     */
    private void parseRule(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            LOG.warning("Skipping malformed access widener rule: " + line);
            return;
        }
        AccessType accessType;
        try {
            accessType = AccessType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warning("Unknown access widener operation: " + parts[0]);
            return;
        }
        String memberKind = parts[1];
        if ("class".equals(memberKind)) {
            String className = parts[2].replace('.', '/');
            addRule(new WidenerRule(accessType, className, null, null));
        } else if ("method".equals(memberKind)) {
            if (parts.length < 5) {
                LOG.warning("Skipping malformed method rule: " + line);
                return;
            }
            String className = parts[2].replace('.', '/');
            String methodName = parts[3];
            String descriptor = parts[4];
            addRule(new WidenerRule(accessType, className, methodName, descriptor));
        } else if ("field".equals(memberKind)) {
            if (parts.length < 5) {
                LOG.warning("Skipping malformed field rule: " + line);
                return;
            }
            String className = parts[2].replace('.', '/');
            String fieldName = parts[3];
            String descriptor = parts[4];
            addRule(new WidenerRule(accessType, className, fieldName, descriptor));
        } else {
            LOG.warning("Unknown access widener member kind: " + memberKind);
        }
    }

    /**
     * Adds a rule to the widener and indexes it by class name.
     *
     * @param rule the rule to add
     */
    private void addRule(WidenerRule rule) {
        rules.add(rule);
        rulesByClass.computeIfAbsent(rule.className(), k -> new ArrayList<>()).add(rule);
    }

    /**
     * Merges all rules from another widener into this one.
     *
     * @param other the widener to merge from
     */
    public void merge(AccessWidener other) {
        for (WidenerRule rule : other.rules) {
            addRule(rule);
        }
    }

    /**
     * @return whether any rules have been registered
     */
    public boolean hasRules() {
        return !rules.isEmpty();
    }

    /**
     * Returns the rules applicable to the given class, or an empty list if
     * none.
     *
     * @param slashedClassName the slashed class name (e.g. {@code "net/Example"})
     * @return the list of rules for that class
     */
    public List<WidenerRule> getRulesForClass(String slashedClassName) {
        return rulesByClass.getOrDefault(slashedClassName, List.of());
    }

    /**
     * @return the total number of rules registered
     */
    public int ruleCount() {
        return rules.size();
    }
}
