package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

import com.prangesoftwaresolutions.audioanchor.BuildConfig;

/**
 * Audio Contract specifying tables, columns and other constants related to the database
 */

public class AudioContract {
    // Content constants
    static final String CONTENT_AUTHORITY = BuildConfig.APPLICATION_ID;
    static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    static final String PATH_AUDIO = "audio";
    static final String PATH_AUDIO_DISTINCT = "audio_distinct";

    // Class for the Recipe Table
    public static abstract class AudioEntry implements BaseColumns {
        // Content URI for the recipe table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_AUDIO);
        public static final Uri CONTENT_URI_DISTINCT = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_AUDIO_DISTINCT);


        // The MIME type of the CONTENT_URI for a list of audios.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO;

        // The MIME type of the CONTENT_URI for a single audio.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO;

        public static final String TABLE_NAME = "audios";

        // The Columns
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_AUDIO_TITLE = "title";
        public static final String COLUMN_AUDIO_TIME = "time";
        public static final String COLUMN_AUDIO_COMPLETED_TIME = "completed_time";
    }
}
