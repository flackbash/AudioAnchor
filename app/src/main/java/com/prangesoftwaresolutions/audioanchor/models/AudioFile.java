package com.prangesoftwaresolutions.audioanchor.models;

import java.io.File;
import java.io.Serializable;

public class AudioFile implements Serializable {

    private int mId;
    private String mTitle;
    private int mAlbumId;
    private int mTime;
    private int mCompletedTime;
    private String mPath;
    private String mAlbumTitle;
    private String mCoverPath;

    public AudioFile(int id, String title, int albumId, int time, int completedTime, String albumTitle, String coverPath, String baseDirectory) {
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

    public int getId() {
        return mId;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getAlbumId() {
        return mAlbumId;
    }

    public int getTime() {
        return mTime;
    }

    public int getCompletedTime() {
        return mCompletedTime;
    }

    public String getPath() {
        return mPath;
    }

    public String getAlbumTitle() {
        return mAlbumTitle;
    }

    public String getCoverPath() {
        return mCoverPath;
    }

    public void setCompletedTime(int mCompletedTime) {
        this.mCompletedTime = mCompletedTime;
    }
}
