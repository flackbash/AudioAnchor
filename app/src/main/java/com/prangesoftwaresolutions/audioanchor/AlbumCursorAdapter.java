package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.io.File;
import java.util.ArrayList;

/**
 * CursorAdapter for the ListView in the Main Activity
 */

public class AlbumCursorAdapter extends CursorAdapter {
    private Context mContext;
    private SharedPreferences mPrefs;
    private LruCache<String, Bitmap> mImageCache;

    AlbumCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
        mContext = context;
        // Get the base directory from the shared preferences.
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // Set up the image cache
        // See https://developer.android.com/topic/performance/graphics/cache-bitmap
        // Get max available Java VM memory. Stored in kilobytes as LruCache takes an int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use fraction of the available memory for this memory cache.
        final int cacheSize = maxMemory / 12;

        mImageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size is measured in kilobytes rather than number of items.
                return bitmap.getByteCount() / 1024;
            }
        };


    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return LayoutInflater.from(context).inflate(R.layout.album_item, viewGroup, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        String directory = mPrefs.getString(mContext.getString(R.string.preference_filename), null);
        // Get the title of the current album and set this text to the titleTV
        TextView titleTV = view.findViewById(R.id.audio_storage_item_title);
        titleTV.setSelected(true);
        String title = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_TITLE));
        titleTV.setText(title);

        // Get the progress of this album and update the view
        TextView progressTV = view.findViewById(R.id.album_info_time_album);
        int id = cursor.getInt(cursor.getColumnIndex(AnchorContract.AlbumEntry._ID));
        int[] times = DBAccessUtils.getAlbumTimes(context, id);
        String timeStr = Utils.getTimeString(context, times[0], times[1]);
        progressTV.setText(timeStr);

        // Get the path of the thumbnail of the current album and set the src of the image view
        ImageView thumbnailIV = view.findViewById(R.id.audio_storage_item_thumbnail);

        if (isCurrentItemActive(cursor)) {
            thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else {
            String path = cursor.getString(cursor.getColumnIndex(AnchorContract.AlbumEntry.COLUMN_COVER_PATH));
            int reqSize = mContext.getResources().getDimensionPixelSize(R.dimen.album_item_height);
            if (path != null) {
                path = directory + File.separator + path;
                if (new File(path).exists()) {
                    Bitmap storedImage = getBitmapFromMemCache(path);
                    if (storedImage != null) {
                        thumbnailIV.setImageBitmap(storedImage);
                    } else {
                        Bitmap image = BitmapUtils.decodeSampledBitmap(path, reqSize, reqSize);
                        thumbnailIV.setImageBitmap(image);
                        addBitmapToMemoryCache(path, image);
                    }
                } else {
                    setEmptyCoverImage(thumbnailIV, reqSize);
                }
            } else {
                setEmptyCoverImage(thumbnailIV, reqSize);
            }
        }

        // Show the deletable image if the file does not exist anymore
        ImageView deletableIV = view.findViewById(R.id.album_item_deletable_img);
        if (directory != null && !(new File(directory, title)).exists()) {
            deletableIV.setVisibility(View.VISIBLE);
        } else {
            deletableIV.setVisibility(View.GONE);
        }
    }

    private void setEmptyCoverImage(ImageView iv, int reqSize) {
        String imageKey = String.valueOf(R.drawable.empty_cover_grey_blue);
        Bitmap storedImage = getBitmapFromMemCache(imageKey);
        if (storedImage != null) {
            iv.setImageBitmap(storedImage);
        } else {
            Bitmap image = BitmapUtils.decodeSampledBitmap(mContext.getResources(), R.drawable.empty_cover_grey_blue, reqSize, reqSize);
            iv.setImageBitmap(image);
            addBitmapToMemoryCache(imageKey, image);
        }
    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mImageCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mImageCache.get(key);
    }

    /*
     * Check if the service is running for an audio file from the current album
     */
    private boolean isCurrentItemActive(Cursor cursor) {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<AudioFile> audioList = new ArrayList<>(storage.loadAudio());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioList.size() && audioIndex != -1) {
                // Index is in a valid range
                AudioFile activeAudio = audioList.get(audioIndex);
                int playingAlbumId = activeAudio.getAlbumId();
                int albumId = cursor.getInt(cursor.getColumnIndex(AnchorContract.AlbumEntry._ID));
                return playingAlbumId == albumId;
            }
        }
        return false;
    }
}
