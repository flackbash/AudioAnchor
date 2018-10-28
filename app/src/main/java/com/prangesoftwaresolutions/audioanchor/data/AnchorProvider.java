package com.prangesoftwaresolutions.audioanchor.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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
    }

    /**
     * Initialize the provider and the database helper object.
     */
    @Override
    public boolean onCreate() {
        // Create and initialize a AnchorDbHelper object to gain access to the audios database.
        mDbHelper = new AnchorDbHelper(getContext());
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
            case AUDIO_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case ALBUM_ID:
                // Query a single row given by the ID in the URI
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AnchorContract.AlbumEntry.TABLE_NAME, projection, selection, selectionArgs,
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
            case AUDIO_ID:
                return AnchorContract.AudioEntry.CONTENT_ITEM_TYPE;
            case ALBUM_ID:
                return AnchorContract.AlbumEntry.CONTENT_ITEM_TYPE;
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
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    /**
     * Insert new audio file with the given ContentValues into the database.
     */
    private Uri insertAudioFile(Uri uri, ContentValues values) {
        // Sanity check values
        if (!sanityCheckAudioFile(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

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
        if (!sanityCheckAlbum(values)) {
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
     * Delete the data at the given selection and selection arguments.
     */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        // Get writable database
        SQLiteDatabase database = mDbHelper.getWritableDatabase();

        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Delete all rows that match the selection and selection args
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM:
                // Delete all rows that match the selection and selection args
                // TODO: need to delete all audio files for the given album as well?
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO_ID:
                // Delete a single row given by the ID in the URI
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case ALBUM_ID:
                // Delete a single row given by the ID in the URI
                // TODO: need to delete all audio files for the given album as well?
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AnchorContract.AlbumEntry.TABLE_NAME, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Delete all rows that match the selection and selection args
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM:
                // Delete all rows that match the selection and selection args
                return updateAlbum(uri, values, selection, selectionArgs);
            case AUDIO_ID:
                // Delete a single row given by the ID in the URI
                selection = AnchorContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAudioFile(uri, values, selection, selectionArgs);
            case ALBUM_ID:
                // Delete a single row given by the ID in the URI
                selection = AnchorContract.AlbumEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateAudioFile(uri, values, selection, selectionArgs);
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
        if (!sanityCheckAudioFile(values)) {
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
        if (!sanityCheckAlbum(values)) {
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
     * Checks ContentValues for validity.
     */
    private boolean sanityCheckAudioFile(ContentValues values) {
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
        // Check whether the album will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.AudioEntry.COLUMN_PATH)) {
            String val = values.getAsString(AnchorContract.AudioEntry.COLUMN_PATH);
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
            if (val == null) {
                return false;
            }
        }
        return true;
    }


    /**
     * Checks ContentValues for validity.
     */
    private boolean sanityCheckAlbum(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AnchorContract.AlbumEntry.COLUMN_TITLE)) {
            String val = values.getAsString(AnchorContract.AlbumEntry.COLUMN_TITLE);
            if (val == null) {
                return false;
            }
        }
        return true;
    }
}
