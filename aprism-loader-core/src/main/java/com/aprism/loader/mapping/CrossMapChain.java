package com.aprism.loader.mapping;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.aprism.loader.contentbind.OfficialMappings;
import com.aprism.loader.remap.TinyMappings;

/**
 * Cross-map chain validation (v26.9 roadmap Alpha.2): verifies that the
 * Fabric Intermediary tiny table and the Mojang official client.txt join
 * into a resolvable chain (intermediary -> official -> runtime) for a
 * sample of classes, and reports coverage counts.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CrossMapChain {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The chain validation report. */
    public record ChainReport(int tinyClasses, int officialClasses,
            int sampled, int resolvable, List<String> unresolved,
            boolean valid) {

        /**
         * @return the report as compact JSON
         */
        public String toJson() {
            return "{\"tinyClasses\":" + tinyClasses
                    + ",\"officialClasses\":" + officialClasses
                    + ",\"sampled\":" + sampled
                    + ",\"resolvable\":" + resolvable
                    + ",\"valid\":" + valid + "}";
        }
    }

    private CrossMapChain() {
    }

    /**
     * Validates the chain for up to {@code sampleLimit} intermediary
     * classes drawn from the tiny table. A sample entry is resolvable when
     * the tiny table yields an official name and the official mappings
     * translate it to a runtime name.
     *
     * @param tinyPath the Fabric Intermediary tiny (v2) file
     * @param clientTxtPath the Mojang official client.txt
     * @param sampleLimit maximum number of intermediary classes to sample
     * @return the chain report
     * @throws IOException when either mapping cannot be read
     */
    public static ChainReport validate(Path tinyPath, Path clientTxtPath,
            int sampleLimit) throws IOException {
        TinyMappings tiny = TinyMappings.parse(tinyPath);
        OfficialMappings official = OfficialMappings.load(clientTxtPath);
        if (official == null) {
            throw new IOException("official mappings not readable: "
                    + clientTxtPath);
        }
        List<String> unresolved = new ArrayList<>();
        int sampled = 0;
        int resolvable = 0;
        for (String intermediary : tiny.intermediaryClassNames()) {
            if (sampled >= sampleLimit) {
                break;
            }
            sampled++;
            String officialName = tiny.classIntermediaryOf(intermediary);
            String runtimeName = official == null ? null
                    : official.runtimeName(officialName);
            if (officialName != null && runtimeName != null
                    && !runtimeName.equals(officialName)) {
                resolvable++;
            } else {
                unresolved.add(intermediary + " -> " + officialName);
            }
        }
        boolean valid = sampled > 0 && resolvable == sampled;
        return new ChainReport(tiny.classCount(), official.size(), sampled,
                resolvable, List.copyOf(unresolved), valid);
    }
}
