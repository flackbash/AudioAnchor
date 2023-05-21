package com.prangesoftwaresolutions.audioanchor.activities;

import android.Manifest;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.listeners.PlayStatusChangeListener;
import com.prangesoftwaresolutions.audioanchor.listeners.SynchronizationStateListener;
import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.receivers.PlayStatusReceiver;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.helpers.Synchronizer;
import com.prangesoftwaresolutions.audioanchor.adapters.AudioFileCursorAdapter;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.BitmapUtils;
import com.prangesoftwaresolutions.audioanchor.utils.DBAccessUtils;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class AlbumActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, PlayStatusChangeListener, SynchronizationStateListener {

    // The album uri and file
    private Album mAlbum;

    // Database variables
    private static final int ALBUM_LOADER = 0;
    private AudioFileCursorAdapter mCursorAdapter;

    // Layout variables
    ListView mListView;
    SwipeRefreshLayout mSwipeRefreshLayout;
    TextView mEmptyTV;
    ImageView mAlbumInfoCoverIV;
    TextView mAlbumInfoTitleTV;
    TextView mAlbumInfoTimeTV;
    FloatingActionButton mPlayPauseFAB;

    // Settings variables
    SharedPreferences mPrefs;
    boolean mShowHiddenFiles;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedTracks = new ArrayList<>();
    ArrayList<Long> mTmpSelectedTracks;
    // Used to disable scrolling in onLoadFinished for DB-ops started from within the activity
    boolean mScroll = true;

    // MediaPlayerService variables
    private MediaPlayerService mPlayer;
    boolean mServiceBound = false;
    boolean mDoNotBindService = false;
    Handler mHandler;
    Runnable mRunnable;
    int mAlbumLastCompletedTime;
    int mAlbumDuration;
    int mCurrAudioLastCompletedTime;
    long mCurrUpdatedAudioId;

    // Receivers
    PlayStatusReceiver mPlayStatusReceiver;

    // Synchronizer
    private Synchronizer mSynchronizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        long albumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mAlbum = Album.getAlbumByID(this, albumId);

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

        // Set up the shared preferences.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mShowHiddenFiles = mPrefs.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));

        // Initialize the cursor adapter
        mCursorAdapter = new AudioFileCursorAdapter(this, null);

        // Initialize synchronizer
        mSynchronizer = new Synchronizer(this);
        mSynchronizer.setListener(this);

        // Set up the views
        mAlbumInfoTitleTV = findViewById(R.id.album_info_title);
        mAlbumInfoTimeTV = findViewById(R.id.album_info_time);
        mAlbumInfoCoverIV = findViewById(R.id.album_info_cover);
        mPlayPauseFAB = findViewById(R.id.play_pause_fab);

        // Use a ListView and CursorAdapter to recycle space
        mListView = findViewById(R.id.list_album);
        mListView.setAdapter(mCursorAdapter);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList_album);
        mListView.setEmptyView(mEmptyTV);

        // Implement onItemClickListener for the list view
        mListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Check if the audio file exists
            AudioFile audio = AudioFile.getAudioFileById(AlbumActivity.this, rowId);

            if (audio == null || !(new File(audio.getPath())).exists()) {
                Toast.makeText(getApplicationContext(), R.string.play_error, Toast.LENGTH_LONG).show();
                return;
            }

            // If the MediaPlayerService is bound, check if it is playing the file that was
            // clicked. If not, stop the current service and let the PlayActivity start a new
            // one
            if (mServiceBound && mPlayer.getCurrentAudioFile().getID() != audio.getID()) {
                Log.e("AlbumActivity", "Unbinding Service ");
                unbindService(serviceConnection);
                mServiceBound = false;
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(new Intent(MediaPlayerService.BROADCAST_UNBIND_CURRENT_SERVICE));
                mPlayer.stopSelf();
            }

            // When returning to the Album or MainActivity next time, the service should be
            // bound again (unless the notification was removed in which case the flag is set to
            // true in the RemoveNotificationReceiver)
            mDoNotBindService = false;
            LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(new Intent(MediaPlayerService.BROADCAST_RESET));

            // Open the PlayActivity for the clicked audio file
            Intent intent = new Intent(AlbumActivity.this, PlayActivity.class);
            intent.putExtra(getString(R.string.curr_audio_id), rowId);
            startActivity(intent);
        });

        // See https://developer.android.com/guide/topics/ui/menus.html#CAB for details
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Adjust menu title and list of selected tracks when items are selected / de-selected
                if (b) {
                    mSelectedTracks.add(l);
                } else {
                    mSelectedTracks.remove(l);
                }
                String menuTitle = getResources().getQuantityString(R.plurals.items_selected,
                        mSelectedTracks.size(), mSelectedTracks.size());
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
                    case R.id.menu_delete:
                        // Check if app has the necessary permissions
                        if (ContextCompat.checkSelfPermission(AlbumActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            // This is necessary because requesting permission destroys action mode
                            // such that selected tracks are cleared
                            mTmpSelectedTracks = new ArrayList<>(mSelectedTracks);
                            ActivityCompat.requestPermissions(AlbumActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE);
                        } else {
                            deleteSelectedTracksWithConfirmation();
                        }

                        actionMode.finish();
                        return true;
                    case R.id.menu_delete_from_db:
                        deleteSelectedTracksFromDBWithConfirmation();
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_not_started:
                        for (long trackId : mSelectedTracks) {
                            // Skip scrolling in Loader if at least one DB operation was performed
                            // (and thus the Loader is called)
                            mScroll &= !DBAccessUtils.markTrackAsNotStarted(AlbumActivity.this, trackId);
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_completed:
                        for (long trackId : mSelectedTracks) {
                           mScroll &= !DBAccessUtils.markTrackAsCompleted(AlbumActivity.this, trackId);
                        }
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

        // Set up SwipeRefreshLayout onRefresh action
        mSwipeRefreshLayout = findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> mSynchronizer.updateDBTables());

        // Set up the FAB onClickListener
        mPlayPauseFAB.setOnClickListener(view -> {
            if (mPlayer == null) {
                return;
            }
            if (mPlayer.isPlaying()) {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PAUSE_AUDIO);
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(broadcastIntent);
            } else {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PLAY_AUDIO);
                LocalBroadcastManager.getInstance(AlbumActivity.this).sendBroadcast(broadcastIntent);
            }
        });

        // Bind to MediaPlayerService if it has been started by the PlayActivity
        bindToServiceIfRunning();

        // Set up play status receiver
        mPlayStatusReceiver = new PlayStatusReceiver(mPlayPauseFAB);
        mPlayStatusReceiver.setListener(this);

        // Register BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));

        // This needs to be a receiver for global broadcasts, as the deleteIntent is broadcast by
        // Android's notification framework
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindToServiceIfRunning();
        getLoaderManager().restartLoader(0, null, this);
    }


    @Override
    protected void onRestart() {
        // Synchronize if setting show-hidden-files has changed
        boolean currentShowHiddenFiles;
        currentShowHiddenFiles = mPrefs.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));
        if (mShowHiddenFiles != currentShowHiddenFiles) {
            mSwipeRefreshLayout.setRefreshing(true);
            mSynchronizer.updateDBTables();
            mShowHiddenFiles = currentShowHiddenFiles;
        }
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        if (mServiceBound) {
            unbindService(serviceConnection);
        }
        unregisterReceiver(mRemoveNotificationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);

        // Stop runnable from continuing to run in the background
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }

        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
        };

        String sel = AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbum.getID())};
        String sortOrder = "CAST(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar_album);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_audio_files);

        // Set album cover
        int reqSize = getResources().getDimensionPixelSize(R.dimen.album_info_height);
        BitmapUtils.setImage(mAlbumInfoCoverIV, mAlbum.getCoverPath(), reqSize);

        // Set the album info time
        int[] times = DBAccessUtils.getAlbumTimes(this, mAlbum.getID());
        Log.e("AlbumActivity", "Update AlbumLastCompletedTime");
        if (mPlayer != null) {
            mCurrAudioLastCompletedTime = mPlayer.getCurrentAudioFile().getCompletedTime();
            mCurrUpdatedAudioId = mPlayer.getCurrentAudioFile().getID();
        }
        mAlbumLastCompletedTime = times[0];
        mAlbumDuration = times[1];
        String timeStr = Utils.getTimeString(this, times[0], times[1]);
        mAlbumInfoTimeTV.setText(timeStr);

        mAlbumInfoTitleTV.setText(mAlbum.getTitle());

        // Swap the new cursor in. The framework will take care of closing the old cursor
        mCursorAdapter.swapCursor(cursor);

        // Scroll to the last played track, unless scrolling is skipped because the reload was
        // triggered by a DB op from within the AlbumActivity such as marking tracks as completed
        if (mScroll) scrollToLastPlayed(cursor);
        mScroll = true;
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

        return (super.onOptionsItemSelected(item));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MainActivity.PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
                } else {
                    mSelectedTracks = mTmpSelectedTracks;
                    deleteSelectedTracksWithConfirmation();
                }
                break;
            }
        }
    }

    /*
     * Bind the AlbumActivity to the MediaPlayerService if the service was started in the PlayActivity
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("AlbumActivity", "OnServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            mServiceBound = true;

            // Perform actions that can only be performed once the service is connected
            // Set up the play-pause FAB image according to the current MediaPlayerService state
            mPlayPauseFAB.setVisibility(View.VISIBLE);
            if (mPlayer.isPlaying()) {
                mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
            } else {
                mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
            }

            Log.e("AlbumActivity", "Update currAudioLastCompletedTime");
            if (mPlayer.getCurrentAudioFile().getAlbumId() == mAlbum.getID()) {
                setCompletedTimeUpdater();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            Log.e("AlbumActivity", "OnServiceDisconnected called");
        }
    };

    @Override
    public void onPlayMsgReceived() {
        mDoNotBindService = false;
    }

    @Override
    public void onSynchronizationFinished() {
        getLoaderManager().restartLoader(0, null, AlbumActivity.this);
        mSwipeRefreshLayout.setRefreshing(false);
        Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();

    }

    /*
     * Bind to MediaPlayerService if it has been started by the PlayActivity
     */
    private void bindToServiceIfRunning() {
        Log.e("AlbumActivity", "service bound: " + mServiceBound + "do not bind service: " + mDoNotBindService);
        if (!mServiceBound && !mDoNotBindService && Utils.isMediaPlayerServiceRunning(this)) {
            Log.e("AlbumActivity", "Service is running - binding service");
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            bindService(playerIntent, serviceConnection, BIND_AUTO_CREATE);
            mServiceBound = true;
        }
    }

    /*
     * Unbind AlbumActivity from MediaPlayerService when the user removes the notification
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("AlbumActivity", "Received broadcast 'remove notification'");
            if (mServiceBound) {
                unbindService(serviceConnection);
                mServiceBound = false;
            }
            mPlayPauseFAB.setVisibility(View.GONE);
            mDoNotBindService = true;
        }
    };

    /*
     * Update the progress for the currently playing ListView item as well as the album progress
     * while a track is playing
     */
    private void setCompletedTimeUpdater() {
        mHandler = new Handler();
        mRunnable = new Runnable() {
            @Override
            public void run() {
                // Stop runnable when service is unbound
                if (!mServiceBound) {
                    mHandler.removeCallbacks(mRunnable);
                    return;
                }

                // Get index of the current audio file in the list view
                StorageUtil storage = new StorageUtil(getApplicationContext());
                int index = storage.loadAudioIndex();

                // Get the ListView item for the current audio file
                View v = mListView.getChildAt(index - mListView.getFirstVisiblePosition());

                if (mPlayer != null && mPlayer.isPlaying() && mPlayer.getCurrentAudioFile().getID() == mCurrUpdatedAudioId) {
                    // Set the progress string for the currently playing ListView item
                    int completedTime = mPlayer.getCurrentPosition();
                    if (v != null) {
                        TextView durationTV = v.findViewById(R.id.audio_file_item_duration);
                        int duration = mPlayer.getCurrentAudioFile().getTime();
                        String timeStr = Utils.getTimeString(AlbumActivity.this, completedTime, duration);
                        durationTV.setText(timeStr);
                    }

                    // Set the progress string for the album
                    int currCompletedAlbumTime = mAlbumLastCompletedTime - mCurrAudioLastCompletedTime + completedTime;
                    String albumTimeStr = Utils.getTimeString(AlbumActivity.this, currCompletedAlbumTime, mAlbumDuration);
                    mAlbumInfoTimeTV.setText(albumTimeStr);
                }
                mHandler.postDelayed(this, 100);
            }
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    /*
     * Scroll to the last non-completed track in the list view
     */
    private void scrollToNotCompletedAudio(Cursor c) {
        // Loop through the database rows and check for non-completed tracks
        int scrollTo = 0;
        c.moveToFirst();
        while (c.moveToNext()) {
            int duration = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME));
            int completed = c.getInt(c.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            if (completed < duration || duration == 0) {
                break;
            }
            scrollTo += 1;
        }
        mListView.setSelection(Math.max(scrollTo - 1, 0));
    }

    /*
     * Scroll to the last played track in the list view
     */
    private void scrollToLastPlayed(Cursor c) {
        // Retrieve current last played ID
        long lastPlayedID = Album.getAlbumByID(this, mAlbum.getID()).getLastPlayedID();
        // Loop through the cursor rows and check for the id that matches the last played track
        int count = 0;
        int scrollTo = 0;
        c.moveToFirst();
        while (c.moveToNext()) {
            long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID));
            if (id == lastPlayedID) {
                scrollTo = count;
                break;
            }
            count += 1;
        }
        mListView.setSelection(Math.max(scrollTo, 0));
    }

    /*
     * Show a confirmation dialog and let the user decide whether to delete the selected
     * tracks from the database
     */
    private void deleteSelectedTracksFromDBWithConfirmation() {
        Long[] selectedTracks = new Long[mSelectedTracks.size()];
        final Long[] selectedTracksArr = mSelectedTracks.toArray(selectedTracks);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String confirmationMessage = getResources().getQuantityString(
                R.plurals.dialog_msg_remove_audio_from_db, mSelectedTracks.size());
        builder.setMessage(confirmationMessage);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete the tracks from the database
            int deletionCount = 0;
            for (long trackId : selectedTracksArr) {
                boolean deleted = DBAccessUtils.deleteTrackFromDB(AlbumActivity.this, trackId);
                if (deleted) {
                    deletionCount++;
                    mScroll = false;
                }
            }
            String deletedTracks = getResources().getQuantityString(R.plurals.tracks_removed_from_db,
                    deletionCount, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // User clicked the "Cancel" button, so dismiss the dialog
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Show a confirmation dialog and let the user decide whether to delete the selected tracks
     */
    private void deleteSelectedTracksWithConfirmation() {
        Long[] selectedTracks = new Long[mSelectedTracks.size()];
        final Long[] selectedTracksArr = mSelectedTracks.toArray(selectedTracks);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String confirmationMessage = getResources().getQuantityString(
                R.plurals.dialog_msg_delete_audio, mSelectedTracks.size());
        builder.setMessage(confirmationMessage);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete selected audio files
            int deletionCount = 0;
            for (long audioFileID : selectedTracksArr) {
                // Stop MediaPlayerService if the currently playing file is from deleted directory
                if (mPlayer != null) {
                    long activeAudioId = mPlayer.getCurrentAudioFile().getID();
                    if (activeAudioId == audioFileID) {
                        mPlayer.stopMedia();
                        mPlayer.stopSelf();
                    }
                }

                // Delete audio file
                boolean keepDeleted = mPrefs.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));
                AudioFile audioFile =  AudioFile.getAudioFileById(AlbumActivity.this, audioFileID);
                boolean deleted = Utils.deleteTrack(this, audioFile, keepDeleted);
                if (deleted) {
                    deletionCount += 1;
                    mScroll = false;
                }
            }
            mSynchronizer.updateDBTables();
            String deletedTracks = getResources().getQuantityString(R.plurals.tracks_deleted,
                    deletionCount, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
            mSelectedTracks.clear();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // User clicked the "Cancel" button, so dismiss the dialog
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
