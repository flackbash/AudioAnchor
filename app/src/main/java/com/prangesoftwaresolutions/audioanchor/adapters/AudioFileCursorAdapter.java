package com.prangesoftwaresolutions.audioanchor.adapters;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;
import java.util.ArrayList;

/**
 * CursorAdapter for the ListView in the Album Activity
 */

public class AudioFileCursorAdapter extends CursorAdapter {

    private Context mContext;
    private String mDirectory;
    private MediaMetadataRetriever mMetadataRetriever;
    private SharedPreferences mPrefs;

    public AudioFileCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
        // Get the base directory from the shared preferences.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDirectory = mPrefs.getString(mContext.getString(R.string.preference_filename), null);
        mMetadataRetriever = new MediaMetadataRetriever();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.audio_file_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the path to the audio file
        String audioTitle = cursor.getString(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
        String albumTitle = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.TABLE_NAME + AnchorContract.AlbumEntry.COLUMN_TITLE));
        String filePath = mDirectory + File.separator + albumTitle + File.separator + audioTitle;

        // Get the title of the current audio file and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.audio_file_item_title);
        String title = "";
        boolean titleFromMetadata = mPrefs.getBoolean(mContext.getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(mContext.getString(R.string.settings_title_from_metadata_default)));
        if (titleFromMetadata) {
            mMetadataRetriever.setDataSource(filePath);
            title = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (title == null || title.isEmpty()) {
            // Also use the file name if the audio file has no metadata title
            title = cursor.getString(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
        }
        titleTV.setText(title);

        // Get the completed time and full time of the current audio file and set this text to the durationTV
        TextView durationTV = view.findViewById(R.id.audio_file_item_duration);
        int duration = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
        int completedTime = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));

        boolean progressInPercent = mPrefs.getBoolean(mContext.getString(R.string.settings_progress_percentage_key), Boolean.getBoolean(mContext.getString(R.string.settings_progress_percentage_default)));

        String timeStr;
        if (progressInPercent) {
            int percent = Math.round(((float) completedTime / duration) * 100);
            timeStr = mContext.getResources().getString(R.string.time_completed_percent, percent);

        } else {
            String completedTimeStr = Utils.formatTime(completedTime, duration);
            String durationStr = Utils.formatTime(duration, duration);
            timeStr = mContext.getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        }
        durationTV.setText(timeStr);

        // Get the path of the thumbnail of the current recipe and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_file_item_thumbnail);

        // Set the audio status thumbnail to playing, finished, paused or not started (=transparent)
        boolean darkTheme = mPrefs.getBoolean(mContext.getString(R.string.settings_dark_key), Boolean.getBoolean(mContext.getString(R.string.settings_dark_default)));
        if (darkTheme) {
            thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked_dark_theme);
        } else {
            thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked);
        }
        int audioId = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry._ID));
        if (isCurrentItemActive(audioId)) {
            thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else if (completedTime >= duration && duration != 0) {
            thumbnailIV.setImageResource(R.drawable.ic_checked);
        } else if (completedTime > 0) {
            thumbnailIV.setImageResource(R.drawable.ic_paused);
        } else {
            thumbnailIV.setImageDrawable(null);
        }

        // Show the deletable image if the file does not exist anymore
        ImageView deletableIV = view.findViewById(R.id.audio_file_item_deletable_img);
        if (!(new File(filePath)).exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }

    /*
     * Check if the service is running for the current audio file
     */
    private boolean isCurrentItemActive(int audioId) {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<Integer> audioIdList = new ArrayList<>(storage.loadAudioIds());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioIdList.size() && audioIndex != -1) {
                // Index is in a valid range
                int activeAudioId = audioIdList.get(audioIndex);
                return activeAudioId == audioId;
            }
        }
        return false;
    }
}
