package com.prangesoftwaresolutions.audioanchor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.util.ArrayList;


public class PlayActivity extends AppCompatActivity {

    public static final String BROADCAST_PLAY_AUDIO = "com.prangesoftwaresolutions.audioanchor.PlayAudio";
    public static final String BROADCAST_PAUSE_AUDIO = "com.prangesoftwaresolutions.audioanchor.PauseAudio";

    private MediaPlayerService mPlayer;
    boolean serviceBound = false;
    boolean mStopServiceOnDestroy = false;
    BroadcastReceiver mPlayStatusReceiver;
    BroadcastReceiver mNewAudioFileReceiver;
    MediaMetadataRetriever mMetadataRetriever;

    // Audio File variables
    AudioFile mAudioFile;

    // The Views
    ImageView mCoverIV;
    TextView mTitleTV;
    TextView mAlbumTV;
    ImageView mPlayIV;
    ImageView mBackIV;
    ImageView mBack10IV;
    ImageView mForwardIV;
    ImageView mForward10IV;
    SeekBar mSeekBar;
    TextView mCompletedTimeTV;
    TextView mTimeTV;
    TextView mSleepCountDownTV;

    // SeekBar variables
    Handler mHandler;
    Runnable mRunnable;

    // SleepTimer variables
    int mLastSleepTime;

    // Bookmark Adapter and Bookmark ListView
    BookmarkCursorAdapter mBookmarkAdapter;
    ListView mBookmarkListView;

    // Settings flags
    private boolean mCoverFromMetadata;
    private boolean mTitleFromMetadata;
    boolean mDarkTheme;

    // Shared preferences
    SharedPreferences mSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // Get the current uri from the intent
        long currAudioId = getIntent().getLongExtra(getString(R.string.curr_audio_id), -1);

        mCoverIV = findViewById(R.id.play_cover);
        mTitleTV = findViewById(R.id.play_audio_file_title);
        mAlbumTV = findViewById(R.id.play_album_title);
        mPlayIV = findViewById(R.id.play_play);
        mBackIV = findViewById(R.id.play_backward);
        mBack10IV = findViewById(R.id.play_backward_10);
        mForwardIV = findViewById(R.id.play_forward);
        mForward10IV = findViewById(R.id.play_forward_10);
        mSeekBar = findViewById(R.id.play_seekbar);
        mCompletedTimeTV = findViewById(R.id.play_completed_time);
        mTimeTV = findViewById(R.id.play_time);
        mSleepCountDownTV = findViewById(R.id.play_sleep_time);

