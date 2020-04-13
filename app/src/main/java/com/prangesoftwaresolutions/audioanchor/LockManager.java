package com.prangesoftwaresolutions.audioanchor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import static android.content.Context.POWER_SERVICE;

class LockManager {
    private final String TAG = "LockManager@" + hashCode();

    private final PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    LockManager(final Context context) {
        mPowerManager = ((PowerManager) context.getApplicationContext().getSystemService(POWER_SERVICE));
    }

    @SuppressLint("WakelockTimeout")  // audio books are potentially very long
    void acquireWakeLock() {
        Log.d("LockManager", "calling acquireWakeLock");
        if (mWakeLock != null && mWakeLock.isHeld()) return;

        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    void releaseWakeLock() {
        Log.d("LockManager", "calling releaseWakeLock()");

        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        mWakeLock = null;
    }
}
