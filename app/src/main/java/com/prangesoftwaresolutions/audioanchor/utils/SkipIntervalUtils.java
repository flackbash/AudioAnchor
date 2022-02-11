package com.prangesoftwaresolutions.audioanchor.utils;

public class SkipIntervalUtils {

    public static int getSkipIntervalFromProgress(int progress) {
        return progress + 1;
    }
    public static int getProgressFromSkipInterval(int skipInterval) {
        return skipInterval - 1;
    }
    public static boolean isMaxSkipInterval(int skipInterval) { return skipInterval == 100; }
}
