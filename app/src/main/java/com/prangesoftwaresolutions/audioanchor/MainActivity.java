package com.prangesoftwaresolutions.audioanchor;

import android.Manifest;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.data.AnchorDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;

// TODO: Option in settings: Show title (from Metadata)
// TODO: Option in settings: if in autoplay play completed files as well
// TODO: Option in settings: Don't show deleted files in list
// TODO: Option in settings: Sort by
// TODO: Support subdirectories? / Support multiple base directories
// TODO: Don't save entire file path in the database, instead put it together from mDirectory, AlbumTitle and AudioFileTitle
// TODO: Mark files that do not exist anymore but are still in the database (or add a delete button)

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // The audio storage directory
    private File mDirectory;
    private String mPrefDirectory;

    // Preferences
    private SharedPreferences mSharedPreferences;
    private boolean mKeepDeleted;

    // Database variables
    private static final int AUDIO_LOADER = 0;
    private AlbumCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;
    ListView mListView;

    // Permission request
    private static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the shared preferences.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefDirectory = mSharedPreferences.getString(getString(R.string.preference_filename), null);
        mKeepDeleted = mSharedPreferences.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new AlbumCursorAdapter(this, null);

        // Use a ListView and CursorAdapter to recycle space
        mListView = findViewById(R.id.list);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList);
        mListView.setEmptyView(mEmptyTV);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long rowId) {
                // Open the AlbumActivity for the clicked album
                Intent intent = new Intent(MainActivity.this, AlbumActivity.class);
                intent.putExtra(getString(R.string.album_id), rowId);
                String albumName = ((TextView) view.findViewById(R.id.audio_storage_item_title)).getText().toString();
                String albumPath = new File(mDirectory.getAbsolutePath() + File.separator + albumName).getAbsolutePath();
                intent.putExtra(getString(R.string.directory_path), albumPath);
                startActivity(intent);
            }
        });

        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                Uri uri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, l);

                // Check if the audio file exists
                String[] proj = new String[]{AnchorContract.AlbumEntry.COLUMN_TITLE};
                Cursor c = getContentResolver().query(uri, proj, null, null, null);

                if (c == null) return false;

                String title = null;
                if (c.moveToNext()) {
                    title = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
                }
                c.close();

                if (title == null) return false;

                // Do not allow the delete action if the file still exists
                if ((new File(mDirectory, title)).exists()) {
                    return false;
                }

                deleteAlbumWithConfirmation(l);
                return true;
            }
        });

        // Check if app has the necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            mListView.setAdapter(mCursorAdapter);

            if (mPrefDirectory == null) {
                showChangeDirectorySelector();
            } else {
                mDirectory = new File(mPrefDirectory);
                updateAlbumTable();
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Set the projection to retrieve the relevant columns from the table
        String[] projection = {
                AnchorContract.AlbumEntry._ID,
                AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.COLUMN_COVER_PATH};

        String sortOrder = "CAST(" + AnchorContract.AlbumEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" +AnchorContract.AlbumEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, AnchorContract.AlbumEntry.CONTENT_URI, projection, null, null, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_albums);

        // Swap the new cursor in. The framework will take care of closing the old cursor
        mCursorAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished() is about to be closed.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.permission_denied, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    mListView.setAdapter(mCursorAdapter);

                    if (mPrefDirectory == null) {
                        showChangeDirectorySelector();
                    } else {
                        mDirectory = new File(mPrefDirectory);
                        updateAlbumTable();
                    }
                }
                break;
            }
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    showExportDirectorySelector();
                }
                break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_choose_directory:
                // Check if app has the necessary permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
                } else {
                    showChangeDirectorySelector();
                }
                return true;
            case R.id.menu_export:
                // Check if app has the necessary permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                } else {
                    showExportDirectorySelector();
                }
                return true;
            case R.id.menu_import:
                showImportFileSelector();
                return true;
            case R.id.menu_synchronize:
                updateAlbumTable();
                getLoaderManager().restartLoader(0, null, this);
                Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();
                return true;
            case R.id.menu_settings:
                // Send an intent to open the Learn Settings
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.menu_about:
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                startActivity(aboutIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChangeDirectorySelector() {
        // TODO: once the navigation bug is fixed the baseDirectory can be set to mDirectory if it's not null
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, null);
        fileDialog.setSelectDirectoryOption(true);
        fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
            public void directorySelected(File directory) {
                // Set the storage directory to the selected directory
                if (mDirectory != null) {
                    changeDirectoryWithConfirmation(directory);
                } else {
                    setDirectory(directory);
                }
            }
        });
        fileDialog.showDialog();
    }

    private void setDirectory(File directory){
        mDirectory = directory;
        updateAlbumTable();

        // Store the selected path in the shared preferences to persist when the app is closed
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(getString(R.string.preference_filename), directory.getAbsolutePath());
        editor.apply();

        // Inform the user about the selected path
        String text = "Path: " + directory.getAbsolutePath();
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    /**
     * Show the change directory confirmation dialog and let the user decide whether to change the directory
     */
    private void changeDirectoryWithConfirmation(final File directory) {
        // Create an AlertDialog.Builder and set the message and click listeners
        // for the positive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_change_dir);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Ok" button, so change the directory.
                getContentResolver().delete(AnchorContract.AlbumEntry.CONTENT_URI, null, null);
                getContentResolver().delete(AnchorContract.AudioEntry.CONTENT_URI, null, null);
                setDirectory(directory);
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Update the album database table if the list of directories in the selected directory do not
     * match the album table entries
     */
    void updateAlbumTable() {
        // Get all subdirectories of the selected audio storage directory.
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                File sel = new File(dir, filename);
                // Only list files that are readable and directories
                return sel.canRead() && sel.isDirectory();
            }
        };

        String[] directoryList;
        if (mDirectory != null && mDirectory.isDirectory()) {
            directoryList = mDirectory.list(filter);
        } else {
            directoryList = new String[]{};
        }

        LinkedHashMap<String, Integer> albumTitles = getAlbumTitles();

        // Insert new directories into the database
        for (String dirTitle : directoryList) {
            if (!albumTitles.containsKey(dirTitle)) {
                insertAlbum(dirTitle);
            } else {
                updateAlbumCover(albumTitles.get(dirTitle), dirTitle);
                albumTitles.remove(dirTitle);
            }
        }

        // Delete missing directories from the database
        if(!mKeepDeleted) {
            for (String title: albumTitles.keySet()) {
                int id = albumTitles.get(title);
                // Delete the album in the albums table
                Uri uri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, id);
                getContentResolver().delete(uri, null, null);
                // Delete all audios from the album in the audio_files table
                String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
                String[] selArgs = {Long.toString(id)};
                getContentResolver().delete(AnchorContract.AudioEntry.CONTENT_URI, sel, selArgs);
            }
        }
    }

    /*
     * Insert a new row in the albums table
     */
    private void insertAlbum(String title) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AlbumEntry.COLUMN_TITLE, title);
        Uri uri = getContentResolver().insert(AnchorContract.AlbumEntry.CONTENT_URI, values);
        updateAlbumCover(ContentUris.parseId(uri), title);
    }

    /*
     * Update the cover path in the albums table
     */
    private void updateAlbumCover(long albumId, String title) {
        // Get the previous cover path
        String oldCoverPath = null;
        String[] proj = new String[]{AnchorContract.AlbumEntry.COLUMN_COVER_PATH};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor c = getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                proj, sel, selArgs, null);
        if (c == null || c.getCount() < 1) {
            return;
        }
        if (c.moveToFirst()) {
            oldCoverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
        }
        c.close();

        if (oldCoverPath == null || !(new File(oldCoverPath).exists())) {
            // Search for a cover in the album directory
            File albumDir = new File(mDirectory.getAbsolutePath() + File.separator + title);
            String coverPath = Utils.getImagePath(albumDir);

            // Update the album cover path in the albums table
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AlbumEntry.COLUMN_COVER_PATH, coverPath);
            getContentResolver().update(AnchorContract.AlbumEntry.CONTENT_URI, values, sel, selArgs);
        }
    }

    /**
     * Retrieve all album titles from the database
     */
    private LinkedHashMap<String, Integer> getAlbumTitles() {
        String[] proj = new String[]{AnchorContract.AlbumEntry._ID, AnchorContract.AlbumEntry.COLUMN_TITLE};
        Cursor c = getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                proj, null, null, null);

        LinkedHashMap<String, Integer> titles = new LinkedHashMap<>();

        // Bail early if the cursor is null
        if (c == null) {
            return titles;
        }

        // Loop through the database rows and add the album titles to the HashMap
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            Integer id = c.getInt(c.getColumnIndex(AnchorContract.AlbumEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }

    private void showExportDirectorySelector() {
        // Let the user select a directory in which to save the database
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, null);
        fileDialog.setSelectDirectoryOption(true);
        fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
            public void directorySelected(File directory) {
                exportDatabase(directory);
            }
        });
        fileDialog.showDialog();
    }

    void exportDatabase(File directory) {
        try {
            String databaseName = AnchorDbHelper.DATABASE_NAME;
            File data = Environment.getDataDirectory();

            if (directory.canWrite()) {
                String currentDBPath = "//data//"+getPackageName()+"//databases//"+databaseName+"";
                String backupDBPath = "audioanchor.db";
                File currentDB = new File(data, currentDBPath);
                File backupDB = new File(directory, backupDBPath);

                if (currentDB.exists()) {
                    FileChannel src = new FileInputStream(currentDB).getChannel();
                    FileChannel dst = new FileOutputStream(backupDB).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();

                    String successStr = getResources().getString(R.string.export_success, backupDB.getAbsoluteFile());
                    Toast.makeText(getApplicationContext(), successStr, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
        }
    }

    private void showImportFileSelector() {
        File baseDirectory = Environment.getExternalStorageDirectory();
        FileDialog fileDialog = new FileDialog(this, baseDirectory, ".db");
        fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
            @Override
            public void fileSelected(File file) {
                importDatabase(file);
            }
        });
        fileDialog.showDialog();
    }

    void importDatabase(File dbFile) {
        try {
            String databaseName = AnchorDbHelper.DATABASE_NAME;
            File data = Environment.getDataDirectory();

            String newDBPath = "//data//"+getPackageName()+"//databases//"+databaseName+"";
            File newDB = new File(data, newDBPath);

            if (dbFile.exists()) {
                FileChannel src = new FileInputStream(dbFile).getChannel();
                FileChannel dst = new FileOutputStream(newDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();

                Toast.makeText(getApplicationContext(), R.string.import_success, Toast.LENGTH_LONG).show();

                // Restart the CursorLoader so that the CursorAdapter is updated.
                getLoaderManager().restartLoader(0, null, this);
            }

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), R.string.import_fail, Toast.LENGTH_LONG).show();

        }
    }

    /**
     * Show the delete album confirmation dialog and let the user decide whether to delete the album
     */
    private void deleteAlbumWithConfirmation(final long albumId) {
        // Create an AlertDialog.Builder and set the message and click listeners
        // for the positive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_album);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Ok" button, so delete the album.
                Uri albumUri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, albumId);
                getContentResolver().delete(albumUri, null, null);

                // Delete all audios from the album in the audio_files table
                String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
                String[] selArgs = {Long.toString(albumId)};
                getContentResolver().delete(AnchorContract.AudioEntry.CONTENT_URI, sel, selArgs);
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Cancel" button, so dismiss the dialog
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
