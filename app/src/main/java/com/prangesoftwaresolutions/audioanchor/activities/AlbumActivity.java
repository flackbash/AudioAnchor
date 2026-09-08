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
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
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
import com.prangesoftwaresolutions.audioanchor.utils.TrackSortUtils;
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
    String mTrackSortOrderPref;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedTracks = new ArrayList<>();
    ArrayList<Long> mTmpSelectedTracks;
    // Used to disable scrolling in onLoadFinished for DB-ops started from within the activity
    boolean mScroll = true;
    // Armed before an action that should re-sync the active playback session's autoplay queue
    // (a pin toggle, or -- detected in onRestart() -- a "Track sort order" preference change) on
    // the next onLoadFinished(), which otherwise also fires for all sorts of routine background
    // writes (periodic position save, last-played timestamp, a sync) that must NOT resync the
    // queue -- see onLoadFinished() for why that distinction matters.
    boolean mSyncPlaybackQueueOnNextLoad = false;

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

    static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE = 1;
    // Request code for the "All files access" system settings screen (Android 11+), not a
    // runtime permission request, so it's handled in onActivityResult() rather than
    // onRequestPermissionsResult().
    static final int PERMISSION_REQUEST_MANAGE_STORAGE_DELETE = 2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        // Get the uri of the recipe sent via the intent
        long albumId = getIntent().getLongExtra(getString(R.string.album_id), -1);
        mAlbum = Album.getAlbumByID(this, albumId);

        // Set up the shared preferences. Must happen before initLoader() below, since
        // initLoader() synchronously calls onCreateLoader(), which now reads the track sort
        // order preference.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mShowHiddenFiles = mPrefs.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));
        mTrackSortOrderPref = mPrefs.getString(getString(R.string.settings_track_sort_order_key), getString(R.string.settings_track_sort_order_default));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(ALBUM_LOADER, null, this);

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
                        if (hasStorageWritePermission()) {
                            deleteSelectedTracksWithConfirmation();
                        } else {
                            // This is necessary because requesting permission destroys action mode
                            // such that selected tracks are cleared
                            mTmpSelectedTracks = new ArrayList<>(mSelectedTracks);
                            requestStorageWritePermission();
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
                    case R.id.menu_pin:
                        mSyncPlaybackQueueOnNextLoad = true;
                        for (long trackId : mSelectedTracks) {
                            DBAccessUtils.pinTrack(AlbumActivity.this, trackId, true);
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_unpin:
                        mSyncPlaybackQueueOnNextLoad = true;
                        for (long trackId : mSelectedTracks) {
                            DBAccessUtils.pinTrack(AlbumActivity.this, trackId, false);
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
        IntentFilter removeNotificationIntentFilter =  new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION);
        ContextCompat.registerReceiver(this, mRemoveNotificationReceiver, removeNotificationIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindToServiceIfRunning();
        // Returning to this screen (e.g. from PlayActivity, where the user may have started a
        // different track) is a legitimate reason to jump back to the currently playing track --
        // see the comment in onLoadFinished() for why that's not the default for every reload.
        mScroll = true;
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

        // Re-sync the active playback session's autoplay queue (see onLoadFinished()) if the
        // "Track sort order" preference changed while we were away, e.g. in Settings.
        String currentTrackSortOrderPref = mPrefs.getString(getString(R.string.settings_track_sort_order_key), getString(R.string.settings_track_sort_order_default));
        if (!mTrackSortOrderPref.equals(currentTrackSortOrderPref)) {
            mSyncPlaybackQueueOnNextLoad = true;
            mTrackSortOrderPref = currentTrackSortOrderPref;
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

        mSynchronizer.shutdown();

        super.onDestroy();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_PINNED,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_DATE_ADDED,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME,
                AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP,
        };

        String sel = AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbum.getID())};

        // The actual ordering (including natural-order title comparison, which SQLite can't do
        // -- see NaturalOrderComparator) is applied in Java in onLoadFinished(). This is just a
        // stable baseline order for the query itself.
        String sortOrder = AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry._ID + " ASC";

        return new CursorLoader(this, AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    private static final class TrackRow implements TrackSortUtils.SortableTrack {
        final long id;
        final String title;
        final boolean pinned;
        final long dateAdded;
        final int time;
        final int completedTime;
        final long lastPlayed;

        TrackRow(long id, String title, boolean pinned, long dateAdded, int time, int completedTime, long lastPlayed) {
            this.id = id;
            this.title = title;
            this.pinned = pinned;
            this.dateAdded = dateAdded;
            this.time = time;
            this.completedTime = completedTime;
            this.lastPlayed = lastPlayed;
        }

        @Override
        public String getTitle() { return title; }
        @Override
        public boolean isPinned() { return pinned; }
        @Override
        public long getDateAdded() { return dateAdded; }
        @Override
        public long getLastPlayedTimestamp() { return lastPlayed; }
        @Override
        public int getTime() { return time; }
        @Override
        public int getCompletedTime() { return completedTime; }
    }

    /*
     * Re-orders the tracks in `cursor` the same way TrackSortUtils orders them for autoplay's
     * next/previous-track queue (see PlayActivity.storeAudioFiles()) -- natural-order title as
     * the tiebreaker, the selected "Track sort order" preference, then pinned tracks floated to
     * the top. Applied here in Java rather than as SQL because natural sort isn't expressible in
     * a plain SQLite ORDER BY. Returns a new Cursor with the same _ID/TITLE/PINNED columns the
     * adapter reads, in the corrected order.
     */
    private Cursor sortTracksNaturally(Cursor cursor) {
        ArrayList<TrackRow> rows = new ArrayList<>(cursor.getCount());
        int idIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID);
        int titleIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TITLE);
        int pinnedIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_PINNED);
        int dateAddedIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_DATE_ADDED);
        int timeIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_TIME);
        int completedTimeIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME);
        int lastPlayedIdx = cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP);

        while (cursor.moveToNext()) {
            rows.add(new TrackRow(
                    cursor.getLong(idIdx),
                    cursor.getString(titleIdx),
                    cursor.getInt(pinnedIdx) != 0,
                    cursor.isNull(dateAddedIdx) ? -1 : cursor.getLong(dateAddedIdx),
                    cursor.getInt(timeIdx),
                    cursor.getInt(completedTimeIdx),
                    cursor.isNull(lastPlayedIdx) ? -1 : cursor.getLong(lastPlayedIdx)));
        }

        TrackSortUtils.sort(this, rows);

        MatrixCursor sorted = new MatrixCursor(new String[]{
                AnchorContract.AudioEntry._ID, AnchorContract.AudioEntry.COLUMN_TITLE, AnchorContract.AudioEntry.COLUMN_PINNED
        }, rows.size());
        for (TrackRow row : rows) {
            sorted.addRow(new Object[]{row.id, row.title, row.pinned ? 1 : 0});
        }
        return sorted;
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

        // Re-sort using natural-order title comparison (see sortTracksNaturally()), then swap
        // the resulting cursor into the adapter. The Loader still owns and closes `cursor`.
        Cursor sortedCursor = sortTracksNaturally(cursor);

        // If a track from this album is the one actually playing, refresh the service's autoplay
        // queue to match what's now displayed -- but only when mSyncPlaybackQueueOnNextLoad was
        // explicitly armed beforehand (a pin toggle, or a "Track sort order" preference change
        // caught in onRestart()). onLoadFinished() also fires for routine background writes that
        // must NOT resync the queue -- notably the service's own periodic position save while
        // playing, which under "sort by progress" would otherwise reorder the queue on every tick
        // and reintroduce exactly the live-drifting instability a frozen per-session queue (see
        // MediaPlayerService.updateAudioIdQueue()) is meant to avoid.
        if (mSyncPlaybackQueueOnNextLoad && mPlayer != null) {
            AudioFile activeAudio = mPlayer.getCurrentAudioFile();
            if (activeAudio != null && activeAudio.getAlbumId() == mAlbum.getID()) {
                ArrayList<Long> orderedIds = new ArrayList<>(sortedCursor.getCount());
                int idIdx = sortedCursor.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID);
                for (sortedCursor.moveToFirst(); !sortedCursor.isAfterLast(); sortedCursor.moveToNext()) {
                    orderedIds.add(sortedCursor.getLong(idIdx));
                }
                sortedCursor.moveToPosition(-1);
                mPlayer.updateAudioIdQueue(orderedIds);
            }
        }
        mSyncPlaybackQueueOnNextLoad = false;

        mCursorAdapter.swapCursor(sortedCursor);

        // Only scroll to the last played track when mScroll was explicitly armed beforehand (see
        // onResume() and onCreate()'s initial value) -- a reload can also be triggered by all
        // sorts of unrelated background writes this Activity had no part in and shouldn't jump
        // the list for: the service's periodic position save while playing, its last-played
        // timestamp update on every play/pause, or a sync completing. Default back to not
        // scrolling after consuming it, so the *next* reload -- whatever triggers it -- doesn't
        // scroll unless something explicitly re-arms this again.
        if (mScroll) scrollToLastPlayed(sortedCursor);
        mScroll = false;
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
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE: {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_MANAGE_STORAGE_DELETE) {
            // The system settings screen has no result code for grant/deny, so just re-check
            // the actual permission state once the user returns to the app.
            if (hasStorageWritePermission()) {
                mSelectedTracks = mTmpSelectedTracks;
                deleteSelectedTracksWithConfirmation();
            } else {
                Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    /*
     * On Android 11+ (API 30+), targeting API 30+ makes scoped storage mandatory and
     * requestLegacyExternalStorage a no-op, so a plain File.delete() only works on files this
     * app created itself. WRITE_EXTERNAL_STORAGE can't help -- it's capped at maxSdkVersion 29
     * in the manifest, so it can never actually be granted on these versions, which used to make
     * every delete attempt fail with no way to fix it (see issue #218). "All files access"
     * (MANAGE_EXTERNAL_STORAGE) is what actually restores raw file deletion there.
     */
    private boolean hasStorageWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // "All files access" is not self-explanatory, so explain it before sending the user
            // off to the system settings screen rather than leaving them to wonder.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.storage_permission_rationale_title)
                    .setMessage(R.string.storage_permission_rationale_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_msg_ok, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse(getString(R.string.app_package_uri)));
                        startActivityForResult(intent, PERMISSION_REQUEST_MANAGE_STORAGE_DELETE);
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE);
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
            updatePlayPauseFABIcon();

            Log.e("AlbumActivity", "Update currAudioLastCompletedTime");
            if (mPlayer.getCurrentAudioFile().getAlbumId() == mAlbum.getID()) {
                // The Loader's onLoadFinished() only refreshes these two fields when it runs
                // while the service is already bound. Binding here is async, same as the
                // Loader's own reload, so onLoadFinished() can just as easily win that race and
                // run first -- with mPlayer still null -- leaving them stale for whichever
                // track was playing before. That stale mCurrUpdatedAudioId then makes the live
                // per-100ms album time updater below silently no-op until the next periodic DB
                // save (up to 15s later) triggers another reload, this time with mPlayer bound.
                // Set them here too so they're correct the moment the service connects,
                // regardless of which of the two races first.
                mCurrAudioLastCompletedTime = mPlayer.getCurrentAudioFile().getCompletedTime();
                mCurrUpdatedAudioId = mPlayer.getCurrentAudioFile().getID();
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
    public void onPauseMsgReceived() {
        updatePlayPauseFABIcon();
    }

    /*
     * Show the play, pause, or replay icon on the play-pause FAB: pause while actually playing,
     * replay if the current track has already finished (pressing play restarts it from the
     * beginning -- see issue #196), otherwise play. Mirrors PlayActivity.updatePlayPauseIcon().
     */
    private void updatePlayPauseFABIcon() {
        if (mPlayer == null) {
            return;
        }
        if (mPlayer.isPlaying()) {
            mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
        } else if (Utils.isFinished(mPlayer.getCurrentAudioFile(), mPlayer.getCurrentPosition())) {
            mPlayPauseFAB.setImageResource(R.drawable.ic_replay_white);
        } else {
            mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
        }
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

                // Find the currently visible ListView row for the playing track by matching its
                // stable row id, not its position in the playback queue -- that queue position
                // (StorageUtil.loadAudioIndex()) is fixed when playback starts and goes stale,
                // silently pointing at the wrong row, as soon as another track above it is
                // deleted from the list (see issue #207).
                View v = null;
                int firstVisible = mListView.getFirstVisiblePosition();
                for (int i = 0; i < mListView.getChildCount(); i++) {
                    if (mListView.getItemIdAtPosition(firstVisible + i) == mCurrUpdatedAudioId) {
                        v = mListView.getChildAt(i);
                        break;
                    }
                }

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
