package com.prangesoftwaresolutions.audioanchor;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;

public class MediaSessionCallback extends MediaSessionCompat.Callback {

    private MediaPlayerService mService;
    private Context mContext;

    MediaSessionCallback(MediaPlayerService service, Context context) {
        mService = service;
        mContext = context;
    }

    @Override
    public void onPlay() {
        super.onPlay();
        mService.play();
    }

    @Override
    public void onPause() {
        super.onPause();
        mService.pause();
    }

    @Override
    public void onSkipToNext() {
        super.onSkipToNext();
    }

    @Override
    public void onSkipToPrevious() {
        super.onSkipToPrevious();
    }

    @Override
    public void onStop() {
        super.onStop();
        mService.stopMedia();
        mService.stopForeground(true);
        mService.stopSelf();
    }

    @Override
    public void onSeekTo(long position) {
        super.onSeekTo(position);
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
        return MediaButtonIntentReceiver.handleIntent(mContext, mediaButtonEvent);
    }
}
