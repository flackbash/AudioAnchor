package com.prangesoftwaresolutions.audioanchor.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Audio Database Helper class
 */

public class AudioDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "audio_anchor.db";

    // Database version. Must be incremented when the database schema is changed.
    private static final int DATABASE_VERSION = 1;

    AudioDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create a String that contains the SQL statement to create the vocab table
        String SQL_CREATE_VOCAB_TABLE = "CREATE TABLE " + AudioContract.AudioEntry.TABLE_NAME + " ("
                + AudioContract.AudioEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + AudioContract.AudioEntry.COLUMN_AUDIO_TITLE + " TEXT NOT NULL, "
                + AudioContract.AudioEntry.COLUMN_AUDIO_TIME + " INTEGER, "
                + AudioContract.AudioEntry.COLUMN_AUDIO_COMPLETED_TIME + " INTEGER);";
        db.execSQL(SQL_CREATE_VOCAB_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
