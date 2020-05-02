package com.prangesoftwaresolutions.audioanchor;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;

class Synchronizer {
    private Context mContext;
    private SharedPreferences mPrefManager;
    private File mDirectory;

    Synchronizer(Context context) {
        mContext = context;
        mPrefManager = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /*
     * Is called when synchronization is finished and should be overwritten in executing activity
     */
    public void finish() {
    }

    /*
     * Update the album database table if the list of directories in the selected directory does not
     * match the album table entries
     */
     void updateDBTables() {
        String dirString = mPrefManager.getString(mContext.getString(R.string.preference_filename), null);
        mDirectory = null;
        if (dirString != null) {
            mDirectory = new File(dirString);
        }

        // Get all subdirectories of the selected audio storage directory.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);
            // Only list files that are readable and directories and not hidden unless corresponding option is set
            boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
            return sel.canRead() && sel.isDirectory() && (showHidden || !sel.getName().startsWith("."));
        };

        String[] directoryList;
        if (mDirectory != null && mDirectory.isDirectory()) {
            directoryList = mDirectory.list(filter);
            if (directoryList == null) {
                directoryList = new String[]{};
            }
        } else {
            directoryList = new String[]{};
        }

        LinkedHashMap<String, Integer> albumTitles = getAlbumTitles();

        // Insert new directories into the database
        for (String dirTitle : directoryList) {
            long id;
            if (!albumTitles.containsKey(dirTitle)) {
                id = insertAlbum(dirTitle);
            } else {
                id = albumTitles.get(dirTitle);
                updateAlbumCover(id, dirTitle);
                albumTitles.remove(dirTitle);
            }
            updateAudioFileTable(dirTitle, id);
        }

        // Delete missing or hidden directories directories from the database
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        for (String title : albumTitles.keySet()) {
            if (!keepDeleted || (!showHidden && title.startsWith("."))) {
                int id = albumTitles.get(title);
                // Stop MediaPlayerService if the currently playing file is from deleted directory
                /*
                if (mPlayer != null) {
                    int activeAlbumId = mPlayer.getCurrentAudioFile().getAlbumId();
                    if (activeAlbumId == id) {
                        mPlayer.stopMedia();
                        mPlayer.stopSelf();
                    }
                }
                */
                // Delete the album in the albums table
                Uri uri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
                // Delete all audios from the album in the audio_files table
                String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
                String[] selArgs = {Long.toString(id)};
                mContext.getContentResolver().delete(AnchorContract.AudioEntry.CONTENT_URI, sel, selArgs);
            }
        }
        finish();
    }

    /*
     * Update the audiofiles table if the list of audio files in the album directory does not
     * match the audiofiles table entries
     */
     private void updateAudioFileTable(String albumDirName, long albumId) {
        // Get all audio files in the album.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);

            // Don't show files starting with a dot (hidden files) unless the option is set

            boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
            if (!showHidden && sel.getName().startsWith(".")) {
                return false;
            }

            // Only list files that are readable and audio files
            String[] supportedFormats = {".mp3", ".wma", ".ogg", ".wav", ".flac", ".m4a", ".m4b", ".aac", ".3gp", ".gsm", ".mid", ".mkv"};
            for (String format : supportedFormats) {
                if (sel.getName().endsWith(format)) return true;
            }
            return false;
        };

        // Get all files in the album directory.
        String[] fileList;
        File albumDir = new File(mDirectory + File.separator + albumDirName);

        if (albumDir.exists()) {
            fileList = albumDir.list(filter);
        } else {
            fileList = new String[]{};
        }

        if (fileList == null) return;

        LinkedHashMap<String, Integer> audioTitles = getAudioFileTitles(albumId);

        // Insert new files into the database
        boolean success = true;
        String errorString = "";

