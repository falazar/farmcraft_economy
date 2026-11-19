package com.falazar.farmupcraft.client;

import journeymap.api.v2.common.waypoint.Waypoint;

public final class JourneyMapFeatureFilter {
    static final String FEATURE_KEY = "farmupcraft:scan_feature";
    static final String FEATURE_VALUE = "lake_island";
    private static boolean enabled;
    private static boolean featuresHidden;

    private JourneyMapFeatureFilter() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        featuresHidden = false;
    }

    public static void hideFeatures() {
        enabled = false;
        featuresHidden = true;
    }

    public static boolean toggleFeatures() {
        featuresHidden = !featuresHidden;
        enabled = false;
        return !featuresHidden;
    }

    public static void reset() {
        enabled = false;
        featuresHidden = false;
    }

    static void tag(Waypoint waypoint) {
        waypoint.setCustomData(FEATURE_KEY, FEATURE_VALUE);
    }

    public static boolean allows(Waypoint waypoint) {
        boolean feature = FEATURE_VALUE.equals(waypoint.getCustomData(FEATURE_KEY));
        return feature ? !featuresHidden : !enabled;
    }

    public static boolean showOnMap(Waypoint waypoint) {
        return allows(waypoint) && waypoint.showOnMap();
    }
}
