package com.prangesoftwaresolutions.audioanchor.models;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.prangesoftwaresolutions.audioanchor.data.AnchorContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Directory {

    public enum Type {
        PARENT_DIR(0),
        SUB_DIR(1);

        private final int value;
        private static final Map<Integer, Type> map = new HashMap<>();

        Type(int value) {
            this.value = value;
        }

        static {
            for (Type type : Type.values()) {
                map.put(type.value, type);
            }
        }

        public static Type valueOf(int type) {
            return map.get(type);
        }
    }

    private long mID = -1;
    private final String mPath;
    private final Type mType;

    private static final String[] mDirectoryColumns = new String[] {
            AnchorContract.DirectoryEntry._ID,
            AnchorContract.DirectoryEntry.COLUMN_PATH,
            AnchorContract.DirectoryEntry.COLUMN_TYPE
    };

    private Directory(long id, String path, int type) {
        mID = id;
        mPath = path;
        mType = Type.valueOf(type);
    }

    public Directory(String path, Type type) {
        mPath = path;
        mType = type;
    }

    public String getPath() {
        return mPath;
    }

    public Type getType() {
        return mType;
    }

    public long getID() {
        return mID;
    }

    public void setID(long id) { mID = id; }

    /*
     * Insert directory into database
     */
    public long insertIntoDB(Context context) {
        ContentValues values = getContentValues();
        Uri uri = context.getContentResolver().insert(AnchorContract.DirectoryEntry.CONTENT_URI, values);

        if (uri == null) {
            return -1;
        }

        mID = ContentUris.parseId(uri);
        return mID;
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(AnchorContract.DirectoryEntry.COLUMN_PATH, mPath);
        values.put(AnchorContract.DirectoryEntry.COLUMN_TYPE, mType.value);
        return values;
    }

    public static String[] getColumns() {
        return mDirectoryColumns;
    }

    /*
     * Retrieve directory with given ID from database
     */
    static Directory getDirectoryByID(Context context, long id) {
        Uri uri = ContentUris.withAppendedId(AnchorContract.DirectoryEntry.CONTENT_URI, id);
        Cursor c = context.getContentResolver().query(uri, mDirectoryColumns, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return null;
        } else if (c.getCount() < 1) {
            c.close();
            return null;
        }

        Directory directory = null;
        if (c.moveToNext()) {
            directory = getDirectoryFromPositionedCursor(c);
        }
        c.close();

        return directory;
    }

    /*
     * Retrieve all directories from the database
     */
    public static ArrayList<Directory> getDirectories(Context context) {
        ArrayList<Directory> directories = new ArrayList<>();
        Cursor c = context.getContentResolver().query(AnchorContract.DirectoryEntry.CONTENT_URI, mDirectoryColumns, null, null, null);

        // Bail early if the cursor is null
        if (c == null) {
            return directories;
        } else if (c.getCount() < 1) {
            c.close();
            return directories;
        }

        while (c.moveToNext()) {
            Directory directory = getDirectoryFromPositionedCursor(c);
            directories.add(directory);
        }
        c.close();

        return directories;
    }

    /*
     * Create a Directory from a cursor that is already at the correct position
     */
    private static Directory getDirectoryFromPositionedCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(AnchorContract.DirectoryEntry._ID));
        String path = c.getString(c.getColumnIndexOrThrow(AnchorContract.DirectoryEntry.COLUMN_PATH));
        int type = c.getInt(c.getColumnIndexOrThrow(AnchorContract.DirectoryEntry.COLUMN_TYPE));
        return new Directory(id, path, type);
    }
}
