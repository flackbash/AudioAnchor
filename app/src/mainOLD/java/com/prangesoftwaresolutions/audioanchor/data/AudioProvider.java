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
 * Content Provider for audioanchor app
 */

public class AudioProvider extends ContentProvider {


    public static final String LOG_TAG = AudioProvider.class.getSimpleName();

    // AnchorDbHelper instance to gain access to the audios database
    private AudioDbHelper mDbHelper;

    private static final int AUDIO = 100;
    private static final int AUDIO_ID = 101;
    private static final int AUDIO_DISTINCT = 110;

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(AudioContract.CONTENT_AUTHORITY, AudioContract.PATH_AUDIO, AUDIO);
        sUriMatcher.addURI(AudioContract.CONTENT_AUTHORITY, AudioContract.PATH_AUDIO + "/#", AUDIO_ID);
        sUriMatcher.addURI(AudioContract.CONTENT_AUTHORITY, AudioContract.PATH_AUDIO_DISTINCT, AUDIO_DISTINCT);
    }


    /**
     * Initialize the provider and the database helper object.
     */
    @Override
    public boolean onCreate() {
        // Create and initialize a AnchorDbHelper object to gain access to the audios database.
        mDbHelper = new AudioDbHelper(getContext());
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

        // This cursor will hold the result of the query
        Cursor cursor;

        // Figure out if the URI matcher can match the URI to a specific code
        int match = sUriMatcher.match(uri);
        switch (match) {
            case AUDIO:
                // Query the vocab table with the given parameters
                cursor = database.query(AudioContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            case AUDIO_DISTINCT:
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                qb.setDistinct(true);
                qb.setTables(AudioContract.AudioEntry.TABLE_NAME);
                cursor = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case AUDIO_ID:
                // Query a single row given by the ID in the URI
                selection = AudioContract.AudioEntry._ID + "=?";
                selectionArgs = new String[] { String.valueOf(ContentUris.parseId(uri)) };

                // Perform query on the recipe table for the given recipe id.
                cursor = database.query(AudioContract.AudioEntry.TABLE_NAME, projection, selection, selectionArgs,
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
                return AudioContract.AudioEntry.CONTENT_LIST_TYPE;
            case AUDIO_ID:
                return AudioContract.AudioEntry.CONTENT_ITEM_TYPE;
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
                return insertRecipe(uri, contentValues);
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    /**
     * Insert new recipe with the given ContentValues into the database.
     */
    private Uri insertRecipe(Uri uri, ContentValues values) {
        // Sanity check values
        if (!sanityCheck(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        long id = db.insert(AudioContract.AudioEntry.TABLE_NAME, null, values);

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
                return database.delete(AudioContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
            case AUDIO_ID:
                // Delete a single row given by the ID in the URI
                selection = AudioContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                getContext().getContentResolver().notifyChange(uri, null);
                return database.delete(AudioContract.AudioEntry.TABLE_NAME, selection, selectionArgs);
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
                return updateRecipe(uri, values, selection, selectionArgs);
            case AUDIO_ID:
                // Delete a single row given by the ID in the URI
                selection = AudioContract.AudioEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateRecipe(uri, values, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }

    /**
     * Update recipe in the database with the given ContentValues.
     */
    private int updateRecipe(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // If there are no values to update, then don't try to update the database
        if (values.size() == 0) {
            return 0;
        }

        // Sanity check values
        if (!sanityCheck(values)) {
            throw new IllegalArgumentException("Sanity check failed: corrupted content values");
        }

        // Get writable database
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Update the table
        int rowsUpdated = db.update(AudioContract.AudioEntry.TABLE_NAME, values, selection, selectionArgs);

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
    private boolean sanityCheck(ContentValues values) {
        // Check whether the title will be updated and that the new title is not null
        if (values.containsKey(AudioContract.AudioEntry.COLUMN_AUDIO_TITLE)) {
            String val = values.getAsString(AudioContract.AudioEntry.COLUMN_AUDIO_TITLE);
            if (val == null) {
                return false;
            }
        }

        // Check whether the time will be updated and that it is not null
        if (values.containsKey(AudioContract.AudioEntry.COLUMN_AUDIO_TIME)) {
            String val = values.getAsString(AudioContract.AudioEntry.COLUMN_AUDIO_TIME);
            if (val == null) {
                return false;
            }
        }
        return true;
    }
}
