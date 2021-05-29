package com.prangesoftwaresolutions.audioanchor.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.models.Bookmark;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefListener;
    private SharedPreferences mPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (mPrefListener == null) {
            mPrefListener = (prefs, key) -> {
                if (key.equals(getString(R.string.settings_dark_key))) {
                    recreate();
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
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mPrefListener);
    }

    public static class AnchorPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // addPreferencesFromResource(R.xml.settings);

            // Make sure the autoplay position preference is enabled iff autoplay is
            final SwitchPreference autoplayPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_key));
            final SwitchPreference autoplayPositionPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_restart_key));

            // This is needed for cases where autoplay was checked while a previous version of
            // AudioAnchor was installed
            autoplayPositionPref.setEnabled(autoplayPref.isChecked());

            initSummary(getPreferenceScreen());
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
            Preference pref = findPreference(s);

            updatePrefSummary(pref);

            if (s.equals(getString(R.string.settings_autoplay_key))) {
                boolean isChecked = ((SwitchPreference) pref).isChecked();
                final SwitchPreference autoplayPositionPref = (SwitchPreference) findPreference(getString(R.string.settings_autoplay_restart_key));
                autoplayPositionPref.setEnabled(isChecked);
            } else if (s.equals(getString(R.string.settings_add_last_play_position_bookmark_key))) {
                boolean isChecked = ((SwitchPreference) pref).isChecked();
                if (!isChecked) {
                    Bookmark.deleteBookmarksWithTitle(getActivity(), getString(R.string.bookmark_last_play_position));
                    Bookmark.deleteBookmarksWithTitle(getActivity(), getString(R.string.bookmark_second_to_last_play_position));
                }
            }
        }

        private void initSummary(Preference p) {
            if (p instanceof PreferenceGroup) {
                PreferenceGroup pGrp = (PreferenceGroup) p;
                for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                    initSummary(pGrp.getPreference(i));
                }
            } else {
                updatePrefSummary(p);
            }
        }

        private void updatePrefSummary(Preference p) {
            if (p instanceof EditTextPreference) {
                EditTextPreference editTextPref = (EditTextPreference) p;
                p.setSummary(editTextPref.getText());
            } else if (p instanceof ListPreference) {
                p.setSummary(((ListPreference)p).getEntry());
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
