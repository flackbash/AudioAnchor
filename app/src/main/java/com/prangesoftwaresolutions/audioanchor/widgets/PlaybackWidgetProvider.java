package com.prangesoftwaresolutions.audioanchor.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.core.content.ContextCompat;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.activities.MainActivity;
import com.prangesoftwaresolutions.audioanchor.activities.PlayActivity;
import com.prangesoftwaresolutions.audioanchor.models.AudioFile;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.utils.SkipIntervalUtils;
import com.prangesoftwaresolutions.audioanchor.utils.StorageUtil;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

import java.util.ArrayList;

/*
 * Base of the home screen playback widgets. The widgets come in several sizes (one subclass,
 * i.e. one entry in the widget picker, each) which only differ in their layout: the same set of
 * views is bound for all of them, and RemoteViews ignores updates to views a layout doesn't have.
 *
 * The widgets are dumb: a button press only ever ends up as an action for MediaPlayerService, the
 * same way the playback notification's buttons do, and what they show is pushed to them by the
 * service whenever it changes (see updateAll()).
 */
public abstract class PlaybackWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_PREFIX = "com.prangesoftwaresolutions.audioanchor.widget.";
    private static final String ACTION_PLAY_PAUSE = ACTION_PREFIX + "PLAY_PAUSE";
    private static final String ACTION_BACKWARD_1 = ACTION_PREFIX + "BACKWARD_1";
    private static final String ACTION_BACKWARD_2 = ACTION_PREFIX + "BACKWARD_2";
    private static final String ACTION_FORWARD_1 = ACTION_PREFIX + "FORWARD_1";
    private static final String ACTION_FORWARD_2 = ACTION_PREFIX + "FORWARD_2";
    private static final String ACTION_BOOKMARK = ACTION_PREFIX + "BOOKMARK";

    // How faded the controls that only work within a playback session look while there is none
    private static final float NO_SESSION_ALPHA = 0.35f;

    // The state most recently pushed by a live playback session. Needed to render on the system's
    // own request (a widget being added or resized) while a session is running; only ever used
    // while the service is actually running (see currentState()).
    private static volatile PlaybackWidgetState sSessionState;

    @LayoutRes
    protected abstract int getLayoutId();

    /*
     * Show the given state on all widgets, or, if it is null (there is no playback session
     * anymore), that of the most recently played track.
     */
    public static void updateAll(Context context, PlaybackWidgetState sessionState) {
        sSessionState = sessionState;

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        PlaybackWidgetState state = null;
        for (PlaybackWidgetProvider provider : new PlaybackWidgetProvider[]{
                new SmallPlaybackWidgetProvider(),
                new MediumPlaybackWidgetProvider(),
                new LargePlaybackWidgetProvider()}) {
            int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, provider.getClass()));
            if (widgetIds.length == 0) {
                continue;
            }
            if (state == null) {
                state = sessionState != null ? sessionState : PlaybackWidgetState.idle(context);
            }
            manager.updateAppWidget(widgetIds, provider.buildRemoteViews(context, state));
        }
    }

    /*
     * Re-render all widgets from what is currently known, e.g. after a setting they display (the
     * skip intervals) changed.
     */
    public static void refreshAll(Context context) {
        updateAll(context, Utils.isMediaPlayerServiceRunning(context) ? sSessionState : null);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        PlaybackWidgetState session = sSessionState;
        PlaybackWidgetState state = (session != null && Utils.isMediaPlayerServiceRunning(context))
                ? session : PlaybackWidgetState.idle(context);
        appWidgetManager.updateAppWidget(appWidgetIds, buildRemoteViews(context, state));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.startsWith(ACTION_PREFIX)) {
            handleButtonPress(context, action);
        } else {
            super.onReceive(context, intent);
        }
    }

    private static void handleButtonPress(Context context, String action) {
        if (action.equals(ACTION_PLAY_PAUSE)) {
            playPause(context);
            return;
        }

        // Every other button acts on the current playback session: without one there is nothing
        // to skip within or to bookmark.
        if (!Utils.isMediaPlayerServiceRunning(context)) {
            return;
        }
        switch (action) {
            case ACTION_BACKWARD_1:
                skip(context, MediaPlayerService.ACTION_BACKWARD, SkipIntervalUtils.getBackwardButton1(context));
                break;
            case ACTION_BACKWARD_2:
                skip(context, MediaPlayerService.ACTION_BACKWARD, SkipIntervalUtils.getBackwardButton2(context));
                break;
            case ACTION_FORWARD_1:
                skip(context, MediaPlayerService.ACTION_FORWARD, SkipIntervalUtils.getForwardButton1(context));
                break;
            case ACTION_FORWARD_2:
                skip(context, MediaPlayerService.ACTION_FORWARD, SkipIntervalUtils.getForwardButton2(context));
                break;
            case ACTION_BOOKMARK:
                // A widget gives no other sign that the bookmark was set
                context.startService(serviceIntent(context, MediaPlayerService.ACTION_BOOKMARK)
                        .putExtra(MediaPlayerService.EXTRA_SHOW_BOOKMARK_TOAST, true));
                break;
            default:
                break;
        }
    }

    private static void skip(Context context, String serviceAction, int skipInterval) {
        context.startService(serviceIntent(context, serviceAction)
                .putExtra(MediaPlayerService.EXTRA_SKIP_INTERVAL, skipInterval));
    }

    /*
     * Toggle playback of the current session, or, if there is none, start one by resuming the
     * most recently played track where it was left off.
     */
    private static void playPause(Context context) {
        if (Utils.isMediaPlayerServiceRunning(context)) {
            context.startService(serviceIntent(context, MediaPlayerService.ACTION_TOGGLE_PAUSE));
            return;
        }

        AudioFile lastPlayed = AudioFile.getMostRecentlyPlayed(context);
        if (lastPlayed == null) {
            Toast.makeText(context.getApplicationContext(), R.string.widget_nothing_to_play, Toast.LENGTH_LONG).show();
            return;
        }

        // The service picks up the queue it should play from storage when it starts, the same
        // way it does when PlayActivity starts it
        ArrayList<Long> queue = AudioFile.getSortedAudioIdsInAlbum(context, lastPlayed.getAlbumId());
        new StorageUtil(context).storeAudioQueue(queue, queue.indexOf(lastPlayed.getID()));
        ContextCompat.startForegroundService(context, serviceIntent(context, MediaPlayerService.ACTION_PLAY));
    }

    private static Intent serviceIntent(Context context, String action) {
        return new Intent(context, MediaPlayerService.class).setAction(action);
    }

    private RemoteViews buildRemoteViews(Context context, PlaybackWidgetState state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), getLayoutId());

        int playPauseIcon = R.drawable.play_border_bright;
        int playPauseDescription = R.string.button_play;
        if (state.playing) {
            playPauseIcon = R.drawable.pause_border_bright;
            playPauseDescription = R.string.button_pause;
        } else if (state.finished) {
            playPauseIcon = R.drawable.replay_border_bright;
            playPauseDescription = R.string.button_replay;
        }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon);
        views.setContentDescription(R.id.widget_play_pause, context.getString(playPauseDescription));
        views.setOnClickPendingIntent(R.id.widget_play_pause, buttonPressIntent(context, ACTION_PLAY_PAUSE));

        views.setTextViewText(R.id.widget_title, state.title);
        views.setTextViewText(R.id.widget_album, state.album);
        views.setOnClickPendingIntent(R.id.widget_info, openPlayerIntent(context, state));

        // The large layout has all four skip buttons, in the same order as PlayActivity: the
        // "1" buttons outermost, the "2" buttons closest to play.
        bindSkipButton(context, views, state, R.id.widget_backward_1, R.id.widget_backward_1_image, R.id.widget_backward_1_text,
                ACTION_BACKWARD_1, SkipIntervalUtils.getBackwardButton1(context), false);
        bindSkipButton(context, views, state, R.id.widget_backward_2, R.id.widget_backward_2_image, R.id.widget_backward_2_text,
                ACTION_BACKWARD_2, SkipIntervalUtils.getBackwardButton2(context), false);
        bindSkipButton(context, views, state, R.id.widget_forward_1, R.id.widget_forward_1_image, R.id.widget_forward_1_text,
                ACTION_FORWARD_1, SkipIntervalUtils.getForwardButton1(context), true);
        bindSkipButton(context, views, state, R.id.widget_forward_2, R.id.widget_forward_2_image, R.id.widget_forward_2_text,
                ACTION_FORWARD_2, SkipIntervalUtils.getForwardButton2(context), true);

        // The medium layout only has room for one skip button on each side, so it gets the pair
        // closest to play in PlayActivity's layout (backward 2, forward 1) rather than an
        // arbitrary pick -- those also happen to share the smaller default interval.
        bindSkipButton(context, views, state, R.id.widget_backward, R.id.widget_backward_image, R.id.widget_backward_text,
                ACTION_BACKWARD_2, SkipIntervalUtils.getBackwardButton2(context), false);
        bindSkipButton(context, views, state, R.id.widget_forward, R.id.widget_forward_image, R.id.widget_forward_text,
                ACTION_FORWARD_1, SkipIntervalUtils.getForwardButton1(context), true);

        views.setOnClickPendingIntent(R.id.widget_bookmark, buttonPressIntent(context, ACTION_BOOKMARK));
        views.setContentDescription(R.id.widget_bookmark, context.getString(R.string.button_bookmark));
        views.setFloat(R.id.widget_bookmark, "setAlpha", sessionAlpha(state));

        return views;
    }

    /*
     * Show the same icon and interval label the skip buttons in PlayActivity show: an interval
     * of "max" means skipping to the previous/next track and gets that icon instead.
     */
    private void bindSkipButton(Context context, RemoteViews views, PlaybackWidgetState state,
                                int buttonId, int imageId, int textId, String action,
                                int skipInterval, boolean forward) {
        boolean skipsTrack = SkipIntervalUtils.isMaxSkipInterval(skipInterval);
        int icon;
        if (skipsTrack) {
            icon = forward ? R.drawable.ic_next_bright : R.drawable.ic_previous_bright;
        } else {
            icon = forward ? R.drawable.forward_bright : R.drawable.backward_bright;
        }
        views.setImageViewResource(imageId, icon);
        views.setViewVisibility(textId, skipsTrack ? View.GONE : View.VISIBLE);
        views.setTextViewText(textId, String.valueOf(skipInterval));

        views.setOnClickPendingIntent(buttonId, buttonPressIntent(context, action));
        views.setContentDescription(buttonId, context.getString(forward ? R.string.button_forward : R.string.button_backward));
        views.setFloat(buttonId, "setAlpha", sessionAlpha(state));
    }

    private static float sessionAlpha(PlaybackWidgetState state) {
        return state.sessionActive ? 1f : NO_SESSION_ALPHA;
    }

    /*
     * A press of one of the widget's buttons is delivered to this very provider (see
     * onReceive()) rather than straight to the service, since what it should do depends on
     * whether there is a playback session at the time it is pressed, not at the time the widget
     * was last drawn. Each action gets its own PendingIntent, as extras don't make them distinct.
     */
    private PendingIntent buttonPressIntent(Context context, String action) {
        Intent intent = new Intent(context, getClass()).setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /*
     * Pressing the track info opens the player for the track the widget shows, or the app itself
     * if there is none.
     */
    private static PendingIntent openPlayerIntent(Context context, PlaybackWidgetState state) {
        Intent intent;
        if (state.audioId != -1) {
            intent = new Intent(context, PlayActivity.class)
                    .putExtra(context.getString(R.string.curr_audio_id), state.audioId);
        } else {
            intent = new Intent(context, MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
