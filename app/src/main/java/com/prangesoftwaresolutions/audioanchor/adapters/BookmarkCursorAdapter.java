package com.prangesoftwaresolutions.audioanchor.adapters;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;


/**
 * CursorAdapter for the Bookmark dialog in the Play Activity
 */

public class BookmarkCursorAdapter extends CursorAdapter {
    private final long mTotalMillis;

    public BookmarkCursorAdapter(Context context, Cursor c, long totalMillis) {
        super(context, c, 0);
        mTotalMillis = totalMillis;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.bookmark_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Get the title of the current bookmark and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.bookmark_title_tv);
        String title = cursor.getString(cursor.getColumnIndexOrThrow(AnchorContract.BookmarkEntry.COLUMN_TITLE));
        titleTV.setText(title);
        // Get the position of the current bookmark
        TextView positionTV = view.findViewById(R.id.bookmark_position_tv);
        long position = cursor.getLong(cursor.getColumnIndexOrThrow(AnchorContract.BookmarkEntry.COLUMN_POSITION));
        String positionString = Utils.formatTime(position, mTotalMillis);
        positionTV.setText(positionString);
    }
}
