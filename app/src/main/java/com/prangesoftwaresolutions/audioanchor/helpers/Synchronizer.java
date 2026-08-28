package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.listeners.SynchronizationStateListener;
import com.prangesoftwaresolutions.audioanchor.models.Album;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.models.Directory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Synchronizer {
    private final Context mContext;
    private final SharedPreferences mPrefManager;
    private SynchronizationStateListener mListener = null;

    // Filesystem scans and DB diffing run here so the calling (UI) thread is never blocked --
    // single-threaded so consecutive sync requests (e.g. rapid pull-to-refresh taps) are
    // serialized rather than racing each other over the same directories.
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean mShutdown = false;

    public Synchronizer(Context context) {
        mContext = context;
        mPrefManager = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setListener(SynchronizationStateListener listener) {
        mListener = listener;
    }

    /*
     * Release background resources. Must be called (e.g. from the host Activity's onDestroy())
     * once this Synchronizer is no longer needed, so a sync still running in the background
     * doesn't call back into a destroyed Activity.
     */
    public void shutdown() {
        mShutdown = true;
        mListener = null;
        mMainHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
    }

    /*
     * Insert a new directory to the database and add its contained albums and audiofiles accordingly
     */
    public void addDirectory(Directory directory) {
        mExecutor.execute(() -> {
            directory.insertIntoDB(mContext);
            updateAlbumTable(directory);
            notifyFinished();
        });
    }

    /*
     * For each directory in the database update albums according to current status of the file system
     */
    public void updateDBTables() {
        mExecutor.execute(() -> {
            ArrayList<Directory> directories = Directory.getDirectories(mContext);
            for (Directory directory : directories) {
                updateAlbumTable(directory);
            }
            notifyFinished();
        });
    }

    /*
     * Notify the listener that a full sync (addDirectory() or updateDBTables() call) has
     * finished, marshalled onto the main thread. This is called exactly once per call above --
     * previously it was fired once per directory from inside updateAlbumTable(), which is why
     * the "Synchronized library" toast could appear multiple times for users with more than one
     * configured directory.
     */
    private void notifyFinished() {
        if (mShutdown) {
            return;
        }
        mMainHandler.post(() -> {
            if (!mShutdown && mListener != null) {
                mListener.onSynchronizationFinished();
            }
        });
    }

    /*
     * Update the album database table if the list of directories in the selected directory does not
     * match the album table entries
     */
    private void updateAlbumTable(Directory directory) {
        // Filter to get all subdirectories in a directory
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);
            // Only list files that are readable and directories and not hidden unless corresponding option is set
            return sel.canRead() && sel.isDirectory() && (showHidden || !sel.getName().startsWith("."));
        };

        ArrayList<String> newAlbumPaths = new ArrayList<>();
        File dir = new File(directory.getPath());
        if (dir.exists() && dir.isDirectory()) {
            if (directory.getType() == Directory.Type.PARENT_DIR) {
                // Add all subdirectories if directory is a parent directory
                String[] subDirArr = dir.list(filter);
                for (String subDirString : subDirArr) {
                    String absolutePath = new File(directory.getPath(), subDirString).getAbsolutePath();
                    newAlbumPaths.add(absolutePath);
                }
            } else if (dir.canRead() && (showHidden || !dir.getName().startsWith("."))) {
                // Add directory if it is a subdirectory
                newAlbumPaths.add(dir.getAbsolutePath());
            }
        }

        LinkedHashMap<String, Album> oldAlbumPaths = new LinkedHashMap<>();
        // Pass the already-known Directory object instead of just its id, so each returned
        // Album doesn't have to re-fetch the (identical) Directory row from the DB itself.
        ArrayList<Album> albums = Album.getAllAlbumsInDirectory(mContext, directory);
        for (Album album : albums) {
            String path = album.getPath();
            oldAlbumPaths.put(path, album);
        }

        // Insert new albums into the database
        for (String newAlbumPath : newAlbumPaths) {
            Album album;
            if (!oldAlbumPaths.containsKey(newAlbumPath)) {
                String albumTitle = new File(newAlbumPath).getName();
                album = new Album(albumTitle, directory);
                album.insertIntoDB(mContext);
            } else {
                album = oldAlbumPaths.get(newAlbumPath);

                // Update cover path
                String oldCoverPath = album.getRelativeCoverPath();
                String newCoverPath = album.updateAlbumCover();
                if (newCoverPath != null && (oldCoverPath == null || !oldCoverPath.equals(newCoverPath))) {
                    album.updateInDB(mContext);
                }

                oldAlbumPaths.remove(newAlbumPath);
            }
            updateAudioFileTable(newAlbumPath, album);
        }

        // Delete missing or hidden directories from the database
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        for (String path : oldAlbumPaths.keySet()) {
            String directoryName = new File(path).getName();
            if (!keepDeleted || (!showHidden && directoryName.startsWith("."))) {
                // Delete the album in the albums table
                long id = oldAlbumPaths.get(path).getID();
                Uri uri = ContentUris.withAppendedId(AnchorContract.AlbumEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
    }


    /*
     * Update the audiofiles table if the list of audio files in the album directory does not
     * match the audiofiles table entries
     */
     private void updateAudioFileTable(String albumPath, Album album) {
        // Get all audio files in the album.
        FilenameFilter filter = (dir, filename) -> {
            File sel = new File(dir, filename);

            // Don't show files starting with a dot (hidden files) unless the option is set
            boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
            if (!showHidden && sel.getName().startsWith(".")) {
                return false;
            }

            // Only list files that are readable and audio files
            String[] supportedFormats = {".mp3", ".wma", ".ogg", ".wav", ".flac", ".m4a", ".m4b", ".aac", ".3gp", ".gsm", ".mid", ".mkv", ".opus"};
            for (String format : supportedFormats) {
                if (sel.getName().endsWith(format)) return true;
            }
            return false;
        };

        // Get all files in the album directory.
        String[] fileList;
        File albumDir = new File(albumPath);

        if (albumDir.exists()) {
            fileList = albumDir.list(filter);
        } else {
            fileList = new String[]{};
        }

        if (fileList == null) return;

        // Pass the already-known Album object instead of just its id, so each returned
        // AudioFile doesn't have to re-fetch the (identical) Album (and, transitively,
        // Directory) row from the DB itself -- this was the dominant cost of a sync on
        // libraries with many audio files.
        ArrayList<AudioFile> audioFiles = AudioFile.getAllAudioFilesInAlbum(mContext, album, null);
        LinkedHashMap<String, AudioFile> audioTitles = new LinkedHashMap<>();
         for (AudioFile audioFile : audioFiles) {
             audioTitles.put(audioFile.getTitle(), audioFile);
         }

        // Build up new-file inserts and stale-file deletes as a single batch of operations,
        // applied in one DB transaction (see AnchorProvider.applyBatch()) instead of one
        // transaction per row -- that per-row commit overhead was the other dominant cost of
        // syncing a library with many new or removed files.
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        // New files still have to be opened individually to read their duration via
        // MediaMetadataRetriever -- that per-file I/O is unavoidable, but the resulting DB
        // write is now batched below instead of being its own round trip.
        for (String audioFileName : fileList) {
            if (!audioTitles.containsKey(audioFileName)) {
                AudioFile audioFile = new AudioFile(mContext, audioFileName, album);
                ops.add(ContentProviderOperation.newInsert(AnchorContract.AudioEntry.CONTENT_URI)
                        .withValues(audioFile.getContentValues())
                        .build());
            } else {
                audioTitles.remove(audioFileName);
            }
        }

        // Delete missing or hidden audio files from the database
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        for (String title : audioTitles.keySet()) {
            if (!keepDeleted || (!showHidden && title.startsWith("."))) {
                long id = audioTitles.get(title).getID();
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
                ops.add(ContentProviderOperation.newDelete(uri).build());
            }
        }

        if (ops.isEmpty()) {
            return;
        }

        try {
            mContext.getContentResolver().applyBatch(AnchorContract.CONTENT_AUTHORITY, ops);
        } catch (RemoteException | OperationApplicationException e) {
            String errorString = mContext.getResources().getString(R.string.audio_file_error, albumPath);
            mMainHandler.post(() -> {
                if (!mShutdown) {
                    Toast.makeText(mContext.getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
