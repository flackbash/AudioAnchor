package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

/**
 * Content Provider for audio_anchor app
 */

public class AnchorProvider extends ContentProvider {


    public static final String LOG_TAG = AnchorProvider.class.getSimpleName();

    // AnchorDbHelper instance to gain access to the audios database
    private AnchorDbHelper mDbHelper;

    private static final int AUDIO = 100;
    private static final int AUDIO_ID = 101;
    private static final int AUDIO_DISTINCT = 110;

    private static final int ALBUM = 200;
    private static final int ALBUM_ID = 201;
    private static final int ALBUM_DISTINCT = 210;

    private static final int BOOKMARK = 300;
    private static final int BOOKMARK_ID = 301;
    private static final int BOOKMARK_DISTINCT = 310;

    private static final int DIRECTORY = 500;
    private static final int DIRECTORY_ID = 501;
    private static final int DIRECTORY_DISTINCT = 510;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // URIs for the audio files table
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_AUDIO_FILES, AUDIO);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_AUDIO_FILES + "/#", AUDIO_ID);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_AUDIO_FILES_DISTINCT, AUDIO_DISTINCT);
        // URIs for the albums table
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_ALBUM, ALBUM);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_ALBUM + "/#", ALBUM_ID);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_ALBUM_DISTINCT, ALBUM_DISTINCT);
        // URIs for the bookmarks table
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_BOOKMARK, BOOKMARK);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_BOOKMARK + "/#", BOOKMARK_ID);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_BOOKMARK_DISTINCT, BOOKMARK_DISTINCT);
        // URIs for the directory table
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_DIRECTORY, DIRECTORY);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_DIRECTORY + "/#", DIRECTORY_ID);
        sUriMatcher.addURI(AnchorContract.CONTENT_AUTHORITY, AnchorContract.PATH_DIRECTORY_DISTINCT, DIRECTORY_DISTINCT);
    }

    /**
     * Initialize the provider and the database helper object.
     */
    @Override
    public boolean onCreate() {
        // Create and initialize a AnchorDbHelper object to gain access to the audios database.
        mDbHelper = AnchorDbHelper.getInstance(getContext());
        return true;
    }

    /**
     * Perform the query for the given URI with given parameters.
     */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        // Get readable database
        SQLiteDatabase database = mDbHelper.getReadableDatabase();
        SQLiteQueryBuilder qb;
        // This cursor will hold the result of the query
        Cursor cursor;

        // Figure out if the URI matcher can match the URI to a specific code
        int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Query the audio files table with the given parameters
                cursor = database.query(AnchorContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case ALBUM:
                // Query the album table with the given parameters
                cursor = database.query(AnchorContract.AlbumEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case BOOKMARK:
                // Query the bookmarks table with the given parameters
                cursor = database.query(AnchorContract.BookmarkEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case DIRECTORY:
                // Query the directories table with the given parameters
                cursor = database.query(AnchorContract.DirectoryEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case AUDIO_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(AnchorContract.AudioEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case ALBUM_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(AnchorContract.AlbumEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case BOOKMARK_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(AnchorContract.BookmarkEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case DIRECTORY_DISTINCT:
                qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(AnchorContract.DirectoryEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case AUDIO_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case ALBUM_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.AlbumEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case BOOKMARK_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.BookmarkEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case DIRECTORY_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.DirectoryEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.DirectoryEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
        }
        // Set notificationUri on the cursor so we know what uri the cursor was created for.
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    /**
     * Returns the MIME type of data for the content URI.
     */
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                return AnchorContract.AudioEntry.CONTENT_LIST_TYPE;
            case ALBUM:
                return AnchorContract.AlbumEntry.CONTENT_LIST_TYPE;
            case BOOKMARK:
                return AnchorContract.BookmarkEntry.CONTENT_LIST_TYPE;
            case DIRECTORY:
                return AnchorContract.DirectoryEntry.CONTENT_LIST_TYPE;
            case AUDIO_ID:
                return AnchorContract.AudioEntry.CONTENT_ITEM_TYPE;
            case ALBUM_ID:
                return AnchorContract.AlbumEntry.CONTENT_ITEM_TYPE;
            case BOOKMARK_ID:
                return AnchorContract.BookmarkEntry.CONTENT_ITEM_TYPE;
            case DIRECTORY_ID:
                return AnchorContract.DirectoryEntry.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }

    /**
     * Insert new data into the provider with the given ContentValues.
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                return insertAudioFile(uri, contentValues);
            case ALBUM:
                return insertAlbum(uri, contentValues);
            case BOOKMARK:
                return insertBookmark(uri, contentValues);
            case DIRECTORY:
                return insertDirectory(uri, contentValues);
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    /**
     * Insert new audio file with the given ContentValues into the database.
     */
    private Uri insertAudioFile(Uri uri, ContentValues values) {
        // Sanity check values
        if (!isValidAudioFileEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // dirty hack since older tables where created with COLUMN_PATH not null
        values.put(AnchorContract.AudioEntry.COLUMN_PATH, "");

        long id = db.insert(AnchorContract.AudioEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Notify all listeners that the data at the given URI has changed
        getContext().getContentResolver().notifyChange(uri, null);

        // Return the new URI with the appended ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Insert new audio file with the given ContentValues into the database.
     */
    private Uri insertAlbum(Uri uri, ContentValues values) {
        // Sanity check values
        if (!isValidAlbumEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(AnchorContract.AlbumEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Notify all listeners that the data at the given URI has changed
        getContext().getContentResolver().notifyChange(uri, null);

        // Return the new URI with the appended ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Insert new bookmark with the given ContentValues into the database.
     */
    private Uri insertBookmark(Uri uri, ContentValues values) {
        // Sanity check values
        if (!isValidBokomarkEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(AnchorContract.BookmarkEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Notify all listeners that the data at the given URI has changed
        getContext().getContentResolver().notifyChange(uri, null);

        // Return the new URI with the appended ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Insert new directory with the given ContentValues into the database.
     */
    private Uri insertDirectory(Uri uri, ContentValues values) {
        // Sanity check values
        if (!isValidDirectoryEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(AnchorContract.DirectoryEntry.TABLE_NAME, null, values);

        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Notify all listeners that the data at the given URI has changed
        getContext().getContentResolver().notifyChange(uri, null);

        // Return the new URI with the appended ID
        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * Delete the data at the given selection and selection arguments.
     */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        // Get writable database
        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        final int match = sUriMatcher.match(uri);
        ArrayList<Long> ids;
        switch (match) {
            case DIRECTORY:
                // Delete corresponding albums
                ids = getAffectedIds(AnchorContract.DirectoryEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = AnchorContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(AnchorContract.AlbumEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Delete all rows that match the selection and selection args
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.DirectoryEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM:
                // Delete corresponding audio files
                ids = getAffectedIds(AnchorContract.AlbumEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(AnchorContract.AudioEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Delete all rows that match the selection and selection args
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO:
                // Delete corresponding bookmarks for each deleted audio file
                ids = getAffectedIds(AnchorContract.AudioEntry.CONTENT_URI, selection, selectionArgs);
                for (long id : ids) {
                    String subSelection = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
                    String[] subSelectionArgs = new String[]{String.valueOf(id)};
                    delete(AnchorContract.BookmarkEntry.CONTENT_URI, subSelection, subSelectionArgs);
                }

                // Delete all rows that match the selection and selection args
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case BOOKMARK:
                // Delete all rows that match the selection and selection args
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.BookmarkEntry.TABLE_NAME, selection, selectionArgs);
            case DIRECTORY_ID:
                // Delete a single row given by the ID in the URI
                long directoryId = ContentUris.parseId(uri);
                selection = AnchorContract.DirectoryEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(directoryId)};

                // Delete corresponding albums
                String selectionAlbum = AnchorContract.AlbumEntry.COLUMN_DIRECTORY + "=?";
                delete(AnchorContract.AlbumEntry.CONTENT_URI, selectionAlbum, selectionArgs);

                // Send notification about change
                getContext().getContentResolver().notifyChange(uri, null);

                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.DirectoryEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM_ID:
                // Delete a single row given by the ID in the URI
                long albumId = ContentUris.parseId(uri);
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(albumId)};

                // Delete corresponding audio files
                String selectionAudioFile = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
                delete(AnchorContract.AudioEntry.CONTENT_URI, selectionAudioFile, selectionArgs);

                // Send notification about change
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(AnchorContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO_ID:
                // Delete a single row given by the ID in the URI
                long audioId = ContentUris.parseId(uri);
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(audioId)};

                // Delete corresponding bookmarks
                String selectionBookmark = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
                delete(AnchorContract.BookmarkEntry.CONTENT_URI, selectionBookmark, selectionArgs);

                // Send notification about change
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(AnchorContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case BOOKMARK_ID:
                // Delete a single row given by the ID in the URI
                selection = AnchorContract.BookmarkEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};

                // Send notification about change
                getContext().getContentResolver().notifyChange(uri, null);

                return database.delete(AnchorContract.BookmarkEntry.TABLE_NAME, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Update all rows that match the selection and selection args
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM:
                return updateAlbum(uri, values, selection, selectionArgs);
            case BOOKMARK:
                return updateBookmark(uri, values, selection, selectionArgs);
            case AUDIO_ID:
                // Update a single row given by the ID in the URI
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM_ID:
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAlbum(uri, values, selection, selectionArgs);
            case BOOKMARK_ID:
                selection = AnchorContract.BookmarkEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateBookmark(uri, values, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }

    /**
     * Update audio files in the database with the given ContentValues.
     */
    private int updateAudioFile(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Sanity check values
        if (!isValidAudioFileEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Update the table
        int rowsUpdated = db.update(AnchorContract.AudioEntry.TABLE_NAME, values, selection, selectionArgs);

        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            if (uri != AnchorContract.AlbumEntry.CONTENT_URI)
                getContext().getContentResolver().notifyChange(AnchorContract.AlbumEntry.CONTENT_URI, null);
        }

        return rowsUpdated;
    }

    /**
     * Update album in the database with the given ContentValues.
     */
    private int updateAlbum(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Sanity check values
        if (!isValidAlbumEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Update the table
        int rowsUpdated = db.update(AnchorContract.AlbumEntry.TABLE_NAME, values, selection, selectionArgs);

        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    /**
     * Update album in the database with the given ContentValues.
     */
    private int updateBookmark(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Sanity check values
        if (!isValidBokomarkEntry(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Update the table
        int rowsUpdated = db.update(AnchorContract.BookmarkEntry.TABLE_NAME, values, selection, selectionArgs);

        // If 1 or more rows were updated, then notify all listeners that the data at the
        // given URI has changed
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    /**
     * Checks ContentValues for validity.
     */
    private boolean isValidAudioFileEntry(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.AudioEntry.COLUMN_TITLE)) {
            String val = values.getAsString(AnchorContract.AudioEntry.COLUMN_TITLE);
            if (val == null) {
                return false;
            }
        }
        // Check whether the album will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.AudioEntry.COLUMN_ALBUM)) {
            String val = values.getAsString(AnchorContract.AudioEntry.COLUMN_ALBUM);
            if (val == null) {
                return false;
            }
        }
        // Check whether the time will be updated and that it is not null
        if (values.containsKey(AnchorContract.AudioEntry.COLUMN_TIME)) {
            String val = values.getAsString(AnchorContract.AudioEntry.COLUMN_TIME);
            if (val == null) {
                return false;
            }
        }
        // Check whether the time will be updated and that it is not null
        if (values.containsKey(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME)) {
            String val = values.getAsString(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME);
            return val != null;
        }
        return true;
    }


    /**
     * Checks ContentValues for validity.
     */
    private boolean isValidAlbumEntry(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.AlbumEntry.COLUMN_TITLE)) {
            String val = values.getAsString(AnchorContract.AlbumEntry.COLUMN_TITLE);
            return val != null;
        }
        return true;
    }


    /**
     * Checks ContentValues for validity.
     */
    private boolean isValidBokomarkEntry(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.BookmarkEntry.COLUMN_TITLE)) {
            String val = values.getAsString(AnchorContract.BookmarkEntry.COLUMN_TITLE);
            if (val == null) {
                return false;
            }
        }
        // Check whether the position will be updated and that the new position is not null
        if (values.containsKey(AnchorContract.BookmarkEntry.COLUMN_POSITION)) {
            String val = values.getAsString(AnchorContract.BookmarkEntry.COLUMN_POSITION);
            if (val == null) {
                return false;
            }
        }
        // Check whether the audio file id will be updated and that the new id is not null
        if (values.containsKey(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE)) {
            String val = values.getAsString(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE);
            return val != null;
        }
        return true;
    }

    /**
     * Checks ContentValues for validity.
     */
    private boolean isValidDirectoryEntry(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.DirectoryEntry.COLUMN_PATH)) {
            String val = values.getAsString(AnchorContract.DirectoryEntry.COLUMN_PATH);
            if (val == null) {
                return false;
            }
            File file = new File(val);
            return file.exists() && file.isDirectory();
        }
        return true;
    }

    /*
     * Return ids of elements in given table that are affected by the given query.
     */
    private ArrayList<Long> getAffectedIds(Uri uri, String selection, String[] selectionArgs) {
        ArrayList<Long> ids = new ArrayList<>();

        // Determine which table is queried
        String column;
        if (uri == AnchorContract.DirectoryEntry.CONTENT_URI) {
            column = AnchorContract.DirectoryEntry._ID;
        } else if (uri == AnchorContract.AlbumEntry.CONTENT_URI) {
            column = AnchorContract.AlbumEntry._ID;
        } else if (uri == AnchorContract.AudioEntry.CONTENT_URI) {
            column = AnchorContract.AudioEntry._ID;
        } else if (uri == AnchorContract.BookmarkEntry.CONTENT_URI) {
            column = AnchorContract.BookmarkEntry._ID;
        } else {
            return ids;
        }

        // Query the database
        Cursor c = query(uri, new String[]{column}, selection, selectionArgs, null);

        if (c == null) {
            return ids;
        } else if (c.getCount() < 1) {
            c.close();
            return ids;
        }

        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndex(column));
            ids.add(id);
        }
        c.close();

        return ids;
    }
}
