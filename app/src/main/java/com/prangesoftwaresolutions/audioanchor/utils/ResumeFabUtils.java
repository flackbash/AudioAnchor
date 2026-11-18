package com.prangesoftwaresolutions.audioanchor.utils;

import com.prangesoftwaresolutions.audioanchor.models.AudioFile;

public final class ResumeFabUtils {
    private ResumeFabUtils() {
    }

    /*
     * Whether the play-pause FAB should be shown: while a playback service is running, or, once
     * it is gone (app killed or crashed), as long as there is a last played track to resume --
     * i.e. one that did not already play to its end. Never after the user dismissed the playback
     * notification (bindingAllowed is false then).
     */
    public static boolean shouldShowResumeFab(
            boolean serviceRunning,
            boolean bindingAllowed,
            AudioFile lastPlayed
    ) {
        boolean uncompletedFile = lastPlayed != null && !Utils.isFinished(lastPlayed, lastPlayed.getCompletedTime());
        return bindingAllowed && (serviceRunning || uncompletedFile);
    }
}
