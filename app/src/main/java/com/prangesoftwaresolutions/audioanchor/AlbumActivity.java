package com.prangesoftwaresolutions.audioanchor;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

public class AlbumActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    // The album uri and file
    private long mAlbumId;

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        mAlbumId = getIntent().getLongExtra(getString(R.string.album_id), -1);

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Set up the shared preferences.
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        String prefKey = getString(R.string.settings_progress_percentage_key);
        String prefDefault = getString(R.string.settings_progress_percentage_default);
        mProgressInPercent = pref.getBoolean(prefKey, Boolean.getBoolean(prefDefault));

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
        String sortOrder = "CAST(" + AnchorContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";

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

    private void setCompletedAlbumTime() {
        SQLiteDatabase db = openOrCreateDatabase(getString(R.string.database_filename), MODE_PRIVATE, null);
        String timeStr = Utils.getAlbumCompletion(db, mAlbumId, mProgressInPercent, getResources());
        db.close();
        mAlbumInfoTimeTV.setText(timeStr);
    }

    private void scrollToNotCompletedAudio(ListView listView) {
        SQLiteDatabase db = openOrCreateDatabase(getString(R.string.database_filename), MODE_PRIVATE, null);
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
