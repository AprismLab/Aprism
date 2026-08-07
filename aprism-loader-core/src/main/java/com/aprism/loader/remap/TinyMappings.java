package com.aprism.loader.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed Tiny v2 mapping tables between two namespaces (canonically
 * {@code official} as namespace 0 and {@code intermediary} as namespace 1).
 * Indexes are built bidirectionally so that both directions are O(1)
 * lookups. Extra namespaces beyond the first two (e.g. yarn's {@code named})
 * are ignored.
 *
 * <p>Tiny v2 format (tab-separated; indentation encodes nesting, one tab per
 * level):
 *
 * <pre>
 * tiny	2	0	official	intermediary
 * 	&lt;property&gt;	&lt;value&gt;            (before the first class)
 * c	&lt;class0&gt;	&lt;class1&gt;
 * 	m	&lt;desc0&gt;	&lt;name0&gt;	&lt;name1&gt;
 * 	f	&lt;desc0&gt;	&lt;name0&gt;	&lt;name1&gt;
 * 	c	&lt;comment&gt;                    (ignored)
 * 		p	&lt;lv&gt;	&lt;var0&gt;	&lt;var1&gt;     (ignored)
 * </pre>
 *
 * <p>Per the spec, member descriptors exist only in namespace 0. Reverse
 * (namespace-1 to namespace-0) member lookups therefore require the incoming
 * descriptor to be translated first; {@link TinyRemapper} performs that
 * translation internally.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class TinyMappings {

    /** A member lookup key. Forward keys use namespace-0 coordinates; reverse keys use namespace-1 owner/name with a namespace-0 descriptor. */
    record MemberKey(String owner, String desc, String name) {
    }

    private final Map<String, String> classForward = new HashMap<>();
    private final Map<String, String> classReverse = new HashMap<>();
    private final Map<MemberKey, String> methodForward = new HashMap<>();
    private final Map<MemberKey, String> methodReverse = new HashMap<>();
    private final Map<MemberKey, String> fieldForward = new HashMap<>();
    private final Map<MemberKey, String> fieldReverse = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();

    private TinyMappings() {
    }

    /**
     * Parses a tiny v2 mapping file.
     *
     * @param path the mapping file
     * @return the parsed mappings
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file is not valid tiny v2
     */
    public static TinyMappings parse(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path)) {
            return parse(r);
        }
    }

    /**
     * Parses tiny v2 content from a reader.
     *
     * @param reader the reader
     * @return the parsed mappings
     * @throws IOException              if reading fails
     * @throws IllegalArgumentException if the content is not valid tiny v2
     */
    public static TinyMappings parse(Reader reader) throws IOException {
        TinyMappings m = new TinyMappings();
        BufferedReader r = reader instanceof BufferedReader br ? br : new BufferedReader(reader);

        String header = r.readLine();
        if (header == null) {
            throw new IllegalArgumentException("Empty mapping file");
        }
        String[] headerCols = header.split("\t");
        if (headerCols.length < 5 || !"tiny".equals(headerCols[0]) || !"2".equals(headerCols[1])) {
            throw new IllegalArgumentException("Not a tiny v2 mapping file (bad header): " + header);
        }

        boolean seenFirstClass = false;
        String currentClass0 = null;
        String currentClass1 = null;

        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }

            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == '\t') {
                indent++;
            }
            String[] cols = line.substring(indent).split("\t", -1);
            String kind = cols[0];

            if (indent == 0) {
                // Class section: c <class0> <class1>
                if (!"c".equals(kind) || cols.length < 2) {
                    throw new IllegalArgumentException("Malformed class line: " + line);
                }
                seenFirstClass = true;
                currentClass0 = cols[1];
                currentClass1 = cols.length > 2 ? cols[2] : "";
                if (!currentClass1.isEmpty()) {
                    m.classForward.put(currentClass0, currentClass1);
                    m.classReverse.put(currentClass1, currentClass0);
                }
            } else if (!seenFirstClass) {
                // File-level properties before the first class section
                m.properties.put(kind, cols.length > 1 ? cols[1] : "");
            } else if (indent == 1) {
                switch (kind) {
                    case "m" -> {
                        // m <desc0> <name0> <name1>
                        if (cols.length >= 4 && !cols[3].isEmpty()) {
                            MemberKey fwd = new MemberKey(currentClass0, cols[1], cols[2]);
                            MemberKey rev = new MemberKey(currentClass1, cols[1], cols[3]);
                            m.methodForward.put(fwd, cols[3]);
                            m.methodReverse.put(rev, cols[2]);
                        }
                    }
                    case "f" -> {
                        // f <desc0> <name0> <name1>
                        if (cols.length >= 4 && !cols[3].isEmpty()) {
                            MemberKey fwd = new MemberKey(currentClass0, cols[1], cols[2]);
                            MemberKey rev = new MemberKey(currentClass1, cols[1], cols[3]);
                            m.fieldForward.put(fwd, cols[3]);
                            m.fieldReverse.put(rev, cols[2]);
                        }
                    }
                    case "c" -> {
                        // class comment: ignored
                    }
                    default -> {
                        // unknown section kinds are skipped (forward compatibility)
                    }
                }
            }
            // indent >= 2: parameters, variables, member comments: ignored
        }
        return m;
    }

    /**
     * @param name0 class name in namespace 0 (e.g. official)
     * @return the namespace-1 name (e.g. intermediary), or {@code null} if unmapped
     */
    public String classNamedOf(String name0) {
        return classForward.get(name0);
    }

    /**
     * @param name1 class name in namespace 1 (e.g. intermediary)
     * @return the namespace-0 name (e.g. official), or {@code null} if unmapped
     */
    public String classIntermediaryOf(String name1) {
        return classReverse.get(name1);
    }

    /**
     * Forward method lookup (namespace-0 coordinates).
     *
     * @param owner0 owner class in namespace 0
     * @param desc0  descriptor in namespace 0
     * @param name0  method name in namespace 0
     * @return the namespace-1 name, or {@code null} if unmapped
     */
    public String methodNamedOf(String owner0, String desc0, String name0) {
        return methodForward.get(new MemberKey(owner0, desc0, name0));
    }

    /**
     * Reverse method lookup (namespace-1 owner and name; namespace-0 descriptor).
     *
     * @param owner1 owner class in namespace 1
     * @param desc0  descriptor translated to namespace 0
     * @param name1  method name in namespace 1
     * @return the namespace-0 name, or {@code null} if unmapped
     */
    public String methodIntermediaryOf(String owner1, String desc0, String name1) {
        return methodReverse.get(new MemberKey(owner1, desc0, name1));
    }

    /**
     * Forward field lookup (namespace-0 coordinates).
     *
     * @param owner0 owner class in namespace 0
     * @param desc0  descriptor in namespace 0
     * @param name0  field name in namespace 0
     * @return the namespace-1 name, or {@code null} if unmapped
     */
    public String fieldNamedOf(String owner0, String desc0, String name0) {
        return fieldForward.get(new MemberKey(owner0, desc0, name0));
    }

    /**
     * Reverse field lookup (namespace-1 owner and name; namespace-0 descriptor).
     *
     * @param owner1 owner class in namespace 1
     * @param desc0  descriptor translated to namespace 0
     * @param name1  field name in namespace 1
     * @return the namespace-0 name, or {@code null} if unmapped
     */
    public String fieldIntermediaryOf(String owner1, String desc0, String name1) {
        return fieldReverse.get(new MemberKey(owner1, desc0, name1));
    }

    /**
     * @return the file-level properties (e.g. {@code escaped-names})
     */
    public Map<String, String> properties() {
        return Map.copyOf(properties);
    }

    /**
     * @return the number of mapped classes
     */
    public int classCount() {
        return classForward.size();
    }

    /**
     * @return the number of mapped methods
     */
    public int methodCount() {
        return methodForward.size();
    }

    /**
     * @return the number of mapped fields
     */
    public int fieldCount() {
        return fieldForward.size();
    }
}
