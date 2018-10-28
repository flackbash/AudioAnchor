package com.prangesoftwaresolutions.audioanchor;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;

public class AlbumActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    // The album uri and file
    private long mAlbumId;
    private File mDirectory;

    // Database variables
    private static final int ALBUM_LOADER = 0;
    private AudioFileCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;
    ImageView mAlbumInfoCoverIV;
    TextView mAlbumInfoTitleTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        mAlbumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mDirectory = new File(getIntent().getStringExtra(getString(R.string.directory_path)));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new AudioFileCursorAdapter(this, null);

        // Set up the views
        mAlbumInfoTitleTV = findViewById(R.id.album_info_title);
        mAlbumInfoCoverIV = findViewById(R.id.album_info_cover);

        // Use a ListView and CursorAdapter to recycle space
        ListView listView = findViewById(R.id.list_album);
        listView.setAdapter(mCursorAdapter);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList_album);
        listView.setEmptyView(mEmptyTV);

        // Implement onItemClickListener for the list view
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long rowId) {
                // Open the PlayActivity for the clicked audio file
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, rowId);
                Intent intent = new Intent(AlbumActivity.this, PlayActivity.class);
                intent.setData(uri);
                startActivity(intent);
            }
        });

        updateAudioFileTable();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.COLUMN_PATH,
                AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME
        };

        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};

        return new CursorLoader(this, AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar_album);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_audio_files);

        // Set the Album Info Text and Image
        String[] proj = {AnchorContract.AlbumEntry.COLUMN_COVER_PATH, AnchorContract.AlbumEntry.COLUMN_TITLE};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};
        Cursor c = getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI, proj, sel, selArgs, null);
        if (c == null || c.getCount() < 1) {
            return;
        }
        if (c.moveToFirst()) {
            String albumTitle = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            mAlbumInfoTitleTV.setText(albumTitle);

            String coverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
            int reqSize = getResources().getDimensionPixelSize(R.dimen.album_info_height);
            BitmapUtils.setImage(mAlbumInfoCoverIV, coverPath, reqSize);
        }
        c.close();

        // Swap the new cursor in. The framework will take care of closing the old cursor
        mCursorAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished() is about to be closed.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return(super.onOptionsItemSelected(item));
    }

    /*
     * Update the album database table if the list of directories in the selected directory do not
     * match the album table entries
     */
    void updateAudioFileTable() {
        // Get all audio files in the album.
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                File sel = new File(dir, filename);
                // Only list files that are readable and audio files
                return sel.getName().endsWith(".mp3") || sel.getName().endsWith(".wma") || sel.getName().endsWith(".ogg") || sel.getName().endsWith(".wav") || sel.getName().endsWith(".flac") || sel.getName().endsWith(".m4a");
            }
        };

        // Get all files in the album directory.
        String[] fileList;
        if (mDirectory != null) {
            fileList = mDirectory.list(filter);
        } else {
            fileList = new String[]{};
        }

        LinkedHashMap<String, Integer> audioTitles = getAudioFileTitles();

        // Insert new directories into the database
        for (String file : fileList) {
            if (!audioTitles.containsKey(file)) {
                insertAudioFile(file);
            } else {
                audioTitles.remove(file);
            }
        }

        // Delete missing audio files from the database
        for (String title: audioTitles.keySet()) {
            Integer id = audioTitles.get(title);
            Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
            getContentResolver().delete(uri, null, null);
        }
    }

    /*
     * Insert a new row in the album database table
     */
    private void insertAudioFile(String title) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_TITLE, title);
        String path = Utils.getPath(this, mDirectory.getName(), title);
        values.put(AnchorContract.AudioEntry.COLUMN_PATH, path);
        values.put(AnchorContract.AudioEntry.COLUMN_ALBUM, mAlbumId);
        getContentResolver().insert(AnchorContract.AudioEntry.CONTENT_URI, values);
    }

    /**
     * Retrieve all audio file titles from the database
     */
    private LinkedHashMap<String, Integer> getAudioFileTitles() {
        SQLiteDatabase db = openOrCreateDatabase("audio_anchor.db", MODE_PRIVATE, null);
        String[] columns = new String[]{AnchorContract.AudioEntry._ID, AnchorContract.AudioEntry.COLUMN_TITLE};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};

        Cursor c = db.query(AnchorContract.AudioEntry.TABLE_NAME,
                columns, sel, selArgs, null, null, null);

        LinkedHashMap<String, Integer> titles = new LinkedHashMap<>();

        // Bail early if the cursor is null
        if (c == null) {
            return titles;
        }

        // Loop through the database rows and add the recipes to the ArrayList
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            Integer id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }
}