        for (String audioFileName : fileList) {
            if (!audioTitles.containsKey(audioFileName)) {
                success = insertAudioFile(audioFileName, albumDirName, albumId);
                if (!success) errorString = albumDirName + "/" + audioFileName;
            } else {
                audioTitles.remove(audioFileName);
            }
        }
        if (!success) {
            errorString = mContext.getResources().getString(R.string.audio_file_error, errorString);
            Toast.makeText(mContext.getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete missing or hidden audio files from the database
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        for (String title : audioTitles.keySet()) {
            if (!keepDeleted || (!showHidden && title.startsWith("."))) {
                Integer id = audioTitles.get(title);
                // Stop MediaPlayerService if the currently playing file is from deleted directory
                /*
                if (mPlayer != null) {
                    int activeAudioId = mPlayer.getCurrentAudioFile().getId();
                    if (activeAudioId == id) {
                        mPlayer.stopMedia();
                        mPlayer.stopSelf();
                    }
                }
                */
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
    }

    /*
     * Insert a new row into the audiofiles table
     */
     private boolean insertAudioFile(String title, String albumDirName, long albumId) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_TITLE, title);
        values.put(AnchorContract.AudioEntry.COLUMN_ALBUM, albumId);

        // Retrieve audio duration from Metadata.
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        try {
            String audioFilePath = mDirectory + File.separator + albumDirName + File.separator + title;
            metaRetriever.setDataSource(audioFilePath);
            String duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            values.put(AnchorContract.AudioEntry.COLUMN_TIME, Long.parseLong(duration));
            metaRetriever.release();
            // Insert the row into the database table
            mContext.getContentResolver().insert(AnchorContract.AudioEntry.CONTENT_URI, values);
        } catch (java.lang.RuntimeException e) {
            return false;
        }

        return true;
    }

    /**
     * Retrieve all audio file titles from the database
     */
     private LinkedHashMap<String, Integer> getAudioFileTitles(long albumId) {
        String[] columns = new String[]{AnchorContract.AudioEntry._ID, AnchorContract.AudioEntry.COLUMN_TITLE};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumId)};

        Cursor c = mContext.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                columns, sel, selArgs, null, null);

        LinkedHashMap<String, Integer> titles = new LinkedHashMap<>();

        // Bail early if the cursor is null
        if (c == null) {
            return titles;
        }

        // Loop through the database rows and add the audio file titles to the hashmap
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }

    /*
     * Insert a new row into the albums table
     */
     private long insertAlbum(String title) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AlbumEntry.COLUMN_TITLE, title);
        Uri uri = mContext.getContentResolver().insert(AnchorContract.AlbumEntry.CONTENT_URI, values);
        updateAlbumCover(ContentUris.parseId(uri), title);
        return ContentUris.parseId(uri);
    }

    /*
     * Update the cover path in the albums table
     */
     private void updateAlbumCover(long albumId, String title) {
        // Get the previous cover path
        String oldCoverPath = null;
        String[] proj = new String[]{AnchorContract.AlbumEntry.COLUMN_COVER_PATH};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor c = mContext.getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                proj, sel, selArgs, null);
        if (c == null || c.getCount() < 1) {
            return;
        }
        if (c.moveToFirst()) {
            oldCoverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
        }
        c.close();

        if (oldCoverPath == null || !(new File(mDirectory.getAbsolutePath() + File.separator + oldCoverPath).exists())) {
            // Search for a cover in the album directory
            File albumDir = new File(mDirectory.getAbsolutePath() + File.separator + title);
            String coverPath = Utils.getImagePath(albumDir);
            if (coverPath != null) {
                coverPath = coverPath.replace(mDirectory.getAbsolutePath(), "");
            }

            // Update the album cover path in the albums table
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AlbumEntry.COLUMN_COVER_PATH, coverPath);
            mContext.getContentResolver().update(AnchorContract.AlbumEntry.CONTENT_URI, values, sel, selArgs);
        }
    }

    /**
     * Retrieve all album titles from the database
     */
     private LinkedHashMap<String, Integer> getAlbumTitles() {
        String[] proj = new String[]{AnchorContract.AlbumEntry._ID, AnchorContract.AlbumEntry.COLUMN_TITLE};
        Cursor c = mContext.getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                proj, null, null, null);

        LinkedHashMap<String, Integer> titles = new LinkedHashMap<>();

        // Bail early if the cursor is null
        if (c == null) {
            return titles;
        }

        // Loop through the database rows and add the album titles to the HashMap
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            Integer id = c.getInt(c.getColumnIndex(AnchorContract.AlbumEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }

}
