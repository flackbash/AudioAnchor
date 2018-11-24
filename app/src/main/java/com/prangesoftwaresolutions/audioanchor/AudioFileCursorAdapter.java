package com.prangesoftwaresolutions.audioanchor;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

/**
 * CursorAdapter for the ListView in the Main Activity
 */

public class AudioFileCursorAdapter extends CursorAdapter {

    private Context mContext;

    AudioFileCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.audio_file_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the title of the current audio file and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.audio_file_item_title);
        String title = cursor.getString(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
        titleTV.setText(title);

        // Get the completed time and full time of the current audio file and set this text to the durationTV
        TextView durationTV = view.findViewById(R.id.audio_file_item_duration);
        int duration = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
        int completedTime = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        String prefKey = mContext.getString(R.string.settings_progress_percentage_key);
        String prefDefault = mContext.getString(R.string.settings_progress_percentage_default);
        boolean progressInPercent = pref.getBoolean(prefKey, Boolean.getBoolean(prefDefault));

        String timeStr;
        if (progressInPercent) {
            int percent = Math.round(((float)completedTime / duration) * 100);
            timeStr = mContext.getResources().getString(R.string.time_completed_percent, percent);

        } else {
            String completedTimeStr = Utils.formatTime(completedTime, duration);
            String durationStr = Utils.formatTime(duration, duration);
            timeStr = mContext.getResources().getString(R.string.time_completed, completedTimeStr, durationStr);
        }
        durationTV.setText(timeStr);

        // Get the path of the thumbnail of the current recipe and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_file_item_thumbnail);

        // Set the audio status thumbnail to transparent, paused or finished
        if (completedTime >= duration && duration != 0) {
            thumbnailIV.setImageResource(R.drawable.ic_checked);
        } else if (completedTime > 0) {
            thumbnailIV.setImageResource(R.drawable.ic_paused);
        } else {
            thumbnailIV.setImageResource(R.drawable.ic_unchecked);
        }

    }
}
