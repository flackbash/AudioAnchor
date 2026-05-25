package com.prangesoftwaresolutions.audioanchor.callbacks;

import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;

import com.prangesoftwaresolutions.audioanchor.receivers.MediaButtonIntentReceiver;
import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;

public class MediaSessionCallback extends MediaSessionCompat.Callback {

    private final MediaPlayerService mService;
    private final Context mContext;

    public MediaSessionCallback(MediaPlayerService service, Context context) {
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
        mService.skipToNextAudioFile();
    }

    @Override
    public void onSkipToPrevious() {
        super.onSkipToPrevious();
        mService.skipToPreviousAudioFile();
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

    @Override
    public void onCustomAction(String action, android.os.Bundle extras) {
        super.onCustomAction(action, extras);
        // Handle custom actions (forward, backward, bookmark)
        Intent intent = new Intent(mService, MediaPlayerService.class);
        intent.setAction(action);
        mService.onStartCommand(intent, 0, 0);
    }
}
