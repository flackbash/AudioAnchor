package com.prangesoftwaresolutions.audioanchor;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.IOException;


public class PlayActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // The Uri of the selected audio file entry
    Uri mCurrentUri;
    private static final int PLAY_LOADER = 0;

    // Audio File variables
    int mId;
    String mTitle;
    int mAlbumId;
    String mPath;
    int mCompletedTime;
    int mTime;

    // The Views
    ImageView mCoverIV;
    TextView mTitleTV;
    TextView mAlbumTV;
    ImageView mPlayIV;
    ImageView mBackIV;
    ImageView mForwardIV;
    SeekBar mSeekBar;
    TextView mCompletedTimeTV;
    TextView mTimeTV;

    // Settings variables
    SharedPreferences mSharedPreferences;
    boolean mAutoplay;

    // The Sound Player
    SimpleSoundPlayer mPlayer;

    // SeekBar variables
    Handler mHandler;
    Runnable mRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        // Get the current uri from the intent
        mCurrentUri = getIntent().getData();

        // Set up the shared preferences.
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mAutoplay = mSharedPreferences.getBoolean(getString(R.string.settings_autoplay_key), Boolean.getBoolean(getString(R.string.settings_autoplay_default)));

        // Set up the audio player
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mPlayer = new SimpleSoundPlayer(audioManager);
        mHandler = new Handler();

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(PLAY_LOADER, null, this);

        mCoverIV = findViewById(R.id.play_cover);
        mTitleTV = findViewById(R.id.play_audio_file_title);
        mAlbumTV = findViewById(R.id.play_album_title);
        mPlayIV = findViewById(R.id.play_play);
        mBackIV = findViewById(R.id.play_backward);
        mForwardIV = findViewById(R.id.play_forward);
        mSeekBar = findViewById(R.id.play_seekbar);
        mCompletedTimeTV = findViewById(R.id.play_completed_time);
        mTimeTV = findViewById(R.id.play_time);

        mPlayIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mPlayer.isPlaying()) {
                    if (mPlayer.play(mAutoplay)) {
                        mPlayIV.setImageResource(R.drawable.pause_button);
                    }
                } else {
                    // Pause the media player if it is clicked and currently playing
                    mPlayer.pause();
                    mPlayIV.setImageResource(R.drawable.play_button);
                }
            }
        });

        mBackIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.backward(30);
            }
        });

        mForwardIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.forward(30);

            }
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mPlayer.setCurrentPosition(progress*1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String[] projection = {
                AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.COLUMN_PATH,
                AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME};

        return new CursorLoader(this, mCurrentUri, projection, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
        // Bail early if the cursor is null or there is less than 1 row in the cursor
        if (c == null || c.getCount() < 1) {
            return;
        }

        // Get the audio file details from the cursor
        if (c.moveToFirst()) {
            setNewAudioFile(c);

            // Set Album Cover and Title
            setAlbumInfo(mAlbumId);
        }
        c.close();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return(super.onOptionsItemSelected(item));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        updateAudioFileStatus();
        mPlayer.releaseMediaPlayer();
    }


    /*
    * Set up a new audio file from a given cursor. I.e. get audio file details, set up the Views,
    * initialize the Player
    */
    void setNewAudioFile(Cursor c) {
        mId = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
        mCurrentUri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, mId);
        mTitle = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
        mPath = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_PATH));
        mAlbumId = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_ALBUM));
        mCompletedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
        mTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));

        // Initialize the player for the current audio file
        try {
            mPlayer.initialize(mPath, mCompletedTime);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), R.string.play_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Update the time column of the audiofiles table if it has not yet been set
        if (mTime == 0) {
            mTime = mPlayer.getDuration();
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_TIME, mTime);
            getContentResolver().update(mCurrentUri, values, null, null);
        }

        // Set TextViews
        mTitleTV.setText(mTitle);
        mTimeTV.setText(Utils.formatTime(mTime, mTime));

        // Create an OnCompletionListener to react when the audio file has finished playing
        MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mPlayer.setCurrentPosition(mPlayer.getDuration());
                Cursor c = null;
                if (mAutoplay) {
                    updateAudioFileStatus();
                    c = getNextAudioFileCursor(mId);
                    if (c != null) {
                        setNewAudioFile(c);
                        c.close();
                        if (!mPlayer.play(mAutoplay)) {
                            mPlayIV.setImageResource(R.drawable.play_button);
                        }
                    }
                }
                if (!mAutoplay || c == null){
                    mPlayIV.setImageResource(R.drawable.play_button);
                }
            }
        };
        mPlayer.setOnCompletionListener(listener);

        // Initialize the seek bar for the current audio file
        initializeSeekBar();
    }

    /*
    * Get the album information and set up the related Views
    */
    void setAlbumInfo(int albumId) {
        // Set the album cover to the ImageView and the album title to the TextView
        String[] proj = {AnchorContract.AlbumEntry.COLUMN_COVER_PATH, AnchorContract.AlbumEntry.COLUMN_TITLE};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(albumId)};
        Cursor ac = getContentResolver().query(AnchorContract.AlbumEntry.CONTENT_URI, proj, sel, selArgs, null);
        if (ac == null || ac.getCount() < 1) {
            return;
        }
        if (ac.moveToFirst()) {
            String albumTitle = ac.getString(ac.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
            mAlbumTV.setText(albumTitle);

            String coverPath = ac.getString(ac.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
            Point size = new Point();
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            Display display;
            int reqSize = getResources().getDimensionPixelSize(R.dimen.default_device_width);
            if (wm != null) {
                display = wm.getDefaultDisplay();
                display.getSize(size);
                reqSize = java.lang.Math.max(size.x, size.y);
            }
            BitmapUtils.setImage(mCoverIV, coverPath, reqSize);
        }
        ac.close();
    }

    /*
    * Get the cursor for the next audio file in the album
    */
    Cursor getNextAudioFileCursor(int currentId) {
        String[] projection = {
                AnchorContract.AudioEntry._ID,
                AnchorContract.AudioEntry.COLUMN_TITLE,
                AnchorContract.AudioEntry.COLUMN_ALBUM,
                AnchorContract.AudioEntry.COLUMN_PATH,
                AnchorContract.AudioEntry.COLUMN_TIME,
                AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME};
        String sel = AnchorContract.AudioEntry.COLUMN_ALBUM + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};
        Cursor c = getContentResolver().query(AnchorContract.AudioEntry.CONTENT_URI, projection, sel, selArgs, null);

        if (c == null || c.getCount() < 1) {
            return null;
        }

        // Get the next audio file after the current in the same album
        if (c.moveToFirst()) {
            int id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
            boolean takeNext = (id == currentId);
            while (c.moveToNext()) {
                if (takeNext) {
                    return c;
                }
                id = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry._ID));
                takeNext = (id == currentId);
            }
        }
        return null;
    }

    /*
    * Update the completed time of the current audio file in the audiofiles table of the database
    */
    void updateAudioFileStatus() {
        // Update the time columns of the audiofiles table
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, mPlayer.getCurrentPosition());
        getContentResolver().update(mCurrentUri, values, null, null);
        getContentResolver().notifyChange(mCurrentUri, null);
    }

    /*
    * Initialize the SeekBar
    */
    void initializeSeekBar(){
        mSeekBar.setMax(mPlayer.getDuration()/1000);

        mRunnable = new Runnable() {
            @Override
            public void run() {
                if(mPlayer!=null){
                    int currentPosition = mPlayer.getCurrentPosition();
                    mSeekBar.setProgress(currentPosition/1000);
                    mCompletedTimeTV.setText(Utils.formatTime(currentPosition, mTime));
                }
                mHandler.postDelayed(mRunnable,100);
            }
        };
        mHandler.postDelayed(mRunnable,100);
    }
}
