package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;

class AudioFile implements Serializable {

    private int mId;
    private String mTitle;
    private int mAlbumId;
    private int mTime;
    private int mCompletedTime;
    private String mPath;
    private String mAlbumTitle;
    private String mCoverPath;

    private AudioFile(int id, String title, int albumId, int time, int completedTime, String albumTitle, String coverPath, String baseDirectory) {
        mId = id;
        mTitle = title;
        mAlbumId = albumId;
        mTime = time;
        mCompletedTime = completedTime;
        mAlbumTitle = albumTitle;
        if (coverPath != null) {
            mCoverPath = baseDirectory + File.separator + coverPath;
        } else {
            mCoverPath = null;
        }
        mPath = baseDirectory + File.separator + mAlbumTitle + File.separator + mTitle;
    }

    int getId() {
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
     * Get the current audio file
     */
    static AudioFile getAudioFile(Context context, Uri AudioFileAlbumUri, String baseDirectory) {
        String[] projection = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_COVER_PATH};
        Cursor c = context.getContentResolver().query(AudioFileAlbumUri, projection, null, null, null);

        if (c == null) {
            throw new SQLException();
        } else if (c.getCount() < 1) {
            c.close();
            throw new SQLException();
        }

        AudioFile audioFile;
        if (c.moveToFirst()) {
            int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            int albumId = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_ALBUM));
            int completedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            int time = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
            String albumTitle = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_TITLE));
            String albumCoverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
            audioFile =  new AudioFile(id, title, albumId, time, completedTime, albumTitle, albumCoverPath, baseDirectory);
        } else {
            c.close();
            throw new SQLException();
        }
        c.close();
        return audioFile;
    }

    static ArrayList<AudioFile> getAllAudioFilesFromAlbum(Context context, int albumId, String sortOrder, String baseDirectory) {
        String[] projection = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_COVER_PATH};

        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor c = context.getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI_AUDIO_ALBUM, projection, sel, selArgs, sortOrder);

        if (c == null) {
            throw new SQLException();
        } else if (c.getCount() < 1) {
            c.close();
            throw new SQLException();
        }

        ArrayList<AudioFile> audioFiles = new ArrayList<>();
        if (c.moveToFirst()) {
            do {
                int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
                String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
                int completedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
                int time = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
                String albumTitle = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_TITLE));
                String albumCoverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
                audioFiles.add(new AudioFile(id, title, albumId, time, completedTime, albumTitle, albumCoverPath, baseDirectory));
            } while (c.moveToNext());

        } else {
            c.close();
            throw new SQLException();
        }
        c.close();
        return audioFiles;
    }

    static int getIndex(ArrayList<AudioFile> audioList, int id) {
        for(int i = 0; i < audioList.size(); i++) {
            AudioFile audioFile = audioList.get(i);
            if(audioFile.getId() == id) {
                return i;
            }
        }
        return -1;
    }
}