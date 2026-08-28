package com.prangesoftwaresolutions.audioanchor.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

public class AudioFile implements Serializable {

    private long mID = -1;
    private final String mTitle;
    private final Album mAlbum;
    private int mTime;
    private int mCompletedTime;
    private long mDateAdded = -1;
    private long mLastPlayedTimestamp = -1;

    private static final String[] mAudioFileColumns = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_DATE_ADDED,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP
    };

    private AudioFile(Context context, long id, String title, long albumId, int time, int completedTime, long dateAdded, long lastPlayedTimestamp) {
        mID = id;
        mTitle = title;
        mAlbum = Album.getAlbumByID(context, albumId);
        mTime = time;
        mCompletedTime = completedTime;
        mDateAdded = dateAdded;
        mLastPlayedTimestamp = lastPlayedTimestamp;
    }

    private AudioFile(long id, String title, Album album, int time, int completedTime, long dateAdded, long lastPlayedTimestamp) {
        mID = id;
        mTitle = title;
        mAlbum = album;
        mTime = time;
        mCompletedTime = completedTime;
        mDateAdded = dateAdded;
        mLastPlayedTimestamp = lastPlayedTimestamp;
    }

    public AudioFile(Context context, String title, long albumId) {
        mTitle = title;
        mAlbum = Album.getAlbumByID(context, albumId);
        setTimeFromMetadata();
        mCompletedTime = 0;
        mDateAdded = System.currentTimeMillis();
    }

    /*
     * Same as AudioFile(Context, String, long), but for callers that already hold the Album
     * object (e.g. Synchronizer, scanning many files per album) so it skips the redundant
     * Album.getAlbumByID() -> Directory.getDirectoryByID() DB round trips per file.
     */
    public AudioFile(Context context, String title, Album album) {
        mTitle = title;
        mAlbum = album;
        setTimeFromMetadata();
        mCompletedTime = 0;
        mDateAdded = System.currentTimeMillis();
    }

    public long getID() {
        return mID;
    }

    public String getTitle() {
        return mTitle;
    }

    public long getAlbumId() {
        return mAlbum.getID();
    }

    public String getAlbumTitle() {
        return mAlbum.getTitle();
    }

    public Album getAlbum() { return mAlbum; }

    public int getTime() {
        return mTime;
    }

    public int getCompletedTime() {
        return mCompletedTime;
    }

    public long getDateAdded() {
        return mDateAdded;
    }

    public long getLastPlayedTimestamp() {
        return mLastPlayedTimestamp;
    }

    public void setLastPlayedTimestamp(long lastPlayedTimestamp) {
        mLastPlayedTimestamp = lastPlayedTimestamp;
    }

    public String getPath() { return mAlbum.getPath() + File.separator + mTitle; }

    public String getCoverPath() {
        return mAlbum.getCoverPath();
    }

    public void setCompletedTime(int completedTime) {
        mCompletedTime = completedTime;
    }

    /*
     * Retrieve audio file duration from metadata.
     */
    private void setTimeFromMetadata() {
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        try {
            String audioFilePath = mAlbum.getPath() + File.separator + mTitle;
            metaRetriever.setDataSource(audioFilePath);
            String duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mTime = Integer.parseInt(duration);
            metaRetriever.release();
        } catch (java.lang.RuntimeException e) {
            mTime = 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Put audio file column values into content values
     */
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_TITLE, mTitle);
        values.put(AnchorContract.AudioEntry.COLUMN_ALBUM, mAlbum.getID());
        values.put(AnchorContract.AudioEntry.COLUMN_TIME, mTime);
        values.put(AnchorContract.AudioEntry.COLUMN_DATE_ADDED, mDateAdded);
        return values;
    }

    /*
     * Insert audio file into the audio_files table in the database
     */
    public long insertIntoDB(Context context) {
        ContentValues values = getContentValues();
        Uri uri = context.getContentResolver().insert(AnchorContract.AudioEntry.CONTENT_URI, values);

        if (uri == null) {
            return -1;
        }

        mID = ContentUris.parseId(uri);
        return mID;
    }

    /*
     * Retrieve audio file with given ID from database
     */
    public static AudioFile getAudioFileById(Context context, long id) {
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
        Cursor c = context.getContentResolver().query(uri, mAudioFileColumns, null, null, null);

        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        AudioFile audioFile = null;
        if (c.moveToFirst()) {
            audioFile = getAudioFileFromPositionedCursor(context, c);
        }
        c.close();

        return audioFile;
    }

    /*
     * Get all audio files in the given album
     */
    public static ArrayList<AudioFile> getAllAudioFilesInAlbum(Context context, long albumId, String sortOrder) {
        ArrayList<AudioFile> audioFiles = new ArrayList<>();
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumId)};

        Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                mAudioFileColumns, sel, selArgs, sortOrder, null);

        // Bail early if the cursor is null
        if (c == null) {
            return audioFiles;
        } else if (c.getCount() < 1) {
            c.close();
            return audioFiles;
        }

        while (c.moveToNext()) {
            AudioFile audioFile = getAudioFileFromPositionedCursor(context, c);
            audioFiles.add(audioFile);
        }
        c.close();

        return audioFiles;
    }

    /*
     * Same as getAllAudioFilesInAlbum(Context, long, String), but for callers that already hold
     * the Album object (e.g. Synchronizer) so each row skips the redundant
     * Album.getAlbumByID() -> Directory.getDirectoryByID() DB round trips.
     */
    public static ArrayList<AudioFile> getAllAudioFilesInAlbum(Context context, Album album, String sortOrder) {
        ArrayList<AudioFile> audioFiles = new ArrayList<>();
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(album.getID())};

        Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                mAudioFileColumns, sel, selArgs, sortOrder, null);

        // Bail early if the cursor is null
        if (c == null) {
            return audioFiles;
        } else if (c.getCount() < 1) {
            c.close();
            return audioFiles;
        }

        while (c.moveToNext()) {
            AudioFile audioFile = getAudioFileFromPositionedCursor(c, album);
            audioFiles.add(audioFile);
        }
        c.close();

        return audioFiles;
    }

    /*
     * Create an Audio File from a cursor that is already at the correct position
     */
    private static AudioFile getAudioFileFromPositionedCursor(Context context, Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID));
        String title = c.getString(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TITLE));
        long albumId = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_ALBUM));
        int completedTime = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
        int time = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME));
        long dateAdded = -1;
        int dateAddedIndex = c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_DATE_ADDED);
        if (dateAddedIndex != -1 && !c.isNull(dateAddedIndex)) {
            dateAdded = c.getLong(dateAddedIndex);
        }
        long lastPlayedTimestamp = -1;
        int lastPlayedTimestampIndex = c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP);
        if (lastPlayedTimestampIndex != -1 && !c.isNull(lastPlayedTimestampIndex)) {
            lastPlayedTimestamp = c.getLong(lastPlayedTimestampIndex);
        }
        return new AudioFile(context, id, title, albumId, time, completedTime, dateAdded, lastPlayedTimestamp);
    }

    /*
     * Same as getAudioFileFromPositionedCursor(Context, Cursor), but for a caller that already
     * knows the row's Album.
     */
    private static AudioFile getAudioFileFromPositionedCursor(Cursor c, Album album) {
        long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID));
        String title = c.getString(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TITLE));
        int completedTime = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
        int time = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME));
        long dateAdded = -1;
        int dateAddedIndex = c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_DATE_ADDED);
        if (dateAddedIndex != -1 && !c.isNull(dateAddedIndex)) {
            dateAdded = c.getLong(dateAddedIndex);
        }
        long lastPlayedTimestamp = -1;
        int lastPlayedTimestampIndex = c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP);
        if (lastPlayedTimestampIndex != -1 && !c.isNull(lastPlayedTimestampIndex)) {
            lastPlayedTimestamp = c.getLong(lastPlayedTimestampIndex);
        }
        return new AudioFile(id, title, album, time, completedTime, dateAdded, lastPlayedTimestamp);
    }
}
