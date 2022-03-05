package com.prangesoftwaresolutions.audioanchor.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.util.ArrayList;

public class Bookmark {
    private long mID = -1;
    private String mTitle;
    private long mPosition;
    private final long mAudioFileID;

    private static final String[] mBookmarkColumns = new String[]{
            AnchorContract.BookmarkEntry._ID,
            AnchorContract.BookmarkEntry.COLUMN_TITLE,
            AnchorContract.BookmarkEntry.COLUMN_POSITION,
            AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE
    };

    public Bookmark(long id, String title, long position, long audioFileID) {
        mID = id;
        mTitle = title;
        mPosition = position;
        mAudioFileID = audioFileID;
    }

    public Bookmark(String title, long position, long audioFileID) {
        mTitle = title;
        mPosition = position;
        mAudioFileID = audioFileID;
    }

    public long getID() {
        return mID;
    }

    public String getTitle() {
        return mTitle;
    }

    public long getPosition() { return mPosition; }

    public void setTitle(String title) { mTitle = title; }

    public void setPosition(long position) { mPosition = position; }

    /*
     * Insert bookmark into database
     */
    public long insertIntoDB(Context context) {
        ContentValues values = getContentValues();
        Uri uri = context.getContentResolver().insert(AnchorContract.BookmarkEntry.CONTENT_URI, values);

        if (uri == null) {
            return -1;
        }

        mID = ContentUris.parseId(uri);
        return mID;
    }

    /*
     * Update bookmark in database
     */
    public void updateInDB(Context context) {
        if (mID == -1) {
            return ;
        }
        Uri uri = ContentUris.withAppendedId(AnchorContract.BookmarkEntry.CONTENT_URI, mID);
        ContentValues values = getContentValues();
        context.getContentResolver().update(uri, values, null, null);
    }

    /*
     * Put bookmark column values into content values
     */
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.BookmarkEntry.COLUMN_TITLE, mTitle);
        values.put(AnchorContract.BookmarkEntry.COLUMN_POSITION, mPosition);
        values.put(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE, mAudioFileID);
        return values;
    }


    /*
     * Retrieve bookmark with given ID from database
     */
    static public Bookmark getBookmarkByID(Context context, long id) {
        Uri uri = ContentUris.withAppendedId(AnchorContract.BookmarkEntry.CONTENT_URI, id);
        Cursor c = context.getContentResolver().query(uri, mBookmarkColumns, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        Bookmark bookmark = null;
        if (c.moveToNext()) {
            bookmark = getBookmarkFromPositionedCursor(c);
        }
        c.close();

        return bookmark;
    }

    /*
     * Retrieve bookmark with the given title for the given audio file from database
     */
    static public Bookmark getBookmarkForAudioFileByTitle(Context context, String title, long audioFileID) {
        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=? AND " + AnchorContract.BookmarkEntry.COLUMN_TITLE + "=?";
        String[] selArgs = {Long.toString(audioFileID), title};
        Cursor c = context.getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI, mBookmarkColumns, sel, selArgs, null);

        // Bail early if the cursor is null
        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        Bookmark bookmark = null;
        if (c.moveToLast()) {
            // If multiple bookmarks for the same audio file with the same name exist, choose the last one
            bookmark = getBookmarkFromPositionedCursor(c);
        }
        c.close();

        return bookmark;
    }

    /*
     * Delete all bookmarks with the given title from the database
     */
    static public void deleteBookmarksWithTitle(Context context, String title) {
        String sel = AnchorContract.BookmarkEntry.COLUMN_TITLE + "=?";
        String[] selArgs = {title};
        context.getContentResolver().delete(AnchorContract.BookmarkEntry.CONTENT_URI, sel, selArgs);
    }

    /*
     * Get all bookmarks for the given audio file ID
     */
    public static ArrayList<Bookmark> getAllBookmarksForAudioFile(Context context, long audioFileID) {
        ArrayList<Bookmark> bookmarks = new ArrayList<>();
        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
        String[] selArgs = {Long.toString(audioFileID)};

        Cursor c = context.getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI,
                mBookmarkColumns, sel, selArgs, null);

        // Bail early if the cursor is null
        if (c == null) {
            return bookmarks;
        } else if (c.getCount() < 1) {
            c.close();
            return bookmarks;
        }

        while (c.moveToNext()) {
            Bookmark bookmark = getBookmarkFromPositionedCursor(c);
            bookmarks.add(bookmark);
        }
        c.close();

        return bookmarks;
    }

    private static Bookmark getBookmarkFromPositionedCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.BookmarkEntry._ID));
        String title = c.getString(c.getColumnIndexOrThrow(AnchorContract.BookmarkEntry.COLUMN_TITLE));
        long position = c.getLong(c.getColumnIndexOrThrow(AnchorContract.BookmarkEntry.COLUMN_POSITION));
        long audioFileID = c.getLong(c.getColumnIndexOrThrow(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE));

        return new Bookmark(id, title, position, audioFileID);
    }

    /*
     * Get a cursor with all bookmarks for the given audio file
     */
    public static Cursor getBookmarksCursor(Context context, long audioFileID) {
        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
        String[] selArgs = {Long.toString(audioFileID)};
        String sortOrder = AnchorContract.BookmarkEntry.COLUMN_POSITION + " ASC";
        return context.getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI, mBookmarkColumns, sel, selArgs, sortOrder);
    }
}
