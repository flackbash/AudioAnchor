package com.prangesoftwaresolutions.audioanchor.utils;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.prangesoftwaresolutions.audioanchor.R;

public class SkipIntervalUtils {

    public static int getSkipIntervalFromProgress(int progress) {
        return progress + 1;
    }
    public static int getProgressFromSkipInterval(int skipInterval) {
        return skipInterval - 1;
    }
    public static boolean isMaxSkipInterval(int skipInterval) { return skipInterval == 100; }

    /*
     * The configured skip intervals of the four skip buttons (see the "Skip intervals" settings),
     * in the order they appear left to right in PlayActivity: backward 1, backward 2, forward 1,
     * forward 2. An interval of "max" means "skip to the previous/next track" (see
     * isMaxSkipInterval()).
     */
    public static int getBackwardButton1(Context context) {
        return getInterval(context, R.string.settings_backward_button_1_key, R.string.settings_skip_interval_big_default);
    }

    public static int getBackwardButton2(Context context) {
        return getInterval(context, R.string.settings_backward_button_2_key, R.string.settings_skip_interval_small_default);
    }

    public static int getForwardButton1(Context context) {
        return getInterval(context, R.string.settings_forward_button_1_key, R.string.settings_skip_interval_small_default);
    }

    public static int getForwardButton2(Context context) {
        return getInterval(context, R.string.settings_forward_button_2_key, R.string.settings_skip_interval_big_default);
    }

    private static int getInterval(Context context, int keyRes, int defaultRes) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(context.getString(keyRes), Integer.parseInt(context.getString(defaultRes)));
    }
}
