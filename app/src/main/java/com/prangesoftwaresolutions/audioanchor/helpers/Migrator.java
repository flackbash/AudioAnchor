package com.prangesoftwaresolutions.audioanchor.helpers;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

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
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, mDatabaseName + ".zip");
        exportDatabaseLauncher.launch(intent);
    }

    /**
     * Launch the file picker to select a ZIP file to import the database.
     */
    public void pickImportFile(OnDatabaseImported callback) {
        this.importCallback = callback;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/zip");
        importDatabaseLauncher.launch(intent);
    }

    /**
     * Open the imported database.
     */
    public SQLiteDatabase openImportedDatabase() {
        File dbDir = new File(mActivity.getFilesDir(), "databases");
        File dbFile = new File(dbDir, mDatabaseName);
        return SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READWRITE
        );
    }

    private void exportDatabaseToUri(Uri uri) throws IOException {

        // Current database files
        String currentDBPath = mActivity.openOrCreateDatabase(
                mDatabaseName,
                Context.MODE_PRIVATE,
                null).getPath();

        File currentDB = new File(currentDBPath);
        File currentDBShm = new File(currentDBPath + "-shm");
        File currentDBWal = new File(currentDBPath + "-wal");
        File[] currentFiles = {currentDB, currentDBShm, currentDBWal};

        try (OutputStream os = mActivity.getContentResolver().openOutputStream(uri);
             ZipOutputStream zos = new ZipOutputStream(os)) {

            for (File file : currentFiles) {
                if (file.exists()) {
                    addFileToZip(file, zos);
                }
            }
        }
    }

    private void addFileToZip(File file, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);

            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }

            zos.closeEntry();
        }
    }

    /**
     * Unzips a database ZIP URI into a temporary folder and returns File objects
     * for the database, WAL, and SHM files.
     */
    public File[] unzipDatabase(Uri zipUri, String databaseName) throws IOException {
        File tempDir = new File(mActivity.getCacheDir(), "db_import_temp");
        if (!tempDir.exists()) tempDir.mkdirs();

        try (InputStream is = mActivity.getContentResolver().openInputStream(zipUri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(tempDir, entry.getName());

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) >= 0) {
                        fos.write(buffer, 0, len);
                    }
                }

                zis.closeEntry();
            }
        }

        // Create File objects for db, shm, wal
        File dbFile = new File(tempDir, databaseName);
        File shmFile = new File(tempDir, databaseName + "-shm");
        File walFile = new File(tempDir, databaseName + "-wal");

        return new File[]{dbFile, shmFile, walFile};
    }

    private void importDatabaseFromUri(Uri uri) throws IOException {
        // Unzip the database files to a temporary location
        File[] importFiles = unzipDatabase(uri, mDatabaseName);

        SQLiteDatabase db = mActivity.openOrCreateDatabase(AnchorDbHelper.DATABASE_NAME, Context.MODE_PRIVATE, null);
        String newDBPath = db.getPath();
        db.close();

        File newDBFile = new File(newDBPath);
        File newDBShm = new File(newDBPath + "-shm");
        File newDBWal = new File(newDBPath + "-wal");
        File[] newFiles = {newDBFile, newDBShm, newDBWal};

        int fileExists = 0;
        for (int i = 0; i < importFiles.length; i++) {
            if (importFiles[i].exists()) {
                FileChannel src = new FileInputStream(importFiles[i]).getChannel();
                FileChannel dst = new FileOutputStream(newFiles[i]).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();

                fileExists++;
            } else {
                newFiles[i].delete();
            }
        }
        if (fileExists > 0) {
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
