package com.prangesoftwaresolutions.audioanchor.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.prangesoftwaresolutions.audioanchor.R;
import com.prangesoftwaresolutions.audioanchor.utils.Utils;

public class AboutActivity extends AppCompatActivity {

    TextView mVersionName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Utils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Make paypal link clickable
        TextView paypalLinkTV = findViewById(R.id.paypal_link_tv);
        paypalLinkTV.setMovementMethod(LinkMovementMethod.getInstance());

        mVersionName = findViewById(R.id.about_text_tv);

        // Extract the version name of the app
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (pInfo != null) {
            String versionName = pInfo.versionName;
            String aboutText = getResources().getString(R.string.about_text, getString(R.string.app_name), versionName);
            mVersionName.setText(aboutText);
        } else {
            mVersionName.setText(getString(R.string.app_name));
        }
    }
}
