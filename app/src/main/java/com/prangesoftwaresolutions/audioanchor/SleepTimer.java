package com.prangesoftwaresolutions.audioanchor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class SleepTimer {

    private CountDownTimer mSleepTimer;

    private final MediaPlayerService mPlayer;
    private TextView mSleepCountDownTV;
    private long mCurrentMillisLeft;
    private int mSecSleepTime;
    private int mFadeOutSec;

    // Shake stuff
    private boolean mShakeDetectionEnabled;
    private final SensorManager mSensorMng;
    private final ShakeDetector mShakeDetector;
    private float mShakeForceRequired = 3f;

    SleepTimer(TextView sleepCountDownTV, MediaPlayerService mediaPlayer, SensorManager sensorMng) {
        mSleepCountDownTV = sleepCountDownTV;
        mPlayer = mediaPlayer;
        mSensorMng = sensorMng;
        mShakeDetector = new ShakeDetector(mShakeForceRequired) {
            @Override
            public void shakeDetected() {
                restartTimer();
                startTimer(false);
            }
        };
    }

    void createTimer(final int secSleepTime, int fadeOutSec, boolean shakeDetectionEnabled, float shakeForceRequiredPercent) {
        // Let the user disable the timer by entering 0 or nothing
        if (secSleepTime == 0) {
            disableTimer();
            return;
        }

        final float mShakeForceMax = 20f;
        final float mShakeForceMin = 0.5f;

        mFadeOutSec = fadeOutSec;
        mShakeDetectionEnabled = shakeDetectionEnabled;

        if (shakeForceRequiredPercent <= 1f && shakeForceRequiredPercent >= 0f) {
            mShakeForceRequired = ((mShakeForceMax - mShakeForceMin) * shakeForceRequiredPercent) + mShakeForceMin;
            mShakeDetector.setShakeTresh(mShakeForceRequired);
        }

        createTimer(secSleepTime);
    }

    private void createTimer(final int secSleepTime) {
        mSecSleepTime = secSleepTime;
        long millis = secSleepTime * 1000;
        mSleepCountDownTV.setVisibility(View.VISIBLE);
        mCurrentMillisLeft = millis;
        String timeString = Utils.formatTime(millis, millis);
        mSleepCountDownTV.setText(timeString);

        if (mSleepTimer != null)
            mSleepTimer.cancel();

        mSleepTimer = new CountDownTimer(millis, 1000) {

            @Override
            public void onTick(long l) {
                mCurrentMillisLeft = l;

                // Update text
                String timeString = Utils.formatTime(l, secSleepTime * 1000);
                mSleepCountDownTV.setText(timeString);

                // Fade-out
                if ((l / 1000) < mFadeOutSec) {
                    mPlayer.decreaseVolume((int) (mFadeOutSec - (l / 1000)), mFadeOutSec);
                }
            }

            @Override
            public void onFinish() {
                finished();
                disableTimer();
            }
        };
    }

    void setNewSleepCountDownTV(TextView countDownTV) {
        boolean visible = false;
        if (mSleepCountDownTV.getVisibility() == View.VISIBLE) {
            visible = true;
        }
        mSleepCountDownTV = countDownTV;

        if (visible) {
            String timeString = Utils.formatTime(mCurrentMillisLeft, mSecSleepTime * 1000);
            mSleepCountDownTV.setText(timeString);
            mSleepCountDownTV.setVisibility(View.VISIBLE);
        }
    }

    private void startShakeDetection() {
        if (mSensorMng != null) {
            Sensor sensor = mSensorMng.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorMng.registerListener(
                    mShakeDetector,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    void disableTimer() {
        mCurrentMillisLeft = 0;

        if (mSleepTimer != null) {
            mSleepTimer.cancel();
            mSleepCountDownTV.setVisibility(View.GONE);

            // We need to avoid the application resetting the volume on slower systems before pause.
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mPlayer.setVolume(1.0f);
                }
            }, 500);
        }

        if (mSensorMng != null) {
            mSensorMng.unregisterListener(mShakeDetector);
        }
    }

    private void restartTimer() {
        if (mCurrentMillisLeft > 0) {
            mSleepTimer.cancel();
            mPlayer.setVolume(1.0f);
            createTimer(mSecSleepTime);
        }

        if (mSensorMng != null) {
            mSensorMng.unregisterListener(mShakeDetector);
        }
    }

    void startTimer(boolean onlyIfPlaying) {
        if (mCurrentMillisLeft <= 0)
            return;

        if (onlyIfPlaying) {
            if (mPlayer.isPlaying()) {
                mSleepTimer.start();
                if (mShakeDetectionEnabled)
                    startShakeDetection();
            }
        } else {
            mSleepTimer.start();
            if (mShakeDetectionEnabled)
                startShakeDetection();
        }
    }

    // For callback when timer is finished
    public void finished() {
    }
}

class ShakeDetector implements SensorEventListener {
    private final float[] mGravity = {0, 0, 0};
    private final float[] mLinearAcc = {0, 0, 0};
    private long mLastMeasurementT = 0;
    private float mShakeTresh;
    final private long mStartTime;

    ShakeDetector(float forceRequired) {
        mShakeTresh = forceRequired;
        mStartTime = System.currentTimeMillis();
    }

    void setShakeTresh(float newVal) {
        mShakeTresh = newVal;
    }

    // Callback for when a shake has been detected
    void shakeDetected() {
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // From https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-accel
        final int sampleMsec = 100;
        final int settleTime = 2000; //let the filter settle

        // We only sample @ mSampleMsec
        if (System.currentTimeMillis() - mLastMeasurementT < sampleMsec) {
            return;
        }
        mLastMeasurementT = System.currentTimeMillis();

        final float alpha = 0.8f;

        // Isolate the force of mGravity with the low-pass filter.
        mGravity[0] = alpha * mGravity[0] + (1 - alpha) * sensorEvent.values[0];
        mGravity[1] = alpha * mGravity[1] + (1 - alpha) * sensorEvent.values[1];
        mGravity[2] = alpha * mGravity[2] + (1 - alpha) * sensorEvent.values[2];

        // Remove the mGravity contribution with the high-pass filter.
        mLinearAcc[0] = sensorEvent.values[0] - mGravity[0];
        mLinearAcc[1] = sensorEvent.values[1] - mGravity[1];
        mLinearAcc[2] = sensorEvent.values[2] - mGravity[2];

        float totalAcc = mLinearAcc[0] + mLinearAcc[1] + mLinearAcc[2];
        if (totalAcc > mShakeTresh && System.currentTimeMillis() - mStartTime > settleTime) {
            shakeDetected();
            Log.d("SHAKE", "Shake with force of: " + totalAcc);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
