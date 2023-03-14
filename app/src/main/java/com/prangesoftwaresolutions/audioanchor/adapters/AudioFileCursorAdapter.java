package com.prangesoftwaresolutions.audioanchor.adapters;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;
import java.util.ArrayList;

/**
 * CursorAdapter for the ListView in the Album Activity
 */

public class AudioFileCursorAdapter extends CursorAdapter {

    private final Context mContext;
    private final MediaMetadataRetriever mMetadataRetriever;
    private final SharedPreferences mPrefs;

    public AudioFileCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mMetadataRetriever = new MediaMetadataRetriever();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.audio_file_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the path to the audio file
        long audioID = cursor.getLong(cursor.getColumnIndexOrThrow(AnchorContract.AudioEntry._ID));
        AudioFile audioFile = AudioFile.getAudioFileById(mContext, audioID);

        // Get the title of the current audio file and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.audio_file_item_title);
        String title = "";
        boolean titleFromMetadata = mPrefs.getBoolean(mContext.getString(R.string.settings_title_from_metadata_key), Boolean.getBoolean(mContext.getString(R.string.settings_title_from_metadata_default)));
        if (titleFromMetadata && audioFile != null && new File(audioFile.getPath()).exists()) {
            mMetadataRetriever.setDataSource(audioFile.getPath());
            title = mMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        }
        if (title == null || title.isEmpty()) {
            // Also use the file name if the audio file has no metadata title
            assert audioFile != null;
            title = audioFile.getTitle();
        }
        titleTV.setText(title);

        // Get the completed time and full time of the current audio file and set this text to the durationTV
        TextView durationTV = view.findViewById(R.id.audio_file_item_duration);
        boolean progressInPercent = mPrefs.getBoolean(mContext.getString(R.string.settings_progress_percentage_key), Boolean.getBoolean(mContext.getString(R.string.settings_progress_percentage_default)));

        String timeStr;
        if (progressInPercent) {
            int percent = Math.round(((float) audioFile.getCompletedTime() / audioFile.getTime()) * 100);
            timeStr = mContext.getResources().getString(R.string.time_completed_percent, percent);

        } else {
            String completedTimeStr = Utils.formatTime(audioFile.getCompletedTime(), audioFile.getTime());
            String durationStr = Utils.formatTime(audioFile.getTime(), audioFile.getTime());
            timeStr = mContext.getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        }
        durationTV.setText(timeStr);

        // Get the path of the thumbnail of the current recipe and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_file_item_thumbnail);

        // Set the audio status thumbnail to playing, finished, paused or not started (=transparent)
        thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked);
        if (isCurrentItemActive(audioID)) {
            thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else if (audioFile.getCompletedTime() >= audioFile.getTime() && audioFile.getTime() != 0) {
            thumbnailIV.setImageResource(R.drawable.ic_checked);
        } else if (audioFile.getCompletedTime() > 0) {
            thumbnailIV.setImageResource(R.drawable.ic_paused);
        } else {
            thumbnailIV.setImageDrawable(null);
        }

        // Show the deletable image if the file does not exist anymore
        ImageView deletableIV = view.findViewById(R.id.audio_file_item_deletable_img);
        if (!(new File(audioFile.getPath())).exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }

    /*
     * Check if the service is running for the current audio file
     */
    private boolean isCurrentItemActive(long audioId) {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<Long> audioIdList = new ArrayList<>(storage.loadAudioIds());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioIdList.size() && audioIndex != -1) {
                // Index is in a valid range
                long activeAudioId = audioIdList.get(audioIndex);
                return activeAudioId == audioId;
            }
        }
        return false;
    }
}
