package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

import com.prangesoftwaresolutions.audioanchor.BuildConfig;

/**
 * Audio Contract specifying tables, columns and other constants related to the database
 */

public class AnchorContract {
    // Content constants
    static final String CONTENT_AUTHORITY = BuildConfig.APPLICATION_ID;
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    static final String PATH_AUDIO_FILES = "audio";
    static final String PATH_AUDIO_FILES_DISTINCT = "audio_distinct";

    static final String PATH_ALBUM = "album";
    static final String PATH_ALBUM_DISTINCT = "album_distinct";

    static final String PATH_BOOKMARK = "bookmark";
    static final String PATH_BOOKMARK_DISTINCT = "bookmark_distinct";

    static final String PATH_DIRECTORY = "directory";
    static final String PATH_DIRECTORY_DISTINCT = "directory_distinct";

    // Class for the Audio File Table
    public static abstract class AudioEntry implements BaseColumns {
        // Content URI for the audio table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_AUDIO_FILES);

        // The MIME type of the CONTENT_URI for a list of audios.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO_FILES;

        // The MIME type of the CONTENT_URI for a single audio.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_AUDIO_FILES_DISTINCT;

        public static final String TABLE_NAME = "audio_files";

        // The Columns
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_ALBUM = "album";
        static final String COLUMN_PATH = "path";
        public static final String COLUMN_TIME = "time";
        public static final String COLUMN_COMPLETED_TIME = "completed_time";
    }

    // Class for the Album Table
    public static abstract class AlbumEntry implements BaseColumns {
        // Content URI for the album table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_ALBUM);

        // The MIME type of the CONTENT_URI for a list of albums.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALBUM;

        // The MIME type of the CONTENT_URI for a single album.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_ALBUM_DISTINCT;

        static final String TABLE_NAME = "albums";

        // The Columns
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_COVER_PATH = "cover_path";
        public static final String COLUMN_DIRECTORY = "directory";
        public static final String COLUMN_LAST_PLAYED = "last_played";
    }

    // Class for the Bookmark Table
    public static abstract class BookmarkEntry implements BaseColumns {
        // Content URI for the bookmark table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_BOOKMARK);

        // The MIME type of the CONTENT_URI for a list of bookmarks.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOOKMARK;

        // The MIME type of the CONTENT_URI for a single bookmark.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_BOOKMARK_DISTINCT;

        static final String TABLE_NAME = "bookmarks";

        // The Columns
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_POSITION = "position";
        public static final String COLUMN_AUDIO_FILE = "audio_file";
    }

    // Class for the Directory Table
    public static abstract class DirectoryEntry implements BaseColumns {
        // Content URI for the directory table
        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_DIRECTORY);

        // The MIME type of the CONTENT_URI for a list of directories.
        static final String CONTENT_LIST_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_DIRECTORY;

        // The MIME type of the CONTENT_URI for a single directory.
        static final String CONTENT_ITEM_TYPE =
                ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_DIRECTORY_DISTINCT;

        static final String TABLE_NAME = "directories";

        // The Columns
        public static final String _ID = BaseColumns._ID;
        public static final String COLUMN_PATH = "path";
        public static final String COLUMN_TYPE = "type";
    }
}
