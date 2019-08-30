package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

public class DirectoriesCursorAdapter extends CursorAdapter {
    private static boolean mRemoveDirView;
   // public boolean mRemoveDirView;

    DirectoriesCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);

        mRemoveDirView = false;

    }


    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.directory_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        TextView dirTV = view.findViewById(R.id.dir_textview);
        String dir = cursor.getString(cursor.getColumnIndex(AnchorContract.DirectoryEntry.COLUMN_DIRECTORY));
        dirTV.setText(dir);

        CheckBox dirShownCB = view.findViewById(R.id.dir_checkbox);
        int dirShown = cursor.getInt(cursor.getColumnIndex(AnchorContract.DirectoryEntry.COLUMN_DIR_SHOWN));

        if (dirShown == 1) {
            dirShownCB.setChecked(true);
        } else if (dirShown == 0) {
            dirShownCB.setChecked(false);
        }

            ImageButton mImgBtn = view.findViewById(R.id.dir_del_btn);

        if (!mRemoveDirView) {
            dirShownCB.setVisibility(View.VISIBLE);
            mImgBtn.setVisibility(View.INVISIBLE);

        } else {
            dirShownCB.setVisibility(View.INVISIBLE);
            mImgBtn.setVisibility(View.VISIBLE);

        }


    }


    public static void setRemoveView() {
        mRemoveDirView = true;

    }

    public static void setDefaultView() {
        mRemoveDirView = false;

    }



}
