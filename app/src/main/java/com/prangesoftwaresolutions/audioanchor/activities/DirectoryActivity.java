package com.prangesoftwaresolutions.audioanchor.activities;

import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.adapters.DirectoryCursorAdapter;
import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;
import com.prangesoftwaresolutions.audioanchor.dialogs.FileDialog;
import com.prangesoftwaresolutions.audioanchor.helpers.Synchronizer;
import com.prangesoftwaresolutions.audioanchor.listeners.SynchronizationStateListener;
import com.prangesoftwaresolutions.audioanchor.models.Directory;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.io.File;
import java.util.ArrayList;

public class DirectoryActivity extends AppCompatActivity  implements LoaderManager.LoaderCallbacks<Cursor>, SynchronizationStateListener {

    // CursorLoader variables
    private static final int AUDIO_LOADER = 0;
    private DirectoryCursorAdapter mCursorAdapter;

    // Variables for multi choice mode
    ArrayList<Long> mSelectedDirectories = new ArrayList<>();

    // Views
    ListView mListView;
    TextView mEmptyTV;

    // Synchronizer
    private Synchronizer mSynchronizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directory);

        // Prepare the CursorLoader. Either re-connect with an existing one or start a new one.
        getLoaderManager().initLoader(AUDIO_LOADER, null, this);

        // Initialize the cursor adapter
        mCursorAdapter = new DirectoryCursorAdapter(this, null);

        // Set up FloatingActionsMenu
        FloatingActionsMenu addDirectoryFAM = findViewById(R.id.add_directory_fam);
        FloatingActionButton addSubDirFAB = findViewById(R.id.add_sub_dir_fab);
        FloatingActionButton addParentDirFAB = findViewById(R.id.add_parent_dir_fab);

        // Set up overlay
        View overlay = findViewById(R.id.overlay);
        overlay.setOnClickListener(v -> addDirectoryFAM.collapse());

        // Set up FAM update listener
        addDirectoryFAM.setOnFloatingActionsMenuUpdateListener( new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener()
        {
            @Override
            public void onMenuExpanded()
            {
                overlay.setVisibility(View.VISIBLE);
            }

            @Override
            public void onMenuCollapsed()
            {
                overlay.setVisibility(View.INVISIBLE);
            }
        });

        // Set up the FAB onClickListeners
        addSubDirFAB.setOnClickListener(v -> {
            addDirectory(false);
            addDirectoryFAM.collapse();
        });
        addParentDirFAB.setOnClickListener(v -> {
            addDirectory(true);
            addDirectoryFAM.collapse();
        });

        // Use a ListView and CursorAdapter to recycle space
        mListView = findViewById(R.id.list);
        mListView.setAdapter(mCursorAdapter);

        // Set the EmptyView for the ListView
        mEmptyTV = findViewById(R.id.emptyList);
        mListView.setEmptyView(mEmptyTV);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(ActionMode actionMode, int i, long l, boolean b) {
                // Adjust menu title and list of selected directories when items are selected / de-selected
                if (b) {
                    mSelectedDirectories.add(l);
                } else {
                    mSelectedDirectories.remove(l);
                }
                String menuTitle = getResources().getString(R.string.items_selected, mSelectedDirectories.size());
                actionMode.setTitle(menuTitle);
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                // Inflate the menu for the CAB
                getMenuInflater().inflate(R.menu.menu_directory_cab, menu);
                // Without this, menu items are always shown in the action bar instead of the overflow menu
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_delete_from_db:
                        // Delete all tracks in the selected albums from db if they do not exist in the file system anymore
                        deleteSelectedDirectoriesFromDBWithConfirmation(mSelectedDirectories);
                        actionMode.finish();
                        return true;

                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                // Make necessary updates to the activity when the CAB is removed
                // By default, selected items are deselected/unchecked.
                mSelectedDirectories.clear();
            }
        });

        // Initialize synchronizer
        mSynchronizer = new Synchronizer(this);
        mSynchronizer.setListener(this);
    }

    private void addDirectory(boolean isParentDirectory) {
        File baseDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        FileDialog fileDialog = new FileDialog(this, baseDirectory, true, null, this);
        fileDialog.addDirectoryListener(directory -> {
            Directory.Type directoryType = isParentDirectory ? Directory.Type.PARENT_DIR : Directory.Type.SUB_DIR;
            Directory newDirectory = new Directory(directory.getAbsolutePath(), directoryType);
            if (allowAddDirectory(newDirectory)) {
                mSynchronizer.addDirectory(newDirectory);
            }
        });
        fileDialog.showDialog();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, AnchorContract.DirectoryEntry.CONTENT_URI, Directory.getColumns(), null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Hide the progress bar when the loading is finished.
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);

        // Set the text of the empty view
        mEmptyTV.setText(R.string.no_directories);

        // Swap the new cursor in. The framework will take care of closing the old cursor
        mCursorAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished() is about to be closed.
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onSynchronizationFinished() {
        getLoaderManager().restartLoader(0, null, DirectoryActivity.this);
        Toast.makeText(getApplicationContext(), R.string.synchronize_success, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedDirectoriesFromDBWithConfirmation(ArrayList<Long> selectedDirectories) {
        Long[] selectedDirectoriesTmpArr = new Long[selectedDirectories.size()];
        final Long[] selectedDirectoriesArr = selectedDirectories.toArray(selectedDirectoriesTmpArr);

        // Create a confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_msg_delete_directory_from_db);
        builder.setPositiveButton(R.string.dialog_msg_ok, (dialog, id) -> {
            // User clicked the "Ok" button, so delete the directories from the database
            int deletionCount = 0;
            for (long directoryId : selectedDirectoriesArr) {
                Uri uri = ContentUris.withAppendedId(AnchorContract.DirectoryEntry.CONTENT_URI, directoryId);
                getContentResolver().delete(uri, null, null);
                deletionCount++;
            }
            String deletedTracks = getResources().getString(R.string.directories_deleted_from_db, deletionCount);
            Toast.makeText(getApplicationContext(), deletedTracks, Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton(R.string.dialog_msg_cancel, (dialog, id) -> {
            // User clicked the "Cancel" button, so dismiss the dialog
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private boolean allowAddDirectory(Directory directory) {
        File newDir = new File(directory.getPath());
        ArrayList<Directory> directories = Directory.getDirectories(this);
        for (int i=0; i<directories.size();i++) {
            File dir = new File(directories.get(i).getPath());
            if (dir.getAbsolutePath().equals(newDir.getAbsolutePath()) && directory.getType() == directories.get(i).getType()) {
                String directoryExists = getResources().getString(R.string.directory_exists, dir.getAbsolutePath());
                Toast.makeText(getApplicationContext(), directoryExists, Toast.LENGTH_LONG).show();
                return false;
            }
            /*
            else if (dir.getAbsolutePath().startsWith(newDir.getAbsolutePath())) {
                String directoryExists = getResources().getString(R.string.sub_directory_exists, newDir.getAbsolutePath(), dir.getAbsolutePath());
                Toast.makeText(getApplicationContext(), directoryExists, Toast.LENGTH_LONG).show();
                return false;
            } else if (newDir.getAbsolutePath().startsWith(dir.getAbsolutePath())) {
                String directoryExists = getResources().getString(R.string.parent_directory_exists, newDir.getAbsolutePath(), dir.getAbsolutePath());
                Toast.makeText(getApplicationContext(), directoryExists, Toast.LENGTH_LONG).show();
                return false;
            }
             */
        }
        return true;
    }
}
