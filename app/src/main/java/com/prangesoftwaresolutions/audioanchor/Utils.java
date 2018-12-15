package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Utility class for AudioAnchor
 */

class Utils {

    static String getPath(Context c, String album, String title) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(c);
        String storageDirectory = sharedPref.getString(c.getString(R.string.preference_filename), null);
        File file = new File(storageDirectory + File.separator + album + File.separator + title);
        return file.getAbsolutePath();
    }

    static String getImagePath(File dir) {
        // Search only for files that are images
        FilenameFilter imgFilter = new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                File sel = new File(dir, filename);
                // Only list files that are readable and images
                return sel.getName().endsWith(".jpg") || sel.getName().endsWith(".jpeg") || sel.getName().endsWith(".png");
            }
        };

        String[] fileList = dir.list(imgFilter);
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
    static String formatTime(long millis, long fullTime) {
        String output;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 60;

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

    static long getMillisFromString(String time) {
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

        int seconds = Integer.parseInt(timeParts[length-1]);
        int minutes = Integer.parseInt(timeParts[length-2]);
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
}
