package com.prangesoftwaresolutions.audioanchor.activities;

import android.app.Dialog;
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
import android.hardware.SensorManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PersistableBundle;
import androidx.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.helpers.SleepTimer;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.models.Bookmark;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.adapters.BookmarkCursorAdapter;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.BitmapUtils;
import com.prangesoftwaresolutions.audioanchor.utils.SkipIntervalUtils;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

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
    SleepTimer mSleepTimer = null;
    SensorManager mSensorManager;
    StorageUtil mStorage;

    // Audio File variables
    AudioFile mAudioFile;
    int mAudioIndex;
    ArrayList<Long> mAudioIdList;

    // The Views
    ImageView mCoverIV;
    TextView mTitleTV;
    TextView mAlbumTV;
    ImageView mPlayIV;
    ImageView mBackward1IV;
    ImageView mBackward2IV;
    ImageView mForward2IV;
    ImageView mForward1IV;
    TextView mBackward1TV;
    TextView mBackward2TV;
    TextView mForward2TV;
    TextView mForward1TV;
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
    private boolean mCoverBelowTrackData;

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

        mBackward1IV = findViewById(R.id.backward_1_iv);
        mBackward2IV = findViewById(R.id.backward_2_iv);
        mForward1IV = findViewById(R.id.forward_1_iv);
        mForward2IV = findViewById(R.id.forward_2_iv);
        mBackward1TV = findViewById(R.id.backward_1_tv);
        mBackward2TV = findViewById(R.id.backward_2_tv);
        mForward1TV = findViewById(R.id.forward_1_tv);
        mForward2TV = findViewById(R.id.forward_2_tv);

        mSeekBar = findViewById(R.id.play_seekbar);
        mCompletedTimeTV = findViewById(R.id.play_completed_time);
        mTimeTV = findViewById(R.id.play_time);
        mSleepCountDownTV = findViewById(R.id.play_sleep_time);

        // Get preferences
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        mTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        mCoverBelowTrackData = mSharedPreferences.getBoolean(getString(R.string.settings_display_cover_below_track_data_key), Boolean.getBoolean(getString(R.string.settings_display_cover_below_track_data_default)));
        mLastSleepTime = mSharedPreferences.getInt(getString(R.string.preference_last_sleep_key), Integer.parseInt(getString(R.string.preference_last_sleep_val)));
        mAudioFile = AudioFile.getAudioFileById(this, currAudioId);
        mMetadataRetriever = new MediaMetadataRetriever();

        mStorage = new StorageUtil(getApplicationContext());

        setNewAudioFile();
        setAlbumCover();

        mHandler = new Handler();
        // Bind service if it is already running
        if (!serviceBound && Utils.isMediaPlayerServiceRunning(this)) {
            bindService();
        }
        // Store audio file queue.
        if (!serviceBound) {
            storeAudioFiles();
        }

        // BroadcastReceivers, all related to service events
        mPlayStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
                Log.e("PlayActivity", "Received Play Status Broadcast " + s);
                if (s != null) {
                    switch (s) {
                        case MediaPlayerService.MSG_PLAY:
                            mPlayIV.setImageResource(R.drawable.pause_button);
                            if (!serviceBound) {
                                bindService();
                            }
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
            // Avoid "App not responding" error when clicking play on a completed file by not
            // starting the MediaPlayerService in that case.
            boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));
            if (mPlayer == null && mAudioFile.getCompletedTime() == mAudioFile.getTime() && !autoplay) {
                return;
            }

            if (mPlayer == null || !mPlayer.isPlaying()) {
                playAudio();
            } else {
                pauseAudio();
            }
        });

        initSkipButtons();

        mBackward1IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_backward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            skipBackward(skipInterval);
        });
        mBackward2IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_backward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
            skipBackward(skipInterval);
        });
        mForward1IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_forward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
            skipForward(skipInterval);
        });
        mForward2IV.setOnClickListener(view -> {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_forward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            skipForward(skipInterval);
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateAudioCompletedTime(progress * 1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Set up Sleep Timer related variables
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Register BroadcastReceivers
        LocalBroadcastManager.getInstance(this).registerReceiver(mPlayStatusReceiver, new IntentFilter(MediaPlayerService.SERVICE_PLAY_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).registerReceiver(mNewAudioFileReceiver, new IntentFilter(MediaPlayerService.SERVICE_NEW_AUDIO));
        // This needs to be a receiver for global broadcasts, as the deleteIntent is broadcast by
        // Android's notification framework
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(MediaPlayerService.BROADCAST_REMOVE_NOTIFICATION));

        boolean immediatePlayback = mSharedPreferences.getBoolean(getString(R.string.settings_immediate_playback_key), Boolean.getBoolean(getString(R.string.settings_immediate_playback_default)));
        if (immediatePlayback) playAudio();

        // Show album cover below track data if the user set it as a preference
        if (mCoverBelowTrackData) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mCoverIV.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, mAlbumTV.getId());
            mCoverIV.setLayoutParams(params);
        }
    }

    void initSkipButtons() {
        int skipIntervalBackwardButton1 = mSharedPreferences.getInt(getString(R.string.settings_backward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
        int skipIntervalBackwardButton2 = mSharedPreferences.getInt(getString(R.string.settings_backward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
        int skipIntervalForwardButton1 = mSharedPreferences.getInt(getString(R.string.settings_forward_button_1_key), Integer.parseInt(getString(R.string.settings_skip_interval_small_default)));
        int skipIntervalForwardButton2 = mSharedPreferences.getInt(getString(R.string.settings_forward_button_2_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));

        // Set the skip interval text within the skip buttons
        mBackward1TV.setText(String.valueOf(skipIntervalBackwardButton1));
        mBackward2TV.setText(String.valueOf(skipIntervalBackwardButton2));
        mForward1TV.setText(String.valueOf(skipIntervalForwardButton1));
        mForward2TV.setText(String.valueOf(skipIntervalForwardButton2));

        // Set the skip icon to next / previous if a button has a max skip interval set
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalBackwardButton1)) {
            mBackward1IV.setImageResource(R.drawable.previous_button);
            mBackward1TV.setVisibility(View.INVISIBLE);
        } else {
            mBackward1IV.setImageResource(R.drawable.backward_button);
            mBackward1TV.setVisibility(View.VISIBLE);
        }
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalBackwardButton2)) {
            mBackward2IV.setImageResource(R.drawable.previous_button);
            mBackward2TV.setVisibility(View.INVISIBLE);
        } else {
            mBackward2IV.setImageResource(R.drawable.backward_button);
            mBackward2TV.setVisibility(View.VISIBLE);
        }
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalForwardButton1)) {
            mForward1IV.setImageResource(R.drawable.next_button);
            mForward1TV.setVisibility(View.INVISIBLE);
        } else {
            mForward1IV.setImageResource(R.drawable.forward_button);
            mForward1TV.setVisibility(View.VISIBLE);
        }
        if (SkipIntervalUtils.isMaxSkipInterval(skipIntervalForwardButton2)) {
            mForward2IV.setImageResource(R.drawable.next_button);
            mForward2TV.setVisibility(View.INVISIBLE);
        } else {
            mForward2IV.setImageResource(R.drawable.forward_button);
            mForward2TV.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.e("PlayActivity", "OnStart called");

        if (mPlayer != null) {
            mAudioFile = mPlayer.getCurrentAudioFile();
            setNewAudioFile();
            setAlbumCover();
        }
        initializeSeekBar();
    }

    @Override
    protected void onRestart() {
        // Recreate if theme, getCoverFromMetadata, getTitleFromMetadata, or coverImageBelowTrackData has changed
        boolean currentGetCoverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));
        boolean currentGetTitleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        boolean currentCoverImageBelowTrackData = mSharedPreferences.getBoolean(getString(R.string.settings_display_cover_below_track_data_key), Boolean.getBoolean(getString(R.string.settings_display_cover_below_track_data_default)));
        if (mCoverFromMetadata != currentGetCoverFromMetadata || mTitleFromMetadata != currentGetTitleFromMetadata || mCoverBelowTrackData != currentCoverImageBelowTrackData) {
            recreate();
        }
        initSkipButtons();

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

        // Stop runnable from continuing to run in the background
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
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
                boolean quickBookmark = mSharedPreferences.getBoolean(getString(R.string.settings_quick_bookmark_key), Boolean.getBoolean(getString(R.string.settings_quick_bookmark_default)));
                if (quickBookmark) {
                    setBookmarkWithoutConfirmation();
                } else {
                    showSetBookmarkDialog(null);
                }
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
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("PlayActivity", "OnServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mPlayer = binder.getService();
            serviceBound = true;

            // Perform actions that can only be performed once the service is connected
            // Set the play ImageView
            if (mPlayer.isPlaying()) {
                mPlayIV.setImageResource(R.drawable.pause_button);
            } else {
                mPlayIV.setImageResource(R.drawable.play_button);
            }

            // Connect SleepTimerTV if a sleep timer is active
            if (mPlayer.getSleepTimer() != null) {
                mPlayer.getSleepTimer().setNewSleepCountDownTV(mSleepCountDownTV);
            } else if (mSleepTimer != null) {
                mPlayer.connectSleepTimer(mSleepTimer);
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
            Intent playerIntent = new Intent(this, MediaPlayerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(playerIntent);
            } else {
                startService(playerIntent);
            }
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void bindService() {
        Intent playerIntent = new Intent(this, MediaPlayerService.class);
        bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
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
        String sortOrder = "CAST(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + " as SIGNED) ASC, LOWER(" + AnchorContract.AudioEntry.TABLE_NAME + "." + AnchorContract.AudioEntry.COLUMN_TITLE + ") ASC";

        ArrayList<AudioFile> audioList = AudioFile.getAllAudioFilesInAlbum(this, mAudioFile.getAlbumId(), sortOrder);
        mAudioIdList = new ArrayList<>();
        for (AudioFile audioFile : audioList) {
            mAudioIdList.add(audioFile.getID());
        }

        mAudioIndex = mAudioIdList.indexOf(mAudioFile.getID());
        mStorage.storeAudioIds(mAudioIdList);
        mStorage.storeAudioIndex(mAudioIndex);
        mStorage.storeAudioId(mAudioFile.getID());
    }

    private void loadAudioFile(int audioIndex) {
        // Load data from SharedPreferences
        mAudioIndex = audioIndex;
        long audioId = mAudioIdList.get(audioIndex);
        mAudioFile = AudioFile.getAudioFileById(this, audioId);
        setNewAudioFile();
        setAlbumCover();
    }

    /*
     * Unbind PlayActivity from MediaPlayerService when the user removes the notification
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
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
            } else if (mAudioFile.getCoverPath() != null) {
                BitmapUtils.setImage(mCoverIV, mAudioFile.getCoverPath(), reqSize);
            } else {
                // No cover image exists for this track. Set default cover image.
                mCoverIV.setImageResource(R.drawable.empty_cover_grey_blue);
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
        // If another Runnable is already connected to the handler, remove old Runnable
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        mRunnable = new Runnable() {
            boolean firstRun = true;

            @Override
            public void run() {
                if (firstRun) {
                    mSeekBar.setMax(mAudioFile.getTime() / 1000);
                    firstRun = false;
                }
                int currentPosition = getAudioCompletedTime();
                mSeekBar.setProgress(currentPosition / 1000);
                mCompletedTimeTV.setText(Utils.formatTime(currentPosition, mAudioFile.getTime()));
                mHandler.postDelayed(mRunnable, 100);
            }
        };
        mHandler.postDelayed(mRunnable, 100);
    }

    void showSleepTimerDialog() {
        // Setup Views
        final View dialogView = this.getLayoutInflater().inflate(R.layout.dialog_sleep_timer, null);
        final EditText setTime = dialogView.findViewById(R.id.sleep_timer_set_time);
        setTime.setText(String.valueOf(mLastSleepTime));
        setTime.setSelection(setTime.getText().length());
        final Button quickButton0 = dialogView.findViewById(R.id.quick_button_0);
        final Button quickButton1 = dialogView.findViewById(R.id.quick_button_1);
        final Button quickButton2 = dialogView.findViewById(R.id.quick_button_2);
        final Button quickButton3 = dialogView.findViewById(R.id.quick_button_3);
        final Button quickButton4 = dialogView.findViewById(R.id.quick_button_4);

        // Setup alertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(R.string.sleep_timer);
        builder.setMessage(R.string.dialog_msg_sleep_timer);

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
            } else {
                startSleepTimer(minutes);
            }

            // Save selection in preferences
            mLastSleepTime = minutes;
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_last_sleep_key), mLastSleepTime);
            editor.apply();
        });

        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();

        // Set quick button click listeners
        setQuickButtonClickListener(quickButton0, alertDialog);
        setQuickButtonClickListener(quickButton1, alertDialog);
        setQuickButtonClickListener(quickButton2, alertDialog);
        setQuickButtonClickListener(quickButton3, alertDialog);
        setQuickButtonClickListener(quickButton4, alertDialog);

        alertDialog.show();
    }

    void setQuickButtonClickListener(Button button, Dialog dialog) {
        button.setOnClickListener(v -> {
            int minutes = Integer.parseInt(button.getText().toString());
            if (mPlayer != null) {
                mPlayer.startSleepTimer(minutes, mSleepCountDownTV);
            } else {
                startSleepTimer(minutes);
            }
            dialog.dismiss();
        });
    }

    void startSleepTimer(int minutes) {
        // Get sleep timer preferences
        boolean shakeEnabledSetting = mSharedPreferences.getBoolean(getString(R.string.settings_shake_key), Boolean.getBoolean(getString(R.string.settings_shake_default)));
        int shakeSensitivitySetting = mSharedPreferences.getInt(getString(R.string.settings_shake_sensitivity_key), Integer.parseInt(getString(R.string.settings_shake_sensitivity_default)));
        float shakeForceRequired = (100 - shakeSensitivitySetting) / 100f;

        if (mSleepTimer == null) {
            mSleepTimer = new SleepTimer(mSleepCountDownTV, mSensorManager, this);
        }

        // Create and start timer
        mSleepTimer.createTimer(minutes * 60, shakeEnabledSetting, shakeForceRequired);
        mSleepTimer.startTimer(false);
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

        int currPos = getAudioCompletedTime();
        String[] currPosArr = Utils.formatTime(currPos, 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setTitle(R.string.go_to);
        builder.setMessage(R.string.dialog_msg_goto);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            try {
                long millis = Utils.getMillisFromString(timeString);
                updateAudioCompletedTime((int) millis);
            } catch (NumberFormatException e) {
                Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /*
     * Set a new bookmark at the current playback time with the default title and inform the user
     * with a toast message.
     */
    void setBookmarkWithoutConfirmation() {
        String title = getResources().getString(R.string.untitled_bookmark);
        Bookmark bookmark = new Bookmark(title, getAudioCompletedTime(), mAudioFile.getID());
        bookmark.insertIntoDB(this);
        String timeString = Utils.formatTime(bookmark.getPosition(), 3600000);
        String addedToastMsg = getResources().getString(R.string.bookmark_added_toast, title, timeString);
        Toast.makeText(getApplicationContext(), addedToastMsg, Toast.LENGTH_SHORT).show();
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

        long bookmarkID = -1;
        if (uri != null) bookmarkID = ContentUris.parseId(uri);
        final Bookmark bookmark;
        if (bookmarkID >= 0) {
            builder.setTitle(R.string.edit_bookmark);
            // Get the bookmark
            bookmark = Bookmark.getBookmarkByID(this, bookmarkID);
            bookmarkTitleET.setText(bookmark.getTitle());
        } else {
            builder.setTitle(R.string.set_bookmark);
            bookmark = new Bookmark("", getAudioCompletedTime(), mAudioFile.getID());
        }

        // Set the edit text views to the current or bookmark position
        String[] currPosArr = Utils.formatTime(bookmark.getPosition(), 3600000).split(":");
        gotoHours.setText(currPosArr[0]);
        gotoMinutes.setText(currPosArr[1]);
        gotoSeconds.setText(currPosArr[2]);

        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the OK button so save the bookmark
            String title = bookmarkTitleET.getText().toString();

            String hours = gotoHours.getText().toString();
            String minutes = gotoMinutes.getText().toString();
            String seconds = gotoSeconds.getText().toString();
            String timeString = hours + ":" + minutes + ":" + seconds;

            if (title.isEmpty()) {
                // Get default title
                title = getResources().getString(R.string.untitled_bookmark);
            }
            try {
                long millis = Utils.getMillisFromString(timeString);
                bookmark.setPosition(millis);
                bookmark.setTitle(title);
                if (bookmark.getID() == -1) {
                    // Insert the bookmark into the bookmarks table
                    bookmark.insertIntoDB(this);
                    String addedToastMsg = getResources().getString(R.string.bookmark_added_toast, title, timeString);
                    Toast.makeText(getApplicationContext(), addedToastMsg, Toast.LENGTH_SHORT).show();
                } else {
                    // Update the bookmark in the bookmarks table
                    bookmark.updateInDB(this);

                    // Reload the ListView
                    Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
                    mBookmarkAdapter = new BookmarkCursorAdapter(PlayActivity.this, c, mAudioFile.getTime());
                    mBookmarkListView.setAdapter(mBookmarkAdapter);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getApplicationContext(), R.string.time_format_error, Toast.LENGTH_SHORT).show();
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
        Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
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
            updateAudioCompletedTime(millis);

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
            Cursor c = Bookmark.getBookmarksCursor(this, mAudioFile.getID());
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
        builder.setTitle(R.string.playback_speed);

        final TextView playbackSpeedTV = dialogView.findViewById(R.id.playback_speed_tv);
        SeekBar playbackSpeedSB = dialogView.findViewById(R.id.playback_speed_sb);

        float normalSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
        int minSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        int maxSpeed = 300;  // max playback speed is 3.0
        playbackSpeedSB.setMax(getProgressFromPlaybackSpeed(maxSpeed));
        int currSpeed = mSharedPreferences.getInt(getString(R.string.preference_playback_speed_key), Integer.parseInt(getString(R.string.preference_playback_speed_default)));
        if (currSpeed < minSpeed) {
            // Ensure backwards compatibility where stored speed was in range 5 - 25
            currSpeed = currSpeed * 10;
            // Store new playback speed in shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), currSpeed);
            editor.apply();
        }
        float currSpeedFloat = currSpeed / normalSpeed;
        playbackSpeedTV.setText(getString(R.string.playback_speed_label, currSpeedFloat));
        int progress = getProgressFromPlaybackSpeed(currSpeed);
        playbackSpeedSB.setProgress(progress);
        playbackSpeedSB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int speed = getPlaybackSpeedFromProgress(progress);
                    float speedFloat = (speed / normalSpeed);
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
                float speedFloat = (speed / normalSpeed);
                if (mPlayer != null) mPlayer.setPlaybackSpeed(speedFloat);
            }
        });

        ImageView decreaseSpeedIV = dialogView.findViewById(R.id.decrease_playback_speed_iv);
        decreaseSpeedIV.setOnClickListener(view -> {
            int speed = getPlaybackSpeedFromProgress(playbackSpeedSB.getProgress());
            speed = Math.max(minSpeed, speed - 1);
            float speedFloat = (speed / normalSpeed);
            playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));
            playbackSpeedSB.setProgress(getProgressFromPlaybackSpeed(speed));

            // Store new playback speed in shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), speed);
            editor.apply();

            // Set playback speed to speed selected by the user
            if (mPlayer != null) mPlayer.setPlaybackSpeed(speedFloat);
        });

        ImageView increaseSpeedIV = dialogView.findViewById(R.id.increase_playback_speed_iv);
        increaseSpeedIV.setOnClickListener(view -> {
            int speed = getPlaybackSpeedFromProgress(playbackSpeedSB.getProgress());
            speed = Math.min(maxSpeed, speed + 1);
            float speedFloat = (speed / normalSpeed);
            playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));
            playbackSpeedSB.setProgress(getProgressFromPlaybackSpeed(speed));

            // Store new playback speed in shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), speed);
            editor.apply();

            // Set playback speed to speed selected by the user
            if (mPlayer != null) mPlayer.setPlaybackSpeed(speedFloat);
        });

        final ImageView resetIV = dialogView.findViewById(R.id.reset);
        resetIV.setOnClickListener(v -> {
            int speed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
            float speedFloat = (speed / normalSpeed);
            playbackSpeedTV.setText(getResources().getString(R.string.playback_speed_label, speedFloat));

            // Store new playback speed in shared preferences
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putInt(getString(R.string.preference_playback_speed_key), speed);
            editor.apply();

            // Set position of seeker to default position 1.0
            playbackSpeedSB.setProgress(getProgressFromPlaybackSpeed(speed));

            // Set playback speed to speed selected by the user
            if (mPlayer != null) {
                mPlayer.setPlaybackSpeed(speedFloat);
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    int getPlaybackSpeedFromProgress(int progress) {
        int min = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        return progress + min;
    }

    int getProgressFromPlaybackSpeed(int speed) {
        int min = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
        return speed - min;
    }

    void updateAudioCompletedTime(int newTime) {
        if (mPlayer != null) {
            mPlayer.setCurrentPosition(newTime);
        } else {
            // Update the current active audio
            mAudioFile.setCompletedTime(newTime);

            // Update the completedTime column of the audiofiles table
            Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mAudioFile.getID());
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, newTime);
            getContentResolver().update(uri, values, null, null);
        }
    }

    int getAudioCompletedTime() {
        if (mPlayer != null) {
            return mPlayer.getCurrentPosition();
        } else {
            return mAudioFile.getCompletedTime();
        }
    }

    /*
     * Skip backward: Depending on the user settings either to the previous file or within the
     * current file.
     * If MediaPlayerService is already started, use MediaPlayerService function, else set previous
     * file within PlayActivity.
     */
    void skipBackward(int skipInterval) {
        if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
            if (mPlayer != null) {
                mPlayer.skipToPreviousAudioFile();
            } else {
                if (mAudioIndex > 0) {
                    // Store current audio completed time
                    updateAudioCompletedTime(mAudioFile.getCompletedTime());
                    // Load new audio file
                    int audioIndex = mAudioIndex - 1;
                    initNewAudioFileViaPlayActivity(audioIndex);
                }
            }
        } else {
            int newTime = Math.max(getAudioCompletedTime() - skipInterval*1000, 0);
            updateAudioCompletedTime(newTime);
        }
    }

    /*
     * Skip forward: Depending on the user settings either to the next file or within the
     * current file.
     * If MediaPlayerService is already started, use MediaPlayerService function, else set next
     * file within PlayActivity.
     */
    void skipForward(int skipInterval) {
        if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
            if (mPlayer != null) {
                mPlayer.skipToNextAudioFile();
            } else {
                if (mAudioIndex + 1 < mAudioIdList.size()) {
                    // Store current audio completed time
                    updateAudioCompletedTime(mAudioFile.getCompletedTime());
                    // Load new audio file
                    int audioIndex = mAudioIndex + 1;
                    initNewAudioFileViaPlayActivity(audioIndex);
                }
            }
        } else {
            int newTime = Math.min(getAudioCompletedTime() + skipInterval * 1000, mAudioFile.getTime());
            updateAudioCompletedTime(newTime);
        }
    }

    /*
     * Initialize the audio file with the given index in the stored AudioFile list for when the
     * MediaPlayerService is not yet started.
     */
    void initNewAudioFileViaPlayActivity(int audioIndex) {
        mAudioIndex = audioIndex;
        loadAudioFile(mAudioIndex);

        // Update the storage
        mStorage.storeAudioIndex(mAudioIndex);
        mStorage.storeAudioId(mAudioFile.getID());

        // Restart audio file from the beginning if this option is set
        boolean restartFromBeginning = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_restart_key), Boolean.getBoolean(getString(R.string.settings_autoplay_restart_default)));
        if (restartFromBeginning) {
            mAudioFile.setCompletedTime(0);
            updateAudioCompletedTime(mAudioFile.getCompletedTime());
        }

        initializeSeekBar();
    }
}
