package com.prangesoftwaresolutions.audioanchor.utils;

public final class ResumeFabUtils {
    private ResumeFabUtils() {
    }

    /*
     * Whether the play-pause FAB should be shown: while a playback service is running, or, once
     * it is gone (app killed or crashed), as long as there is a last played track to resume.
     * Never after the user dismissed the playback notification (bindingAllowed is false then).
     */
    public static boolean shouldShowResumeFab(boolean serviceRunning, boolean bindingAllowed, boolean hasLastPlayed) {
        return bindingAllowed && (serviceRunning || hasLastPlayed);
    }
}
