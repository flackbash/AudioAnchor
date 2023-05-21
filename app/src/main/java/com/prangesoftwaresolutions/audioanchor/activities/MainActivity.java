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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.dialogs.FileDialog;
import com.prangesoftwaresolutions.audioanchor.helpers.Migrator;
import com.prangesoftwaresolutions.audioanchor.listeners.PlayStatusChangeListener;
import com.prangesoftwaresolutions.audioanchor.listeners.SynchronizationStateListener;
import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.receivers.PlayStatusReceiver;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.helpers.Synchronizer;
import com.prangesoftwaresolutions.audioanchor.adapters.AlbumCursorAdapter;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.DBAccessUtils;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>, PlayStatusChangeListener, SynchronizationStateListener {

    // Preferences
    private SharedPreferences mSharedPreferences;
    private boolean mShowHiddenFiles;

    // Database variables
    private static final int AUDIO_LOADER = 0;
    private AlbumCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;
    ListView mListView;
    SwipeRefreshLayout mSwipeRefreshLayout;
    FloatingActionButton mPlayPauseFAB;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedAlbums = new ArrayList<>();
    ArrayList<Long> mTmpSelectedAlbums;
    ArrayList<AudioFile> mTmpAlbumAudioFiles;

    // Permission request
    private static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0;
    static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE = 1;
    static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_EXPORT = 2;

    // MediaPlayerService variables
    private MediaPlayerService mPlayer;
    boolean mServiceBound = false;
    boolean mDoNotBindService = false;
    Handler mHandler;
    Runnable mRunnable;
    int mAlbumLastCompletedTime;
    int mAlbumDuration;
    int mCurrAudioLastCompletedTime;
    int mCurrPlayingAlbumPosition;
    long mCurrUpdatedAudioId;

    // Receivers
    PlayStatusReceiver mPlayStatusReceiver;

    // Synchronizer
    private Synchronizer mSynchronizer;

    // Migrator
    private Migrator mMigrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the shared preferences.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mShowHiddenFiles = mSharedPreferences.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new AlbumCursorAdapter(this, null);

        // Initialize synchronizer
        mSynchronizer = new Synchronizer(this);
        mSynchronizer.setListener(this);

        // Initialize migrator
        mMigrator = new Migrator(this);

        // Use a ListView and CursorAdapter to recycle space
        mListView = findViewById(R.id.list);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList);
        mListView.setEmptyView(mEmptyTV);

        mListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Open the AlbumActivity for the clicked album
            Intent intent = new Intent(MainActivity.this, AlbumActivity.class);
            intent.putExtra(getString(R.string.album_id), rowId);
            startActivity(intent);
        });

        // See https://developer.android.com/guide/topics/ui/menus.html#CAB for details
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Adjust menu title and list of selected albums when items are selected / de-selected
                if (b) {
                    mSelectedAlbums.add(l);
                } else {
                    mSelectedAlbums.remove(l);
                }
                String menuTitle = getResources().getQuantityString(R.plurals.items_selected,
                        mSelectedAlbums.size(), mSelectedAlbums.size());
                actionMode.setTitle(menuTitle);
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Inflate the menu for the CAB
                getMenuInflater().inflate(R.menu.menu_main_cab, menu);
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
                // Get track ids for all selected albums
                ArrayList<AudioFile>  albumAudioFiles = new ArrayList<>();
                for (long albumId : mSelectedAlbums) {
                    albumAudioFiles.addAll(AudioFile.getAllAudioFilesInAlbum(MainActivity.this, albumId, null));
                }

                // Perform action
                switch (menuItem.getItemId()) {
                    case R.id.menu_delete_from_db:
                        // Delete selected albums from db if they do not exist in the file system anymore
                        deleteSelectedAlbumsFromDBWithConfirmation();
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_not_started:
                        // Mark all tracks in the selected albums as not started
                        for (AudioFile audioFile : albumAudioFiles) {
                            DBAccessUtils.markTrackAsNotStarted(MainActivity.this, audioFile.getID());
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_completed:
                        // Mark all tracks in the selected albums as completed
                        for (AudioFile audioFile : albumAudioFiles) {
                            DBAccessUtils.markTrackAsCompleted(MainActivity.this, audioFile.getID());
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
                mSelectedAlbums.clear();
            }
        });

        // Set up SwipeRefreshLayout onRefresh action
        mSwipeRefreshLayout = findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(() -> mSynchronizer.updateDBTables());

        mPlayPauseFAB = findViewById(R.id.play_pause_fab);

        // Set up the FAB onClickListener
        mPlayPauseFAB.setOnClickListener(view -> {
            if (mPlayer == null) {
                return;
            }
            if (mPlayer.isPlaying()) {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PAUSE_AUDIO);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent);
                mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
            } else {
                Intent broadcastIntent = new Intent(PlayActivity.BROADCAST_PLAY_AUDIO);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent);
                mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
            }
        });

        // Check if app has the necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            mListView.setAdapter(mCursorAdapter);
        }

        // Bind to MediaPlayerService if it has been started by the PlayActivity
        bindToServiceIfRunning();

        // Set up play status receiver
        mPlayStatusReceiver = new PlayStatusReceiver(mPlayPauseFAB);
        mPlayStatusReceiver.setListener(this);

        // Register BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mUnbindCurrentServiceReceiver, new IntentFilter(MediaPlayerService.BROADCAST_UNBIND_CURRENT_SERVICE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mResetReceiver, new IntentFilter(MediaPlayerService.BROADCAST_RESET));

        // This needs to be a receiver for global broadcasts, as the deleteIntent is broadcast by
        // Android's notification framework
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));

        // Extract version code and version name of the app
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (pInfo != null) {
            int previousVersionCode = mSharedPreferences.getInt(getString(R.string.preference_version_code_key), Integer.parseInt(getString(R.string.preference_version_code_default)));
            int versionCode = pInfo.versionCode;
            String versionName = pInfo.versionName;
            if (previousVersionCode != versionCode) {
                // TODO: Show Changelog
                // Save new version code and name
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putInt(getString(R.string.preference_version_code_key), versionCode);
                editor.putString(getString(R.string.preference_version_name_key), versionName);
                editor.apply();
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String sortOrderPref = mSharedPreferences.getString(getString(R.string.settings_sort_order_key), getString(R.string.settings_sort_order_default));
        String sortOrder = "";
        if (sortOrderPref.equals(getString(R.string.settings_sort_order_by_directory_value))) {
            sortOrder += AnchorContract.AlbumEntry.COLUMN_DIRECTORY + " ASC, ";
        }
        sortOrder += "CAST(" + AnchorContract.AlbumEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AlbumEntry.COLUMN_TITLE + ") ASC";
        return new CursorLoader(this, AnchorContract.AlbumEntry.CONTENT_URI, Album.getColumns(), null, null, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_albums);

        if (mPlayer != null) {
            long albumId = mPlayer.getCurrentAudioFile().getAlbumId();

            // Set the album completion time
            int[] times = DBAccessUtils.getAlbumTimes(this, albumId);
            mCurrUpdatedAudioId = mPlayer.getCurrentAudioFile().getID();
            mCurrAudioLastCompletedTime = mPlayer.getCurrentAudioFile().getCompletedTime();
            mAlbumLastCompletedTime = times[0];
            mAlbumDuration = times[1];

            // Get the position of the currently playing ListView item
            int currPosition = -1;
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (true) {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow(AnchorContract.AlbumEntry._ID));
                    currPosition++;
                    if (id == albumId) {
                        mCurrPlayingAlbumPosition = currPosition;
                        break;
                    }
                    if (!cursor.moveToNext()) {
                        break;
                    }
                }
            }
        }

        // Swap the new cursor in. The framework will take care of closing the old cursor
        mCursorAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished() is about to be closed.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    protected void onRestart() {
        // Synchronize if setting show-hidden-files has changed
        boolean currentShowHiddenFiles;
        currentShowHiddenFiles = mSharedPreferences.getBoolean(getString(R.string.settings_show_hidden_key), Boolean.getBoolean(getString(R.string.settings_show_hidden_default)));
        if (mShowHiddenFiles != currentShowHiddenFiles) {
            mSwipeRefreshLayout.setRefreshing(true);
            mSynchronizer.updateDBTables();
            mShowHiddenFiles = currentShowHiddenFiles;
        }
        super.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindToServiceIfRunning();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    protected void onDestroy() {
        if (mServiceBound) {
            unbindService(serviceConnection);
        }
        unregisterReceiver(mRemoveNotificationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mUnbindCurrentServiceReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mResetReceiver);

        // Stop runnable from continuing to run in the background
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.permission_denied, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    mListView.setAdapter(mCursorAdapter);
                }
                break;
            }
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_EXPORT: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
                } else {
                    showExportDirectorySelector();
                }
                break;
            }
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_DELETE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.write_permission_denied, Toast.LENGTH_LONG).show();
                } else {
                    mSelectedAlbums = mTmpSelectedAlbums;
                    deleteSelectedAlbumWithConfirmation(mTmpAlbumAudioFiles);
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
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_READ_EXTERNAL_STORAGE);
                } else {
                    Intent directoryIntent = new Intent(this, DirectoryActivity.class);
                    startActivity(directoryIntent);
                }
                return true;
            case R.id.menu_export:
                // Check if app has the necessary permissions
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE_EXPORT);
                } else {
                    showExportDirectorySelector();
                }
                return true;
            case R.id.menu_import:
                showImportFileSelector();
                return true;
            case R.id.menu_synchronize:
                mSwipeRefreshLayout.setRefreshing(true);
                mSynchronizer.updateDBTables();
                return true;
            case R.id.menu_settings:
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

    /*
     * Bind the MainActivity to the MediaPlayerService if the service was started in the PlayActivity
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("MainActivity", "OnServiceConnected called");
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

            setCompletedTimeUpdater();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
            Log.e("MainActivity", "OnServiceDisconnected called");
        }
    };

    @Override
    public void onPlayMsgReceived() {
        mDoNotBindService = false;
    }

    @Override
    public void onSynchronizationFinished() {
        getLoaderManager().restartLoader(0, null, MainActivity.this);
        mSwipeRefreshLayout.setRefreshing(false);
        Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();
    }

    /*
     * Bind to MediaPlayerService if it has been started by the PlayActivity
     */
    private void bindToServiceIfRunning() {
        if (!mServiceBound && !mDoNotBindService && Utils.isMediaPlayerServiceRunning(this)) {
            Log.e("MainActivity", "Service is running - binding service");
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            bindService(playerIntent, serviceConnection, BIND_AUTO_CREATE);
            mServiceBound = true;
        }
    }

    /*
     * Unbind MainActivity from MediaPlayerService when the user removes the notification
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MainActivity", "Received broadcast 'remove notification'");
            if (mServiceBound) {
                unbindService(serviceConnection);
                mServiceBound = false;
            }
            mPlayPauseFAB.setVisibility(View.GONE);
            mDoNotBindService = true;
        }
    };

    /*
     * Unbind MainActivity from MediaPlayerService but bind again when returning to activity
     */
    private final BroadcastReceiver mUnbindCurrentServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MainActivity", "Received broadcast 'unbind current service'");
            if (mServiceBound) {
                unbindService(serviceConnection);
                mServiceBound = false;
            }
            mPlayPauseFAB.setVisibility(View.GONE);
            // Reset doNotBind flag
            mDoNotBindService = false;
        }
    };

    /*
     * Unbind MainActivity from MediaPlayerService when the user removes the notification
     */
    private final BroadcastReceiver mResetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MainActivity", "Received broadcast 'reset'");
            mDoNotBindService = false;
        }
    };

    /*
     * Update the progress for the album of the currently playing audio file
     */
    private void setCompletedTimeUpdater() {
        mHandler = new Handler();
        mRunnable = () -> {
            // Stop runnable when service is unbound
            if (!mServiceBound) {
                mHandler.removeCallbacks(mRunnable);
                return;
            }

            // Get the ListView item for the current audio file
            View v = mListView.getChildAt(mCurrPlayingAlbumPosition - mListView.getFirstVisiblePosition());

            if (v == null) {
                mHandler.postDelayed(mRunnable, 100);
                return;
            }
            TextView durationTV = v.findViewById(R.id.album_info_time_album);

            if (mPlayer != null && mPlayer.isPlaying() && mPlayer.getCurrentAudioFile().getID() == mCurrUpdatedAudioId) {
                // Set the progress string for the album of the currently playing audio file
                int completedTime = mPlayer.getCurrentPosition();
                int currCompletedAlbumTime = mAlbumLastCompletedTime - mCurrAudioLastCompletedTime + completedTime;
                String albumTimeStr = Utils.getTimeString(MainActivity.this, currCompletedAlbumTime, mAlbumDuration);
                durationTV.setText(albumTimeStr);
            }
            mHandler.postDelayed(mRunnable, 100);
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    /*
     * Show file selector where the user can select a directory to which to export the database
     */
    private void showExportDirectorySelector() {
        // Let the user select a directory in which to save the database
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, true, null, this);
        fileDialog.addDirectoryListener(file -> mMigrator.exportDatabase(file));
        fileDialog.showDialog();
    }

    /*
     * Show file selector where the user can select a .db file from which to import a database
     */
    private void showImportFileSelector() {
        File baseDirectory = Environment.getExternalStorageDirectory();
        FileDialog fileDialog = new FileDialog(this, baseDirectory, false, ".db", this);
        fileDialog.addFileListener(file -> {
            mMigrator.importDatabase(file);
            mSwipeRefreshLayout.setRefreshing(true);
            mSynchronizer.updateDBTables();
        });
        fileDialog.showDialog();
    }

    /*
     * Show a confirmation dialog and let the user decide whether to delete the selected albums
     * and / or its tracks from the database
     */
    private void deleteSelectedAlbumsFromDBWithConfirmation() {
        Long[] selectedAlbumsTmpArr = new Long[mSelectedAlbums.size()];
        final Long[] selectedAlbumsArr = mSelectedAlbums.toArray(selectedAlbumsTmpArr);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String confirmationMessage = getResources().getQuantityString(
                R.plurals.dialog_msg_remove_album_from_db, mSelectedAlbums.size());
        builder.setMessage(confirmationMessage);
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete the album and / or tracks from the database
            int deletionCount = 0;
            for (long albumId : selectedAlbumsArr) {
                boolean deleted = DBAccessUtils.deleteAlbumFromDB(MainActivity.this, albumId);
                if (deleted) deletionCount++;
            }
            String deletedAlbums = getResources().getQuantityString(R.plurals.albums_removed_from_db,
                    deletionCount, deletionCount);
            Toast.makeText(getApplicationContext(), deletedAlbums, Toast.LENGTH_LONG).show();
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
     * Show a confirmation dialog and let the user decide whether to delete the selected albums
     * and / or its tracks from the database
     */
    private void deleteSelectedAlbumWithConfirmation(final ArrayList<AudioFile> selectedTracks) {
        Long[] selectedAlbumsTmpArr = new Long[mSelectedAlbums.size()];
        final Long[] selectedAlbumsArr = mSelectedAlbums.toArray(selectedAlbumsTmpArr);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String confirmationMessage = getResources().getQuantityString(
                R.plurals.dialog_msg_delete_album, mSelectedAlbums.size());
        builder.setMessage(confirmationMessage);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // Delete tracks within the selected albums
            int trackDeletionCount = 0;
            for (AudioFile audioFile : selectedTracks) {
                long audioFileID = audioFile.getID();

                // Stop MediaPlayerService if the currently playing file is from deleted directory
                if (mPlayer != null) {
                    long activeAudioId = mPlayer.getCurrentAudioFile().getID();
                    if (activeAudioId == audioFileID) {
                        mPlayer.stopMedia();
                        mPlayer.stopSelf();
                    }
                }

                boolean keepDeleted = mSharedPreferences.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));
                boolean deleted = Utils.deleteTrack(this, audioFile, keepDeleted);
                if (deleted) trackDeletionCount += 1;
            }

            // Delete selected albums
            int albumDeletionCount = 0;
            for (long albumId : selectedAlbumsArr) {
                Album album = Album.getAlbumByID(this, albumId);
                if (album != null) {
                    File albumDir = new File(album.getPath());
                    boolean deleted = Utils.deleteRecursively(albumDir);
                    if (deleted) {
                        albumDeletionCount++;
                        DBAccessUtils.deleteAlbumFromDB(MainActivity.this, albumId);
                    }
                }
            }

            // Update database tables and notify user about the deletion
            mSynchronizer.updateDBTables();
            String deletedAlbums = getResources().getQuantityString(R.plurals.quant_albums, albumDeletionCount, albumDeletionCount);
            String deletedTracks = getResources().getQuantityString(R.plurals.quant_tracks, trackDeletionCount, trackDeletionCount);
            String deletedFiles = getResources().getString(R.string.album_and_tracks_deleted, deletedAlbums, deletedTracks);
            Toast.makeText(getApplicationContext(), deletedFiles, Toast.LENGTH_LONG).show();
            mSelectedAlbums.clear();
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
