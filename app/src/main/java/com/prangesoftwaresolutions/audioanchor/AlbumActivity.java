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
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.util.ArrayList;

public class AlbumActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    // The album uri and file
    private long mAlbumId;
    private String mAlbumName;
    private String mDirectory;

    // Database variables
    private static final int ALBUM_LOADER = 0;
    private AudioFileCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;
    ImageView mAlbumInfoCoverIV;
    TextView mAlbumInfoTitleTV;
    TextView mAlbumInfoTimeTV;

    // Settings variables
    SharedPreferences mPrefs;
    boolean mDarkTheme;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedTracks = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        mAlbumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mAlbumName = getIntent().getStringExtra(getString(R.string.album_name));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Set up the shared preferences.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDarkTheme = mPrefs.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));

        mDirectory = mPrefs.getString(getString(R.string.preference_filename), null);

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
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI_AUDIO_ALBUM, rowId);

                // Check if the audio file exists
                AudioFile audio = AudioFile.getAudioFile(AlbumActivity.this, uri, mDirectory);
                if (!(new File(audio.getPath())).exists()) {
                    Toast.makeText(getApplicationContext(), R.string.play_error, Toast.LENGTH_LONG).show();
                    return;
                }

                // Open the PlayActivity for the clicked audio file
                Intent intent = new Intent(AlbumActivity.this, PlayActivity.class);
                intent.setData(uri);
                startActivity( intent );
            }
        });

        // See https://developer.android.com/guide/topics/ui/menus.html#CAB for details
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Adjust menu title and list of selected tracks when items are selected / de-selected
                if (b) {
                    mSelectedTracks.add(l);
                } else {
                    mSelectedTracks.remove(l);
                }
                String menuTitle = getResources().getString(R.string.items_selected, mSelectedTracks.size());
                actionMode.setTitle(menuTitle);
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Inflate the menu for the CAB
                getMenuInflater().inflate(R.menu.menu_album_cab, menu);
                // Without this, menu items are always shown in the action bar instead of the overflow menu
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
                return true;

            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_delete_from_db:
                        deleteSelectedTracksFromDBWithConfirmation();
                        actionMode.finish(); // Action picked, so close the CAB
                        return true;
                    case R.id.menu_mark_as_not_started:
                        markSelectedTracksAsNotStarted();
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_completed:
                        markSelectedTracksAsCompleted();
                        actionMode.finish();
                        return true;
                    default:
                        return false;
                }

            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Make necessary updates to the activity when the CAB is removed
                // By default, selected items are deselected/unchecked.
                mSelectedTracks.clear();
            }
        });

        scrollToNotCompletedAudio(listView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    protected void onRestart() {
        // recreate if theme has changed
        boolean currentDarkTheme;
        currentDarkTheme = mPrefs.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        if (mDarkTheme != currentDarkTheme) {
            recreate();
        }
        super.onRestart();
    }


    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.TABLE_NAME + "." + AnchorContract.AlbumEntry.COLUMN_COVER_PATH
        };

        String sel = AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};
        String sortOrder = "CAST(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, AnchorContract.AudioEntry.CONTENT_URI_AUDIO_ALBUM, projection, sel, selArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar_album);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_audio_files);

        if (cursor != null && cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                // Set album cover
                String coverPath = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
                coverPath = mDirectory + File.separator + coverPath;
                int reqSize = getResources().getDimensionPixelSize(R.dimen.album_info_height);
                BitmapUtils.setImage(mAlbumInfoCoverIV, coverPath, reqSize);

                // Set the album info time
                setCompletedAlbumTime();
            }
        }
        mAlbumInfoTitleTV.setText(mAlbumName);

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
        getMenuInflater().inflate(R.menu.menu_album, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_settings:
                // Send an intent to open the Settings
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
        }

        return(super.onOptionsItemSelected(item));
    }

    private void setCompletedAlbumTime() {
        boolean progressInPercent = mPrefs.getBoolean(getString(R.string.settings_progress_percentage_key), Boolean.getBoolean(getString(R.string.settings_progress_percentage_default)));
        String timeStr = Utils.getAlbumCompletion(this, mAlbumId, progressInPercent, getResources());
        mAlbumInfoTimeTV.setText(timeStr);
    }

    private void scrollToNotCompletedAudio(ListView listView) {
        String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, AnchorContract.AudioEntry.COLUMN_TIME};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};

        Cursor c = getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                columns, sel, selArgs, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return;
        } else if (c.getCount() < 1) {
            c.close();
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

    private void deleteSelectedTracksFromDBWithConfirmation() {
        Long[] selectedTracks = new Long[mSelectedTracks.size()];
        final Long[] selectedTracksArr = mSelectedTracks.toArray(selectedTracks);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_audio);
        builder.setPositiveButton(R.string.dialog_msg_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked the "Ok" button, so delete the tracks from the database
                int deletionCount = 0;
                for (long trackId : selectedTracksArr) {
                    Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI_AUDIO_ALBUM, trackId);
                    Uri deleteUri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);

                    // Don't allow delete action if the track still exists
                    AudioFile audio = AudioFile.getAudioFile(AlbumActivity.this, uri, mDirectory);
                    if (! (new File(audio.getPath())).exists()) {
                        getContentResolver().delete(deleteUri, null, null);
                        deletionCount ++;

                        // Delete all bookmarks for the deleted track
                        deleteBookmarksForTrack(trackId);
                    }

                }
                String deletedTracks = getResources().getString(R.string.tracks_deleted, deletionCount);
                Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();

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

    private void deleteBookmarksForTrack(long trackId) {
        // Get all bookmarks associated with the trackId
        String[] columns = new String[]{AnchorContract.BookmarkEntry._ID, AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE};
        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
        String[] selArgs = {Long.toString(trackId)};

        Cursor c = getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI,
                columns, sel, selArgs, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return;
        } else if (c.getCount() < 1) {
            c.close();
            return;
        }

        while (c.moveToNext()) {
            // Delete bookmarks associated with the track from the database
            int bookmarkId = c.getInt(c.getColumnIndex(AnchorContract.BookmarkEntry._ID));
            Uri deleteUri = ContentUris.withAppendedId(AnchorContract.BookmarkEntry.CONTENT_URI, bookmarkId);
            getContentResolver().delete(deleteUri, null, null);
        }
        c.close();
    }

    private void markSelectedTracksAsNotStarted() {
        // Set the completedTime column of the audiofiles table for each selected track to 0
        for (long trackId : mSelectedTracks) {
            Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, 0);
            getContentResolver().update(uri, values, null, null);
            getContentResolver().notifyChange(uri, null);
        }
    }

    private void markSelectedTracksAsCompleted() {
        // Set the completedTime column of the audiofiles table for each selected track to the totalTime
        // of the track
        for (long trackId : mSelectedTracks) {
            // Get total time for a selected track
            String[] columns = new String[]{AnchorContract.AudioEntry.COLUMN_TIME};
            String sel = AnchorContract.AudioEntry._ID + "=?";
            String[] selArgs = {Long.toString(trackId)};

            Cursor c = getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                    columns, sel, selArgs, null, null);

            // Bail early if the cursor is null
            if (c == null) {
                return;
            } else if (c.getCount() < 1) {
                c.close();
                return;
            }

            int totalTime = 0;
            while (c.moveToNext()) {
                totalTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
            }
            c.close();

            Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, trackId);
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, totalTime);
            getContentResolver().update(uri, values, null, null);
            getContentResolver().notifyChange(uri, null);
        }
    }
}
