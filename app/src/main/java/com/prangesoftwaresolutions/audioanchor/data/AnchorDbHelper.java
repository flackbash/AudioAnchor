package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.Directory;

import java.util.ArrayList;

/**
 * Audio Database Helper class
 */

public class AnchorDbHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "audio_anchor.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 3;

    private static AnchorDbHelper mInstance = null;
    private final Context mContext;

    public AnchorDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
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
                + AnchorContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER, "
                + AnchorContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER, "
                + AnchorContract.AlbumEntry.COLUMN_COVER_PATH + " TEXT);";

        // Create a String that contains the SQL statement to create the bookmark table
        String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE " + AnchorContract.BookmarkEntry.TABLE_NAME + " ("
                + AnchorContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                + AnchorContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                + AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";

        // Create a String that contains the SQL statement to create the directory table
        String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + AnchorContract.DirectoryEntry.TABLE_NAME + " ("
                + AnchorContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AnchorContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                + AnchorContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";

        db.execSQL(SQL_CREATE_AUDIO_FILE_TABLE);
        db.execSQL(SQL_CREATE_ALBUM_TABLE);
        db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        db.execSQL(SQL_CREATE_DIRECTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        if (i < 2) {
            // Create a String that contains the SQL statement to create the bookmark table
            String SQL_CREATE_BOOKMARK_TABLE = "CREATE TABLE IF NOT EXISTS " + AnchorContract.BookmarkEntry.TABLE_NAME + " ("
                    + AnchorContract.BookmarkEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + AnchorContract.BookmarkEntry.COLUMN_TITLE + " TEXT NOT NULL, "
                    + AnchorContract.BookmarkEntry.COLUMN_POSITION + " INTEGER, "
                    + AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + " INTEGER);";
            db.execSQL(SQL_CREATE_BOOKMARK_TABLE);
        }
        if (i < 3) {
            // Create directory table
            String SQL_CREATE_DIRECTORY_TABLE = "CREATE TABLE IF NOT EXISTS " + AnchorContract.DirectoryEntry.TABLE_NAME + " ("
                    + AnchorContract.DirectoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + AnchorContract.DirectoryEntry.COLUMN_PATH + " TEXT NOT NULL, "
                    + AnchorContract.DirectoryEntry.COLUMN_TYPE + " INTEGER);";
            db.execSQL(SQL_CREATE_DIRECTORY_TABLE);

            // Insert current mDirectory from preferences into directory table
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String currentDirPath = prefs.getString(mContext.getString(R.string.preference_filename), "");
            Directory currentDirectory = new Directory(currentDirPath, Directory.Type.PARENT_DIR);
            ContentValues values = currentDirectory.getContentValues();
            // Use direct call to db. Calling Directory.insertIntoDB() yields
            //  java.lang.IllegalStateException: getDatabase called recursively
            long directoryID = db.insert(AnchorContract.DirectoryEntry.TABLE_NAME, null, values);
            currentDirectory.setID(directoryID);

            // Add directory column to album table
            String SQL_ADD_DIRECTORY_COLUMN = "ALTER TABLE " + AnchorContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + AnchorContract.AlbumEntry.COLUMN_DIRECTORY + " INTEGER";
            db.execSQL(SQL_ADD_DIRECTORY_COLUMN);

            // Add column_last_played to album table
            String SQL_ADD_LAST_PLAYED_COLUMN = "ALTER TABLE " + AnchorContract.AlbumEntry.TABLE_NAME + " ADD COLUMN " + AnchorContract.AlbumEntry.COLUMN_LAST_PLAYED + " INTEGER";
            db.execSQL(SQL_ADD_LAST_PLAYED_COLUMN);

            // Populate column_directory in album table
            ArrayList<Album> albums = getAllAlbums(db, currentDirectory);
            for (Album album : albums) {
                ContentValues albumValues = album.getContentValues();
                String selection = AnchorContract.AlbumEntry._ID + "=?";
                String[] selectionArgs = new String[]{String.valueOf(album.getID())};
                db.update(AnchorContract.AlbumEntry.TABLE_NAME, albumValues, selection, selectionArgs);
            }
        }
    }

    static AnchorDbHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AnchorDbHelper(context.getApplicationContext());
        }
        return mInstance;
    }

    /*
     * Get all albums from the database
     */
    private ArrayList<Album> getAllAlbums(SQLiteDatabase db, Directory directory) {
        ArrayList<Album> albums = new ArrayList<>();
        Cursor c = db.query(AnchorContract.AlbumEntry.TABLE_NAME, Album.getColumns(), null, null, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return albums;
        } else if (c.getCount() < 1) {
            c.close();
            return albums;
        }

        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry._ID));
            String title = c.getString(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_TITLE));
            String coverPath = c.getString(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
            long lastPlayed = -1;
            if (!c.isNull(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_LAST_PLAYED))) {
                lastPlayed = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_LAST_PLAYED));
            }
            Album album = new Album(id, title, directory, coverPath, lastPlayed);
            albums.add(album);
        }
        c.close();

        return albums;
    }
}
