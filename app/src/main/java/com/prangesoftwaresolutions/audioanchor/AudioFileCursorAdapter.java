package com.prangesoftwaresolutions.audioanchor;


import android.content.Context;
import android.database.Cursor;
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
    AudioFileCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.audio_file_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the title of the current Recipe and set this text to the recipeTV
        TextView titleTV = view.findViewById(R.id.audio_file_item_title);
        String title = cursor.getString(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TITLE));
        titleTV.setText(title);

        // Get the path of the thumbnail of the current recipe and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_file_item_thumbnail);

        // Set the audio status thumbnail to transparent, paused or finished
        int completedTime = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_COMPLETED_TIME));
        int time = cursor.getInt(cursor.getColumnIndex(AnchorContract.AudioEntry.COLUMN_TIME));
        if (completedTime >= time && time != 0) {
            thumbnailIV.setImageResource(R.drawable.ic_checked);
        } else if (completedTime > 0) {
            thumbnailIV.setImageResource(R.drawable.ic_paused);
        } else {
            thumbnailIV.setImageResource(R.drawable.ic_unchecked);
        }

    }
}
