package com.prangesoftwaresolutions.audioanchor.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.util.Log;
import android.view.View;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.listeners.PlayStatusChangeListener;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

public class PlayStatusReceiver extends BroadcastReceiver {

    PlayStatusChangeListener mListener = null;
    FloatingActionButton mPlayPauseFAB;

    public PlayStatusReceiver(FloatingActionButton playPauseFAB) {
        mPlayPauseFAB = playPauseFAB;
    }

    public void setListener(PlayStatusChangeListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("PlayStatusReceiver", "Received PlayStatus Broadcast");
        String s = intent.getStringExtra(MediaPlayerService.SERVICE_MESSAGE_PLAY_STATUS);
        if (s != null) {
            switch (s) {
                case MediaPlayerService.MSG_PLAY:
                    Utils.setIcon(mPlayPauseFAB, R.drawable.ic_pause_white, R.string.button_pause);
                    mPlayPauseFAB.setVisibility(View.VISIBLE);
                    if (mListener != null) {
                        mListener.onPlayMsgReceived();
                    }
                    break;
                case MediaPlayerService.MSG_PAUSE:
                    // Whether this should show the play or replay icon depends on whether the
                    // track has finished (see issue #196) -- only the listener Activity has the
                    // player/track state needed to tell, so leave the icon choice to it instead
                    // of guessing here.
                    mPlayPauseFAB.setVisibility(View.VISIBLE);
                    if (mListener != null) {
                        mListener.onPauseMsgReceived();
                    }
                    break;
                case MediaPlayerService.MSG_STOP:
                    Utils.setIcon(mPlayPauseFAB, R.drawable.ic_play_white, R.string.button_play);
                    mPlayPauseFAB.setVisibility(View.GONE);
                    break;
            }
        }
    }
}
