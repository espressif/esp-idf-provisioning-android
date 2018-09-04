// Copyright 2018 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.espressif.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.espressif.avs.ConfigureAVS;
import com.espressif.provision.Provision;
import com.espressif.provision.R;

public class LoginWithAmazon extends AppCompatActivity {
    private static final String TAG = "Espressif::" + LoginWithAmazon.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_with_amazon);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Intent intent = getIntent();
        final String productId = intent.getStringExtra(ConfigureAVS.PRODUCT_ID_KEY);
        final String productDSN = intent.getStringExtra(ConfigureAVS.PRODUCT_DSN_KEY);
        final String codeVerifier = intent.getStringExtra(ConfigureAVS.CODE_VERIFIER_KEY);
        final String transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);

        final Activity thisActivity = this;
        View loginButton = findViewById(R.id.login_with_amazon);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConfigureAVS.loginWithAmazon(thisActivity,
                        productId,
                        productDSN,
                        codeVerifier,
                        new ConfigureAVS.AmazonLoginListener() {
                            @Override
                            public void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier) {

                                Class landingActivity = ProvisionLanding.class;
                                if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {
                                    landingActivity = BLEProvisionLanding.class;
                                }

                                Intent alexaInfoIntent = new Intent(thisActivity, landingActivity);
                                alexaInfoIntent.putExtra(ConfigureAVS.CLIENT_ID_KEY, clientId);
                                alexaInfoIntent.putExtra(ConfigureAVS.AUTH_CODE_KEY, authCode);
                                alexaInfoIntent.putExtra(ConfigureAVS.REDIRECT_URI_KEY, redirectUri);
                                alexaInfoIntent.putExtra(ConfigureAVS.CODE_VERIFIER_KEY, codeVerifier);
                                alexaInfoIntent.putExtras(getIntent());
                                startActivityForResult(alexaInfoIntent, Provision.REQUEST_PROVISIONING_CODE);
                            }

                            @Override
                            public void LoginFailed() {

                            }
                        });
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Provision.REQUEST_PROVISIONING_CODE &&
                resultCode == RESULT_OK) {
            setResult(resultCode);
            finish();
        }
    }

}
