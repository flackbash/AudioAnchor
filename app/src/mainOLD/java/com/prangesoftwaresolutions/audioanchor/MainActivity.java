package com.prangesoftwaresolutions.audioanchor;

import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FilenameFilter;

public class MainActivity extends AppCompatActivity {

    // The audio storage directory
    private File mDirectory;

    // Preferences
    private SharedPreferences mSharedPreferences;

    // Layout variables
    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String storageDirectory = mSharedPreferences.getString(getString(R.string.preference_filename), null);

        if (storageDirectory == null) {
            // Let the user select a file and import the recipes.
            File path = new File(Environment.getExternalStorageDirectory() + "//DIR//");
            FileDialog fileDialog = new FileDialog(this, path, null);
            fileDialog.setSelectDirectoryOption(true);
            fileDialog.addDirectoryListener(new FileDialog.DirectorySelectedListener() {
                public void directorySelected(File directory) {
                    // Set the storage directory to the selected directory
                    mDirectory = directory;
                    initializeRecyclerView();

                    // Store the selected path in the shared preferences to persist when the app is closed
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putString(getString(R.string.preference_filename), directory.getAbsolutePath());
                    editor.apply();

                    // Inform the user about the selected path
                    String text = "Path: " + directory.getAbsolutePath();
                    Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
                }
            });
            fileDialog.showDialog();
        } else {
            mDirectory = new File(storageDirectory);
            initializeRecyclerView();
        }
    }

    void initializeRecyclerView() {
        // Get all subdirectories of the selected audio storage directory.
        // Only list files that are readable and directories
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                File sel = new File(dir, filename);
                return sel.canRead() && sel.isDirectory();
            }
        };
        String[] directoryList;
        if (mDirectory != null) {
            directoryList = mDirectory.list(filter);

            // Set up the RecyclerView
            mRecyclerView = findViewById(R.id.storage_recycler_view);
            // Use a linear layout manager
            mLayoutManager = new LinearLayoutManager(this);
            mRecyclerView.setLayoutManager(mLayoutManager);
            // Specify an adapter
            mAdapter = new AudioStorageAdapter(this, directoryList);
            mRecyclerView.setAdapter(mAdapter);
        }
    }
}
