package com.espressif.ui.user_module;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ForgotPasswordContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.ForgotPasswordHandler;
import com.espressif.provision.R;
import com.espressif.ui.fragments.ForgotPasswordFragment;
import com.espressif.ui.fragments.ResetPasswordFragment;

public class ForgotPasswordActivity extends AppCompatActivity {

    private AlertDialog userDialog;
    private ForgotPasswordContinuation forgotPasswordContinuation;
    private TextView tvTitle, tvBack, tvCancel;

    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        init();
    }

    @Override
    public void onBackPressed() {

        Log.e("TAG", "ON BACK PRESSED");

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof ResetPasswordFragment) {

            Log.e("TAG", "Reset Password fragment on back pressed");

            Fragment forgotPasswordFragment = new ForgotPasswordFragment();
            Bundle data = new Bundle();
            data.putString("email", email);
            forgotPasswordFragment.setArguments(data);
            loadForgotPasswordFragment(forgotPasswordFragment);

        } else {
            super.onBackPressed();
        }
    }

    private void init() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvBack.setOnClickListener(backButtonClickListener);
        tvCancel.setOnClickListener(cancelButtonClickListener);

        Fragment forgotPasswordFragment = new ForgotPasswordFragment();
        loadForgotPasswordFragment(forgotPasswordFragment);
    }

    private void loadForgotPasswordFragment(Fragment forgotPasswordFragment) {

        tvTitle.setText(R.string.title_activity_forgot_password);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, forgotPasswordFragment);
        transaction.commit();
    }

    private void loadResetPasswordFragment(Fragment resetPasswordFragment) {

        tvTitle.setText(R.string.title_activity_reset_password);
        tvBack.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.VISIBLE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, resetPasswordFragment);
        transaction.commit();
    }

    public void forgotPassword(String email) {

        this.email = email;
        AppHelper.getPool().getUser(email).forgotPasswordInBackground(forgotPasswordHandler);
    }

    public void resetPassword(String newPassword, String verificationCode) {

        forgotPasswordContinuation.setPassword(newPassword);
        forgotPasswordContinuation.setVerificationCode(verificationCode);
        forgotPasswordContinuation.continueTask();
    }

    View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.e("TAG", "On Back button click");
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

            if (currentFragment instanceof ResetPasswordFragment) {

                onBackPressed();
            }
        }
    };

    View.OnClickListener cancelButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.e("TAG", "On Cancel button click");
            finish();
        }
    };

    // Callbacks
    ForgotPasswordHandler forgotPasswordHandler = new ForgotPasswordHandler() {

        @Override
        public void onSuccess() {

            Log.e("TAG", "ON SUCCESS");
            hideLoading();
            showDialogMessage(getString(R.string.dialog_title_password_changed), "", true);
        }

        @Override
        public void getResetCode(ForgotPasswordContinuation forgotPasswordContinuation) {

            hideLoading();
            getForgotPasswordCode(forgotPasswordContinuation);
        }

        @Override
        public void onFailure(Exception e) {

            hideLoading();
            showDialogMessage(getString(R.string.dialog_title_forgot_password_failed), AppHelper.formatException(e), false);
        }
    };

    private void getForgotPasswordCode(ForgotPasswordContinuation forgotPasswordContinuation) {

        this.forgotPasswordContinuation = forgotPasswordContinuation;

        Fragment resetPasswordFragment = new ResetPasswordFragment();
        Bundle data = new Bundle();
        data.putString("destination", forgotPasswordContinuation.getParameters().getDestination());
        data.putString("deliveryMed", forgotPasswordContinuation.getParameters().getDeliveryMedium());
        resetPasswordFragment.setArguments(data);
        loadResetPasswordFragment(resetPasswordFragment);
    }

    private void showDialogMessage(String title, String body, final boolean shouldExit) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                try {
                    userDialog.dismiss();

                    if (shouldExit) {
                        finish();
                        // TODO Clear back-stack and start new screen.
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }

    private void hideLoading() {

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof ForgotPasswordFragment) {

            Log.e("TAG", "Hide loading of forgot password");
            ((ForgotPasswordFragment) currentFragment).hideLoading();

        } else if (currentFragment instanceof ResetPasswordFragment) {

            Log.e("TAG", "Hide loading of reset password");
            ((ResetPasswordFragment) currentFragment).hideLoading();
        }
    }
}
