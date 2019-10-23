package com.prangesoftwaresolutions.audioanchor;

import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if(mPrefListener == null) {
            mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals(getString(R.string.settings_dark_key))) {
                        recreate();
                    }
                }
            };
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return  true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
    }

    public static class AnchorPreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            // Make sure the autoplay position preference is enabled iff autoplay is
            final SwitchPreference autoplayPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_key));
            final SwitchPreference autoplayPositionPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_restart_key));

            // This is needed for cases where autoplay was checked while a previous version of
            // AudioAnchor was installed
            if (autoplayPref.isChecked()) {
                autoplayPositionPref.setEnabled(true);
            } else {
                autoplayPositionPref.setEnabled(false);
            }

            autoplayPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean isChecked = (boolean) o;
                    if (isChecked) {
                        autoplayPositionPref.setEnabled(true);
                    } else {
                        autoplayPositionPref.setEnabled(false);
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String stringValue = o.toString();
            preference.setSummary(stringValue);
            return true;
        }
    }
}
