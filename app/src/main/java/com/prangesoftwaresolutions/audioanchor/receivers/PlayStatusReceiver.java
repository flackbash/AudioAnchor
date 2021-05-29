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
                    mPlayPauseFAB.setImageResource(R.drawable.ic_pause_white);
                    mPlayPauseFAB.setVisibility(View.VISIBLE);
                    if (mListener != null) {
                        mListener.onPlayMsgReceived();
                    }
                    break;
                case MediaPlayerService.MSG_PAUSE:
                    mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
                    mPlayPauseFAB.setVisibility(View.VISIBLE);
                    break;
                case MediaPlayerService.MSG_STOP:
                    mPlayPauseFAB.setImageResource(R.drawable.ic_play_white);
                    mPlayPauseFAB.setVisibility(View.GONE);
                    break;
            }
        }
    }
}
