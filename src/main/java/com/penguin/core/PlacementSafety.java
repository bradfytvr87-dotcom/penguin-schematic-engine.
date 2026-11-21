package com.penguin.core;

/**
 * Client-side safety policy. This mod never places blocks, sends placement packets, or automates clicks:
 * every block is placed by the player. It does not bypass, evade, or replace a server's anti-cheat.
 */
public final class PlacementSafety {
    /** Vanilla survival block interaction range in blocks. */
    public static final double REACH = 4.5;

    private PlacementSafety() {}

    public static boolean isSafeManualMode(boolean automationEnabled, boolean packetInjectionEnabled) {
        return !automationEnabled && !packetInjectionEnabled;
    }

    public static boolean isWithinPreviewDistance(double distance, double maxDistance) {
        return Double.isFinite(distance) && distance >= 0 && distance <= maxDistance;
    }

    /** True when a block centre is inside normal reach, so the HUD can tell you to move closer. */
    public static boolean isWithinReach(double distance) {
        return isWithinPreviewDistance(distance, REACH + 0.87); // +half block diagonal, centre to far corner
    }
}
