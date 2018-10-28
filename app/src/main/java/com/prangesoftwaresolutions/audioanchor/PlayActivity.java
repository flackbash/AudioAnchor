package com.prangesoftwaresolutions.audioanchor;

import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

public class PlayActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // The Uri of the selected audio file entry
    Uri mCurrentUri;
    private static final int PLAY_LOADER = 0;

    // Audio File variables
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
                    if (mPlayer.play()) {
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
        if (c.moveToFirst()) {
            mTitle = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
            mPath = c.getString(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_PATH));
            mAlbumId = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_ALBUM));
            mCompletedTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
            mTime = c.getInt(c.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
        }
        if (mTime == 0) {
            mTime = mPlayer.getDuration();
            ContentValues values = new ContentValues();
            values.put(AnchorContract.AudioEntry.COLUMN_TIME, mTime);
            getContentResolver().update(mCurrentUri, values, null, null);
        }

        // Set TextViews
        mTitleTV.setText(mTitle);
        mTimeTV.setText(Utils.formatTime(mTime, mTime));

        // Set the album cover to the ImageView and the album title to the TextView
        String[] proj = {AnchorContract.AlbumEntry.COLUMN_COVER_PATH, AnchorContract.AlbumEntry.COLUMN_TITLE};
        String sel = AnchorContract.AlbumEntry._ID + "=?";
        String[] selArgs = {Long.toString(mAlbumId)};
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

        // Initialize the player
        mPlayer.initialize(mPath, mCompletedTime);
        initializeSeekBar();

        // Create an OnCompletionListener to react when the audio file has finished playing
        MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mPlayIV.setImageResource(R.drawable.play_button);
                mPlayer.setCurrentPosition(mPlayer.getDuration());
            }
        };
        mPlayer.setOnCompletionListener(listener);
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

        // Update the time columns of the audiofiles table
        ContentValues values = new ContentValues();
        values.put(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME, mPlayer.getCurrentPosition());
        getContentResolver().update(mCurrentUri, values, null, null);

        mPlayer.releaseMediaPlayer();

        getContentResolver().notifyChange(mCurrentUri, null);
    }

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
