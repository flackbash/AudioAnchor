package com.prangesoftwaresolutions.audioanchor.utils;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.helpers.NaturalOrderComparator;

import java.util.Collections;
import java.util.List;

/*
 * Sorts a list of tracks exactly the way AlbumActivity displays them: natural-order (numeric
 * aware) title as the tiebreaker, then the user's selected "Track sort order" preference, then
 * pinned tracks floated to the top regardless of that preference. The single implementation
 * here backs both AlbumActivity's displayed list and the autoplay next/previous-track queue
 * built in PlayActivity, which previously used its own natural-title-only order and so could
 * disagree with what was actually shown on screen once pins were involved.
 */
public final class TrackSortUtils {

    public interface SortableTrack {
        String getTitle();

        boolean isPinned();

        // -1 for "unknown" (e.g. tracks synced before the corresponding feature existed).
        long getDateAdded();

        long getLastPlayedTimestamp();

        int getTime();

        int getCompletedTime();
    }

    private TrackSortUtils() {
    }

    public static <T extends SortableTrack> void sort(Context context, List<T> tracks) {
        String sortOrderPref = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.settings_track_sort_order_key),
                        context.getString(R.string.settings_track_sort_order_default));

        // Stable sorts applied least-significant key first, so each later pass preserves the
        // relative order ties were left in by the previous one.
        Collections.sort(tracks, (a, b) -> NaturalOrderComparator.INSTANCE.compare(a.getTitle(), b.getTitle()));

        if (sortOrderPref.equals(context.getString(R.string.settings_track_sort_order_by_date_added_newest_value))) {
            // Most recently added first. Tracks synced before this feature existed have an
            // unknown date_added, which sorts last here.
            Collections.sort(tracks, (a, b) -> compareUnknownAsMinusInfinity(b.getDateAdded(), a.getDateAdded()));
        } else if (sortOrderPref.equals(context.getString(R.string.settings_track_sort_order_by_date_added_oldest_value))) {
            // Least recently added first. Unknown date_added sorts first, i.e. tracks that
            // predate this feature are treated as the oldest.
            Collections.sort(tracks, (a, b) -> compareUnknownAsMinusInfinity(a.getDateAdded(), b.getDateAdded()));
        } else if (sortOrderPref.equals(context.getString(R.string.settings_track_sort_order_by_progress_value))) {
            // Least progress first (0% at the top). 0 for tracks with zero duration rather than
            // dividing by zero.
            Collections.sort(tracks, (a, b) -> Double.compare(progressFraction(a), progressFraction(b)));
        } else if (sortOrderPref.equals(context.getString(R.string.settings_track_sort_order_by_last_played_value))) {
            // Most recently played first. Tracks that were never played (or predate this
            // feature) have an unknown last_played_timestamp, which sorts last.
            Collections.sort(tracks, (a, b) -> compareUnknownAsMinusInfinity(b.getLastPlayedTimestamp(), a.getLastPlayedTimestamp()));
        }

        // Pinned tracks always float to the top of the track list, regardless of sort order.
        Collections.sort(tracks, (a, b) -> Boolean.compare(b.isPinned(), a.isPinned()));
    }

    private static double progressFraction(SortableTrack track) {
        return track.getTime() == 0 ? 0.0 : (double) track.getCompletedTime() / track.getTime();
    }

    /*
     * Ascending compare treating -1 ("unknown") as the smallest possible value. Swap the
     * arguments at the call site to get -1 treated as the largest value (sorts last) instead.
     */
    private static int compareUnknownAsMinusInfinity(long a, long b) {
        if (a == -1 && b == -1) return 0;
        if (a == -1) return -1;
        if (b == -1) return 1;
        return Long.compare(a, b);
    }
}
