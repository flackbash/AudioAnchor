package com.prangesoftwaresolutions.audioanchor.receivers;

// Taken from https://github.com/AdrienPoupa/VinylMusicPlayer and modified
/*
 * Copyright (C) 2007 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;

import com.prangesoftwaresolutions.audioanchor.services.MediaPlayerService;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

/**
 * Used to control headset playback.
 * Single press: pause/resume
 * Double press: 30 seconds forward
 * Triple press: 30 seconds backward
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {
    private static final int MSG_HEADSET_DOUBLE_CLICK_TIMEOUT = 2;

    private static final int DOUBLE_CLICK = 400;

    private static WakeLock mWakeLock = null;
    private static int mClickCounter = 0;
    private static long mLastClickTime = 0;

    @SuppressLint("HandlerLeak") // false alarm, handler is already static
    private static final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(final Message msg) {
            if (msg.what == MSG_HEADSET_DOUBLE_CLICK_TIMEOUT) {
                final int clickCount = msg.arg1;
                final String command;

                switch (clickCount) {
                    case 1:
                        command = MediaPlayerService.ACTION_TOGGLE_PAUSE;
                        break;
                    case 2:
                        command = MediaPlayerService.ACTION_FORWARD;
                        break;
                    case 3:
                        command = MediaPlayerService.ACTION_BACKWARD;
                        break;
                    default:
                        command = null;
                        break;
                }

                if (command != null) {
                    final Context context = (Context) msg.obj;
                    // If OnStartCommand is called, the playback is started automatically
                    if (!Utils.isMediaPlayerServiceRunning(context)) {
                        if (command.equals(MediaPlayerService.ACTION_TOGGLE_PAUSE)) {
                            startService(context, "");
                        } else {
                            startService(context, command);
                            startService(context, MediaPlayerService.ACTION_TOGGLE_PAUSE);
                        }
                    } else {
                        startService(context, command);
                    }
                }
            }
        }
    };

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (handleIntent(context, intent) && isOrderedBroadcast()) {
            abortBroadcast();
        }
    }

    public static boolean handleIntent(final Context context, final Intent intent) {
        final String intentAction = intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
            final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null) {
                return false;
            }

            final int keycode = event.getKeyCode();
            final int action = event.getAction();
            // Fallback to system time if event time was not available.
            final long eventTime = event.getEventTime() != 0 ? event.getEventTime() : System.currentTimeMillis();

            String command = null;
            switch (keycode) {
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    command = MediaPlayerService.ACTION_STOP;
                    break;
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    command = MediaPlayerService.ACTION_TOGGLE_PAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    command = MediaPlayerService.ACTION_FORWARD;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    command = MediaPlayerService.ACTION_BACKWARD;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    command = MediaPlayerService.ACTION_PAUSE;
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    command = MediaPlayerService.ACTION_PLAY;
                    break;
            }
            if (command != null) {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        // Only consider the first event in a sequence, not the repeat events,
                        // so that we don't trigger in cases where the first event went to
                        // a different app (e.g. when the user ends a phone call by
                        // long pressing the headset button)

                        // The service may or may not be running, but we need to send it
                        // a command.
                        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK || keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                            if (eventTime - mLastClickTime >= DOUBLE_CLICK) {
                                mClickCounter = 0;
                            }

                            mClickCounter++;
                            mHandler.removeMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT);

                            Message msg = mHandler.obtainMessage(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT, mClickCounter, 0, context);

                            long delay = mClickCounter < 3 ? DOUBLE_CLICK : 0;
                            if (mClickCounter >= 3) {
                                mClickCounter = 0;
                            }
                            mLastClickTime = eventTime;
                            acquireWakeLockAndSendMessage(context, msg, delay);
                        } else {
                            startService(context, command);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void startService(Context context, String command) {
        final Intent intent = new Intent(context, MediaPlayerService.class);
        intent.setAction(command);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void acquireWakeLockAndSendMessage(Context context, Message msg, long delay) {
        if (mWakeLock == null) {
            Context appContext = context.getApplicationContext();
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            assert pm != null;
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioAnchor:MediaButtonWakeLock");
            mWakeLock.setReferenceCounted(false);
        }
        // Set a partial wake lock for 5 minutes after a media button command was sent
        mWakeLock.acquire(300000);
        mHandler.sendMessageDelayed(msg, delay);
    }

}
