package com.prangesoftwaresolutions.audioanchor.widgets;

import android.content.Context;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

/*
 * Everything the playback widgets display. Either that of a live playback session (pushed by
 * MediaPlayerService whenever something changes) or, while no session exists, that of the most
 * recently played track, i.e. the one pressing play would resume.
 */
public final class PlaybackWidgetState {
    // Whether a playback session exists, i.e. whether skipping and bookmarking make sense
    final boolean sessionActive;
    final boolean playing;
    // Whether the track has been played to its end, in which case play restarts it (replay icon)
    final boolean finished;
    // -1 if there is no track at all
    final long audioId;
    final String title;
    final String album;

    private PlaybackWidgetState(boolean sessionActive, boolean playing, boolean finished, long audioId, String title, String album) {
        this.sessionActive = sessionActive;
        this.playing = playing;
        this.finished = finished;
        this.audioId = audioId;
        this.title = title;
        this.album = album;
    }

    public static PlaybackWidgetState ofSession(long audioId, String title, String album, boolean playing, boolean finished) {
        return new PlaybackWidgetState(true, playing, finished, audioId, title, album);
    }

    public static PlaybackWidgetState idle(Context context) {
        AudioFile lastPlayed = AudioFile.getMostRecentlyPlayed(context);
        if (lastPlayed == null) {
            return new PlaybackWidgetState(false, false, false, -1,
                    context.getString(R.string.widget_nothing_played), "");
        }
        return new PlaybackWidgetState(false, false,
                Utils.isFinished(lastPlayed, lastPlayed.getCompletedTime()),
                lastPlayed.getID(), lastPlayed.getTitle(), lastPlayed.getAlbumTitle());
    }
}
