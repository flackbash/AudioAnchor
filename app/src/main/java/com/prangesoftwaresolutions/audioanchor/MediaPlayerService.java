package com.prangesoftwaresolutions.audioanchor;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.IOException;
import java.util.ArrayList;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;


/**
 * Media Player Service class
 * Based on a tutorial by Valdio Veliu. See https://github.com/sitepoint-editors/AudioPlayer
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    public static final String ACTION_PLAY = "com.prangesoftwaresolutions.audioanchor.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_PAUSE";
    public static final String ACTION_TOGGLE_PAUSE = "com.prangesoftwaresolutions.audioanchor.ACTION_TOGGLE_PAUSE";
    public static final String ACTION_BACKWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_BACKWARD";
    public static final String ACTION_FORWARD = "com.prangesoftwaresolutions.audioanchor.ACTION_FORWARD";

    public static final String SERVICE_PLAY_STATUS_CHANGE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY_STATUS_CHANGE";
    public static final String SERVICE_MESSAGE_PLAY_STATUS = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_PLAYING";
    public static final String SERVICE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY_STATUS_CHANGE";
    public static final String SERVICE_MESSAGE_NEW_AUDIO = "com.prangesoftwaresolutions.audioanchor.SERVICE_MESSAGE_NEW_AUDIO";
    public static final String MSG_PLAY = "com.prangesoftwaresolutions.audioanchor.SERVICE_PLAY";
    public static final String MSG_PAUSE = "com.prangesoftwaresolutions.audioanchor.SERVICE_PAUSE";

    private MediaPlayer mMediaPlayer;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer notification IDs
    private static final int NOTIFICATION_ID = 101;
    private static final String CHANNEL_ID = "com.prangesoftwaresolutions.audioanchor.NOTIFICATION_CHANNEL";

    // Notification builder
    NotificationCompat.Builder mNotificationBuilder;

    //Used to pause/resume MediaPlayer
    private boolean resumeAfterCall = false;

    //AudioFocus
    private AudioManager audioManager;

    // Communication with clients
    private final IBinder iBinder = new LocalBinder();
    LocalBroadcastManager mBroadcaster;

    //List of available Audio files
    private ArrayList<AudioFile> mAudioMap;
    private int mAudioIndex = -1;
    private AudioFile activeAudio; //an object on the currently playing audio

    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    // Autoplay flag
    private boolean mAutoplay;

    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBroadcaster = LocalBroadcastManager.getInstance(this);

        // Set up the shared preferences.
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoplay = sharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));

        // Perform one-time setup procedures
        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();
        // Register BroadcastReceivers for broadcasts from PlayActivity
        register_playNewAudio();
        register_pauseAudio();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("BLABLABLA", "OnStartCommand called");
        if (mAudioMap == null || mAudioIndex == -1) {
            try {
                //Load data from SharedPreferences
                StorageUtil storage = new StorageUtil(getApplicationContext());
                mAudioMap = new ArrayList<>(storage.loadAudio());
                mAudioIndex = storage.loadAudioIndex();

                if (mAudioIndex < mAudioMap.size() && mAudioIndex != -1) {
                    //index is in a valid range
                    activeAudio = mAudioMap.get(mAudioIndex);
                } else {
                    stopSelf();
                }
            } catch (NullPointerException e) {
                stopSelf();
            }
        }

        //Request audio focus
        if (!requestAudioFocus()) {
            stopSelf();
        }

        if (mediaSessionManager == null) {
            initMediaSession();
            initMediaPlayer();
            buildNotification();
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        removeNotification();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("BLABLABLA", "OnDestroy called");

        if (mMediaPlayer != null) {
            stopMedia();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);
        unregisterReceiver(pauseAudio);

        //clear cached playlist
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }

    /**
     * Service Binder
     */
    class LocalBinder extends Binder {
        MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        setCurrentPosition(getDuration());
        updateAudioFileStatus();

        boolean playingNext = false;
        if (mAutoplay) {
            if (mAudioIndex + 1 < mAudioMap.size()) {
                mAudioIndex++;
                new StorageUtil(this).storeAudioIndex(mAudioIndex);
                activeAudio = mAudioMap.get(mAudioIndex);
                sendNewAudioFile(mAudioIndex);
                playingNext = true;
                loadAudioFile(activeAudio.getPath(), activeAudio.getCompletedTime());
                play();
                buildNotification();
            }
        }

        if (!playingNext) {
            // Notify the play activity
            sendPlayStatusResult(MSG_PAUSE);

            removeNotification();
            //stop the service
            stopSelf();
        }
    }

    @Override
    public void onAudioFocusChange(int focusState) {

        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                Log.e("BLABLABLA", "Audiofocus Gain");
                if (mMediaPlayer == null) {
                    initMediaPlayer();
                    loadAudioFile(activeAudio.getPath(), activeAudio.getCompletedTime());
                }
                setVolume(1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                Log.e("BLABLABLA", "Audiofocus Loss");
                pause();
                buildNotification();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                Log.e("BLABLABLA", "Audiofocus loss transient");

                if (mMediaPlayer.isPlaying()) mMediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.e("BLABLABLA", "Audiofocus loss can duck");

                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mMediaPlayer.isPlaying()) setVolume(0.1f);
                break;
        }
    }


    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = 0;
        if (audioManager != null) {
            result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void removeAudioFocus() {
        audioManager.abandonAudioFocus(this);
    }

    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer() {
        if (mMediaPlayer == null)
            mMediaPlayer = new MediaPlayer();//new MediaPlayer instance

        //Set up MediaPlayer event listeners
        mMediaPlayer.setOnCompletionListener(this);

        //Reset so that the MediaPlayer is not pointing to another data source
        mMediaPlayer.reset();

        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            // Set the data source to the mediaFile location
            mMediaPlayer.setDataSource(activeAudio.getPath());
            // mMediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mMediaPlayer.prepareAsync();
        // mMediaPlayer.seekTo(activeAudio.getCompletedTime());
    }

    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pause();
            buildNotification();
        }
    };

    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mMediaPlayer != null && !ongoingCall) {
                            resumeAfterCall = mMediaPlayer.isPlaying();
                            pause();
                            buildNotification();
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
                                    buildNotification();
                                }
                            }
                        }
                        resumeAfterCall = false;
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }

        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioAnchor");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                play();
                buildNotification();
            }

            @Override
            public void onPause() {
                super.onPause();
                pause();
                buildNotification();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                final KeyEvent event = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) {
                    return false;
                }

                final int keycode = event.getKeyCode();
                final int action = event.getAction();

                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        switch (keycode) {
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                pause();
                                buildNotification();
                                break;
                            case KeyEvent.KEYCODE_HEADSETHOOK:
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) pause();
                                else play();
                                buildNotification();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                forward(30);
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                backward(30);
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                                pause();
                                buildNotification();
                                break;
                            case KeyEvent.KEYCODE_MEDIA_PLAY:
                                play();
                                buildNotification();
                                break;
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void updateMetaData() {
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(),
                R.drawable.empty_cover_grey_blue); //replace with medias albumArt
        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbumTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
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

    private void buildNotification() {
        createNotificationChannel();

        int notificationAction = R.drawable.ic_media_pause;
        PendingIntent play_pauseAction;
        String title = "pause";

        //Build a new notification according to the current state of the MediaPlayer
        if (isPlaying()) {
            //create the pause action
            play_pauseAction = playbackAction(1);
        } else {
            notificationAction = R.drawable.ic_media_play;
            //create the play action
            play_pauseAction = playbackAction(0);
            title = "play";
        }

        Bitmap notificationCover;
        if (activeAudio.getCoverPath() == null) {
            notificationCover = BitmapFactory.decodeResource(getResources(), R.drawable.empty_cover_grey_blue);
        } else {
            notificationCover = BitmapFactory.decodeFile(activeAudio.getCoverPath());
        }

        Intent startActivityIntent = new Intent(this, PlayActivity.class);
        startActivityIntent.setData(ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, activeAudio.getId()));
        startActivityIntent.putExtra("albumId", activeAudio.getAlbumId());
        PendingIntent launchIntent = PendingIntent.getActivity(this, 0,
                startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create a new Notification
        mNotificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                // Hide the timestamp
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession.getSessionToken())
                        // Show our playback controls in the compat view
                        .setShowActionsInCompactView(0, 1, 2))
                // Set the Notification color
                .setColor(getResources().getColor(R.color.colorAccent))
                // Set the large and small icons
                .setLargeIcon(notificationCover)
                .setSmallIcon(R.drawable.ic_notification_new)
                // Set Notification content information
                .setContentText(activeAudio.getAlbumTitle())
                .setContentTitle(activeAudio.getTitle())
                // Set the intent for the activity that is launched on click
                .setContentIntent(launchIntent)
                // Set the visibility for the lock screen
                .setVisibility(VISIBILITY_PUBLIC)
                // Add playback actions
                .addAction(R.drawable.ic_media_backward, "backward", playbackAction(3))
                .addAction(notificationAction, title, play_pauseAction)
                .addAction(R.drawable.ic_media_forward, "forward", playbackAction(2));

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
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
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_TOGGLE_PAUSE)) {
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) transportControls.pause();
            else transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_FORWARD)) {
            forward(30);
        } else if (actionString.equalsIgnoreCase(ACTION_BACKWARD)) {
            backward(30);
        }
    }


    /**
     * Play audio
     */
    private BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (requestAudioFocus()) {
                play();
                buildNotification();
            }
        }
    };

    /**
     * Pause Audio
     */
    private BroadcastReceiver pauseAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pause();
            buildNotification();
        }
    };

    private void register_playNewAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(PlayActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }


    private void register_pauseAudio() {
        //Register pauseMedia receiver
        IntentFilter filter = new IntentFilter(PlayActivity.Broadcast_PAUSE_AUDIO);
        registerReceiver(pauseAudio, filter);
    }

    boolean isPlaying() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    void loadAudioFile(String path, int position) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
        }
        try {
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(path);
            mMediaPlayer.prepare();
            mMediaPlayer.seekTo(position);
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    void play() {
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying() && (mAutoplay || getCurrentPosition() != getDuration())) {
            Log.e("BLABLABLABLA", "currentPos: " + getCurrentPosition());
            mMediaPlayer.start();
            sendPlayStatusResult(MSG_PLAY);
        }
    }

    void stopMedia() {
        if (mMediaPlayer != null) {
            updateAudioFileStatus();
            sendPlayStatusResult(MSG_PAUSE);
            mMediaPlayer.stop();
        }
        buildNotification();
    }

    void pause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            sendPlayStatusResult(MSG_PAUSE);
            Log.e("BLABLABLABLA", "currentPos: " + getCurrentPosition());
        }
    }

    /*
     * Skip the specified amount of seconds forward
     */
    void forward(int seconds) {
        int newPos = Math.min(getDuration(), mMediaPlayer.getCurrentPosition() + seconds*1000);
        mMediaPlayer.seekTo(newPos);
    }

    /*
     * Skip the specified amount of seconds backward
     */
    void backward(int seconds) {
        int newPos = Math.max(0,mMediaPlayer.getCurrentPosition() - seconds*1000);
        Log.e("BLABLABLABLA", "New Pos: " + newPos);
        mMediaPlayer.seekTo(newPos);
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
    int getCurrentPosition() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    /*
     * Set current position of the played audio file
     */
    void setCurrentPosition(int progress) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(progress);
        }
    }

    void decreaseVolume(int step, int totalSteps) {
        float deltaVolume = (float) (1.0 / totalSteps);
        float currVolume = (float) (1.0 - (step * deltaVolume));
        if (mMediaPlayer != null) {
            setVolume(currVolume);
        }
    }

    void setVolume(float volume) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
        }
    }

    public void sendPlayStatusResult(String message) {
        Intent intent = new Intent(SERVICE_PLAY_STATUS_CHANGE);
        if(message != null) {
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
        // Update the current activeAudio
        activeAudio.setCompletedTime(getCurrentPosition());

        // Update the completedTime column of the audiofiles table
        Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, activeAudio.getId());
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, getCurrentPosition());
        getContentResolver().update(uri, values, null, null);
        getContentResolver().notifyChange(uri, null);
    }

    AudioFile getCurrentAudioFile() {
        return activeAudio;
    }
}