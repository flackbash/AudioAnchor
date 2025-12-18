package com.prangesoftwaresolutions.audioanchor.helpers;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.data.AnchorDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class Migrator {
    private final AppCompatActivity mActivity;

    // Database name
    private final String mDatabaseName = AnchorDbHelper.DATABASE_NAME;

    // ActivityResultLaunchers for SAF pickers
    private ActivityResultLauncher<Intent> exportDatabaseLauncher;
    private ActivityResultLauncher<Intent> importDatabaseLauncher;

    // Callback interfaces
    public interface OnDatabaseExported {
        void onExported();
        void onError(Exception e);
    }

    public interface OnDatabaseImported {
        void onImported();
        void onError(Exception e);
    }

    private OnDatabaseExported exportCallback;
    private OnDatabaseImported importCallback;


    public Migrator(AppCompatActivity activity) {
        mActivity = activity;

        initActivityResultLaunchers();
    }

    private void initActivityResultLaunchers() {
        // Export launcher
        exportDatabaseLauncher = mActivity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        try {
                            exportDatabaseToUri(uri);
                            if (exportCallback != null) exportCallback.onExported();
                        } catch (IOException e) {
                            if (exportCallback != null) exportCallback.onError(e);
                        }
                    }
                }
        );

        // Import launcher
        importDatabaseLauncher = mActivity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        try {
                            importDatabaseFromUri(uri);
                            if (importCallback != null) importCallback.onImported();
                        } catch (IOException e) {
                            if (importCallback != null) importCallback.onError(e);
                        }
                    }
                }
        );
    }

    /**
     * Launch the file picker to select a location to export the database ZIP.
     */
    public void pickExportLocation(OnDatabaseExported callback) {
        this.exportCallback = callback;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, mDatabaseName);
        exportDatabaseLauncher.launch(intent);
    }

    /**
     * Launch the file picker to select a ZIP file to import the database.
     */
    public void pickImportFile(OnDatabaseImported callback) {
        this.importCallback = callback;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/octet-stream");
        importDatabaseLauncher.launch(intent);
    }

    private void exportDatabaseToUri(Uri uri) throws IOException {
        // Current database files
        String currentDBPath = mActivity.getDatabasePath(mDatabaseName).getPath();

        // Copy the .db file to the Uri
        try (InputStream is = new FileInputStream(currentDBPath);
             OutputStream os = mActivity.getContentResolver().openOutputStream(uri)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
        }
    }

    private void importDatabaseFromUri(Uri uri) throws IOException {
        // Get path to your app's database file
        File dbFile = mActivity.getDatabasePath(AnchorDbHelper.DATABASE_NAME);

        // Close the database if it's open
        SQLiteDatabase db;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
            db.close();
        } catch (Exception e) {
            // ignore if DB isn't open
        }

        // Copy the .db file from the Uri to your database location
        boolean importSuccessful = false;
        try (InputStream is = mActivity.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(dbFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            os.flush();
            importSuccessful = true;
        }

        if (importSuccessful) {
            // Adjust album cover paths to contain only the cover file name to enable
            // import of dbs that were exported in a previous version with the full path names
            // Get the old cover path
            String[] proj = new String[]{
                    AnchorContract.AlbumEntry._ID,
                    AnchorContract.AlbumEntry.COLUMN_COVER_PATH};
            Cursor c = mActivity.getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                    proj, null, null, null);
            if (c != null) {
                if (c.getCount() > 0) {
                    c.moveToFirst();
                    while (c.moveToNext()) {
                        String oldCoverPath = c.getString(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
                        int id = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AlbumEntry._ID));
                        if (oldCoverPath != null && !oldCoverPath.isEmpty()) {
                            // Replace the old cover path in the database by the new relative path
                            String newCoverPath = new File(oldCoverPath).getName();
                            ContentValues values = new ContentValues();
                            values.put(AnchorContract.AlbumEntry.COLUMN_COVER_PATH, newCoverPath);
                            Uri albumUri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, id);
                            mActivity.getContentResolver().update(albumUri, values, null, null);
                        }
                    }
                }
                c.close();
            }
        }

        // Make sure onUpgrade() is called in case a database of version 1 or 2 was imported
        // onUpgrade() is called when getReadableDatabase() or getWriteableDatabase() is called
        // We need a new instance of AnchorDbHelper here, not completely sure why, something
        // about the version number probably
        AnchorDbHelper dbHelper = new AnchorDbHelper(mActivity);
        dbHelper.getWritableDatabase();
    }
}
