package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Adapter class taken from
 * https://developer.android.com/guide/topics/ui/layout/recyclerview#java
 */

public class AudioStorageAdapter extends RecyclerView.Adapter<AudioStorageAdapter.AudioStorageViewHolder> {
    private String[] mDataset;
    private Context mContext;

    // Provide a suitable constructor (depends on the kind of dataset)
    AudioStorageAdapter(Context context, String[] myDataset) {
        mDataset = myDataset;
        mContext = context;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public AudioStorageAdapter.AudioStorageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.audio_storage_item, null);
        return new AudioStorageViewHolder(mContext, v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(AudioStorageViewHolder holder, int position) {
        // - get element from the dataset at this position
        // - replace the contents of the view with that element
        holder.mTitleTV.setText(mDataset[position]);
    }

    // Return the size of the dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.length;
    }


    // Provide a reference to the views for each data item
    static class AudioStorageViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView mTitleTV;
        ImageView mThumbnailIV;
        Context mContext;

        AudioStorageViewHolder(Context context, View v) {
            super(v);

            mContext = context;

            mTitleTV = v.findViewById(R.id.audio_storage_item_title);
            mThumbnailIV = v.findViewById(R.id.audio_storage_item_thumbnail);
            v.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();

            // Check if an item was deleted, but the user clicked it before the UI removed it
            if (position != RecyclerView.NO_POSITION) {
                Toast.makeText(mContext, mTitleTV.getText(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}

