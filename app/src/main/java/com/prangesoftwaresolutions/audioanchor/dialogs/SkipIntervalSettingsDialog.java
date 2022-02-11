package com.prangesoftwaresolutions.audioanchor.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.helpers.SkipIntervalPreference;
import com.prangesoftwaresolutions.audioanchor.utils.SkipIntervalUtils;

public class SkipIntervalSettingsDialog extends PreferenceDialogFragmentCompat {

    int mSkipIntervalValue;
    TextView mSkipIntervalTV;
    SeekBar mSkipIntervalSB;
    ImageView mSeekbarMaxIV;
    ImageView mDecreaseIntervalIV;
    ImageView mIncreaseIntervalIV;

    public static SkipIntervalSettingsDialog newInstance(String key) {
        final SkipIntervalSettingsDialog fragment = new SkipIntervalSettingsDialog();
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mSkipIntervalTV = view.findViewById(R.id.skip_interval_tv);
        mSkipIntervalSB = view.findViewById(R.id.skip_interval_sb);
        mSeekbarMaxIV = view.findViewById(R.id.seekbar_max_iv);
        mDecreaseIntervalIV = view.findViewById(R.id.decrease_interval_iv);
        mIncreaseIntervalIV = view.findViewById(R.id.increase_interval_iv);

        // Set the icon indicating the maximum value of the SeekBar to the "next" icon if the
        // corresponding button is a forward button (default is the "previous" icon)
        if (((SkipIntervalPreference) getPreference()).isForward()) {
            mSeekbarMaxIV.setImageResource(R.drawable.ic_next);
        }

        // Get the skipInterval value from the related Preference
        mSkipIntervalValue = 0;
        DialogPreference preference = getPreference();
        if (preference instanceof SkipIntervalPreference) {
            mSkipIntervalValue = ((SkipIntervalPreference) preference).getSkipInterval();
        }

        // Set initial values of the SeekBar and the TextView
        mSkipIntervalSB.setProgress(SkipIntervalUtils.getProgressFromSkipInterval(mSkipIntervalValue));
        mSkipIntervalTV.setText(getSkipIntervalString(mSkipIntervalValue));

        mSkipIntervalSB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int skipInterval = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
                mSkipIntervalTV.setText(getSkipIntervalString(skipInterval));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(seekBar.getProgress());
            }
        });

        mDecreaseIntervalIV.setOnClickListener(view1 -> {
            int progress = mSkipIntervalSB.getProgress();
            progress = Math.max(0, progress - 1);
            mSkipIntervalSB.setProgress(progress);
            mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
        });

        mIncreaseIntervalIV.setOnClickListener(view1 -> {
            int progress = mSkipIntervalSB.getProgress();
            progress = Math.min(mSkipIntervalSB.getMax(), progress + 1);
            mSkipIntervalSB.setProgress(progress);
            mSkipIntervalValue = SkipIntervalUtils.getSkipIntervalFromProgress(progress);
        });
    }

    String getSkipIntervalString(int skipInterval) {
        if (skipInterval == SkipIntervalUtils.getSkipIntervalFromProgress(mSkipIntervalSB.getMax())) {
            if (((SkipIntervalPreference) getPreference()).isForward()) {
                return getResources().getString(R.string.settings_skip_interval_next);
            } else {
                return getResources().getString(R.string.settings_skip_interval_previous);
            }
        } else {
            return getResources().getString(R.string.settings_skip_interval, skipInterval);
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Get the related Preference and save the value
            DialogPreference preference = getPreference();
            if (preference instanceof SkipIntervalPreference) {
                SkipIntervalPreference skipIntervalPreference = ((SkipIntervalPreference) preference);
                // This allows the client to ignore the user value.
                if (skipIntervalPreference.callChangeListener(mSkipIntervalValue)) {
                    // Save the value
                    skipIntervalPreference.setSkipInterval(mSkipIntervalValue);
                }
            }
        }
    }
}
