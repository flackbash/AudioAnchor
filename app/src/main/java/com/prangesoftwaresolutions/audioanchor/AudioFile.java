package com.prangesoftwaresolutions.audioanchor;

import java.io.File;
import java.io.Serializable;

class AudioFile implements Serializable {

    private int mId;
    private String mTitle;
    private int mAlbumId;
    private int mTime;
    private int mCompletedTime;
    private String mPath;
    private String mAlbumTitle;
    private String mCoverPath;

    AudioFile(int id, String title, int albumId, int time, int completedTime, String albumTitle, String coverPath, String baseDirectory) {
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
}