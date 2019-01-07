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
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
    TextView mAlbumInfoTimeTV;

    // Settings variables
    boolean mProgressInPercent;
    private boolean mKeepDeleted;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        mAlbumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mDirectory = new File(getIntent().getStringExtra(getString(R.string.directory_path)));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Set up the shared preferences.
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String prefKey = getString(R.string.settings_progress_percentage_key);
        String prefDefault = getString(R.string.settings_progress_percentage_default);
        mProgressInPercent = pref.getBoolean(prefKey, Boolean.getBoolean(prefDefault));
        mKeepDeleted = pref.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));

        // Initialize the cursor adapter
        mCursorAdapter = new AudioFileCursorAdapter(this, null);

        // Set up the views
        mAlbumInfoTitleTV = findViewById(R.id.album_info_title);
        mAlbumInfoTimeTV = findViewById(R.id.album_info_time);
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
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, rowId);

                // Check if the audio file exists
                AudioFile audio = AudioFile.getAudioFile(AlbumActivity.this, uri);
                if (!(new File(audio.getPath())).exists()) {
                    Toast.makeText(getApplicationContext(), R.string.play_error, Toast.LENGTH_LONG).show();
                    return;
                }

                // Open the PlayActivity for the clicked audio file
                Intent intent = new Intent(AlbumActivity.this, PlayActivity.class);
                intent.setData(uri);
                intent.putExtra("albumId", (int)mAlbumId);
                startActivity( intent );
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, l);

                // Don't allow delete action if the audio file still exists
                AudioFile audio = AudioFile.getAudioFile(AlbumActivity.this, uri);
                if ((new File(audio.getPath())).exists()) {
                    return false;
                }

                deleteAudioWithConfirmation(uri);
                return true;
            }
        });

        scrollToNotCompletedAudio(listView);

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
        String sortOrder = "LOWER(" + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar_album);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_audio_files);

        // Set the slbum info text and image
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

        // Set the album info time
        setCompletedAlbumTime();

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

        if (fileList == null) return;

        LinkedHashMap<String, Integer> audioTitles = getAudioFileTitles();

        // Insert new files into the database
        for (String file : fileList) {
            if (!audioTitles.containsKey(file)) {
                insertAudioFile(file);
            } else {
                audioTitles.remove(file);
            }
        }

        // Delete missing audio files from the database
        if (!mKeepDeleted) {
            for (String title: audioTitles.keySet()) {
                Integer id = audioTitles.get(title);
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
                getContentResolver().delete(uri, null, null);
            }
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

        // Retrieve audio duration from Metadata.
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        metaRetriever.setDataSource(path);
        String duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        values.put(AnchorContract.AudioEntry.COLUMN_TIME, Long.parseLong(duration));
        metaRetriever.release();

        // Insert the row into the database table
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

        // Loop through the database rows and add the audio file titles to the hashmap
        while (c.moveToNext()) {
            String title = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            titles.put(title, id);
        }

        c.close();
        return titles;
    }

    private void setCompletedAlbumTime() {
        SQLiteDatabase db = openOrCreateDatabase("audio_anchor.db", MODE_PRIVATE, null);
        String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, AnchorContract.AudioEntry.COLUMN_TIME};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};

        Cursor c = db.query(AnchorContract.AudioEntry.TABLE_NAME,
                columns, sel, selArgs, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return;
        }

        // Loop through the database rows and sum up the audio durations and completed time
        int sumDuration = 0;
        int sumCompletedTime = 0;
        while (c.moveToNext()) {
            sumDuration += c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
            sumCompletedTime += c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
        }

        c.close();

        // Set the text for the album time TextView
        String timeStr;
        if (!mProgressInPercent) {
            String durationStr = Utils.formatTime(sumDuration, sumDuration);
            String completedTimeStr = Utils.formatTime(sumCompletedTime, sumDuration);
            timeStr = getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        } else {
            int percent = Math.round(((float)sumCompletedTime / sumDuration) * 100);
            timeStr = getResources().getString(R.string.time_completed_percent, percent);
        }
        mAlbumInfoTimeTV.setText(timeStr);
    }

    private void scrollToNotCompletedAudio(ListView listView) {
        SQLiteDatabase db = openOrCreateDatabase("audio_anchor.db", MODE_PRIVATE, null);
        String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, AnchorContract.AudioEntry.COLUMN_TIME};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};

        Cursor c = db.query(AnchorContract.AudioEntry.TABLE_NAME,
                columns, sel, selArgs, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return;
        }

        // Loop through the database rows and sum up the audio durations and completed time
        int scrollTo = 0;
        while (c.moveToNext()) {
            int duration = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
            int completed = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            if (completed < duration || duration == 0) {
                break;
            }
            scrollTo += 1;
        }
        c.close();

        listView.setSelection(Math.max(scrollTo - 1, 0));
    }

    /**
     * Show the delete audio confirmation dialog and let the user decide whether to delete the audio
     */
    private void deleteAudioWithConfirmation(final Uri audioUri) {
        // Create an AlertDialog.Builder and set the message and click listeners
        // for the positive and negative buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_audio);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Ok" button, so delete the audio.
                getContentResolver().delete(audioUri, null, null);
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
