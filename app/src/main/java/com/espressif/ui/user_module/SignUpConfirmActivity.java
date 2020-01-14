package com.espressif.ui.user_module;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserCodeDeliveryDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.GenericHandler;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.VerificationHandler;
import com.espressif.provision.R;
import com.espressif.ui.Utils;

public class SignUpConfirmActivity extends AppCompatActivity {

    private TextView tvConfMsg;
    private EditText etEmail;
    private EditText etConfCode;
    private CardView btnConfirm;
    private TextView txtConfirmBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;
    private TextView tvResendCode;
    private AlertDialog userDialog;
    private TextView tvTitle, tvBack, tvCancel;

    private String email, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up_confirm);
        init();
    }

    private void init() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_sign_up_confirm);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelButtonClickListener);

        tvConfMsg = findViewById(R.id.tv_sign_up_confirm_msg_1);
        etEmail = findViewById(R.id.et_email);
        etConfCode = findViewById(R.id.et_verification_code);
        btnConfirm = findViewById(R.id.btn_confirm);
        txtConfirmBtn = findViewById(R.id.text_btn);
        arrowImage = findViewById(R.id.iv_arrow);
        progressBar = findViewById(R.id.progress_indicator);
        tvResendCode = findViewById(R.id.tv_resend_code);

        txtConfirmBtn.setText(R.string.btn_confirm);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {

            if (extras.containsKey("name")) {

                email = extras.getString("name");
                password = extras.getString("password");
                etEmail.setText(email);
                etConfCode.requestFocus();

                if (extras.containsKey("destination")) {

                    String dest = extras.getString("destination");
                    String delMed = extras.getString("deliveryMed");

                    if (dest != null && delMed != null && dest.length() > 0 && delMed.length() > 0) {
                        tvConfMsg.setText("A confirmation code was sent to " + dest + " via " + delMed);
                    } else {
                        tvConfMsg.setText("A confirmation code was sent");
                    }
                }
            } else {
                tvConfMsg.setText("Request for a confirmation code or confirm with the code you already have.");
            }
        }

        btnConfirm.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendConfCode();
            }
        });

        tvResendCode.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                reqConfCode();
            }
        });

        etConfCode.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    sendConfCode();
                }
                return false;
            }
        });
    }

    View.OnClickListener cancelButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.e("TAG", "On Cancel button click");
            finish();
        }
    };

    private void sendConfCode() {

        email = etEmail.getText().toString();
        String confirmCode = etConfCode.getText().toString();
        etEmail.setError(null);
        etConfCode.setError(null);

        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;

        } else if (!Utils.isValidEmail(email)) {

            etEmail.setError(getString(R.string.error_invalid_email));
            return;

        } else if (TextUtils.isEmpty(confirmCode)) {

            etConfCode.setError(getString(R.string.error_confirmation_code_empty));
            return;
        }

        showLoading();
        AppHelper.getPool().getUser(email).confirmSignUpInBackground(confirmCode, true, confHandler);
    }

    private void reqConfCode() {

        email = etEmail.getText().toString();

        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;
        }
        AppHelper.getPool().getUser(email).resendConfirmationCodeInBackground(resendConfCodeHandler);
    }

    GenericHandler confHandler = new GenericHandler() {

        @Override
        public void onSuccess() {

            hideLoading();
            showDialogMessage("Success!", email + " has been confirmed!", true);
        }

        @Override
        public void onFailure(Exception exception) {

            hideLoading();
            showDialogMessage("Confirmation failed", AppHelper.formatException(exception), false);
        }
    };

    VerificationHandler resendConfCodeHandler = new VerificationHandler() {

        @Override
        public void onSuccess(CognitoUserCodeDeliveryDetails cognitoUserCodeDeliveryDetails) {

            etConfCode.requestFocus();
            showDialogMessage(getString(R.string.dialog_title_conf_code_sent), "Code sent to " + cognitoUserCodeDeliveryDetails.getDestination() + " via " + cognitoUserCodeDeliveryDetails.getDeliveryMedium() + ".", false);
        }

        @Override
        public void onFailure(Exception exception) {

            showDialogMessage(getString(R.string.dialog_title_conf_code_req_fail), AppHelper.formatException(exception), false);
        }
    };

    private void showDialogMessage(String title, String body, final boolean exitActivity) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    userDialog.dismiss();

                    if (exitActivity) {

                        Intent intent = new Intent();
                        if (email == null)
                            email = "";
                        intent.putExtra("email", email);
                        intent.putExtra("password", password);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }

    private void showLoading() {

        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);
        txtConfirmBtn.setText(R.string.btn_confirming);
        progressBar.setVisibility(View.VISIBLE);
        arrowImage.setVisibility(View.GONE);
    }

    public void hideLoading() {

        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1f);
        txtConfirmBtn.setText(R.string.btn_confirm);
        progressBar.setVisibility(View.GONE);
        arrowImage.setVisibility(View.VISIBLE);
    }
}
