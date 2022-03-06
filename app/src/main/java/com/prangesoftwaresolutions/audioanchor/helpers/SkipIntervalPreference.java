package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;

import com.prangesoftwaresolutions.audioanchor.R;

/*
* Custom preference class to set a skip interval for the skip buttons.
* Based on this tutorial:
* https://medium.com/@JakobUlbrich/building-a-settings-screen-for-android-part-3-ae9793fd31ec
*/
public class SkipIntervalPreference extends DialogPreference {

    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    static final int DEFAULT_VAL = 30;

    Context mContext;
    String mDialogTitle;
    String mSettingsKey;
    int mDefault;
    int mSkipInterval;

    public SkipIntervalPreference(Context context) {
        this(context, null);
    }

    public SkipIntervalPreference(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.dialogPreferenceStyle);
    }

    public SkipIntervalPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public SkipIntervalPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        setDialogLayoutResource(R.layout.dialog_skip_interval_settings);

        // Get dialogTitle as specified in the xml file
        int dialogTitleId = attrs.getAttributeResourceValue(ANDROID_NS, "dialogTitle", 0);
        if(dialogTitleId == 0) mDialogTitle = attrs.getAttributeValue(ANDROID_NS, "dialogTitle");
        else mDialogTitle = mContext.getString(dialogTitleId);

        // Get settings key as specified in the xml file
        int settingsKeyId = attrs.getAttributeResourceValue(ANDROID_NS, "key", 0);
        if(settingsKeyId == 0) mSettingsKey = attrs.getAttributeValue(ANDROID_NS, "key");
        else mSettingsKey = mContext.getString(settingsKeyId);

        // Get default seekbar value as specified in the xml file with a string resource
        // Note that this will fail if the xml file contains the actual value and not a string resource
        int defaultValueResourceId = (attrs.getAttributeResourceValue(ANDROID_NS, "defaultValue", DEFAULT_VAL));
        mDefault = Integer.parseInt(context.getString(defaultValueResourceId));

        // setDialogMessage(...) does not seem to work, even when including a TextView with id
        // R.id.message in the dialog_skip_button_settings.xml, therefore only use dialogTitle
        setDialogTitle(mContext.getString(R.string.settings_skip_interval_msg, getButtonTitle().toLowerCase()));
        setPositiveButtonText(R.string.dialog_msg_ok);
        setNegativeButtonText(R.string.dialog_msg_cancel);
    }

    public int getSkipInterval() {
        return mSkipInterval;
    }

    public void setSkipInterval(int skipInterval) {
        mSkipInterval = skipInterval;
        persistInt(skipInterval);  // Save to Shared Preferences
    }

    public String getButtonTitle() {
        return mDialogTitle;
    }

    public boolean isForward() {
        return mSettingsKey.toLowerCase().contains("forward");
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, DEFAULT_VAL);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        // Read the value from Shared Preferences. Use the default value if it is not possible.
        int skipInterval = getPersistedInt(mDefault);
        setSkipInterval(skipInterval);
    }
}
