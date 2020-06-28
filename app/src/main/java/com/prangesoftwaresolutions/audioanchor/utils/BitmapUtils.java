package com.prangesoftwaresolutions.audioanchor.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;

/**
 * Utils to make handling bitmaps in a responsible manner easier
 */

public class BitmapUtils {

    private static final String LOG_TAG = Utils.class.getName();

    /**
     * Set the image of the given image view to the given image file, return false if the file does
     * not exist.
     */
    public static void setImage(ImageView iv, String file, int reqSize) {
        if (file == null) {
            return;
        }
        File f = new File(file);
        if (!f.exists()) {
            return;
        }
        Bitmap bmp = decodeSampledBitmap(file, reqSize, reqSize);
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }
    }

    /**
     * Taken from https://developer.android.com/topic/performance/graphics/load-bitmap.html
     * This helps downSample an image.
     */
    public static Bitmap decodeSampledBitmap(String file, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeFile(file, options);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory." + e);
        }
        return null;
    }

    /**
     * Taken from https://developer.android.com/topic/performance/graphics/load-bitmap.html
     * This helps downSample an image.
     */
    public static Bitmap decodeSampledBitmap(Resources res, int resID, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resID, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeResource(res, resID, options);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory." + e);
        }
        return null;
    }

    /**
     * Taken from https://developer.android.com/topic/performance/graphics/load-bitmap.html
     * This helps downSample an image.
     */
    public static Bitmap decodeSampledBitmap(byte[] coverBytes, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length, options);
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(LOG_TAG, "Out of memory." + e);
        }
        return null;
    }

    /**
     * Taken from https://developer.android.com/topic/performance/graphics/load-bitmap.html
     * This helps downSample an image.
     */
    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
