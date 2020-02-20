package com.espressif.ui.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.widget.TextView;

import com.espressif.provision.R;

public class AppInfoActivity extends AppCompatActivity {

    private TextView tvVersion;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_info);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_app_info);
        setSupportActionBar(toolbar);

        initViews();
    }

    private void initViews() {

        tvVersion = findViewById(R.id.tv_app_version);
        String version = getString(R.string.app_version);

        try {

            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = version + " " + pInfo.versionName;

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        tvVersion.setText(version);
    }
}
