package com.prangesoftwaresolutions.audioanchor;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
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

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;

// TODO: Don't include file extension in title --> get file metadata?
// TODO: Show border around button play

public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // The audio storage directory
    private File mDirectory;

    // Preferences
    private SharedPreferences mSharedPreferences;

    // Database variables
    private static final int AUDIO_LOADER = 0;
    private AlbumCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String storageDirectory = mSharedPreferences.getString(getString(R.string.preference_filename), null);

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new AlbumCursorAdapter(this, null);

        // Use a ListView and CursorAdapter to recycle space
        ListView listView = findViewById(R.id.list);
        listView.setAdapter(mCursorAdapter);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList);
        listView.setEmptyView(mEmptyTV);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
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

        if (storageDirectory == null) {
            showDirectorySelector();
        } else {
            mDirectory = new File(storageDirectory);
            updateAlbumTable();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Set the projection to retrieve the relevant columns from the table
        String[] projection = {
                AnchorContract.AlbumEntry._ID,
                AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.COLUMN_COVER_PATH};

        return new CursorLoader(this, AnchorContract.AlbumEntry.CONTENT_URI, projection, null, null, null);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_choose_directory:
                showDirectorySelector();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDirectorySelector() {
        // Let the user select a file and import the recipes.
        File baseDirectory;
        if (mDirectory != null) {
            baseDirectory = mDirectory;
        } else {
            baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        }
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
        builder.setPositiveButton(R.string.dialog_msg_change, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Change" button, so change the directory.
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
        if (mDirectory != null) {
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
            if (coverPath != null) {
                // Update the album cover path in the albums table
                ContentValues values = new ContentValues();
                values.put(AnchorContract.AlbumEntry.COLUMN_COVER_PATH, coverPath);
                getContentResolver().update(AnchorContract.AlbumEntry.CONTENT_URI, values, sel, selArgs);
            }
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

        // Loop through the database rows and add the recipes to the ArrayList
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            Integer id = c.getInt(c.getColumnIndex(AnchorContract.AlbumEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }
}
