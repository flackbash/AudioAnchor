package com.prangesoftwaresolutions.audioanchor;

import android.Manifest;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.data.AnchorDbHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;


public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // The audio storage directory
    private File mDirectory;
    private String mPrefDirectory;

    // Preferences
    private SharedPreferences mSharedPreferences;
    private boolean mDarkTheme;

    // Database variables
    private static final int AUDIO_LOADER = 0;
    private AlbumCursorAdapter mCursorAdapter;

    // Layout variables
    TextView mEmptyTV;
    ListView mListView;
    FloatingActionButton mPlayPauseFAB;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedAlbums = new ArrayList<>();

    // Permission request
    private static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 0;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

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
    int mCurrUpdatedAudioId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the shared preferences.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefDirectory = mSharedPreferences.getString(getString(R.string.preference_filename), null);
        mDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new AlbumCursorAdapter(this, null);

        // Use a ListView and CursorAdapter to recycle space
        mListView = findViewById(R.id.list);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList);
        mListView.setEmptyView(mEmptyTV);

        mListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Open the AlbumActivity for the clicked album
            Intent intent = new Intent(MainActivity.this, AlbumActivity.class);
            intent.putExtra(getString(R.string.album_id), rowId);
            String albumName = ((TextView) view.findViewById(R.id.audio_storage_item_title)).getText().toString();
            intent.putExtra(getString(R.string.album_name), albumName);
            startActivity(intent);
        });

        // See https://developer.android.com/guide/topics/ui/menus.html#CAB for details
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Adjust menu title and list of selected tracks when items are selected / de-selected
                if (b) {
                    mSelectedAlbums.add(l);
                } else {
                    mSelectedAlbums.remove(l);
                }
                String menuTitle = getResources().getString(R.string.items_selected, mSelectedAlbums.size());
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
                ArrayList<Long> trackIds = new ArrayList<>();
                switch (menuItem.getItemId()) {
                    case R.id.menu_delete_from_db:
                        // Get track ids for all selected albums
                        for (long albumId : mSelectedAlbums) {
                            ArrayList<Long> albumTrackIds = DBAccessUtils.getTrackIdsForAlbum(MainActivity.this, albumId);
                            trackIds.addAll(albumTrackIds);
                        }

                        // Delete all tracks in the selected albums from db if they do not exist in the file system anymore
                        deleteSelectedTracksFromDBWithConfirmation(trackIds);
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_not_started:
                        // Get track ids for all selected albums
                        for (long albumId : mSelectedAlbums) {
                            ArrayList<Long> albumTrackIds = DBAccessUtils.getTrackIdsForAlbum(MainActivity.this, albumId);
                            trackIds.addAll(albumTrackIds);
                        }

                        // Mark all tracks in the selected albums as not started
                        for (long trackId : trackIds) {
                            DBAccessUtils.markTrackAsNotStarted(MainActivity.this, trackId);
                        }
                        actionMode.finish();
                        return true;
                    case R.id.menu_mark_as_completed:
                        // Get track ids for all selected albums
                        for (long albumId : mSelectedAlbums) {
                            ArrayList<Long> albumTrackIds = DBAccessUtils.getTrackIdsForAlbum(MainActivity.this, albumId);
                            trackIds.addAll(albumTrackIds);
                        }

                        // Mark all tracks in the selected albums as completed
                        for (long trackId : trackIds) {
                            DBAccessUtils.markTrackAsCompleted(MainActivity.this, trackId);
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
            }
        }

        // Bind to MediaPlayerService if it has been started by the PlayActivity
        bindToServiceIfRunning();

        // Register BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mUnbindCurrentServiceReceiver, new IntentFilter(MediaPlayerService.BROADCAST_UNBIND_CURRENT_SERVICE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mResetReceiver, new IntentFilter(MediaPlayerService.BROADCAST_RESET));

        // This needs to be a receiver for global broadcasts, as the deleteIntent is broadcast by
        // Android's notification framework
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // Set the projection to retrieve the relevant columns from the table
        String[] projection = {
                AnchorContract.AlbumEntry._ID,
                AnchorContract.AlbumEntry.COLUMN_TITLE,
                AnchorContract.AlbumEntry.COLUMN_COVER_PATH};

        String sortOrder = "CAST(" + AnchorContract.AlbumEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AlbumEntry.COLUMN_TITLE + ") ASC";

        return new CursorLoader(this, AnchorContract.AlbumEntry.CONTENT_URI, projection, null, null, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_albums);

        if (mPlayer != null) {
            int albumId = mPlayer.getCurrentAudioFile().getAlbumId();

            // Set the album completion time
            int[] times = DBAccessUtils.getAlbumTimes(this, albumId);
            Log.e("MainActivity", "Update AlbumLastCompletedTime");
            mCurrUpdatedAudioId = mPlayer.getCurrentAudioFile().getId();
            mCurrAudioLastCompletedTime = mPlayer.getCurrentAudioFile().getCompletedTime();
            mAlbumLastCompletedTime = times[0];
            mAlbumDuration = times[1];

            // Get the position of the currently playing ListView item
            int currPosition = -1;
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                while (true) {
                    int id = cursor.getInt(cursor.getColumnIndex(AnchorContract.AlbumEntry._ID));
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
        // Recreate if theme has changed
        boolean currentDarkTheme;
        currentDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        if (mDarkTheme != currentDarkTheme) {
            recreate();
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
        switch (requestCode) {
            case PERMISSION_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
                    Toast.makeText(getApplicationContext(), R.string.permission_denied, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    mListView.setAdapter(mCursorAdapter);

                    if (mPrefDirectory == null) {
                        showChangeDirectorySelector();
                    } else {
                        mDirectory = new File(mPrefDirectory);
                        updateDBTables();
                    }
                }
                break;
            }
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // Permission was not granted
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
                updateDBTables();
                getLoaderManager().restartLoader(0, null, this);
                Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();
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
    private ServiceConnection serviceConnection = new ServiceConnection() {
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
    private BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
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
    private BroadcastReceiver mUnbindCurrentServiceReceiver = new BroadcastReceiver() {
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
    private BroadcastReceiver mResetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MainActivity", "Received broadcast 'reset'");
            mDoNotBindService = false;
        }
    };

    // BroadcastReceivers, all related to service events
    private BroadcastReceiver mPlayStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MainActivity", "Received PlayStatus Broadcast");
            String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
            if (s != null) {
                switch (s) {
                    case MediaPlayerService.MSG_PLAY:
                        mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
                        mPlayPauseFAB.setVisibility(View.VISIBLE);
                        mDoNotBindService = false;
                        break;
                    case MediaPlayerService.MSG_PAUSE:
                        mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
                        mPlayPauseFAB.setVisibility(View.VISIBLE);
                        break;
                    case MediaPlayerService.MSG_STOP:
                        mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
                        mPlayPauseFAB.setVisibility(View.GONE);
                        break;
                }
            }
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

            if (mPlayer != null && mPlayer.isPlaying() && mPlayer.getCurrentAudioFile().getId() == mCurrUpdatedAudioId) {
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

    private void showChangeDirectorySelector() {
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, null);
        fileDialog.setSelectDirectoryOption(true);
        fileDialog.addDirectoryListener(directory -> {
            // Set the storage directory to the selected directory
            if (mDirectory != null) {
                changeDirectoryWithConfirmation(directory);
            } else {
                setDirectory(directory);
            }
        });
        fileDialog.showDialog();
    }

    private void setDirectory(File directory) {
        mDirectory = directory;
        updateDBTables();

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
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so change the directory.
            getContentResolver().delete(AnchorContract.AlbumEntry.CONTENT_URI, null, null);
            getContentResolver().delete(AnchorContract.AudioEntry.CONTENT_URI, null, null);
            setDirectory(directory);
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
     * Update the album database table if the list of directories in the selected directory does not
     * match the album table entries
     */
    void updateDBTables() {
        // Get all subdirectories of the selected audio storage directory.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);
            // Only list files that are readable and directories
            return sel.canRead() && sel.isDirectory();
        };

        String[] directoryList;
        if (mDirectory != null && mDirectory.isDirectory()) {
            directoryList = mDirectory.list(filter);
            if (directoryList == null) {
                directoryList = new String[]{};
            }
        } else {
            directoryList = new String[]{};
        }

        LinkedHashMap<String, Integer> albumTitles = getAlbumTitles();

        // Insert new directories into the database
        for (String dirTitle : directoryList) {
            long id;
            if (!albumTitles.containsKey(dirTitle)) {
                id = insertAlbum(dirTitle);
            } else {
                id = albumTitles.get(dirTitle);
                updateAlbumCover(id, dirTitle);
                albumTitles.remove(dirTitle);
            }
            updateAudioFileTable(dirTitle, id);
        }

        // Delete missing directories from the database
        boolean keepDeleted = mSharedPreferences.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));
        if (!keepDeleted) {
            for (String title : albumTitles.keySet()) {
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
     * Update the audiofiles table if the list of audio files in the album directory does not
     * match the audiofiles table entries
     */
    void updateAudioFileTable(String albumDirName, long albumId) {
        // Get all audio files in the album.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);
            // Only list files that are readable and audio files
            String[] supportedFormats = {".mp3", ".wma", ".ogg", ".wav", ".flac", ".m4a", ".m4b", ".aac", ".3gp", ".gsm", ".mid", ".mkv"};
            for (String format : supportedFormats) {
                if (sel.getName().endsWith(format)) return true;
            }
            return false;
        };

        // Get all files in the album directory.
        String[] fileList;
        File albumDir = new File(mDirectory + File.separator + albumDirName);

        if (albumDir.exists()) {
            fileList = albumDir.list(filter);
        } else {
            fileList = new String[]{};
        }

        if (fileList == null) return;

        LinkedHashMap<String, Integer> audioTitles = getAudioFileTitles(albumId);

        // Insert new files into the database
        boolean success = true;
        String errorString = "";

        for (String audioFileName : fileList) {
            if (!audioTitles.containsKey(audioFileName)) {
                success = insertAudioFile(audioFileName, albumDirName, albumId);
                if (!success) errorString = albumDirName + "/" + audioFileName;
            } else {
                audioTitles.remove(audioFileName);
            }
        }
        if (!success) {
            errorString = getResources().getString(R.string.audio_file_error, errorString);
            Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Delete missing audio files from the database
        boolean keepDeleted = mSharedPreferences.getBoolean(getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(getString(R.string.settings_keep_deleted_default)));
        if (!keepDeleted) {
            for (String title : audioTitles.keySet()) {
                Integer id = audioTitles.get(title);
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
                getContentResolver().delete(uri, null, null);
            }
        }
    }

    /*
     * Insert a new row into the audiofiles table
     */
    private boolean insertAudioFile(String title, String albumDirName, long albumId) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_TITLE, title);
        values.put(AnchorContract.AudioEntry.COLUMN_ALBUM, albumId);

        // Retrieve audio duration from Metadata.
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        try {
            String audioFilePath = mDirectory + File.separator + albumDirName + File.separator + title;
            metaRetriever.setDataSource(audioFilePath);
            String duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            values.put(AnchorContract.AudioEntry.COLUMN_TIME, Long.parseLong(duration));
            metaRetriever.release();
            // Insert the row into the database table
            getContentResolver().insert(AnchorContract.AudioEntry.CONTENT_URI, values);
        } catch (java.lang.RuntimeException e) {
            return false;
        }

        return true;
    }

    /**
     * Retrieve all audio file titles from the database
     */
    private LinkedHashMap<String, Integer> getAudioFileTitles(long albumId) {
        String[] columns = new String[]{AnchorContract.AudioEntry._ID, AnchorContract.AudioEntry.COLUMN_TITLE};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(albumId)};

        Cursor c = getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI,
                columns, sel, selArgs, null, null);

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

    /*
     * Insert a new row into the albums table
     */
    private long insertAlbum(String title) {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AlbumEntry.COLUMN_TITLE, title);
        Uri uri = getContentResolver().insert(AnchorContract.AlbumEntry.CONTENT_URI, values);
        updateAlbumCover(ContentUris.parseId(uri), title);
        return ContentUris.parseId(uri);
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

        if (oldCoverPath == null || !(new File(mDirectory.getAbsolutePath() + File.separator + oldCoverPath).exists())) {
            // Search for a cover in the album directory
            File albumDir = new File(mDirectory.getAbsolutePath() + File.separator + title);
            String coverPath = Utils.getImagePath(albumDir);
            if (coverPath != null) {
                coverPath = coverPath.replace(mDirectory.getAbsolutePath(), "");
            }

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

    /*
     * Show file selector where the user can select a directory to which to export the database
     */
    private void showExportDirectorySelector() {
        // Let the user select a directory in which to save the database
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, null);
        fileDialog.setSelectDirectoryOption(true);
        fileDialog.addDirectoryListener(this::exportDatabase);
        fileDialog.showDialog();
    }

    /*
     * Export database to the specified directory
     */
    void exportDatabase(File directory) {
        try {
            if (directory.canWrite()) {
                String currentDBPath = openOrCreateDatabase(AnchorDbHelper.DATABASE_NAME, MODE_PRIVATE, null).getPath();

                File currentDB = new File(currentDBPath);
                File currentDBShm = new File(currentDBPath + "-shm");
                File currentDBWal = new File(currentDBPath + "-wal");
                File[] currentFiles = {currentDB, currentDBShm, currentDBWal};

                String backupDBPath = "audioanchor.db";
                File backupDB = new File(directory, backupDBPath);
                File backupDBShm = new File(directory, backupDBPath + "-shm");
                File backupDBWal = new File(directory, backupDBPath + "-wal");
                File[] backupFiles = {backupDB, backupDBShm, backupDBWal};

                int fileExists = 0;
                for (int i = 0; i < currentFiles.length; i++) {
                    if (currentFiles[i].exists()) {
                        FileChannel src = new FileInputStream(currentFiles[i]).getChannel();
                        FileChannel dst = new FileOutputStream(backupFiles[i]).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();

                        fileExists++;
                    }
                }
                if (fileExists > 0) {
                    String successStr = getResources().getString(R.string.export_success, backupDB.getAbsoluteFile());
                    Toast.makeText(getApplicationContext(), successStr, Toast.LENGTH_LONG).show();
                    Log.e("Export", "Exported " + fileExists + " files");
                } else {
                    Toast.makeText(getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), R.string.export_fail, Toast.LENGTH_LONG).show();
            Log.e("MainActivity", e.getMessage());
        }
    }

    /*
     * Show file selector where the user can select a .db file from which to import a database
     */
    private void showImportFileSelector() {
        File baseDirectory = Environment.getExternalStorageDirectory();
        FileDialog fileDialog = new FileDialog(this, baseDirectory, ".db");
        fileDialog.addFileListener(file -> {
            importDatabase(file);
            updateDBTables();
        });
        fileDialog.showDialog();
    }

    /*
     * Import database from the specified db file
     */
    void importDatabase(File dbFile) {
        try {
            File dbFileShm = new File(dbFile + "-shm");
            File dbFileWal = new File(dbFile + "-wal");
            File[] importFiles = {dbFile, dbFileShm, dbFileWal};

            SQLiteDatabase db = openOrCreateDatabase(AnchorDbHelper.DATABASE_NAME, MODE_PRIVATE, null);
            String newDBPath = db.getPath();
            db.close();

            File newDB = new File(newDBPath);
            File newDBShm = new File(newDBPath + "-shm");
            File newDBWal = new File(newDBPath + "-wal");
            File[] newFiles = {newDB, newDBShm, newDBWal};

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
                Cursor c = getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI,
                        proj, null, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        while (c.moveToNext()) {
                            String oldCoverPath = c.getString(c.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
                            int id = c.getInt(c.getColumnIndex(AnchorContract.AlbumEntry._ID));
                            if (oldCoverPath != null && !oldCoverPath.isEmpty()) {
                                // Replace the old cover path in the database by the new relative path
                                String newCoverPath = new File(oldCoverPath).getName();
                                ContentValues values = new ContentValues();
                                values.put(AnchorContract.AlbumEntry.COLUMN_COVER_PATH, newCoverPath);
                                Uri albumUri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, id);
                                getContentResolver().update(albumUri, values, null, null);
                            }
                        }
                    }
                    c.close();
                }

                Toast.makeText(getApplicationContext(), R.string.import_success, Toast.LENGTH_LONG).show();

                // Restart the CursorLoader so that the CursorAdapter is updated.
                getLoaderManager().restartLoader(0, null, this);

                Log.e("Import", "Imported " + fileExists + " files.");
            }

        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), R.string.import_fail, Toast.LENGTH_LONG).show();
            Log.e("MainActivity", e.getMessage());

        }
    }

    /*
     * Show a confirmation dialog and let the user decide whether to delete the selected albums
     * and / or its tracks from the database
     */
    private void deleteSelectedTracksFromDBWithConfirmation(final ArrayList<Long> selectedTracks) {
        Long[] selectedAlbumsTmpArr = new Long[mSelectedAlbums.size()];
        final Long[] selectedAlbumsArr = mSelectedAlbums.toArray(selectedAlbumsTmpArr);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_album);
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete the album and / or tracks from the database
            int trackDeletionCount = 0;
            for (long trackId : selectedTracks) {
                boolean deleted = DBAccessUtils.deleteTrackFromDB(MainActivity.this, trackId);
                if (deleted) {
                    DBAccessUtils.deleteBookmarksForTrack(MainActivity.this, trackId);
                    trackDeletionCount++;
                }
            }
            int albumDeletionCount = 0;
            for (long albumId : selectedAlbumsArr) {
                boolean deleted = DBAccessUtils.deleteAlbumFromDB(MainActivity.this, albumId);
                if (deleted) albumDeletionCount++;
            }
            String deletedFiles = getResources().getString(R.string.files_deleted, albumDeletionCount, trackDeletionCount);
            Toast.makeText(getApplicationContext(), deletedFiles, Toast.LENGTH_LONG).show();
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
