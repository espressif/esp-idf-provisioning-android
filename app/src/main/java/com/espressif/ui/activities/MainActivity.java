package com.espressif.ui.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.provision.R;
import com.espressif.ui.adapters.TabsPagerAdapter;
import com.espressif.ui.fragments.LoginFragment;
import com.espressif.ui.user_module.AppHelper;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private ViewPager viewPager;
    private AlertDialog userDialog;
    private String email, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        TabsPagerAdapter tabsPagerAdapter = new TabsPagerAdapter(this, getSupportFragmentManager());

        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(tabsPagerAdapter);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        Log.e(TAG, "onActivityResult, requestCode : " + requestCode + " , resultCode : " + resultCode);

        if (requestCode == 11 && resultCode == RESULT_OK) {

            Log.e(TAG, "Result received from Sign up confirm");
            viewPager.setCurrentItem(0);

            Fragment page = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + viewPager.getCurrentItem());

            if (page != null && page instanceof LoginFragment) {

                if (data != null) {
                    email = data.getStringExtra("email");
                    password = data.getStringExtra("password");
                    ((LoginFragment) page).updateUi(email, password);
                } else {
                    ((LoginFragment) page).hideLoginLoading();
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void launchProvisioningApp() {

        Intent espMainActivity = new Intent(getApplicationContext(), EspMainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }

    public void signInUser(String email, String password) {

        this.email = email;
        this.password = password;
        AppHelper.getPool().getUser(email).getSessionInBackground(authenticationHandler);
    }

    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {

        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {

            Log.d(TAG, " -- Auth Success");
            Log.d(TAG, "Username : " + cognitoUserSession.getUsername());
            Log.d(TAG, "IdToken : " + cognitoUserSession.getIdToken().getJWTToken());
            Log.d(TAG, "AccessToken : " + cognitoUserSession.getAccessToken().getJWTToken());
            Log.d(TAG, "RefreshToken : " + cognitoUserSession.getRefreshToken().getToken());

            AppHelper.setCurrSession(cognitoUserSession);
            SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_EMAIL, email);
            editor.putString(AppConstants.KEY_ID_TOKEN, cognitoUserSession.getIdToken().getJWTToken());
            editor.putString(AppConstants.KEY_ACCESS_TOKEN, cognitoUserSession.getAccessToken().getJWTToken());
            editor.putString(AppConstants.KEY_REFRESH_TOKEN, cognitoUserSession.getRefreshToken().getToken());
            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
            editor.apply();

            AppHelper.newDevice(device);
            ApiManager.getInstance(getApplicationContext()).getTokenAndUserId();
            Fragment page = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + viewPager.getCurrentItem());
            if (page != null && page instanceof LoginFragment) {
                ((LoginFragment) page).hideLoginLoading();
            }
            launchProvisioningApp();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String username) {

            Log.e(TAG, "getAuthenticationDetails ");
//            hideLoginLoading();
            Locale.setDefault(Locale.US);
            getUserAuthentication(authenticationContinuation, username);
        }

        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation multiFactorAuthenticationContinuation) {
            // Nothing to do here
            Log.e(TAG, "getMFACode");
        }

        @Override
        public void onFailure(Exception e) {

            Fragment page = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + viewPager.getCurrentItem());
            if (page != null && page instanceof LoginFragment) {
                ((LoginFragment) page).hideLoginLoading();
            }
            showDialogMessage(getString(R.string.dialog_title_login_failed), AppHelper.formatException(e));
            e.printStackTrace();
        }

        @Override
        public void authenticationChallenge(ChallengeContinuation continuation) {

            // Nothing to do for this app.
            /*
             * For Custom authentication challenge, implement your logic to present challenge to the
             * user and pass the user's responses to the continuation.
             */
            Log.e(TAG, "authenticationChallenge : " + continuation.getChallengeName());
        }
    };

    private void getUserAuthentication(AuthenticationContinuation continuation, String username) {

        if (username != null) {
            email = username;
            AppHelper.setUser(username);
        }

        if (this.password == null) {

//            etEmail.setText(username);
//            password = etPassword.getText().toString();

            if (TextUtils.isEmpty(password)) {

                Fragment page = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.view_pager + ":" + viewPager.getCurrentItem());
                if (page != null && page instanceof LoginFragment) {
                    ((LoginFragment) page).hideLoginLoading();
                }
//                layoutPassword.setError(null);
                return;
            }
        }

        AuthenticationDetails authenticationDetails = new AuthenticationDetails(email, password, null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }

    private void showDialogMessage(String title, String body) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    userDialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }
}
