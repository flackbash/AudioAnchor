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

public class AlbumCursorAdapter extends CursorAdapter {
    private Context mContext;

    AlbumCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.album_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the title of the current album and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.audio_storage_item_title);
        String title = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
        titleTV.setText(title);

        // Get the path of the thumbnail of the current album and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_storage_item_thumbnail);
        String path = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
        if (path != null) {
            int reqSize = mContext.getResources().getDimensionPixelSize(R.dimen.album_item_height);
            BitmapUtils.setImage(thumbnailIV, path, reqSize);
        } else {
            thumbnailIV.setImageResource(R.drawable.empty_cover_grey_blue);
        }
    }
}