        // Get preferences
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        mTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        mLastSleepTime = mSharedPreferences.getInt(getString(R.string.preference_last_sleep_key), Integer.parseInt(getString(R.string.preference_last_sleep_val)));
        mDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));


        mAudioFile = DBAccessUtils.getAudioFileById(this, currAudioId);
        mMetadataRetriever = new MediaMetadataRetriever();

        setNewAudioFile();
        setAlbumCover();

        mHandler = new Handler();

        // Start the play service
        startService();

        // BroadcastReceivers, all related to service events
        mPlayStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e("PlayActivity", "Received Play Status Broadcast");
                String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
                if (s != null) {
                    switch (s) {
                        case MediaPlayerService.MSG_PLAY:
                            mPlayIV.setImageResource(R.drawable.pause_button);
                            break;
                        case MediaPlayerService.MSG_PAUSE:
                        case MediaPlayerService.MSG_STOP:
                            mPlayIV.setImageResource(R.drawable.play_button);
                            break;
                    }
                }
            }
        };

        mNewAudioFileReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int audioIndex = intent.getIntExtra(MediaPlayerService.SERVICE_MESSAGE_NEW_AUDIO, -1);
                if (audioIndex > -1) {
                    loadAudioFile(audioIndex);
                    initializeSeekBar();
                }
            }
        };

        mPlayIV.setOnClickListener(view -> {

            if (!mPlayer.isPlaying()) {
                playAudio();
            } else {
                pauseAudio();
            }

        });

        mBackIV.setOnClickListener(view -> mPlayer.backward(30));
        mBack10IV.setOnClickListener(view -> mPlayer.backward(10));

        mForwardIV.setOnClickListener(view -> mPlayer.forward(30));
        mForward10IV.setOnClickListener(view -> mPlayer.forward(10));

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mPlayer.setCurrentPosition(progress * 1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Register BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mNewAudioFileReceiver, new IntentFilter(MediaPlayerService.SERVICE_NEW_AUDIO));
        // This needs to be a receiver for global broadcasts, as the deleteIntent is broadcast by
        // Android's notification framework
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));

        boolean immediatePlayback = mSharedPreferences.getBoolean(getString(R.string.settings_immediate_playback_key), Boolean.getBoolean(getString(R.string.settings_immediate_playback_default)));
        if (immediatePlayback) playAudio();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e("PlayActivity", "OnStart called");

        if (mPlayer != null) {
            mAudioFile = mPlayer.getCurrentAudioFile();
            setNewAudioFile();
            setAlbumCover();
            initializeSeekBar();
        }
    }

    @Override
    protected void onRestart() {
        // Recreate if theme, getCoverFromMetadata or getTitleFromMetadata has changed
        boolean currentDarkTheme = mSharedPreferences.getBoolean(getString(R.string.settings_dark_key), Boolean.getBoolean(getString(R.string.settings_dark_default)));
        boolean currentGetCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        boolean currentGetTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        if (mDarkTheme != currentDarkTheme || mCoverFromMetadata != currentGetCoverFromMetadata || mTitleFromMetadata != currentGetTitleFromMetadata) {
            recreate();
        }
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            Log.e("PlayActivity", "Unbinding Service");
            unbindService(serviceConnection);
        }
        if (mStopServiceOnDestroy && mPlayer != null) {
            Log.e("PlayActivity", "Stopping Service");
            mPlayer.stopSelf();
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mPlayStatusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNewAudioFileReceiver);
        unregisterReceiver(mRemoveNotificationReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sleep_timer:
                showSleepTimerDialog();
                return true;
            case R.id.menu_goto:
                showGoToDialog();
                return true;
            case R.id.menu_set_bookmark:
                showSetBookmarkDialog(null);
                return true;
            case R.id.menu_show_bookmarks:
                showShowBookmarksDialog();
                return true;
            case R.id.menu_playback_speed:
                showPlaybackSpeedDialog();
                return true;
            case R.id.menu_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return (super.onOptionsItemSelected(item));
    }


    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean("serviceStatus", serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("serviceStatus");
    }

    // Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("PlayActivity", "OnServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            serviceBound = true;

            // Perform actions that can only be performed once the service is connected
            // Initialize the seek bar for the current audio file
            initializeSeekBar();

            // Set the play ImageView
            if (mPlayer.isPlaying()) {
                mPlayIV.setImageResource(R.drawable.pause_button);
            } else {
                mPlayIV.setImageResource(R.drawable.play_button);
            }

            // Connect SleepTimerTV if a sleep timer is active
            if (mPlayer.getSleepTimer() != null) {
                mPlayer.getSleepTimer().setNewSleepCountDownTV(mSleepCountDownTV);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private void startService() {
        // Check if service is active
        if (!serviceBound) {
            storeAudioFiles();
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(playerIntent);
            } else {
                startService(playerIntent);
            }
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void playAudio() {
        // Send a broadcast to the service that the audio should be played
        startService();
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_PLAY_AUDIO));
        mStopServiceOnDestroy = false;
    }

    private void pauseAudio() {
        // Send a broadcast to the service that the audio should be paused
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(BROADCAST_PAUSE_AUDIO));
    }

    private void storeAudioFiles() {
        // Store Serializable audioList in SharedPreferences
        String sortOrder = "LOWER(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";
        ArrayList<Integer> audioIdList = DBAccessUtils.getAllAudioIdsFromAlbum(this, mAudioFile.getAlbumId(), sortOrder);
        int audioIndex = audioIdList.indexOf(mAudioFile.getId());
        StorageUtil storage = new StorageUtil(getApplicationContext());
        storage.storeAudioIds(audioIdList);
        storage.storeAudioIndex(audioIndex);
        storage.storeAudioId(mAudioFile.getId());
    }

    private void loadAudioFile(int audioIndex) {
        // Load data from SharedPreferences
        StorageUtil storage = new StorageUtil(getApplicationContext());
        ArrayList<Integer> audioList = new ArrayList<>(storage.loadAudioIds());
        int audioId = audioList.get(audioIndex);
        mAudioFile = DBAccessUtils.getAudioFileById(this, audioId);
        setNewAudioFile();
        setAlbumCover();
    }

    /*
     * Unbind PlayActivity from MediaPlayerService when the user removes the notification
     */
    private BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("PlayActivity", "Received broadcast 'remove notification'");
            mStopServiceOnDestroy = true;
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /*
     * Set up the views for the current audio file
     */
    void setNewAudioFile() {
        // Set TextViews
        String title = "";
        if (mTitleFromMetadata) {
            mMetadataRetriever.setDataSource(mAudioFile.getPath());
            title = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (title == null || title.isEmpty()) {
            title = mAudioFile.getTitle();
        }
        mTitleTV.setText(title);
        mTimeTV.setText(Utils.formatTime(mAudioFile.getTime(), mAudioFile.getTime()));
        mAlbumTV.setText(mAudioFile.getAlbumTitle());
    }

    /*
     * Set album cover view for the current cover path
     */
    void setAlbumCover() {
        // Set the album cover to the ImageView and the album title to the TextView
        Point size = new Point();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display;
        int reqSize = getResources().getDimensionPixelSize(R.dimen.default_device_width);
        if (wm != null) {
            display = wm.getDefaultDisplay();
            display.getSize(size);
            reqSize = java.lang.Math.max(size.x, size.y);
        }

        if (mCoverFromMetadata) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(mAudioFile.getPath());

            byte[] coverData = mmr.getEmbeddedPicture();

            // Convert the byte array to a bitmap
            if (coverData != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(coverData, 0, coverData.length);
                mCoverIV.setImageBitmap(bitmap);
            } else {
                BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
            }
        } else {
            BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
        }

        mCoverIV.setAdjustViewBounds(true);

    }

    /*
     * Initialize the SeekBar
     */
    void initializeSeekBar() {
        mRunnable = new Runnable() {
            boolean firstRun = true;

            @Override
            public void run() {
                if (mPlayer != null) {
                    if (firstRun) {
                        mSeekBar.setMax(mPlayer.getDuration() / 1000);
                        firstRun = false;
                    }
                    int currentPosition = mPlayer.getCurrentPosition();
                    mSeekBar.setProgress(currentPosition / 1000);
                    mCompletedTimeTV.setText(Utils.formatTime(currentPosition, mAudioFile.getTime()));
                }
                mHandler.postDelayed(mRunnable, 100);
            }
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    void showSleepTimerDialog() {
        // Setup editText
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_sleep_timer, null);
        final EditText setTime = dialogView.findViewById(R.id.sleep_timer_set_time);
        setTime.setText(String.valueOf(mLastSleepTime));
        setTime.setSelection(setTime.getText().length());

        // Setup alertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(R.string.sleep_timer);
        builder.setMessage(R.string.dialog_msg_sleep_timer);
        // User clicked the OK button so set the sleep timer
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String minutesString = setTime.getText().toString();
            int minutes;
            if (minutesString.isEmpty()) {
                minutes = 0;
            } else {
                minutes = Integer.parseInt(minutesString);
            }

            if (mPlayer != null) {
                mPlayer.startSleepTimer(minutes, mSleepCountDownTV);
            }

            // Save selection in preferences
            mLastSleepTime = minutes;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_last_sleep_key), mLastSleepTime);
            editor.apply();
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
     * Show a dialog that let's the user specify a position to which to jump to
     */
    void showGoToDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_goto, null);
        builder.setView(dialogView);

        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        int currPos = mPlayer.getCurrentPosition();
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setTitle(R.string.go_to);
        builder.setMessage(R.string.dialog_msg_goto);
        // User clicked the OK button so set the sleep timer
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            try {
                long millis = Utils.getMillisFromString(timeString);
                mPlayer.setCurrentPosition((int) millis);
            } catch (NumberFormatException e) {
                Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
            }
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
     * Show a dialog that lets the user specify a title for the bookmark. Let the user confirm
     * that they want to create the bookmark and save the bookmark.
     * If uri is null, a new bookmark is created. Otherwise the bookmark with the corresponding uri
     * is updated.
     */
    void showSetBookmarkDialog(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_bookmark, null);
        builder.setView(dialogView);

        final EditText bookmarkTitleET = dialogView.findViewById(R.id.bookmark_title_et);
        final EditText gotoHours = dialogView.findViewById(R.id.goto_hours);
        final EditText gotoMinutes = dialogView.findViewById(R.id.goto_minutes);
        final EditText gotoSeconds = dialogView.findViewById(R.id.goto_seconds);

        int currPos;
        if (uri != null) {
            builder.setTitle(R.string.edit_bookmark);
            // Get the position of the bookmark
            String[] projection = {
                    AnchorContract.BookmarkEntry.COLUMN_TITLE,
                    AnchorContract.BookmarkEntry.COLUMN_POSITION};
            Cursor c = getContentResolver().query(uri, projection, null, null, null);
            if (c != null) {
                c.moveToFirst();
                currPos = c.getInt(c.getColumnIndex(AnchorContract.BookmarkEntry.COLUMN_POSITION));
                String title = c.getString(c.getColumnIndex(AnchorContract.BookmarkEntry.COLUMN_TITLE));
                bookmarkTitleET.setText(title);
                c.close();
            } else {
                currPos = 0;
            }
        } else {
            builder.setTitle(R.string.set_bookmark);
            currPos = mPlayer.getCurrentPosition();
        }

        // Set the edit text views to the current position
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        // User clicked the OK button so save the bookmark
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String title = bookmarkTitleET.getText().toString();

            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            if (title.isEmpty()) {
                Toast.makeText(getApplicationContext(), R.string.empty_title_error, Toast.LENGTH_SHORT).show();
            } else {
                try {
                    long millis = Utils.getMillisFromString(timeString);
                    int audioFileId = mAudioFile.getId();
                    ContentValues values = new ContentValues();
                    values.put(AnchorContract.BookmarkEntry.COLUMN_TITLE, title);
                    values.put(AnchorContract.BookmarkEntry.COLUMN_POSITION, millis);
                    values.put(AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE, audioFileId);
                    if (uri != null) {
                        // Update the bookmark in the bookmarks table
                        String sel = AnchorContract.BookmarkEntry._ID + "=?";
                        String[] selArgs = {Long.toString(ContentUris.parseId(uri))};
                        getContentResolver().update(uri, values, sel, selArgs);

                        // Reload the ListView
                        Cursor c = getBookmarks();
                        mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
                        mBookmarkListView.setAdapter(mBookmarkAdapter);
                    } else {
                        getContentResolver().insert(AnchorContract.BookmarkEntry.CONTENT_URI, values);
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
        if (uri != null) {
            builder.setNeutralButton(R.string.dialog_msg_delete, (dialog, id) -> {
                // User clicked the "Delete" button, so delete the bookmark
                deleteBookmarkWithConfirmation(uri);
            });
        }
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
     * Show a dialog that shows all bookmarks for the current audio file. If the user clicks on a
     * book mark, the current position of the player is set to that bookmark.
     */
    void showShowBookmarksDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_show_bookmarks, null);
        builder.setView(dialogView);

        mBookmarkListView = dialogView.findViewById(R.id.list_bookmarks);

        // Get the cursor adapter and set it to the list view
        Cursor c = getBookmarks();
        mBookmarkAdapter = new BookmarkCursorAdapter(this, c, mAudioFile.getTime());
        mBookmarkListView.setAdapter(mBookmarkAdapter);

        // Set the EmptyView for the ListView
        TextView emptyTV = dialogView.findViewById(R.id.emptyView_bookmarks);
        mBookmarkListView.setEmptyView(emptyTV);

        // Implement onItemClickListener for the list view
        mBookmarkListView.setOnItemClickListener((adapterView, view, i, rowId) -> {
            // Set the current position of the player to the clicked bookmark
            TextView positionTV = view.findViewById(R.id.bookmark_position_tv);
            String positionString = positionTV.getText().toString();
            int millis = (int) Utils.getMillisFromString(positionString);
            mPlayer.setCurrentPosition(millis);

            // Notify the user about the time jump via a toast
            TextView nameTV = view.findViewById(R.id.bookmark_title_tv);
            String name = nameTV.getText().toString();
            String jumpToast = getResources().getString(R.string.jumped_to_bookmark, name, positionString);
            Toast.makeText(getApplicationContext(), jumpToast, Toast.LENGTH_SHORT).show();
        });

        mBookmarkListView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            Uri uri = ContentUris.withAppendedId(AnchorContract.BookmarkEntry.CONTENT_URI, l);
            showSetBookmarkDialog(uri);
            return true;
        });

        builder.setTitle(R.string.bookmarks);
        // User clicked the OK button so set the sleep timer
        builder.setPositiveButton(R.string.dialog_msg_close, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Get a cursor with all bookmarks for the current audio file
     */
    Cursor getBookmarks() {
        String[] projection = {
                AnchorContract.BookmarkEntry._ID,
                AnchorContract.BookmarkEntry.COLUMN_TITLE,
                AnchorContract.BookmarkEntry.COLUMN_POSITION};

        String sel = AnchorContract.BookmarkEntry.COLUMN_AUDIO_FILE + "=?";
        String[] selArgs = {Long.toString(mAudioFile.getId())};
        String sortOrder = AnchorContract.BookmarkEntry.COLUMN_POSITION + " ASC";
        return getContentResolver().query(AnchorContract.BookmarkEntry.CONTENT_URI, projection, sel, selArgs, sortOrder);
    }

    /**
     * Show the delete bookmark confirmation dialog and let the user decide whether to delete the bookmark
     */
    void deleteBookmarkWithConfirmation(final Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_bookmark);
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete the bookmark.
            getContentResolver().delete(uri, null, null);

            // Reload the ListView
            Cursor c = getBookmarks();
            mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
            mBookmarkListView.setAdapter(mBookmarkAdapter);
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
     * Show a dialog that let's the user specify the playback speed
     */
    void showPlaybackSpeedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_playback_speed, null);
        builder.setView(dialogView);

        final TextView playbackSpeedTV = dialogView.findViewById(R.id.playback_speed_tv);
        final ImageView resetIV = dialogView.findViewById(R.id.reset);
        resetIV.setOnClickListener(v -> {
            int speed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
            float speedFloat = (float) (speed / 10.0);
            playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));

            // Store new playback speed in shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), speed);
            editor.apply();

            // Set playback speed to speed selected by the user
            mPlayer.setPlaybackSpeed(speedFloat);
        });
        SeekBar playbackSpeedSB = dialogView.findViewById(R.id.playback_speed_sb);
        playbackSpeedSB.setMax(25);  // min + max = 5 + 25 = 30 --> max playback speed 3.0
        int currSpeed = mSharedPreferences.getInt(getString(R.string.preference_playback_speed_key), Integer.parseInt(getString(R.string.preference_playback_speed_default)));
        int progress = getProgressFromPlaybackSpeed(currSpeed);
        playbackSpeedSB.setProgress(progress);
        playbackSpeedSB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int speed = getPlaybackSpeedFromProgress(progress);
                    float speedFloat = (float) (speed / 10.0);
                    playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int speed = getPlaybackSpeedFromProgress(seekBar.getProgress());

                // Store new playback speed in shared preferences
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putInt(getString(R.string.preference_playback_speed_key), speed);
                editor.apply();

                // Set playback speed to speed selected by the user
                float speedFloat = (float) (speed / 10.0);
                mPlayer.setPlaybackSpeed(speedFloat);
            }
        });

        builder.setTitle(R.string.playback_speed);

        // Set the text of the TextView to the default speed
        int speed = mSharedPreferences.getInt(getString(R.string.preference_playback_speed_key), Integer.parseInt(getString(R.string.preference_playback_speed_default)));
        float speedFloat = (float) (speed / 10.0);
        playbackSpeedTV.setText(getString(R.string.playback_speed_label, speedFloat));

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    int getPlaybackSpeedFromProgress(int progress) {
        int min = 5;
        return progress + min;
    }

    int getProgressFromPlaybackSpeed(int speed) {
        int min = 5;
        return speed - min;
    }
}
