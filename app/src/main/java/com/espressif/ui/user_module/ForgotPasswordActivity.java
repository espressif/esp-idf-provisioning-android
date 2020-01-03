/*
 * Copyright 2013-2017 Amazon.com,
 * Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the
 * License. A copy of the License is located at
 *
 *      http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, express or implied. See the License
 * for the specific language governing permissions and
 * limitations under the License.
 */

package com.espressif.ui.user_module;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.espressif.provision.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private EditText codeInput;
    private Button setPassword;
    private AlertDialog userDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exit(null, null);
            }
        });

        TextView main_title = (TextView) findViewById(R.id.forgot_password_toolbar_title);
        main_title.setText("Forgot password");

        init();
    }

    public void forgotPassword(View view) {
        getCode();
    }

    private void init() {

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey("destination")) {
                String dest = extras.getString("destination");
                String delMed = extras.getString("deliveryMed");
                TextView message = (TextView) findViewById(R.id.textViewForgotPasswordMessage);
                String textToDisplay = "Code to set a new password was sent to " + dest + " via " + delMed;
                message.setText(textToDisplay);
            }
        }

        passwordInput = (EditText) findViewById(R.id.editTextForgotPasswordPass);
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordUserIdLabel);
                    label.setText(passwordInput.getHint());
                    passwordInput.setBackground(getDrawable(R.drawable.text_border_selector));
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.textViewForgotPasswordUserIdMessage);
                label.setText(" ");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordUserIdLabel);
                    label.setText("");
                }
            }
        });

        confirmPasswordInput = (EditText) findViewById(R.id.editTextForgotPasswordConfirmPass);
        confirmPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordConfirmPassLabel);
                    label.setText(confirmPasswordInput.getHint());
                    confirmPasswordInput.setBackground(getDrawable(R.drawable.text_border_selector));
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.textViewForgotPasswordConfirmPassMessage);
                label.setText(" ");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordConfirmPassLabel);
                    label.setText("");
                }
            }
        });

        codeInput = (EditText) findViewById(R.id.editTextForgotPasswordCode);
        codeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordCodeLabel);
                    label.setText(codeInput.getHint());
                    codeInput.setBackground(getDrawable(R.drawable.text_border_selector));
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                TextView label = (TextView) findViewById(R.id.textViewForgotPasswordCodeMessage);
                label.setText(" ");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    TextView label = (TextView) findViewById(R.id.textViewForgotPasswordCodeLabel);
                    label.setText("");
                }
            }
        });
    }

    private void getCode() {

        String newPassword = passwordInput.getText().toString();
        if (TextUtils.isEmpty(newPassword)) {
            TextView label = (TextView) findViewById(R.id.textViewForgotPasswordUserIdMessage);
            label.setText(passwordInput.getHint() + " cannot be empty");
            passwordInput.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        String newConfirmPassword = confirmPasswordInput.getText().toString();
        if (TextUtils.isEmpty(newConfirmPassword)) {
            TextView label = (TextView) findViewById(R.id.textViewForgotPasswordConfirmPassMessage);
            label.setText(confirmPasswordInput.getHint() + " cannot be empty");
            confirmPasswordInput.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        if (!newPassword.equals(newConfirmPassword)) {
            TextView label = (TextView) findViewById(R.id.textViewForgotPasswordConfirmPassMessage);
            label.setText(" Passwords not matched");
            confirmPasswordInput.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }

        String verCode = codeInput.getText().toString();
        if (verCode == null || verCode.length() < 1) {
            TextView label = (TextView) findViewById(R.id.textViewForgotPasswordCodeMessage);
            label.setText(codeInput.getHint() + " cannot be empty");
            codeInput.setBackground(getDrawable(R.drawable.text_border_error));
            return;
        }
        exit(newPassword, verCode);
    }

    private void exit(String newPass, String code) {

        Intent intent = new Intent();
        if (newPass == null || code == null) {
            newPass = "";
            code = "";
        }
        intent.putExtra("newPass", newPass);
        intent.putExtra("code", code);
        setResult(RESULT_OK, intent);
        finish();
    }
}
