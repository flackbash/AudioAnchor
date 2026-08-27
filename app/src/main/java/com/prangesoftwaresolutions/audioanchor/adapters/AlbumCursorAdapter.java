package com.prangesoftwaresolutions.audioanchor.adapters;


import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.utils.BitmapUtils;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CursorAdapter for the ListView in the Main Activity
 */

public class AlbumCursorAdapter extends CursorAdapter {
    // Aliases for the per-album aggregate time columns that MainActivity adds to the cursor's
    // projection (correlated subqueries over audio_files), so this adapter doesn't have to issue
    // a separate DB query for every row on every scroll.
    public static final String COLUMN_TOTAL_TIME = "agg_total_time";
    public static final String COLUMN_COMPLETED_TIME = "agg_completed_time";

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final LruCache<String, Bitmap> mImageCache;
    private final ExecutorService mImageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Cache of the currently active (playing/loaded) album id, recomputed once per cursor swap
    // rather than once per row per scroll frame.
    private long mActiveAlbumId = -1;

    private static final class ViewHolder {
        TextView titleTV;
        TextView progressTV;
        ImageView thumbnailIV;
        ImageView deletableIV;
        ImageView pinnedIV;
    }

    public AlbumCursorAdapter(Context context, Cursor c) {
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

        mActiveAlbumId = computeActiveAlbumId();
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        Cursor old = super.swapCursor(newCursor);
        mActiveAlbumId = computeActiveAlbumId();
        return old;
    }

    /*
     * Release background resources. Must be called (e.g. from the host Activity's onDestroy())
     * once this adapter is no longer needed.
     */
    public void shutdown() {
        mImageExecutor.shutdownNow();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        View view = LayoutInflater.from(context).inflate(R.layout.album_item, viewGroup, false);
        ViewHolder holder = new ViewHolder();
        holder.titleTV = view.findViewById(R.id.audio_storage_item_title);
        holder.progressTV = view.findViewById(R.id.album_info_time_album);
        holder.thumbnailIV = view.findViewById(R.id.audio_storage_item_thumbnail);
        holder.deletableIV = view.findViewById(R.id.album_item_deletable_img);
        holder.pinnedIV = view.findViewById(R.id.album_item_pinned_img);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder) view.getTag();

        // Get the title of the current album and set this text to the titleTV
        holder.titleTV.setSelected(true);
        String title = cursor.getString(cursor.getColumnIndexOrThrow(AnchorContract.AlbumEntry.COLUMN_TITLE));
        holder.titleTV.setText(title);

        // Get the progress of this album (precomputed as part of the cursor's own query -- see
        // MainActivity.onCreateLoader()) and update the view
        int totalTime = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_TIME));
        int completedTime = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMPLETED_TIME));
        String timeStr = Utils.getTimeString(context, completedTime, totalTime);
        holder.progressTV.setText(timeStr);

        // Build the Album directly from the already-loaded cursor row instead of re-querying
        // the database for data the cursor already has.
        int albumId = cursor.getInt(cursor.getColumnIndexOrThrow(AnchorContract.AlbumEntry._ID));
        Album album = Album.getAlbumFromPositionedCursor(mContext, cursor);
        if (albumId == mActiveAlbumId) {
            holder.thumbnailIV.setBackgroundResource(R.drawable.ic_unchecked);
            holder.thumbnailIV.setImageResource(R.drawable.ic_playing);
        } else {
            holder.thumbnailIV.setBackground(null);
            String path = album.getCoverPath();
            int reqSize = mContext.getResources().getDimensionPixelSize(R.dimen.album_item_height);
            if (path != null && new File(path).exists()) {
                Bitmap storedImage = getBitmapFromMemCache(path);
                if (storedImage != null) {
                    holder.thumbnailIV.setImageBitmap(storedImage);
                } else {
                    holder.thumbnailIV.setImageDrawable(null);
                    loadBitmapAsync(holder.thumbnailIV, path, reqSize);
                }
            } else {
                setEmptyCoverImage(holder.thumbnailIV, reqSize);
            }
        }

        // Show the deletable image if the file does not exist anymore
        if (!(new File(album.getPath())).exists()) {
            holder.deletableIV.setVisibility(View.VISIBLE);
        } else {
            holder.deletableIV.setVisibility(View.GONE);
        }

        // Show the pin indicator if the album is pinned
        holder.pinnedIV.setVisibility(album.isPinned() ? View.VISIBLE : View.GONE);
    }

    /*
     * Decode the bitmap at the given path on a background thread, then apply it to the
     * ImageView on the main thread -- but only if that ImageView hasn't since been recycled for
     * a different row (guarded by comparing the requested path against the view's current tag).
     */
    private void loadBitmapAsync(ImageView iv, String path, int reqSize) {
        iv.setTag(R.id.audio_storage_item_thumbnail, path);
        mImageExecutor.execute(() -> {
            Bitmap bitmap = BitmapUtils.decodeSampledBitmap(path, reqSize, reqSize);
            if (bitmap != null) {
                addBitmapToMemoryCache(path, bitmap);
            }
            mMainHandler.post(() -> {
                if (path.equals(iv.getTag(R.id.audio_storage_item_thumbnail)) && bitmap != null) {
                    iv.setImageBitmap(bitmap);
                }
            });
        });
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
     * Determine the id of the album that the MediaPlayerService currently has loaded, if any.
     * Computed once per cursor swap rather than once per row per scroll frame.
     */
    private long computeActiveAlbumId() {
        boolean serviceStarted = Utils.isMediaPlayerServiceRunning(mContext);
        if (serviceStarted) {
            StorageUtil storage = new StorageUtil(mContext.getApplicationContext());
            ArrayList<Long> audioIdList = new ArrayList<>(storage.loadAudioIds());
            int audioIndex = storage.loadAudioIndex();
            if (audioIndex < audioIdList.size() && audioIndex != -1) {
                // Index is in a valid range
                long activeAudioId = audioIdList.get(audioIndex);
                AudioFile activeAudio;
                try {
                    activeAudio = AudioFile.getAudioFileById(mContext, activeAudioId);
                } catch (SQLException e) {
                    return -1;
                }
                if (activeAudio != null) {
                    return activeAudio.getAlbumId();
                }
            }
        }
        return -1;
    }
}
