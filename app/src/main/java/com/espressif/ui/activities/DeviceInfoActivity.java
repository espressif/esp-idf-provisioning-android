package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.ui.models.DeviceInfo;

public class DeviceInfoActivity extends AppCompatActivity {

    private DeviceInfo deviceInfo;
    private TextView tvDeviceName, tvConnectedWifi, tvIpAddress, tvMac, tvSerialNumber, tvFwVersion;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_device_info);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        deviceInfo = intent.getParcelableExtra(AppConstants.KEY_DEVICE_INFO);
        initViews();
    }

    private void initViews() {

        tvDeviceName = findViewById(R.id.tv_device_name);
        tvConnectedWifi = findViewById(R.id.tv_connected_wifi);
        tvIpAddress = findViewById(R.id.tv_ip_addr);
        tvMac = findViewById(R.id.tv_mac);
        tvSerialNumber = findViewById(R.id.tv_serial_number);
        tvFwVersion = findViewById(R.id.tv_fw_version);

        tvDeviceName.setText(deviceInfo.getDeviceName());
        tvConnectedWifi.setText(deviceInfo.getConnectedWifi());
        tvIpAddress.setText(deviceInfo.getDeviceIp());
        tvMac.setText(deviceInfo.getMac());
        tvSerialNumber.setText(deviceInfo.getSerialNumber());
        tvFwVersion.setText(deviceInfo.getFwVersion());
    }
}
