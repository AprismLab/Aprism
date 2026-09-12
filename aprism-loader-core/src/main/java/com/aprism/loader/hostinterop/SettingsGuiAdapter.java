package com.aprism.loader.hostinterop;

import java.util.List;

/**
 * Settings-GUI adapter contract (v26.9 roadmap Alpha.7): the
 * host-agnostic shape of "present these mod settings on your settings
 * surface". A host that advertises {@link HostCapability#SETTINGS_GUI}
 * implements it; the existing {@code SettingsRegistry}/{@code ModSettings}
 * declarations are the data source, this is the presentation seam.
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface SettingsGuiAdapter {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * One settings surface request.
     *
     * @param title the screen title
     * @param categories the categories to present, in order
     */
    record SettingsScreenRequest(String title, List<String> categories) {
        /** Validates the request. */
        public SettingsScreenRequest {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title required");
            }
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    /**
     * Presents the settings surface (headless hosts may only record it).
     *
     * @param request the request
     * @return the presentation report
     */
    AdapterReport presentSettings(SettingsScreenRequest request);
}
