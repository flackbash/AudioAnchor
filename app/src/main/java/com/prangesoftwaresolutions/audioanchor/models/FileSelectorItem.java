package com.prangesoftwaresolutions.audioanchor.models;

import androidx.annotation.NonNull;

import com.prangesoftwaresolutions.audioanchor.R;

public class FileSelectorItem implements Comparable<FileSelectorItem> {

    public enum Type {
        BACK,
        DIRECTORY,
        FILE
    }

    String mText;
    int mIconId;
    Type mType;

    public FileSelectorItem(String text, Type type) {
        mText = text;
        mType = type;
        switch (type) {
            case BACK:
                mIconId = R.drawable.ic_back_grey;
                break;
            case DIRECTORY:
                mIconId = R.drawable.ic_directory_grey;
                break;
            default:
                mIconId = R.drawable.ic_file_grey;
                break;
        }
    }

    @Override
    public String toString() {
        return mText;
    }

    public int getIconId() {
        return mIconId;
    }

    @Override
    public int compareTo(@NonNull FileSelectorItem o) {
        int c = this.mType.compareTo(o.mType);
        if (c == 0) c = this.mText.toLowerCase().compareTo(o.mText.toLowerCase());
        return c;
    }
}
