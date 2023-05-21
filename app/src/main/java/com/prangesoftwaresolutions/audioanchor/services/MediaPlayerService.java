package com.prangesoftwaresolutions.audioanchor.services;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.listeners.SleepTimerStatusListener;
import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.helpers.LockManager;
import com.prangesoftwaresolutions.audioanchor.models.Bookmark;
import com.prangesoftwaresolutions.audioanchor.receivers.MediaButtonIntentReceiver;
import com.prangesoftwaresolutions.audioanchor.callbacks.MediaSessionCallback;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.helpers.SleepTimer;
import com.prangesoftwaresolutions.audioanchor.activities.PlayActivity;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.BitmapUtils;
import com.prangesoftwaresolutions.audioanchor.utils.SkipIntervalUtils;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;

import java.io.IOException;
import java.util.ArrayList;

import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;


/*
 * Media Player Service class
 * Based on a tutorial by Valdio Veliu. See https://github.com/sitepoint-editors/AudioPlayer
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener, SleepTimerStatusListener {

    public static final String ACTION_PLAY = "com.prangesoftwaresolutions.audioanchor.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_PAUSE";
    public static final String ACTION_TOGGLE_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_TOGGLE_PAUSE";
    public static final String ACTION_BACKWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_BACKWARD";
    public static final String ACTION_FORWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_FORWARD";
    public static final String ACTION_STOP = "com.prangesoftwaresolutions.audioanchor.ACTION_STOP";
    public static final String ACTION_BOOKMARK = "com.prangesoftwaresolutions.audioanchor.ACTION_BOOKMARK";

    public static final String SERVICE_PLAY_STATUS_CHANGE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY_STATUS_CHANGE";
    public static final String SERVICE_MESSAGE_PLAY_STATUS = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_PLAYING";
    public static final String SERVICE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY_STATUS_CHANGE";
    public static final String SERVICE_MESSAGE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_NEW_AUDIO";
    public static final String MSG_PLAY = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY";
    public static final String MSG_PAUSE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PAUSE";
    public static final String MSG_STOP = "com.prangesoftwaresolutions.audioanchor.SERVICE_STOP";

    public static final String BROADCAST_REMOVE_NOTIFICATION = "com.prangesoftwaresolutions.audioanchor.REMOVE_NOTIFICATION";
    public static final String BROADCAST_UNBIND_CURRENT_SERVICE = "com.prangesoftwaresolutions.audioanchor.UNBIND_CURRENT_SERVICE";
    public static final String BROADCAST_RESET = "com.prangesoftwaresolutions.audioanchor.RESET";

    private MediaPlayer mMediaPlayer;

    // MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;

    // Metadata Retriever
    MediaMetadataRetriever mMetadataRetriever;

    // AudioPlayer notification IDs
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "com.prangesoftwaresolutions.audioanchor.NOTIFICATION_CHANNEL";

    // Notification builder and manager
    NotificationCompat.Builder mNotificationBuilder;
    NotificationManager mNotificationManager;

    // Used to pause/resume MediaPlayer
    private boolean resumeAfterCall = false;

    // AudioFocus
    private AudioManager audioManager;
    private boolean mIsPausedByTransientFocusLoss = false;

    // Lock Manager
    LockManager mLockManager;

    // Communication with clients
    private final IBinder iBinder = new LocalBinder();
    LocalBroadcastManager mBroadcaster;

    // List of available Audio files
    private ArrayList<Long> mAudioIdQueue;
    private int mAudioIndex = -1;
    private AudioFile mActiveAudio;

    // Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    // Shared Preferences
    SharedPreferences mSharedPreferences;

    // SleepTimer variables
    SleepTimer mSleepTimer;
    SensorManager mSensorManager;
    boolean mStopAtEndOfCurrentTrack = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mBroadcaster = LocalBroadcastManager.getInstance(this);
        mMetadataRetriever = new MediaMetadataRetriever();

        // Set up the shared preferences.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Manage playback for incoming calls
        callStateListener();

        // Register system wide BroadcastReceiver for changes in audio outputs
        registerReceiver(mBecomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        // Register BroadcastReceivers for broadcasts from PlayActivity
        mBroadcaster.registerReceiver(mPlayAudioReceiver, new IntentFilter(PlayActivity.BROADCAST_PLAY_AUDIO));
        mBroadcaster.registerReceiver(mPauseAudioReceiver, new IntentFilter(PlayActivity.BROADCAST_PAUSE_AUDIO));
        registerReceiver(mRemoveNotificationReceiver, new IntentFilter(BROADCAST_REMOVE_NOTIFICATION));

        // Notification manager
         mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Sensor manager for the sleep timer reset shake detection
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Set up AudioManager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Set up the LockManager
        mLockManager = new LockManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("MediaPlayerService", "calling onStartCommand()");
        if (mAudioIdQueue == null || mAudioIndex == -1) {
            try {
                // Load data from SharedPreferences
                StorageUtil storage = new StorageUtil(this);
                mAudioIdQueue = new ArrayList<>(storage.loadAudioIds());
                mAudioIndex = storage.loadAudioIndex();

                if (mAudioIndex < mAudioIdQueue.size() && mAudioIndex != -1) {
                    // Index is in a valid range
                    long activeAudioId = mAudioIdQueue.get(mAudioIndex);
                    mActiveAudio = AudioFile.getAudioFileById(this, activeAudioId);
                } else {
                    stopForeground(true);
                    stopSelf();
                }
            } catch (NullPointerException e) {
                stopForeground(true);
                stopSelf();
            }
        }

        if (!requestAudioFocus()) {
            stopForeground(true);
            stopSelf();
        }

        if (mediaSession == null) {
            initMediaSession();
            initMediaPlayer(mActiveAudio.getPath(), mActiveAudio.getCompletedTime());
            play();
        }

        // Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e("MediaPlayerService", "OnUnbind called");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("MediaPlayerService", "calling onDestroy()");
        if (mSleepTimer != null)
            mSleepTimer.disableTimer();

        if (mMediaPlayer != null) {
            stopMedia();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        removeAudioFocus();

        // Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        mLockManager.releaseWakeLock();
        mLockManager = null;

        // Unregister BroadcastReceivers
        unregisterReceiver(mBecomingNoisyReceiver);
        mBroadcaster.unregisterReceiver(mPlayAudioReceiver);
        mBroadcaster.unregisterReceiver(mPauseAudioReceiver);
        unregisterReceiver(mRemoveNotificationReceiver);

        // Clear cached playlist and set current audio index to -1
        new StorageUtil(this).clearCachedAudioPlaylist();
    }

    /*
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private AudioAttributes createAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    void initMediaPlayer(String path, int position) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(this);
        }
        try {
            mMediaPlayer.reset();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mMediaPlayer.setAudioAttributes(createAudioAttributes());
            } else {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            mMediaPlayer.setDataSource(path);

            // Set playback speed according to preferences
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int speed = mSharedPreferences.getInt(getString(R.string.preference_playback_speed_key), Integer.parseInt(getString(R.string.preference_playback_speed_default)));
                int minSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_minimum));
                if (speed < minSpeed) {
                    // Ensure backwards compatibility where stored speed was in range 5 - 25
                    speed = speed * 10;
                    // Store new playback speed in shared preferences
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putInt(getString(R.string.preference_playback_speed_key), speed);
                    editor.apply();
                }
                float normalSpeed = Integer.parseInt(getString(R.string.preference_playback_speed_default));
                setPlaybackSpeedIfInLegalRange((speed / normalSpeed));
            }

            mMediaPlayer.prepare();
            mMediaPlayer.seekTo(position);

        } catch (IOException e) {
            e.printStackTrace();
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        setCurrentPosition(getDuration());
        updateAudioFileStatus();

        boolean playingNext = false;
        boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));

        if (autoplay && !mStopAtEndOfCurrentTrack) {
            playingNext = initNextAudioFile();
        } else if (mStopAtEndOfCurrentTrack) {
            terminateSleepTimer();
        }

        if (playingNext) {
            play();
        } else {
            // Notify the play activity that the playback was paused
            sendPlayStatusResult(MSG_STOP);

            removeNotification();

            // Send broadcast that the notification was removed
            // The MediaPlayerService receiver will then also stop the service by calling stopSelf()
            sendBroadcast(new Intent(BROADCAST_REMOVE_NOTIFICATION));

            mLockManager.releaseWakeLock();
        }
    }

    public boolean initNextAudioFile() {
        boolean autoplayRestart = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_restart_key), Boolean.getBoolean(getString(R.string.settings_autoplay_restart_default)));
        if (mAudioIndex + 1 < mAudioIdQueue.size()) {
            mAudioIndex++;
            StorageUtil storage = new StorageUtil(this);
            storage.storeAudioIndex(mAudioIndex);
            long activeAudioId = mAudioIdQueue.get(mAudioIndex);
            mActiveAudio = AudioFile.getAudioFileById(this, activeAudioId);
            if (mActiveAudio != null) {
                storage.storeAudioId(mActiveAudio.getID());
            }
            sendNewAudioFile(mAudioIndex);
            int startPosition;
            if (autoplayRestart) {
                startPosition = 0;
            } else {
                startPosition = mActiveAudio.getCompletedTime();
            }
            initMediaPlayer(mActiveAudio.getPath(), startPosition);
            updateAudioFileStatus();  // Needed if startPosition is set to 0 such that the time in the AlbumActivity is updated
            updateMetaData();
            buildNotification();
            return true;
        }
        return false;
    }

    public boolean initPreviousAudioFile() {
        boolean autoplayRestart = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_restart_key), Boolean.getBoolean(getString(R.string.settings_autoplay_restart_default)));
        if (mAudioIndex - 1 >= 0) {
            mAudioIndex--;
            StorageUtil storage = new StorageUtil(this);
            storage.storeAudioIndex(mAudioIndex);
            long activeAudioId = mAudioIdQueue.get(mAudioIndex);
            mActiveAudio = AudioFile.getAudioFileById(this, activeAudioId);
            if (mActiveAudio != null) {
                storage.storeAudioId(mActiveAudio.getID());
            }
            sendNewAudioFile(mAudioIndex);
            int startPosition;
            if (autoplayRestart) {
                startPosition = 0;
            } else {
                startPosition = mActiveAudio.getCompletedTime();
            }
            initMediaPlayer(mActiveAudio.getPath(), startPosition);
            updateAudioFileStatus();
            updateMetaData();
            buildNotification();
            return true;
        }
        return false;
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        Log.e("MediaPlayerService", "calling onAudioFocusChange()");
        // Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.e("MediaPlayerService", "Audiofocus Gain");
                if (mMediaPlayer == null) {
                    initMediaPlayer(mActiveAudio.getPath(), mActiveAudio.getCompletedTime());
                }

                if (mIsPausedByTransientFocusLoss) {
                    // Resume playback if audiofocus was lost only temporarily
                    play();
                    mIsPausedByTransientFocusLoss = false;
                }
                setVolume(1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                Log.e("MediaPlayerService", "Audiofocus Loss");
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                Log.e("MediaPlayerService", "Audiofocus loss transient");

                if (mMediaPlayer.isPlaying()) {
                    pauseDueToAudioInterruption();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                Log.e("MediaPlayerService", "Audiofocus loss can duck");
                if (mMediaPlayer.isPlaying()) {
                    boolean duckAudio = mSharedPreferences.getBoolean(getString(R.string.settings_duck_audio_key), Boolean.getBoolean(getString(R.string.settings_duck_audio_default)));
                    if (duckAudio) {
                        setVolume(0.1f);
                    } else {
                        pauseDueToAudioInterruption();
                    }
                }
                break;
        }
    }

    /*
     * Request AudioFocus
     */
    private boolean requestAudioFocus() {
        int result = 0;
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attributes = createAudioAttributes();
                AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this)
                        .build();
                result = audioManager.requestAudioFocus(request);
            } else {
                result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void removeAudioFocus() {
        audioManager.abandonAudioFocus(this);
    }

    private void pauseDueToAudioInterruption() {
        pause();
        mIsPausedByTransientFocusLoss = true;
    }

    /*
     * Receive broadcasts about change in audio outputs
     */
    private final BroadcastReceiver mBecomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Pause audio on ACTION_AUDIO_BECOMING_NOISY
            pause();
        }
    };

    /*
     * Receive broadcast when a new audio file starts playing
     */
    private final BroadcastReceiver mPlayAudioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (requestAudioFocus()) {
                play();
            }
        }
    };

    /*
     * Receive broadcast when the audio is paused
     */
    private final BroadcastReceiver mPauseAudioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pause();
        }
    };

    /*
     * Receive broadcast when the user deletes the notification
     */
    private final BroadcastReceiver mRemoveNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("MediaPlayerService", "Received broadcast 'remove notification'");
            stopForeground(true);
            stopSelf();
        }
    };

    /*
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        // Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    // If at least one call exists or the phone is ringing pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mMediaPlayer != null && !ongoingCall) {
                            resumeAfterCall = mMediaPlayer.isPlaying();
                            pause();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mMediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                if (resumeAfterCall) {
                                    play();
                                }
                            }
                        }
                        resumeAfterCall = false;
                        break;
                }
            }
        };
        // Register the listener with the telephony manager. Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /*
     * MediaSession and Notification actions
     */
    private void initMediaSession() {
        if (mediaSessionManager != null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }

        ComponentName mediaButtonReceiverComponentName = new ComponentName(getApplicationContext(), MediaButtonIntentReceiver.class);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mediaButtonReceiverComponentName);

        PendingIntent mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);

        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioAnchor", mediaButtonReceiverComponentName, mediaButtonReceiverPendingIntent);
        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCallback(this, getApplicationContext()));
        // Set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        // Set MediaButtonReceiver to be able to restart the inactive MediaSession using media buttons
        mediaSession.setMediaButtonReceiver(mediaButtonReceiverPendingIntent);
        // Set mediaSession's MetaData
        updateMetaData();
    }

    private void updateMetaData() {
        boolean coverOnLockscreen = mSharedPreferences.getBoolean(getString(R.string.settings_cover_on_lockscreen_key), Boolean.getBoolean(getString(R.string.settings_cover_on_lockscreen_default)));
        MediaMetadataCompat mediaMetadataCompat;
        if (coverOnLockscreen) {
            Bitmap albumArt = getNotificationImage(150);
            mediaMetadataCompat = new MediaMetadataCompat.Builder()
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mActiveAudio.getAlbumTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mActiveAudio.getTitle())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,mActiveAudio.getTime())
                    .build();
        } else {
            mediaMetadataCompat = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, mActiveAudio.getAlbumTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mActiveAudio.getTitle())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,mActiveAudio.getTime())
                    .build();
        }
        // Update the current metadata
        mediaSession.setMetadata(mediaMetadataCompat);
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            channel.setShowBadge(false);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Bitmap getNotificationImage(int size) {
        Bitmap notificationCover;
        boolean coverFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_cover_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_cover_from_metadata_default)));

        if (coverFromMetadata) {
            mMetadataRetriever.setDataSource(mActiveAudio.getPath());
            byte[] coverData = mMetadataRetriever.getEmbeddedPicture();

            if (coverData != null) {
                notificationCover = BitmapUtils.decodeSampledBitmap(coverData, size, size);
            } else if (mActiveAudio.getCoverPath() != null) {
                notificationCover = BitmapUtils.decodeSampledBitmap(mActiveAudio.getCoverPath(), size, size);
            } else {
                notificationCover = BitmapUtils.decodeSampledBitmap(getResources(), R.drawable.empty_cover_grey_blue, size, size);
            }
        } else {
            if (mActiveAudio.getCoverPath() != null) {
                notificationCover = BitmapUtils.decodeSampledBitmap(mActiveAudio.getCoverPath(), size, size);
            } else {
                notificationCover = BitmapUtils.decodeSampledBitmap(getResources(), R.drawable.empty_cover_grey_blue, size, size);
            }
        }
        return notificationCover;
    }

    private void buildNotification() {
        createNotificationChannel();

        // Get play/pause image, action and title according to the current state of the MediaPlayer
        int playPauseImageResource = R.drawable.ic_media_pause;
        PendingIntent playPauseAction = playbackAction(1);
        String playPauseTitle = getString(R.string.button_pause);
        if (!isPlaying()) {
            playPauseImageResource = R.drawable.ic_media_play;
            // Create the play action
            playPauseAction = playbackAction(0);
            playPauseTitle = getString(R.string.button_play);
        }

        // Get skip icons according to the notification skip intervals from the settings
        int skipIntervalBackward = mSharedPreferences.getInt(getString(R.string.settings_notification_backward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
        int skipIntervalForward = mSharedPreferences.getInt(getString(R.string.settings_notification_forward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
        int skipBackwardImageResource = (SkipIntervalUtils.isMaxSkipInterval(skipIntervalBackward)) ? R.drawable.ic_notification_previous : R.drawable.ic_notification_backward;
        int skipForwardImageResource = (SkipIntervalUtils.isMaxSkipInterval(skipIntervalForward)) ? R.drawable.ic_notification_next : R.drawable.ic_notification_forward;
        if (skipIntervalBackward == 30) skipBackwardImageResource = R.drawable.ic_notification_backward_30;
        if (skipIntervalForward == 30) skipForwardImageResource = R.drawable.ic_notification_forward_30;

        Bitmap notificationCover = getNotificationImage(200);

        String audioTitle = "";
        boolean titleFromMetadata = mSharedPreferences.getBoolean(getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(getString(R.string.settings_title_from_metadata_default)));
        if (titleFromMetadata) {
            mMetadataRetriever.setDataSource(mActiveAudio.getPath());
            audioTitle = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (audioTitle == null || audioTitle.isEmpty()) {
            audioTitle = mActiveAudio.getTitle();
        }

        // Set up intent to start PlayActivity when the notification is clicked
        Intent startActivityIntent = new Intent(this, PlayActivity.class);
        startActivityIntent.putExtra(getString(R.string.curr_audio_id), mActiveAudio.getID());
        PendingIntent launchIntent = PendingIntent.getActivity(this, 0,
                startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Set up intent to stop service when notification is removed
        Intent intent = new Intent(BROADCAST_REMOVE_NOTIFICATION);
        PendingIntent deleteIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 0, intent, 0);

        // Create a new notification
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                // Hide the timestamp
                .setShowWhen(false)
                // Set the notification style
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession.getSessionToken())
                        // Show our playback controls in the compat view
                        .setShowActionsInCompactView(1, 2, 3))
                // Set the notification color
                .setColor(getResources().getColor(R.color.colorAccent))
                // Set the large and small icons
                .setLargeIcon(notificationCover)
                .setSmallIcon(R.drawable.ic_notification_new)
                // Set notification content information
                .setContentText(mActiveAudio.getAlbumTitle())
                .setContentTitle(audioTitle)
                // Set the intent for the activity that is launched on click
                .setContentIntent(launchIntent)
                // Set intent that is launched on delete notificaiton
                .setDeleteIntent(deleteIntent)
                // Set the visibility for the lock screen
                .setVisibility(VISIBILITY_PUBLIC)
                // Make notification non-removable if the track is currently playing
                .setOngoing(playPauseTitle.equals(getString(R.string.button_pause)))
                // Add playback actions
                .addAction(R.drawable.ic_media_bookmark, getString(R.string.button_bookmark), playbackAction(4))
                .addAction(skipBackwardImageResource, getString(R.string.button_backward), playbackAction(3))
                .addAction(playPauseImageResource, playPauseTitle, playPauseAction)
                .addAction(skipForwardImageResource, getString(R.string.button_forward), playbackAction(2));

        Notification notification = mNotificationBuilder.build();
        if (isPlaying()) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackActionIntent = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                // Play
                playbackActionIntent.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, 0);
            case 1:
                // Pause
                playbackActionIntent.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, 0);
            case 2:
                // Skip forward
                playbackActionIntent.setAction(ACTION_FORWARD);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, 0);
            case 3:
                // Skip backward
                playbackActionIntent.setAction(ACTION_BACKWARD);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, 0);
            case 4:
                // Set bookmark
                playbackActionIntent.setAction(ACTION_BOOKMARK);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, 0);
            default:
                break;
        }
        return null;
    }

    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            pause();
        } else if (actionString.equalsIgnoreCase(ACTION_TOGGLE_PAUSE)) {
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) pause();
            else play();
        } else if (actionString.equalsIgnoreCase(ACTION_FORWARD)) {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_notification_forward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
                skipToNextAudioFile();
            } else {
                forward(skipInterval);
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            }
        } else if (actionString.equalsIgnoreCase(ACTION_BACKWARD)) {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_notification_backward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
                skipToPreviousAudioFile();
            } else {
                backward(skipInterval);
                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            }
        } else if (actionString.equalsIgnoreCase(ACTION_BOOKMARK)) {
            setBookmark();
        }
    }

    private void setBookmark() {
        String title = getResources().getString(R.string.untitled_bookmark);
        Bookmark bookmark = new Bookmark(title, getCurrentPosition(), mActiveAudio.getID());
        bookmark.insertIntoDB(this);
    }

    public boolean isPlaying() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    public void play() {
        // Get Autoplay and Autorewind settings
        boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));
        int autorewindTime = Integer.parseInt(mSharedPreferences.getString(getString(R.string.settings_autorewind_key), getString(R.string.settings_autorewind_default)));

        // Request a partial wake lock for the duration of the playback
        mLockManager.acquireWakeLock();

        if (mMediaPlayer != null && !mMediaPlayer.isPlaying() && (autoplay || getCurrentPosition() != getDuration())) {
            if (getCurrentPosition() != getDuration()) {
                mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() - autorewindTime * 1000);
            }
            mMediaPlayer.start();
            sendPlayStatusResult(MSG_PLAY);
            mediaSession.setActive(true);
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            buildNotification();

            updateLastPlayedAudio();
            boolean addLastPlayPositionBookmarks = mSharedPreferences.getBoolean(getString(R.string.settings_add_last_play_position_bookmark_key), Boolean.getBoolean(getString(R.string.settings_add_last_play_position_bookmark_default)));
            if (addLastPlayPositionBookmarks) updateLastPlayPositionBookmarks();
        }
    }

    public void stopMedia() {
        // Release the partial wake lock to save battery
        mLockManager.releaseWakeLock();

        if (mMediaPlayer != null) {
            updateAudioFileStatus();
            sendPlayStatusResult(MSG_STOP);
            mMediaPlayer.stop();
            setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        }
        mediaSession.setActive(false);
        stopForeground(true);
    }

    public void pause() {
        // Release the partial wake lock to save battery
        mLockManager.releaseWakeLock();

        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            updateAudioFileStatus();
            sendPlayStatusResult(MSG_PAUSE);
            setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            buildNotification();
        }
        stopForeground(false);
    }

    /*
     * Skip the specified amount of seconds forward
     */
    void forward(int seconds) {
        int newPos = Math.min(getDuration(), mMediaPlayer.getCurrentPosition() + seconds * 1000);
        mMediaPlayer.seekTo(newPos);
        updateAudioFileStatus();
    }

    /*
     * Skip the specified amount of seconds backward
     */
    void backward(int seconds) {
        int newPos = Math.max(0, mMediaPlayer.getCurrentPosition() - seconds * 1000);
        mMediaPlayer.seekTo(newPos);
        updateAudioFileStatus();
    }

    /*
     * Skip to next audio file
     */
    public void skipToNextAudioFile() {
        boolean wasPlaying = mMediaPlayer != null && mMediaPlayer.isPlaying();
        updateAudioFileStatus();
        initNextAudioFile();
        if (wasPlaying) play();
    }

    /*
     * Skip to previous audio file
     */
    public void skipToPreviousAudioFile() {
        boolean wasPlaying = mMediaPlayer != null && mMediaPlayer.isPlaying();
        updateAudioFileStatus();
        initPreviousAudioFile();
        if (wasPlaying) play();
    }

    /*
     * Get total duration of the audio file
     */
    int getDuration() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getDuration();
        }
        return 0;
    }

    /*
     * Get current position of the played audio file
     */
    public int getCurrentPosition() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    /*
     * Set current position of the played audio file
     */
    public void setCurrentPosition(int progress) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(progress);
            setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        }
        updateAudioFileStatus();
    }

    public void decreaseVolume(int step, int totalSteps) {
        float deltaVolume = (float) (1.0 / totalSteps);
        float currVolume = (float) (1.0 - (step * deltaVolume));
        if (mMediaPlayer != null) {
            setVolume(currVolume);
        }
    }

    public void setVolume(float volume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void setPlaybackSpeed(float speed) {
        if (mMediaPlayer == null) return;

        boolean isPlaying = mMediaPlayer.isPlaying();
        if (!isPlaying) {
            initMediaPlayer(mActiveAudio.getPath(), mMediaPlayer.getCurrentPosition());
        } else {
            setPlaybackSpeedIfInLegalRange(speed);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    void setPlaybackSpeedIfInLegalRange(float speed) {
        try {
            mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(speed));
        } catch (IllegalArgumentException e) {
            String illegalSpeed = getResources().getString(R.string.illegal_speed, speed);
            Toast.makeText(getApplicationContext(), illegalSpeed, Toast.LENGTH_LONG).show();
        }
    }

    public void sendPlayStatusResult(String message) {
        Intent intent = new Intent(SERVICE_PLAY_STATUS_CHANGE);
        if (message != null) {
            intent.putExtra(SERVICE_MESSAGE_PLAY_STATUS, message);
            mBroadcaster.sendBroadcast(intent);
        }
    }

    public void sendNewAudioFile(int audioIndex) {
        Intent intent = new Intent(SERVICE_NEW_AUDIO);
        intent.putExtra(SERVICE_MESSAGE_NEW_AUDIO, audioIndex);
        mBroadcaster.sendBroadcast(intent);
    }

    /*
     * Update the completed time of the current audio file in the audiofiles table of the database
     */
    void updateAudioFileStatus() {
        // Update the current active audio
        mActiveAudio.setCompletedTime(getCurrentPosition());

        // Update the completedTime column of the audiofiles table
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mActiveAudio.getID());
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, getCurrentPosition());
        getContentResolver().update(uri, values, null, null);
    }

    /*
     * Update the last played column of the album table
     */
    void updateLastPlayedAudio() {
        Album album = mActiveAudio.getAlbum();
        album.setLastPlayedID(mActiveAudio.getID());
        album.updateInDB(this);
    }

    /*
     * Add or update the last and second-to-last play position bookmarks
     */
    void updateLastPlayPositionBookmarks() {
        // Get last play position bookmark
        String lastTitle = getString(R.string.bookmark_last_play_position);
        Bookmark lastBookmark = Bookmark.getBookmarkForAudioFileByTitle(this, lastTitle, mActiveAudio.getID());
        long secondToLastPlayPosition = -1;
        // Insert or update bookmark
        if (lastBookmark == null) {
            lastBookmark = new Bookmark(lastTitle, getCurrentPosition(), mActiveAudio.getID());
            lastBookmark.insertIntoDB(this);
        } else {
            secondToLastPlayPosition = lastBookmark.getPosition();
            lastBookmark.setPosition(getCurrentPosition());
            lastBookmark.updateInDB(this);
        }

        // Get second to last play position bookmark
        if (secondToLastPlayPosition >= 0) {
            String secondToLastTitle = getString(R.string.bookmark_second_to_last_play_position);
            Bookmark secondToLastBookmark = Bookmark.getBookmarkForAudioFileByTitle(this, secondToLastTitle, mActiveAudio.getID());
            // Insert or update bookmark
            if (secondToLastBookmark == null) {
                secondToLastBookmark = new Bookmark(secondToLastTitle, secondToLastPlayPosition, mActiveAudio.getID());
                secondToLastBookmark.insertIntoDB(this);
            } else {
                secondToLastBookmark.setPosition(secondToLastPlayPosition);
                secondToLastBookmark.updateInDB(this);
            }
        }
    }

    /*
     * Start the sleep timer
     */
    public void startSleepTimer(int minutes, TextView countDownTV) {
        mStopAtEndOfCurrentTrack = false;

        // Get sleep timer preferences
        boolean shakeEnabledSetting = mSharedPreferences.getBoolean(getString(R.string.settings_shake_key), Boolean.getBoolean(getString(R.string.settings_shake_default)));
        int shakeSensitivitySetting = mSharedPreferences.getInt(getString(R.string.settings_shake_sensitivity_key), R.string.settings_shake_sensitivity_default);
        float shakeForceRequired = (100 - shakeSensitivitySetting) / 100f;

        if (mSleepTimer == null) {
            mSleepTimer = new SleepTimer(countDownTV, this, mSensorManager, this);
            mSleepTimer.setListener(this);
        }

        // Create and start timer
        mSleepTimer.createTimer(minutes * 60, shakeEnabledSetting, shakeForceRequired);
        mSleepTimer.startTimer(false);
    }

    @Override
    public void onSleepTimerFinished() {
        boolean stopAtEndOfTrack = mSharedPreferences.getBoolean(getString(R.string.settings_continue_until_end_key), Boolean.getBoolean(getString(R.string.settings_continue_until_end_default)));
        if (stopAtEndOfTrack) {
            mStopAtEndOfCurrentTrack = true;
        } else {
            pause();
        }
    }

    private void terminateSleepTimer() {
        mStopAtEndOfCurrentTrack = false;
        mSleepTimer.disableTimer();
    }

    public SleepTimer getSleepTimer() {
        return mSleepTimer;
    }

    public void connectSleepTimer(SleepTimer sleepTimer) {
        mStopAtEndOfCurrentTrack = false;
        mSleepTimer = sleepTimer;
        mSleepTimer.setPlayer(this);
        mSleepTimer.setListener(this);
    }

    public AudioFile getCurrentAudioFile() {
        return mActiveAudio;
    }

    private void setMediaPlaybackState(int state) {
        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        float playbackSpeed = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            playbackSpeed = mMediaPlayer.getPlaybackParams().getSpeed();
        }
        if( state == PlaybackStateCompat.STATE_PLAYING ) {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE);
            playbackstateBuilder.setState(state,mMediaPlayer.getCurrentPosition(), playbackSpeed);
        } else {
            playbackstateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY);
            playbackstateBuilder.setState(state, mMediaPlayer.getCurrentPosition(), 0);
        }
        mediaSession.setPlaybackState(playbackstateBuilder.build());
    }
}
