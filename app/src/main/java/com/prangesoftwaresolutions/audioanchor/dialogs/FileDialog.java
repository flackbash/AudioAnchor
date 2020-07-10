package com.prangesoftwaresolutions.audioanchor.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Environment;
import android.util.Log;

import com.prangesoftwaresolutions.audioanchor.R;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Class to navigate the file system and choose a file (hopefully)
 * Taken from https://stackoverflow.com/questions/3592717/choose-file-dialog
 */

public class FileDialog {
    private static final String PARENT_DIR = "..";
    private final String TAG = getClass().getName();
    private String[] fileList;
    private File currentPath;

    public interface FileSelectedListener {
        void fileSelected(File file);
    }

    public interface DirectorySelectedListener {
        void directorySelected(File directory);
    }

    private ListenerList<FileSelectedListener> fileListenerList = new ListenerList<>();
    private ListenerList<DirectorySelectedListener> dirListenerList = new ListenerList<>();
    private final Activity activity;
    private boolean selectDirectoryOption;
    private String fileEndsWith;
    private HashMap<String, HashSet<String>> childDirectories = new HashMap<>();

    public FileDialog(Activity activity, File initialPath, String fileEndsWith) {
        this.activity = activity;
        setFileEndsWith(fileEndsWith);
        if (!initialPath.exists()) initialPath = Environment.getExternalStorageDirectory();
        loadFileList(initialPath);
    }

    /**
     * @return file dialog
     */
    private Dialog createFileDialog() {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(currentPath.getPath());
        if (selectDirectoryOption) {
            builder.setPositiveButton(R.string.dialog_msg_select_dir, (dialog1, which) -> {
                Log.d(TAG, currentPath.getPath());
                fireDirectorySelectedEvent(currentPath);
            });
            builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog1, which) -> {
                if (dialog1 != null) {
                    dialog1.dismiss();
                }
            });
        }

        builder.setItems(fileList, (dialog12, which) -> {
            String fileChosen = fileList[which];
            File chosenFile = getChosenFile(fileChosen);
            if (chosenFile.isDirectory()) {
                loadFileList(chosenFile);
                dialog12.cancel();
                dialog12.dismiss();
                showDialog();
            } else fireFileSelectedEvent(chosenFile);
        });

        dialog = builder.show();
        return dialog;
    }


    public void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    public void removeFileListener(FileSelectedListener listener) {
        fileListenerList.remove(listener);
    }

    public void setSelectDirectoryOption(boolean selectDirectoryOption) {
        this.selectDirectoryOption = selectDirectoryOption;
    }

    public void addDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.add(listener);
    }

    public void removeDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.remove(listener);
    }

    /**
     * Show file dialog
     */
    public void showDialog() {
        createFileDialog().show();
    }

    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(listener -> listener.fileSelected(file));
    }

    private void fireDirectorySelectedEvent(final File directory) {
        dirListenerList.fireEvent(listener -> listener.directorySelected(directory));
    }

    private void loadFileList(File path) {
        this.currentPath = path;
        List<String> fileList = new ArrayList<>();
        if (path.exists()) {
            if (path.getParentFile() != null) fileList.add(PARENT_DIR);
            FilenameFilter filter = (dir, filename) -> {
                File sel = new File(dir, filename);
                if (!sel.canRead()) return false;
                if (selectDirectoryOption) return sel.isDirectory();
                else {
                    boolean endsWith = fileEndsWith == null || filename.toLowerCase().endsWith(fileEndsWith);
                    return endsWith || sel.isDirectory();
                }
            };
            String[] fileListTmp = path.list(filter);
            String currPathName = path.getAbsolutePath();
            // Add those directories that can not be displayed because root privileges are required
            if (childDirectories.containsKey(currPathName)) {
                for (String childDir : childDirectories.get(currPathName)) {
                    if ((fileListTmp == null || !Arrays.asList(fileListTmp).contains(childDir)) && new File(currPathName, childDir).exists()) {
                        fileList.add(childDir);
                        Log.i("FileDialog.java", "Directory " + currPathName + " is not readable. Manually adding directory " + childDir);
                    }
                }
            }
            if (fileListTmp != null) {
                Collections.addAll(fileList, fileListTmp);
            }
        }
        this.fileList = fileList.toArray(new String[]{});
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) {
            File parentFile = currentPath.getParentFile();
            // Save the child directories in a hash map. This is necessary, because root privileges
            // are required to read certain directories. This way, all directories can still be shown
            if (!childDirectories.containsKey(parentFile.getAbsolutePath())) {
                HashSet<String> childDirs = new HashSet<>();
                childDirectories.put(parentFile.getAbsolutePath(), childDirs);
            }
            childDirectories.get(parentFile.getAbsolutePath()).add(currentPath.getName());
            return parentFile;
        } else return new File(currentPath, fileChosen);
    }

    private void setFileEndsWith(String fileEndsWith) {
        this.fileEndsWith = fileEndsWith != null ? fileEndsWith.toLowerCase() : null;
    }
}

class ListenerList<L> {
    private List<L> listenerList = new ArrayList<>();

    public interface FireHandler<L> {
        void fireEvent(L listener);
    }

    public void add(L listener) {
        listenerList.add(listener);
    }

    void fireEvent(FireHandler<L> fireHandler) {
        List<L> copy = new ArrayList<>(listenerList);
        for (L l : copy) {
            fireHandler.fireEvent(l);
        }
    }

    void remove(L listener) {
        listenerList.remove(listener);
    }

    public List<L> getListenerList() {
        return listenerList;
    }
}
