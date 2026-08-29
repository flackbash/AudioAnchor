package com.prangesoftwaresolutions.audioanchor.services;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.Manifest;
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
import android.content.pm.PackageManager;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC;


/*
 * Media Player Service class
 * Based on a tutorial by Valdio Veliu. See https://github.com/sitepoint-editors/AudioPlayer
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener, SleepTimerStatusListener {

    public static final String ACTION_PLAY = "com.prangesoftwaresolutions.audioanchor.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_PAUSE";
    public static final String ACTION_TOGGLE_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_TOGGLE_PAUSE";
    public static final String ACTION_BACKWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_BACKWARD";
    public static final String ACTION_FORWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_FORWARD";
    public static final String ACTION_STOP = "com.prangesoftwaresolutions.audioanchor.ACTION_STOP";
    public static final String ACTION_BOOKMARK = "com.prangesoftwaresolutions.audioanchor.ACTION_BOOKMARK";

    public static final String SERVICE_PLAY_STATUS_CHANGE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY_STATUS_CHANGE";
    public static final String SERVICE_MESSAGE_PLAY_STATUS = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_PLAYING";
    public static final String SERVICE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_NEW_AUDIO";
    public static final String SERVICE_MESSAGE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_NEW_AUDIO";
    public static final String MSG_PLAY = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY";
    public static final String MSG_PAUSE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PAUSE";
    public static final String MSG_STOP = "com.prangesoftwaresolutions.audioanchor.SERVICE_STOP";

    public static final String BROADCAST_REMOVE_NOTIFICATION = "com.prangesoftwaresolutions.audioanchor.REMOVE_NOTIFICATION";
    public static final String BROADCAST_UNBIND_CURRENT_SERVICE = "com.prangesoftwaresolutions.audioanchor.UNBIND_CURRENT_SERVICE";
    public static final String BROADCAST_RESET = "com.prangesoftwaresolutions.audioanchor.RESET";

    public static final String LOG_TAG = MediaPlayerService.class.getSimpleName();

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

    // All MediaPlayer calls that can block (setDataSource/prepare/seekTo/setPlaybackParams/etc.)
    // run serialized on this single background thread instead of the caller's thread. Some
    // audio containers (e.g. very long single-stream Ogg/Opus files) can take tens of seconds
    // for the framework to prepare or seek within, which would otherwise freeze the UI since
    // PlayActivity calls into this same-process service directly. Read-only state queries
    // (getCurrentPosition/getDuration/isPlaying) still happen on the caller's thread since
    // they're cheap; they're wrapped in try/catch since they can now race the narrow window
    // where this executor is transitioning the player between states.
    private final ExecutorService mPlayerExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // How long to wait after onCompletion() before tearing down the just-finished track and
    // loading the next one. Fixes https://github.com/praecipitator/AudioAnchor (interruptFix):
    // calling MediaPlayer.reset() immediately on completion races the framework's own teardown
    // of the finished track -- confirmed via logcat timestamps to both truncate its last audio
    // and occasionally throw a spurious MediaPlayer error (what=-38) when the gap is ~20ms.
    // 50ms consistently avoided both in testing.
    private final int NEXT_TRACK_WAIT_TIME = 50;

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
        IntentFilter removeNotificationIntentFilter = new IntentFilter(BROADCAST_REMOVE_NOTIFICATION);
        ContextCompat.registerReceiver(this, mRemoveNotificationReceiver, removeNotificationIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

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
                    return START_STICKY;
                }
            } catch (NullPointerException e) {
                stopForeground(true);
                stopSelf();
                return START_STICKY;
            }
        }

        if (!requestAudioFocus()) {
            stopForeground(true);
            stopSelf();
            return START_STICKY;
        }

        if (mediaSession == null) {
            initMediaSession();
            initMediaPlayer(mActiveAudio.getPath(), mActiveAudio.getCompletedTime(), this::play);
        }

        // Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);

        return START_STICKY;
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
            // Null the field immediately so no new work gets submitted against this player,
            // but release it on mPlayerExecutor so it runs after any stop() already queued
            // there by stopMedia() -- release() racing a not-yet-run stop() on another thread
            // would be unsafe.
            final MediaPlayer playerToRelease = mMediaPlayer;
            mMediaPlayer = null;
            mPlayerExecutor.execute(playerToRelease::release);
        }
        mPlayerExecutor.shutdown();
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

    private AudioAttributes createAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    /*
     * Set up the MediaPlayer for the given path/position and run onReady (on the main thread)
     * once it's actually ready to use. The blocking setup work (setDataSource/prepare/seekTo)
     * runs on mPlayerExecutor -- for a normal file this completes almost instantly, but for a
     * very long single-stream file (e.g. a multi-hour Ogg/Opus audiobook) Android's extractor
     * can take tens of seconds, and this keeps that off the UI thread. Do not touch mMediaPlayer
     * from anywhere else while this is in flight -- everything that mutates it must go through
     * mPlayerExecutor so calls are never made concurrently from two threads.
     */
    void initMediaPlayer(String path, int position, Runnable onReady) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnErrorListener(this);
        }
        mPlayerExecutor.execute(() -> {
            if (initMediaPlayerBlocking(path, position) && onReady != null) {
                mMainHandler.post(onReady);
            }
        });
    }

    // Must only run on mPlayerExecutor's thread.
    private boolean initMediaPlayerBlocking(String path, int position) {
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
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            stopForeground(true);
            stopSelf();
            return false;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        setCurrentPosition(getDuration());
        updateAudioFileStatus();

        boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));

        if (autoplay && !mStopAtEndOfCurrentTrack) {
            if (!haveNextAudioFile()) {
                finishPlaybackAfterCompletion();
            } else {
                // Give the framework a moment to finish tearing down the just-completed track
                // before reusing the same MediaPlayer for the next one. Confirmed via logcat
                // timestamps that calling reset() ~20ms after onCompletion() (i.e. immediately)
                // races that teardown: it can truncate the last bit of the finished track's
                // audio, and on one observed run threw a spurious MediaPlayer error (what=-38)
                // that silently killed playback. Widening the gap to NEXT_TRACK_WAIT_TIME (50ms)
                // avoided both in repeated testing.
                mMainHandler.postDelayed(() -> {
                    if (!initNextAudioFile(true)) {
                        finishPlaybackAfterCompletion();
                    }
                }, NEXT_TRACK_WAIT_TIME);
            }
        } else {
            if (mStopAtEndOfCurrentTrack) {
                terminateSleepTimer();
            }
            finishPlaybackAfterCompletion();
        }
    }

    private boolean haveNextAudioFile() {
        return (mAudioIndex + 1 < mAudioIdQueue.size());
    }

    private void finishPlaybackAfterCompletion() {
        // Notify the play activity that the playback was paused
        sendPlayStatusResult(MSG_STOP);

        removeNotification();

        // Send broadcast that the notification was removed
        // The MediaPlayerService receiver will then also stop the service by calling stopSelf()
        sendBroadcast(new Intent(BROADCAST_REMOVE_NOTIFICATION).setPackage(getPackageName()));

        mLockManager.releaseWakeLock();
    }

    /*
     * Called by the framework when the MediaPlayer hits an unrecoverable error (e.g. a
     * malformed or unsupported file, or -- as seen with very long single-stream Ogg/Opus
     * files -- the platform's own extractor/decoder failing). Without this listener such a
     * failure left the player silently stuck in the Error state: the UI kept showing whatever
     * position it last had, indistinguishable from a paused, working track. Returning true
     * tells the framework we've handled it (a plain onCompletion() would otherwise not fire,
     * but would also be wrong here since this isn't a normal end-of-track).
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(LOG_TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        if (mActiveAudio != null) {
            String errorMsg = getResources().getString(R.string.audio_file_error, mActiveAudio.getTitle());
            Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
        }
        finishPlaybackAfterCompletion();
        return true;
    }

    /*
     * Advance to the next audio file, if there is one. The (potentially slow) player setup for
     * the new file happens asynchronously; playAfter decides whether to start playing it, or
     * just reflect the paused state, once it's actually ready.
     */
    public boolean initNextAudioFile(boolean playAfter) {
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
            initMediaPlayer(mActiveAudio.getPath(), startPosition, () -> {
                updateAudioFileStatus();  // Needed if startPosition is set to 0 such that the time in the AlbumActivity is updated
                updateMetaData();
                buildNotification();
                if (playAfter) {
                    play();
                } else {
                    setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                }
            });
            return true;
        }
        return false;
    }

    /*
     * Go back to the previous audio file, if there is one. See initNextAudioFile() for the
     * playAfter/async behavior.
     */
    public boolean initPreviousAudioFile(boolean playAfter) {
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
            initMediaPlayer(mActiveAudio.getPath(), startPosition, () -> {
                updateAudioFileStatus();
                updateMetaData();
                buildNotification();
                if (playAfter) {
                    play();
                } else {
                    setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                }
            });
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
                    initMediaPlayer(mActiveAudio.getPath(), mActiveAudio.getCompletedTime(), this::resumeAfterAudioFocusGain);
                } else {
                    resumeAfterAudioFocusGain();
                }
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

                if (isPlaying()) {
                    pauseDueToAudioInterruption();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                Log.e("MediaPlayerService", "Audiofocus loss can duck");
                if (isPlaying()) {
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

    private void resumeAfterAudioFocusGain() {
        if (mIsPausedByTransientFocusLoss) {
            // Resume playback if audiofocus was lost only temporarily
            play();
            mIsPausedByTransientFocusLoss = false;
        }
        setVolume(1.0f);
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
        // Register the listener with the telephony manager. Listen for changes to the device call state.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            // Starting listening for PhoneState changes
            phoneStateListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    switch (state) {
                        // If at least one call exists or the phone is ringing pause the MediaPlayer
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                        case TelephonyManager.CALL_STATE_RINGING:
                            if (mMediaPlayer != null && !ongoingCall) {
                                resumeAfterCall = isPlaying();
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
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            Log.w(LOG_TAG, "READ_PHONE_STATE permission not granted; cannot listen to phone state changes");
        }
    }

    /*
     * MediaSession and Notification actions
     */
    private void initMediaSession() {
        if (mediaSessionManager != null) return;

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        ComponentName mediaButtonReceiverComponentName = new ComponentName(getApplicationContext(), MediaButtonIntentReceiver.class);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mediaButtonReceiverComponentName);

        PendingIntent mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE);

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
        PendingIntent launchIntent = PendingIntent.getActivity(this,
                0,
                startActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Set up intent to stop service when notification is removed
        Intent intent = new Intent(this, MediaPlayerService.class);
        intent.setAction(BROADCAST_REMOVE_NOTIFICATION);

        PendingIntent deleteIntent = PendingIntent.getService(
                this,
                10, // Use a unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
                // Set the visibility for the lock screen
                .setVisibility(VISIBILITY_PUBLIC)
                // Make notification non-removable if the track is currently playing
                .setOngoing(playPauseTitle.equals(getString(R.string.button_pause)))
                // Add playback actions
                .addAction(R.drawable.ic_media_bookmark, getString(R.string.button_bookmark), playbackAction(4))
                .addAction(skipBackwardImageResource, getString(R.string.button_backward), playbackAction(3))
                .addAction(playPauseImageResource, playPauseTitle, playPauseAction)
                .addAction(skipForwardImageResource, getString(R.string.button_forward), playbackAction(2))
                .setDeleteIntent(deleteIntent);

        Notification notification = mNotificationBuilder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackActionIntent = new Intent(this, MediaPlayerService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        switch (actionNumber) {
            case 0:
                // Play
                playbackActionIntent.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, flags);
            case 1:
                // Pause
                playbackActionIntent.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, flags);
            case 2:
                // Skip forward
                playbackActionIntent.setAction(ACTION_FORWARD);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, flags);
            case 3:
                // Skip backward
                playbackActionIntent.setAction(ACTION_BACKWARD);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, flags);
            case 4:
                // Set bookmark
                playbackActionIntent.setAction(ACTION_BOOKMARK);
                return PendingIntent.getService(this, actionNumber, playbackActionIntent, flags);
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
        } else if (actionString.equalsIgnoreCase(ACTION_STOP) || actionString.equalsIgnoreCase(BROADCAST_REMOVE_NOTIFICATION)) {
            Log.e("MediaPlayerService", "Action Stop/Remove received via Intent");
            stopForeground(true);
            stopSelf();
        } else if (actionString.equalsIgnoreCase(ACTION_TOGGLE_PAUSE)) {
            if (isPlaying()) pause();
            else play();
        } else if (actionString.equalsIgnoreCase(ACTION_FORWARD)) {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_notification_forward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
                skipToNextAudioFile();
            } else {
                forward(skipInterval);
                // Update the playback state to reflect the new position in the progress bar
                int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                setMediaPlaybackState(state);
            }
        } else if (actionString.equalsIgnoreCase(ACTION_BACKWARD)) {
            int skipInterval = mSharedPreferences.getInt(getString(R.string.settings_notification_backward_button_key), Integer.parseInt(getString(R.string.settings_skip_interval_big_default)));
            if (SkipIntervalUtils.isMaxSkipInterval(skipInterval)) {
                skipToPreviousAudioFile();
            } else {
                backward(skipInterval);
                // Update the playback state to reflect the new position in the progress bar
                int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                setMediaPlaybackState(state);
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
            try {
                return mMediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                // mMediaPlayer is being (re)initialized on mPlayerExecutor right now.
                e.printStackTrace();
            }
        }
        return false;
    }

    public void play() {
        if (mMediaPlayer == null) return;

        // Get Autoplay and Autorewind settings
        boolean autoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));
        int autorewindTime = Integer.parseInt(mSharedPreferences.getString(getString(R.string.settings_autorewind_key), getString(R.string.settings_autorewind_default)));

        // Request a partial wake lock for the duration of the playback
        mLockManager.acquireWakeLock();

        // All the decisions below are made inside the executor task itself (not here) so they
        // see the player's state as of when they actually run, not as of when play() happened
        // to be called -- important since a call made while a previous track's setup is still
        // in flight only runs once that setup (and any state it changes) has completed.
        mPlayerExecutor.execute(() -> {
            boolean started;
            try {
                if (mMediaPlayer.isPlaying()) return;
                int currentPosition = mMediaPlayer.getCurrentPosition();
                int duration = mMediaPlayer.getDuration();
                if (!(autoplay || currentPosition != duration)) return;
                if (currentPosition != duration) {
                    mMediaPlayer.seekTo(currentPosition - autorewindTime * 1000);
                }
                mMediaPlayer.start();
                started = true;
            } catch (IllegalStateException e) {
                e.printStackTrace();
                started = false;
            }
            if (started) {
                mMainHandler.post(this::onPlaybackStarted);
            }
        });
    }

    private void onPlaybackStarted() {
        sendPlayStatusResult(MSG_PLAY);
        mediaSession.setActive(true);
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        buildNotification();

        updateLastPlayedAudio();
        boolean addLastPlayPositionBookmarks = mSharedPreferences.getBoolean(getString(R.string.settings_add_last_play_position_bookmark_key), Boolean.getBoolean(getString(R.string.settings_add_last_play_position_bookmark_default)));
        if (addLastPlayPositionBookmarks) updateLastPlayPositionBookmarks();
    }

    public void stopMedia() {
        // Release the partial wake lock to save battery
        mLockManager.releaseWakeLock();

        if (mMediaPlayer != null) {
            updateAudioFileStatus();
            sendPlayStatusResult(MSG_STOP);
            mPlayerExecutor.execute(() -> {
                try {
                    mMediaPlayer.stop();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            });
            setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        }
        mediaSession.setActive(false);
        stopForeground(true);
    }

    public void pause() {
        // Release the partial wake lock to save battery
        mLockManager.releaseWakeLock();

        if (mMediaPlayer != null) {
            mPlayerExecutor.execute(() -> {
                boolean wasPlaying;
                try {
                    wasPlaying = mMediaPlayer.isPlaying();
                    if (wasPlaying) {
                        mMediaPlayer.pause();
                    }
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    wasPlaying = false;
                }
                if (wasPlaying) {
                    mMainHandler.post(this::onPlaybackPaused);
                }
            });
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
    }

    private void onPlaybackPaused() {
        updateAudioFileStatus();
        sendPlayStatusResult(MSG_PAUSE);
        setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        buildNotification();
    }

    /*
     * Skip the specified amount of milliseconds forward (positive) or backward (negative),
     * clamped to the track's bounds.
     */
    private void seekRelativeAsync(int deltaMillis) {
        if (mMediaPlayer == null) return;
        mPlayerExecutor.execute(() -> {
            try {
                int duration = mMediaPlayer.getDuration();
                int newPos = Math.max(0, Math.min(duration, mMediaPlayer.getCurrentPosition() + deltaMillis));
                mMediaPlayer.seekTo(newPos);
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return;
            }
            mMainHandler.post(this::updateAudioFileStatus);
        });
    }

    /*
     * Skip the specified amount of seconds forward
     */
    void forward(int seconds) {
        seekRelativeAsync(seconds * 1000);
    }

    /*
     * Skip the specified amount of seconds backward
     */
    void backward(int seconds) {
        seekRelativeAsync(-seconds * 1000);
    }

    /*
     * Skip to next audio file
     */
    public void skipToNextAudioFile() {
        boolean wasPlaying = isPlaying();
        updateAudioFileStatus();
        initNextAudioFile(wasPlaying);
    }

    /*
     * Skip to previous audio file
     */
    public void skipToPreviousAudioFile() {
        boolean wasPlaying = isPlaying();
        updateAudioFileStatus();
        initPreviousAudioFile(wasPlaying);
    }

    /*
     * Get total duration of the audio file
     */
    int getDuration() {
        if (mMediaPlayer != null) {
            try {
                return mMediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    /*
     * Get current position of the played audio file. Returns -1 (rather than 0) when the
     * position can't reliably be read right now -- either there is no player yet, or
     * mPlayerExecutor is mid-(re)initialization -- so callers that persist this value (see
     * updateAudioFileStatus()) can tell "unknown" apart from a real position of zero and avoid
     * overwriting stored progress with a bogus value.
     */
    public int getCurrentPosition() {
        if (mMediaPlayer != null) {
            try {
                return mMediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    /*
     * Set current position of the played audio file
     */
    public void setCurrentPosition(int progress) {
        if (mMediaPlayer != null) {
            mPlayerExecutor.execute(() -> {
                try {
                    mMediaPlayer.seekTo(progress);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                mMainHandler.post(() -> {
                    setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);
                    updateAudioFileStatus();
                });
            });
        } else {
            updateAudioFileStatus();
        }
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
            mPlayerExecutor.execute(() -> {
                try {
                    mMediaPlayer.setVolume(volume, volume);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void setPlaybackSpeed(float speed) {
        if (mMediaPlayer == null) return;

        if (!isPlaying()) {
            initMediaPlayer(mActiveAudio.getPath(), Math.max(0, getCurrentPosition()), null);
        } else {
            mPlayerExecutor.execute(() -> setPlaybackSpeedIfInLegalRange(speed));
        }
    }

    // Must only run on mPlayerExecutor's thread (it's also called from initMediaPlayerBlocking(),
    // which already runs there).
    @TargetApi(Build.VERSION_CODES.M)
    void setPlaybackSpeedIfInLegalRange(float speed) {
        try {
            mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(speed));
        } catch (IllegalArgumentException e) {
            String illegalSpeed = getResources().getString(R.string.illegal_speed, speed);
            // Toast requires a Looper thread; this can run on mPlayerExecutor.
            mMainHandler.post(() -> Toast.makeText(getApplicationContext(), illegalSpeed, Toast.LENGTH_LONG).show());
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
        int position = getCurrentPosition();
        if (position < 0) {
            // Position isn't reliably known right now (see getCurrentPosition()) -- skip this
            // update rather than persisting a bogus value over the real stored progress.
            return;
        }

        // Update the current active audio
        mActiveAudio.setCompletedTime(position);

        // Update the completedTime column of the audiofiles table
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mActiveAudio.getID());
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, position);
        getContentResolver().update(uri, values, null, null);
    }

    /*
     * Update the last played column of the album table
     */
    void updateLastPlayedAudio() {
        long now = System.currentTimeMillis();

        Album album = mActiveAudio.getAlbum();
        album.setLastPlayedID(mActiveAudio.getID());
        album.setLastPlayedTimestamp(now);
        album.updateInDB(this);

        // Also record the timestamp on the track itself, for the "sort tracks by last played"
        // option -- a direct targeted update (like updateAudioFileStatus()'s completed_time
        // write below) rather than going through AudioFile.getContentValues(), which is only
        // meant for inserting brand-new tracks.
        mActiveAudio.setLastPlayedTimestamp(now);
        Uri audioUri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mActiveAudio.getID());
        ContentValues audioValues = new ContentValues();
        audioValues.put(AnchorContract.AudioEntry.COLUMN_LAST_PLAYED_TIMESTAMP, now);
        getContentResolver().update(audioUri, audioValues, null, null);
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
        int shakeSensitivitySetting = mSharedPreferences.getInt(getString(R.string.settings_shake_sensitivity_key), Integer.parseInt(getString(R.string.settings_shake_sensitivity_default)));
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
        if (mMediaPlayer == null) return;

        PlaybackStateCompat.Builder playbackstateBuilder = new PlaybackStateCompat.Builder();
        float playbackSpeed = 1;
        int position = 0;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playbackSpeed = mMediaPlayer.getPlaybackParams().getSpeed();
            }
            position = mMediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            // mMediaPlayer is being (re)initialized on mPlayerExecutor right now; fall back to
            // position 0 / normal speed for this update, the next one will have real values.
            e.printStackTrace();
        }

        // Define all available actions including skip actions
        long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY |
                       PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                       PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        playbackstateBuilder.setActions(actions);
        if( state == PlaybackStateCompat.STATE_PLAYING ) {
            playbackstateBuilder.setState(state, position, playbackSpeed);
        } else {
            playbackstateBuilder.setState(state, position, 0);
        }

        // Add custom actions for Android 16 compatibility
        // These allow the notification actions to be properly routed to the service
        playbackstateBuilder.addCustomAction(ACTION_BACKWARD, getString(R.string.button_backward), R.drawable.ic_notification_backward);
        playbackstateBuilder.addCustomAction(ACTION_FORWARD, getString(R.string.button_forward), R.drawable.ic_notification_forward);
        playbackstateBuilder.addCustomAction(ACTION_BOOKMARK, getString(R.string.button_bookmark), R.drawable.ic_media_bookmark);

        mediaSession.setPlaybackState(playbackstateBuilder.build());
    }
}
