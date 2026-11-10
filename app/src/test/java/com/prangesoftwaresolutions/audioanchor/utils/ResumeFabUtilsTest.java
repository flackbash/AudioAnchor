package com.prangesoftwaresolutions.audioanchor.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResumeFabUtilsTest {
    @Test
    public void shownWhenServiceRunning() {
        assertTrue(ResumeFabUtils.shouldShowResumeFab(true, true, true));
    }

    // Issue #8: after the app is killed there is no service, but the last played track is in the DB
    @Test
    public void shownWhenServiceDeadButLastPlayedExists() {
        assertTrue(ResumeFabUtils.shouldShowResumeFab(false, true, true));
    }

    @Test
    public void hiddenWhenNothingWasEverPlayed() {
        assertFalse(ResumeFabUtils.shouldShowResumeFab(false, true, false));
    }

    // The user dismissed the notification: stay hidden until the next launch
    @Test
    public void hiddenWhenBindingNotAllowed() {
        assertFalse(ResumeFabUtils.shouldShowResumeFab(false, false, true));
        assertFalse(ResumeFabUtils.shouldShowResumeFab(true, false, true));
    }
}
