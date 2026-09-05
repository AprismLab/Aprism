package com.aprism.loader.mapping;

import java.nio.file.Path;
import java.util.List;

/**
 * Harness-facing CLI for the mapping asset manager (v26.9-Alpha.2).
 *
 * <pre>
 * MappingAssetManagerCli --cache &lt;dir&gt; --fetch &lt;version&gt;
 * MappingAssetManagerCli --cache &lt;dir&gt; --diagnose &lt;version&gt; [--profile REMAPPED|NO_REMAP] [--offline]
 * MappingAssetManagerCli --chain --tiny &lt;tiny-v2&gt; --client &lt;client.txt&gt; [--sample &lt;n&gt;]
 * </pre>
 *
 * Exit codes: 0 ready/valid, 2 incomplete/invalid, 1 error.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MappingAssetManagerCli {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private MappingAssetManagerCli() {
    }

    /**
     * CLI entry point.
     *
     * @param args arguments per the class doc
     * @throws Exception on IO failure
     */
    public static void main(String[] args) throws Exception {
        String cache = null;
        String fetch = null;
        String diagnose = null;
        String profile = "REMAPPED";
        boolean offline = false;
        boolean chain = false;
        String tiny = null;
        String client = null;
        int sample = 64;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--cache" -> cache = args[i + 1];
                case "--fetch" -> fetch = args[i + 1];
                case "--diagnose" -> diagnose = args[i + 1];
                case "--profile" -> profile = args[i + 1];
                case "--offline" -> offline = true;
                case "--tiny" -> tiny = args[i + 1];
                case "--client" -> client = args[i + 1];
                case "--sample" -> sample = Integer.parseInt(args[i + 1]);
                default -> {
                }
            }
        }
        chain = List.of(args).contains("--chain");

        if (fetch != null) {
            requireCache(cache);
            MappingAssetManager manager =
                    new MappingAssetManager(Path.of(cache), false);
            MappingAssetManager.FetchedAssets assets = manager.fetchFor(fetch);
            System.out.println("fetched " + assets.version() + " -> "
                    + assets.clientMappings() + " + " + assets.clientJar());
            System.out.println(manager.diagnose(fetch, true).toJson());
            return;
        }
        if (diagnose != null) {
            requireCache(cache);
            MappingAssetManager manager =
                    new MappingAssetManager(Path.of(cache), offline);
            MappingAssetManager.ProfileReport report =
                    manager.diagnose(diagnose, "REMAPPED".equalsIgnoreCase(profile));
            System.out.println(report.toJson());
            System.exit(report.ready() ? 0 : 2);
        }
        if (chain && tiny != null && client != null) {
            CrossMapChain.ChainReport report = CrossMapChain.validate(
                    Path.of(tiny), Path.of(client), sample);
            System.out.println(report.toJson());
            System.exit(report.valid() ? 0 : 2);
        }
        System.err.println("nothing to do: pass --fetch, --diagnose, or --chain");
        System.exit(1);
    }

    private static void requireCache(String cache) {
        if (cache == null) {
            System.err.println("--cache is required");
            System.exit(1);
        }
    }
}
