package com.prangesoftwaresolutions.audioanchor.utils;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;

public class DBAccessUtils {

    /*
     * Get the completion time and the duration of the album with the given id
     */
    public static int[] getAlbumTimes(Context context, long albumID) {
        // Query the database for the track completion times
        String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, AnchorContract.AudioEntry.COLUMN_TIME};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumID)};
        int[] times = new int[2];
        int sumDuration = 0;
        int sumCompletedTime = 0;
        try (Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                columns, sel, selArgs, null, null)) {

            // Loop through the database rows and sum up the audio durations and completed times
            while (moveToNext(c)) {
                sumDuration += c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME));
                sumCompletedTime += c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            }

        }

        times[0] = sumCompletedTime;
        times[1] = sumDuration;
        return times;
    }

    public static boolean moveToNext(Cursor cursor) {
        boolean hasNext = false;
        if (cursor != null && cursor.getCount() > 0) {
            hasNext = cursor.moveToNext();
        }
        return hasNext;
    }

    public static boolean moveToLast(Cursor cursor) {
        boolean hasNext = false;
        if (cursor != null && cursor.getCount() > 0) {
            hasNext = cursor.moveToLast();
        }
        return hasNext;
    }


    /*
     * Delete track with the specified id from the database
     */
    public static boolean deleteTrackFromDB(Context context, long trackId) {
        Uri deleteUri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);

        // Don't allow delete action if the track still exists
        AudioFile audio = AudioFile.getAudioFileById(context, trackId);
        if (audio != null && !(new File(audio.getPath())).exists()) {
            // Delete track from database
            context.getContentResolver().delete(deleteUri, null, null);
            return true;
        }
        return false;
    }


    /*
     * Delete album with the specified id from the database
     */
    public static boolean deleteAlbumFromDB(Context context, long albumId) {
        // Get the title of the album to check if the album still exists in the file system
        Uri uri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, albumId);
        String[] proj = new String[]{AnchorContract.AlbumEntry.COLUMN_TITLE};
        String title = null;
        try (Cursor c = context.getContentResolver().query(uri, proj, null, null, null)) {
            if (moveToNext(c)) {
                title = c.getString(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_TITLE));
            }
        }

        if (title == null) return false;

        // Don't allow delete action if the album still exists
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String directory = prefs.getString(context.getResources().getString(R.string.preference_filename), null);
        if (!(new File(directory, title)).exists()) {
            // Delete album from database
            context.getContentResolver().delete(uri, null, null);
            return true;
        }
        return false;
    }


    /*
     * Mark track with the specified id as not started, i.e. set completedTime to 0 in the db
     */
    public static void markTrackAsNotStarted(Context context, long trackId) {
        // Do not mark as not started if the track is currently active in the MediaPlayerService
        StorageUtil storage = new StorageUtil(context);
        if (storage.loadAudioId() == trackId) {
            Toast.makeText(context, R.string.cannot_mark_as_not_started, Toast.LENGTH_SHORT).show();
            return;
        }

        // Mark as not started
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, 0);
        context.getContentResolver().update(uri, values, null, null);
    }


    /*
     * Mark track with the specified id as completed, i.e. set completedTime to totalTime in the db
     */
    public static void markTrackAsCompleted(Context context, long trackId) {
        // Do not mark as completed if the track is currently active in the MediaPlayerService
        StorageUtil storage = new StorageUtil(context);
        if (storage.loadAudioId() == trackId) {
            Toast.makeText(context, R.string.cannot_mark_as_completed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get total time for the specified track
        String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_TIME};
        String sel = AnchorContract.AudioEntry._ID + "=?";
        String[] selArgs = {Long.toString(trackId)};
        int totalTime = 0;

        try (Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                columns, sel, selArgs, null, null)) {

            while (moveToNext(c)) {
                totalTime = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME));
            }
        }

        // Set completedTime to totalTime
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, totalTime);
        context.getContentResolver().update(uri, values, null, null);
    }
}
