package com.espressif.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

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
import com.espressif.ui.user_module.AppHelper;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = SplashActivity.class.getSimpleName();

    private String email;
    private Handler handler;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler = new Handler();
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        AppHelper.init(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();

        email = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        String accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        boolean isOAuthLogin = sharedPreferences.getBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
        Log.e(TAG, "Email : " + email);
        Log.e(TAG, "accessToken : " + accessToken);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(accessToken)) {

            handler.postDelayed(launchLoginScreenTask, 1500);

        } else {

            ApiManager apiManager = ApiManager.getInstance(getApplicationContext());
            boolean isTokenExpired = apiManager.isTokenExpired();

            if (isTokenExpired) {

                if (isOAuthLogin) {

                    ApiManager.getInstance(getApplicationContext()).setTokenAndUserId();
                    apiManager.getNewToken();

                } else {

                    AppHelper.setUser(email);
                    AppHelper.getPool().getUser(email).getSessionInBackground(authenticationHandler);
                }

            } else {

                ApiManager.getInstance(getApplicationContext()).setTokenAndUserId();
                handler.postDelayed(launchProvisioningAppTask, 1500);
            }
        }
    }

    public void launchProvisioningApp() {

        Intent espMainActivity = new Intent(getApplicationContext(), EspMainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }

    public void launchLoginScreen() {

        Intent espMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        espMainActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(espMainActivity);
        finish();
    }

    private Runnable launchLoginScreenTask = new Runnable() {

        @Override
        public void run() {
            launchLoginScreen();
        }
    };

    private Runnable launchProvisioningAppTask = new Runnable() {

        @Override
        public void run() {
            launchProvisioningApp();
        }
    };

    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {

        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice device) {

            Log.d(TAG, " -- Auth Success");
            Log.d(TAG, "Username : " + cognitoUserSession.getUsername());
            Log.d(TAG, "IdToken : " + cognitoUserSession.getIdToken().getJWTToken());
            Log.d(TAG, "AccessToken : " + cognitoUserSession.getAccessToken().getJWTToken());
            Log.d(TAG, "RefreshToken : " + cognitoUserSession.getRefreshToken().getToken());

            AppHelper.setCurrSession(cognitoUserSession);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_EMAIL, email);
            editor.putString(AppConstants.KEY_ID_TOKEN, cognitoUserSession.getIdToken().getJWTToken());
            editor.putString(AppConstants.KEY_ACCESS_TOKEN, cognitoUserSession.getAccessToken().getJWTToken());
            editor.putString(AppConstants.KEY_REFRESH_TOKEN, cognitoUserSession.getRefreshToken().getToken());
            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
            editor.apply();

            AppHelper.newDevice(device);
            ApiManager.getInstance(getApplicationContext()).setTokenAndUserId();
            launchProvisioningApp();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String username) {

            Log.d(TAG, "getAuthenticationDetails");
            Locale.setDefault(Locale.US);
            getUserAuthentication(authenticationContinuation, username);
        }

        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation multiFactorAuthenticationContinuation) {
            // Nothing to do here
            Log.d(TAG, "getMFACode");
        }

        @Override
        public void onFailure(Exception e) {

            Log.e(TAG, "onFailure");
            e.printStackTrace();
            launchLoginScreen();
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

        Log.d(TAG, "getUserAuthentication");
        if (username != null) {
            email = username;
            AppHelper.setUser(username);
        }

        AuthenticationDetails authenticationDetails = new AuthenticationDetails(email, "", null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }
}
