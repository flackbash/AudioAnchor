package com.prangesoftwaresolutions.audioanchor.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.R;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Utility class for AudioAnchor
 */

public class Utils {

    /*
     * Set the Activity Theme according to the user preferences.
     * Call in onCreate before setContentView and super.onCreate to apply theme
     */
    public static void setActivityTheme(Context context) {
        SharedPreferences prefManager = PreferenceManager.getDefaultSharedPreferences(context);

        String darkTheme;
        try {
            darkTheme = prefManager.getString(context.getString(R.string.settings_dark_key), context.getString(R.string.settings_dark_default));
        } catch (ClassCastException e) {
            // This is needed so switching from the old version where darkTheme was stored as boolean
            // to the new version where it is stored as a string works.
            boolean darkThemeBool = prefManager.getBoolean(context.getString(R.string.settings_dark_key), Boolean.getBoolean(context.getString(R.string.settings_dark_default)));
            darkTheme = (darkThemeBool) ? context.getString(R.string.settings_dark_theme_true_value) : context.getString(R.string.settings_dark_theme_false_value);
            SharedPreferences.Editor editor = prefManager.edit();
            editor.putString(context.getString(R.string.settings_dark_key), darkTheme);
            editor.apply();
        }

        if (darkTheme.equals(context.getString(R.string.settings_dark_theme_true_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (darkTheme.equals(context.getString(R.string.settings_dark_theme_false_value))){
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        context.setTheme(R.style.AppTheme);
    }

    /*
     * Get path of the album cover image for the specified directory.
     */
    public static String getImagePath(File dir) {
        // Search only for files that are images
        FilenameFilter imgFilter = (dir1, filename) -> {
            File sel = new File(dir1, filename);
            // Only list files that are readable and images
            return sel.getName().endsWith(".jpg") || sel.getName().endsWith(".jpeg") || sel.getName().endsWith(".png") || sel.getName().endsWith(".webp");
        };

        String[] fileList = dir.list(imgFilter);
        if (fileList == null) return null;
        if (fileList.length > 0) {
            // No way of knowing which image is the correct one, so simply choose one.
            return new File(dir + File.separator + fileList[0]).getAbsolutePath();
        }
        return null;
    }


    /*
     * Bring milli seconds into a proper time format.
     * Taken from http://techin-android.blogspot.com/2012/01/millisecond-to-hhmmss-format-convertor.html
     */
    public static String formatTime(long millis, long fullTime) {
        String output;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        long hoursFullTime = fullTime / (60 * 60 * 1000);

        String secondsD = String.valueOf(seconds);
        String minutesD = String.valueOf(minutes);
        String hoursD = String.valueOf(hours);

        if (seconds < 10)
            secondsD = "0" + seconds;
        if (minutes < 10)
            minutesD = "0" + minutes;
        if (hours < 10)
            hoursD = "0" + hours;

        if (hours > 0 || hoursFullTime > 0) {
            output = hoursD + ":" + minutesD + ":" + secondsD;
        } else {
            output = minutesD + ":" + secondsD;
        }
        return output;
    }


    /*
     * Turn a time string into the number of milliseconds
     */
    public static long getMillisFromString(String time) {
        long millis;
        String[] timeParts = time.split(":");
        int length = timeParts.length;

        // Return 0 if the string is malformatted
        if (length != 2 && length != 3) {
            throw new NumberFormatException("Illegal time format.");
        }

        for (String part : timeParts) {
            if (part.length() != 2) {
                throw new NumberFormatException("Illegal time format.");
            }
        }

        int seconds = Integer.parseInt(timeParts[length - 1]);
        int minutes = Integer.parseInt(timeParts[length - 2]);
        if (seconds > 59 || minutes > 59) {
            throw new NumberFormatException("Illegal time format.");
        }

        millis = minutes * 60 * 1000 + seconds * 1000;

        if (length == 3) {
            int hours = Integer.parseInt(timeParts[0]);
            millis += hours * 60 * 60 * 1000;
        }

        return millis;
    }

    /*
     * Return the time string as percentage or xx:xx / xx:xx depending on the user preferences
     */
    public static String getTimeString(Context context, int completedTime, int duration) {
        // Check whether to return time string as percentage
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean progressInPercent = prefs.getBoolean(context.getResources().getString(R.string.settings_progress_percentage_key), Boolean.getBoolean(context.getResources().getString(R.string.settings_progress_percentage_default)));

        String timeStr;
        if (progressInPercent) {
            int percent = Math.round(((float) completedTime / duration) * 100);
            timeStr = context.getResources().getString(R.string.time_completed_percent, percent);
        } else {
            String durationStr = Utils.formatTime(duration, duration);
            String completedTimeStr = Utils.formatTime(completedTime, duration);
            timeStr = context.getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        }
        return timeStr;
    }


    public static boolean isMediaPlayerServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MediaPlayerService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean deleteTrack(Context context, AudioFile audioFile, boolean keepDeletedInDB) {
        // Delete track from file system
        if (audioFile == null) {
            return false;
        }

        File file = new File(audioFile.getPath());
        boolean deleted = file.delete();

        if (deleted) {
            // Delete track from the database if keep_deleted is false
            if (!keepDeletedInDB) {
                DBAccessUtils.deleteTrackFromDB(context, audioFile.getID());
            }
            return true;
        }
        return false;
    }

    /*
     * Recursively delete a file from the file system
     */
    public static boolean deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursively(child);
        return fileOrDirectory.delete();
    }
}
