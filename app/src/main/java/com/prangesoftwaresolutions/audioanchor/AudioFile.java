package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.Serializable;
import java.util.HashMap;

class AudioFile implements Serializable {

    private int mId;
    private String mTitle;
    private int mAlbumId;
    private int mTime;
    private int mCompletedTime;
    private String mPath;
    private String mAlbumTitle;
    private String mCoverPath;

    private AudioFile(int id, String title, int albumId, int time, int completedTime, String path, String albumTitle, String coverPath) {
        mId = id;
        mTitle = title;
        mAlbumId = albumId;
        mTime = time;
        mCompletedTime = completedTime;
        mPath = path;
        mAlbumTitle = albumTitle;
        mCoverPath = coverPath;
    }

    int getmId() {
        return mId;
    }

    String getTitle() {
        return mTitle;
    }

    int getAlbumId() {
        return mAlbumId;
    }

    int getTime() {
        return mTime;
    }

    int getCompletedTime() {
        return mCompletedTime;
    }

    String getPath() {
        return mPath;
    }

    String getAlbumTitle() {
        return mAlbumTitle;
    }

    String getCoverPath() {
        return mCoverPath;
    }

    void setTime(int time) {
        mTime = time;
    }

    void setCompletedTime(int mCompletedTime) {
        this.mCompletedTime = mCompletedTime;
    }

    /*
     * Get the title of the current album
     */
    static private String[] getAlbumInfo(Context context, int albumId) {
        String[] proj = {AnchorContract.AlbumEntry.COLUMN_TITLE, AnchorContract.AlbumEntry.COLUMN_COVER_PATH};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor c = context.getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI, proj, sel, selArgs, null);
        if (c == null || c.getCount() < 1) {
            throw new SQLException();
        }

        String[] albumInfo = new String[2];
        if (c.moveToFirst()) {
            albumInfo[0] = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            albumInfo[1] = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
        }
        c.close();
        return albumInfo;
    }

    /*
     * Get the current audio file
     */
    static AudioFile getAudioFile(Context context, Uri AudioFileUri) {
        String[] projection = {
                AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.COLUMN_PATH,
                AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME};
        Cursor c = context.getContentResolver().query(AudioFileUri, projection, null, null, null);

        if (c == null || c.getCount() < 1) {
            throw new SQLException();
        }

        AudioFile audioFile;
        if (c.moveToFirst()) {
            int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            int albumId = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_ALBUM));
            int completedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            int time = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
            String path = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_PATH));
            String[] albumInfo = getAlbumInfo(context, albumId);
            audioFile =  new AudioFile(id, title, albumId, time, completedTime, path, albumInfo[0], albumInfo[1]);
        } else {
            throw new SQLException();
        }
        c.close();
        return audioFile;
    }

    static HashMap<Integer, AudioFile> getAllAudioFilesFromAlbum(Context context, int albumId) {
        String[] projection = {
                AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.COLUMN_PATH,
                AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME};

        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, null);

        if (c == null || c.getCount() < 1) {
            throw new SQLException();
        }

        HashMap<Integer, AudioFile> audioFiles = new HashMap<>();
        if (c.moveToFirst()) {
            // Album info is the same for all files given the query
            String[] albumInfo = getAlbumInfo(context, albumId);
            do {
                int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
                String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
                int completedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
                int time = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
                String path = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_PATH));
                audioFiles.put(id, new AudioFile(id, title, albumId, time, completedTime, path, albumInfo[0], albumInfo[1]));
            } while (c.moveToNext());

        } else {
            throw new SQLException();
        }
        c.close();
        return audioFiles;
    }
}