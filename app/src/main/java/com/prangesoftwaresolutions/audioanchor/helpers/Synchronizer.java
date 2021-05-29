package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
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

public class Synchronizer {
    private final Context mContext;
    private final SharedPreferences mPrefManager;
    private SynchronizationStateListener mListener = null;

    public Synchronizer(Context context) {
        mContext = context;
        mPrefManager = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setListener(SynchronizationStateListener listener) {
        mListener = listener;
    }

    /*
     * Insert a new directory to the database and add its contained albums and audiofiles accordingly
     */
    public void addDirectory(Directory directory) {
        directory.insertIntoDB(mContext);
        updateAlbumTable(directory);
    }

    /*
     * For each directory in the database update albums according to current status of the file system
     */
    public void updateDBTables() {
        ArrayList<Directory> directories = Directory.getDirectories(mContext);
        for (Directory directory : directories) {
            updateAlbumTable(directory);
        }
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
        ArrayList<Album> albums = Album.getAllAlbumsInDirectory(mContext, directory.getID());
        for (Album album : albums) {
            String path = album.getPath();
            oldAlbumPaths.put(path, album);
        }

        // Insert new albums into the database
        for (String newAlbumPath : newAlbumPaths) {
            long id;
            if (!oldAlbumPaths.containsKey(newAlbumPath)) {
                String albumTitle = new File(newAlbumPath).getName();
                Album album = new Album(albumTitle, directory);
                id = album.insertIntoDB(mContext);
            } else {
                Album album = oldAlbumPaths.get(newAlbumPath);
                id = album.getID();

                // Update cover path
                String oldCoverPath = album.getRelativeCoverPath();
                String newCoverPath = album.updateAlbumCover();
                if (newCoverPath != null && (oldCoverPath == null || !oldCoverPath.equals(newCoverPath))) {
                    album.updateInDB(mContext);
                }

                oldAlbumPaths.remove(newAlbumPath);
            }
            updateAudioFileTable(newAlbumPath, id);
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
        if (mListener != null) {
            mListener.onSynchronizationFinished();
        }
    }


    /*
     * Update the audiofiles table if the list of audio files in the album directory does not
     * match the audiofiles table entries
     */
     private void updateAudioFileTable(String albumPath, long albumId) {
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

        ArrayList<AudioFile> audioFiles = AudioFile.getAllAudioFilesInAlbum(mContext, albumId, null);
        LinkedHashMap<String, AudioFile> audioTitles = new LinkedHashMap<>();
         for (AudioFile audioFile : audioFiles) {
             audioTitles.put(audioFile.getTitle(), audioFile);
         }

         // Insert new files into the database
        String errorString = null;
        for (String audioFileName : fileList) {
            if (!audioTitles.containsKey(audioFileName)) {
                AudioFile audioFile = new AudioFile(mContext, audioFileName, albumId);
                long id = audioFile.insertIntoDB(mContext);
                if (id == -1) errorString = albumPath + "/" + audioFileName;
            } else {
                audioTitles.remove(audioFileName);
            }
        }
        if (errorString != null) {
            errorString = mContext.getResources().getString(R.string.audio_file_error, errorString);
            Toast.makeText(mContext.getApplicationContext(), errorString, Toast.LENGTH_SHORT).show();
            return;
        }

        // Delete missing or hidden audio files from the database
        boolean keepDeleted = mPrefManager.getBoolean(mContext.getString(R.string.settings_keep_deleted_key), Boolean.getBoolean(mContext.getString(R.string.settings_keep_deleted_default)));
        boolean showHidden = mPrefManager.getBoolean(mContext.getString(R.string.settings_show_hidden_key), Boolean.getBoolean(mContext.getString(R.string.settings_show_hidden_default)));
        for (String title : audioTitles.keySet()) {
            if (!keepDeleted || (!showHidden && title.startsWith("."))) {
                long id = audioTitles.get(title).getID();
                Uri uri = ContentUris.withAppendedId(AnchorContract.AudioEntry.CONTENT_URI, id);
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
    }
}
