package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Audio Database Helper class
 */

public class AnchorDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "audio_anchor.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 3;

    private static AnchorDbHelper mInstance = null;

    AnchorDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create a String that contains the SQL statement to create the audio file table
        String SQL_CREATE_AUDIO_FILE_TABLE = "CREATE TABLE " + AnchorContract.AudioEntry.TABLE_NAME + " ("
                + AnchorContract.AudioEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.AudioEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.AudioEntry.COLUMN_ALBUM + " TEXT NOT NULL, "
                + AnchorContract.AudioEntry.COLUMN_PATH + " TEXT NOT NULL, "
                + AnchorContract.AudioEntry.COLUMN_TIME + " INTEGER DEFAULT 0, "
                + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME + " INTEGER DEFAULT 0);";

        // Create a String that contains the SQL statement to create the album table
        String SQL_CREATE_ALBUM_TABLE = "CREATE TABLE " + AnchorContract.AlbumEntry.TABLE_NAME + " ("
                + AnchorContract.AlbumEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.AlbumEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.AlbumEntry.COLUMN_COVER_PATH + " TEXT, "
                + AnchorContract.AlbumEntry.COLUMN_BASE_DIR + " TEXT NOT NULL, "
                + AnchorContract.AlbumEntry.COLUMN_ALBUM_SHOWN + " INTEGER);";

        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE " + AnchorContract.BookmarkEntry.TABLE_NAME + " ("
                + AnchorContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";

        // Create a String that contains the SQL statement to create the directories table
        String SQL_CREATE_DIRECTORIES_TABLE = "CREATE TABLE " + AnchorContract.DirectoryEntry.TABLE_NAME + " ("
                + AnchorContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.DirectoryEntry.COLUMN_DIRECTORY + " TEXT NOT NULL, "
                + AnchorContract.DirectoryEntry.COLUMN_DIR_SHOWN + " INTEGER);";



        db.execSQL(SQL_CREATE_AUDIO_FILE_TABLE);
        db.execSQL(SQL_CREATE_ALBUM_TABLE);
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        db.execSQL(SQL_CREATE_DIRECTORIES_TABLE);
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


        // Create a String that contains the SQL statement to create the directories table
        String SQL_CREATE_DIRECTORIES_TABLE = "CREATE TABLE " + AnchorContract.DirectoryEntry.TABLE_NAME + " ("
                + AnchorContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.DirectoryEntry.COLUMN_DIRECTORY + " TEXT NOT NULL, "
                + AnchorContract.DirectoryEntry.COLUMN_DIR_SHOWN + " INTEGER);";
        db.execSQL(SQL_CREATE_DIRECTORIES_TABLE);

/*
        String SQL_ADD_COLUMN_TO_ALBUM_TABLE = "ALTER TABLE " + AnchorContract.AlbumEntry.TABLE_NAME +
                " ADD COLUMN " + AnchorContract.AlbumEntry.COLUMN_BASE_DIR_ID + " INTEGER;";

        db.execSQL(SQL_ADD_COLUMN_TO_ALBUM_TABLE);
*/
    }

    static AnchorDbHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AnchorDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }
}
