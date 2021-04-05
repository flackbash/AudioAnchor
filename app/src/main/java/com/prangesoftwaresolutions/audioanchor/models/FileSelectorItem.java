package com.prangesoftwaresolutions.audioanchor.models;

public class FileSelectorItem{
    String mText;
    int mIconId;

    public FileSelectorItem(String text, int iconId) {
        mText = text;
        mIconId = iconId;
    }

    @Override
    public String toString() {
        return mText;
    }

    public int getIconId() {
        return mIconId;
    }
}
