package com.prangesoftwaresolutions.audioanchor.helpers;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Deletes audio files from shared storage without needing the MANAGE_EXTERNAL_STORAGE ("All
 * files access") permission.
 *
 * On Android 11+ (API 30), scoped storage blocks a plain File.delete() on files this app didn't
 * create, so deleting a track that's already part of the user's library used to require "All
 * files access" -- a broad permission subject to extra Play Store review -- just for this one
 * feature. MediaStore.createDeleteRequest() is the scoped-storage-compliant replacement: it asks
 * the user to confirm deleting a specific set of files via a single system dialog, no extra
 * permission needed. It only works on files MediaStore already knows about, so each file is
 * scanned first to make sure it's indexed, which also gives us its content Uri.
 *
 * On older versions scoped storage doesn't apply; a plain File.delete() works there once the
 * (normal, non-restricted) WRITE_EXTERNAL_STORAGE permission is granted.
 */
public class MediaStoreDeleteHelper {
    private final AppCompatActivity mActivity;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> mWritePermissionLauncher;
    private final ActivityResultLauncher<IntentSenderRequest> mDeleteRequestLauncher;

    // The files a pending async step (permission request or delete request) will report as
    // deleted if it succeeds, and the listener to report back to once it resolves. There's only
    // ever one such step in flight at a time, triggered by the most recent deleteFiles() call.
    private List<File> mPendingFiles;
    private OnDeleteFinishedListener mPendingListener;

    public interface OnDeleteFinishedListener {
        // The subset of the requested files that were actually deleted -- all of them if the
        // user approved, none if they denied/cancelled or a file couldn't be resolved.
        void onDeleteFinished(Set<File> deletedFiles);
    }

    public MediaStoreDeleteHelper(AppCompatActivity activity) {
        mActivity = activity;
        mWritePermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), this::onWritePermissionResult);
        mDeleteRequestLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(), this::onDeleteRequestResult);
    }

    /*
     * Release background resources. Must be called from the host Activity's onDestroy().
     */
    public void shutdown() {
        mExecutor.shutdownNow();
    }

    /*
     * Delete the given files, prompting the user for permission/confirmation if required on this
     * Android version. Always calls back on the main thread, synchronously if no prompt is
     * needed.
     */
    public void deleteFiles(List<File> files, OnDeleteFinishedListener listener) {
        if (files.isEmpty()) {
            listener.onDeleteFinished(new HashSet<>());
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deleteViaMediaStore(files, listener);
            return;
        }

        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            listener.onDeleteFinished(deleteDirectly(files));
        } else {
            mPendingFiles = files;
            mPendingListener = listener;
            mWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void onWritePermissionResult(boolean granted) {
        List<File> files = mPendingFiles;
        OnDeleteFinishedListener listener = mPendingListener;
        mPendingFiles = null;
        mPendingListener = null;
        if (listener == null) return;

        listener.onDeleteFinished(granted ? deleteDirectly(files) : new HashSet<>());
    }

    private Set<File> deleteDirectly(List<File> files) {
        Set<File> deleted = new HashSet<>();
        for (File file : files) {
            if (file.delete()) deleted.add(file);
        }
        return deleted;
    }

    /*
     * Scan each file to make sure MediaStore has it indexed (which also gives us its content
     * Uri), then ask the user to confirm deleting all of them via a single system dialog.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private void deleteViaMediaStore(List<File> files, OnDeleteFinishedListener listener) {
        mExecutor.execute(() -> {
            ConcurrentHashMap<String, Uri> pathToUri = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(files.size());
            String[] paths = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                paths[i] = files.get(i).getAbsolutePath();
            }
            MediaScannerConnection.scanFile(mActivity, paths, null, (path, uri) -> {
                if (uri != null) {
                    pathToUri.put(path, uri);
                }
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            mActivity.runOnUiThread(() -> {
                if (pathToUri.isEmpty()) {
                    listener.onDeleteFinished(new HashSet<>());
                    return;
                }

                List<File> resolvedFiles = new ArrayList<>();
                List<Uri> uris = new ArrayList<>();
                for (String path : pathToUri.keySet()) {
                    resolvedFiles.add(new File(path));
                    uris.add(pathToUri.get(path));
                }

                mPendingFiles = resolvedFiles;
                mPendingListener = listener;
                PendingIntent deleteRequest = MediaStore.createDeleteRequest(mActivity.getContentResolver(), uris);
                mDeleteRequestLauncher.launch(new IntentSenderRequest.Builder(deleteRequest.getIntentSender()).build());
            });
        });
    }

    private void onDeleteRequestResult(ActivityResult result) {
        List<File> files = mPendingFiles;
        OnDeleteFinishedListener listener = mPendingListener;
        mPendingFiles = null;
        mPendingListener = null;
        if (listener == null) return;

        boolean approved = result.getResultCode() == Activity.RESULT_OK;
        listener.onDeleteFinished(approved ? new HashSet<>(files) : new HashSet<>());
    }
}
