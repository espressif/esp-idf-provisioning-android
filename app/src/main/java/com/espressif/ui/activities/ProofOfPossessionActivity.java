package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.espressif.AppConstants;
import com.espressif.provision.Provision;
import com.espressif.provision.R;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ProofOfPossessionActivity.class.getSimpleName();

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnNext;
    private TextView txtNextBtn;

    private String deviceName;
    private TextView tvPopInstruction;
    private EditText etPop;
    private String securityVersion, transportVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);

        initViews();

        deviceName = getIntent().getStringExtra(AppConstants.KEY_DEVICE_NAME);
        transportVersion = getIntent().getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        securityVersion = getIntent().getStringExtra(Provision.CONFIG_SECURITY_KEY);

        if (!TextUtils.isEmpty(deviceName)) {
            String popText = getString(R.string.pop_instruction) + " " + deviceName;
            tvPopInstruction.setText(popText);
        }

        btnNext.setOnClickListener(nextBtnClickListener);
        String key = getString(R.string.proof_of_possesion);

        if (!TextUtils.isEmpty(key)) {

            etPop.setText(key);
            etPop.setSelection(etPop.getText().length());
        }
        etPop.requestFocus();
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            final String pop = etPop.getText().toString();
            Log.d(TAG, "POP : " + pop);

            if (transportVersion.equals(Provision.CONFIG_TRANSPORT_WIFI)) {

                if (ProvisionLanding.deviceCapabilities.contains("wifi_scan")) {
                    goToWiFiScanListActivity();
                } else {
                    goToProvisionActivity();
                }

            } else if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {

                if (BLEProvisionLanding.bleTransport.deviceCapabilities.contains("wifi_scan")) {
                    goToWiFiScanListActivity();
                } else {
                    goToProvisionActivity();
                }
            }
        }
    };

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.e("TAG", "On Cancel button click");
            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);
        tvPopInstruction = findViewById(R.id.tv_pop);
        etPop = findViewById(R.id.et_pop);

        tvTitle.setText(R.string.title_activity_pop);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelBtnClickListener);

        btnNext = findViewById(R.id.btn_next);
        txtNextBtn = findViewById(R.id.text_btn);

        txtNextBtn.setText(R.string.btn_next);
        btnNext.setOnClickListener(nextBtnClickListener);
    }

    private void goToWiFiScanListActivity() {

        Intent launchWiFiScanList = new Intent(getApplicationContext(), WiFiScanActivity.class);
        launchWiFiScanList.putExtras(getIntent());
        launchWiFiScanList.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, etPop.getText().toString());
        startActivity(launchWiFiScanList);
        finish();
    }

    private void goToProvisionActivity() {

        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
        launchProvisionInstructions.putExtras(getIntent());
        launchProvisionInstructions.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, etPop.getText().toString());
        startActivity(launchProvisionInstructions);
        finish();
    }
}
