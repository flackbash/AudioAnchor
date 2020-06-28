package com.prangesoftwaresolutions.audioanchor.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Audio Database Helper class
 */

public class AnchorDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "audio_anchor.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 2;

    private static AnchorDbHelper mInstance = null;

    private AnchorDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create a String that contains the SQL statement to create the audio file table
        String SQL_CREATE_AUDIO_FILE_TABLE = "CREATE TABLE " + AnchorContract.AudioEntry.TABLE_NAME + " ("
                + AnchorContract.AudioEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.AudioEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.AudioEntry.COLUMN_ALBUM + " TEXT NOT NULL, "
                + AnchorContract.AudioEntry.COLUMN_PATH + " TEXT, "
                + AnchorContract.AudioEntry.COLUMN_TIME + " INTEGER DEFAULT 0, "
                + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME + " INTEGER DEFAULT 0);";

        // Create a String that contains the SQL statement to create the album table
        String SQL_CREATE_ALBUM_TABLE = "CREATE TABLE " + AnchorContract.AlbumEntry.TABLE_NAME + " ("
                + AnchorContract.AlbumEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.AlbumEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.AlbumEntry.COLUMN_COVER_PATH + " TEXT);";

        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE " + AnchorContract.BookmarkEntry.TABLE_NAME + " ("
                + AnchorContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";

        db.execSQL(SQL_CREATE_AUDIO_FILE_TABLE);
        db.execSQL(SQL_CREATE_ALBUM_TABLE);
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE IF NOT EXISTS " + AnchorContract.BookmarkEntry.TABLE_NAME + " ("
                + AnchorContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
    }

    static AnchorDbHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AnchorDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }
}
