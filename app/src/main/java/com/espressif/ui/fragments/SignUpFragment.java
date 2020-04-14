package com.espressif.ui.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserAttributes;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserCodeDeliveryDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.SignUpHandler;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.provision.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.MainActivity;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.SignUpConfirmActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class SignUpFragment extends Fragment {

    private static final String TAG = SignUpFragment.class.getSimpleName();

    private EditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private TextInputLayout layoutPassword;
    private TextInputLayout layoutConfirmPassword;
    private CheckBox cbTermsCondition;
    private CardView btnRegister;
    private TextView txtRegisterBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;
    private AlertDialog userDialog;
    private TextView tvPolicy;

    private String email, password, confirmPassword;
    private SharedPreferences sharedPreferences;

    public SignUpFragment() {
        // Required empty public constructor
    }

    public static SignUpFragment newInstance() {
        return new SignUpFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_signup, container, false);
        init(rootView);
        return rootView;
    }

    private View.OnClickListener registerBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            doSignUp();
        }
    };

    private void doSignUp() {

        etEmail.setError(null);
        layoutPassword.setError(null);
        layoutConfirmPassword.setError(null);

        // Read user data and register
        CognitoUserAttributes userAttributes = new CognitoUserAttributes();

        email = etEmail.getText().toString();
        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;

        } else if (!Utils.isValidEmail(email)) {

            etEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        password = etPassword.getText().toString();
        if (TextUtils.isEmpty(password)) {

            layoutPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        confirmPassword = etConfirmPassword.getText().toString();
        if (TextUtils.isEmpty(confirmPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_confirm_password_empty));
            return;
        }

        if (!password.equals(confirmPassword)) {

            layoutConfirmPassword.setError(getString(R.string.error_password_not_matched));
            return;
        }

        if (!cbTermsCondition.isChecked()) {

            Toast.makeText(getActivity(), R.string.error_agree_terms_n_condition, Toast.LENGTH_SHORT).show();
            return;
        }

        String userInput = etEmail.getText().toString();
        if (userInput != null) {
            if (userInput.length() > 0) {
                userAttributes.addAttribute(AppHelper.getSignUpFieldsC2O().get(etEmail.getHint()).toString(), userInput);
            }
        }

        btnRegister.setEnabled(false);
        btnRegister.setAlpha(0.5f);
        txtRegisterBtn.setText(R.string.btn_registering);
        progressBar.setVisibility(View.VISIBLE);
        arrowImage.setVisibility(View.GONE);

        AppHelper.getPool().signUpInBackground(email, password, userAttributes, null, signUpHandler);
    }

    private void init(View view) {

        sharedPreferences = getActivity().getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        txtRegisterBtn = view.findViewById(R.id.text_btn);
        arrowImage = view.findViewById(R.id.iv_arrow);
        progressBar = view.findViewById(R.id.progress_indicator);
        tvPolicy = view.findViewById(R.id.tv_terms_condition);

        SpannableString stringForPolicy = new SpannableString(getString(R.string.read_terms_condition));

        ClickableSpan privacyPolicyClick = new ClickableSpan() {

            @Override
            public void onClick(View textView) {
                textView.invalidate();
                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PRIVACY_URL));
                startActivity(openURL);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getResources().getColor(R.color.colorPrimary));
                ds.setUnderlineText(true);
            }
        };

        ClickableSpan termsOfUseClick = new ClickableSpan() {

            @Override
            public void onClick(View textView) {
                textView.invalidate();
                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.TERMS_URL));
                startActivity(openURL);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getResources().getColor(R.color.colorPrimary));
                ds.setUnderlineText(true);
            }
        };

        stringForPolicy.setSpan(privacyPolicyClick, 29, 43, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        stringForPolicy.setSpan(termsOfUseClick, 48, 60, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvPolicy.setText(stringForPolicy);
        tvPolicy.setMovementMethod(LinkMovementMethod.getInstance());

        // Email
        etEmail = view.findViewById(R.id.et_email);
        etEmail.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    email = s.toString();
                }
                enableRegisterButton();
            }
        });

        // Password
        layoutPassword = view.findViewById(R.id.layout_password);
        etPassword = view.findViewById(R.id.et_password);
        etPassword.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    password = s.toString();
                }
                enableRegisterButton();
            }
        });

        // Confirm Password
        layoutConfirmPassword = view.findViewById(R.id.layout_confirm_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_password);
        etConfirmPassword.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    confirmPassword = s.toString();
                }
                enableRegisterButton();
            }
        });

        cbTermsCondition = view.findViewById(R.id.checkbox_terms_condition);
        btnRegister = view.findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(registerBtnClickListener);
        enableRegisterButton();

        etConfirmPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {

                    if (cbTermsCondition.isChecked()) {

                        doSignUp();

                    } else {
                        cbTermsCondition.setChecked(true);
                    }
                }
                return false;
            }
        });
    }

    SignUpHandler signUpHandler = new SignUpHandler() {

        @Override
        public void onSuccess(CognitoUser user, boolean signUpConfirmationState,
                              CognitoUserCodeDeliveryDetails cognitoUserCodeDeliveryDetails) {

            Log.e(TAG, "Signup handler , signUpConfirmationState : " + signUpConfirmationState);

            // Check signUpConfirmationState to see if the user is already confirmed
            btnRegister.setEnabled(true);
            btnRegister.setAlpha(1f);
            txtRegisterBtn.setText(R.string.btn_register);
            progressBar.setVisibility(View.GONE);
            arrowImage.setVisibility(View.VISIBLE);

//            closeWaitDialog();
            Boolean regState = signUpConfirmationState;
            if (signUpConfirmationState) {
                // User is already confirmed
                showDialogMessage(getString(R.string.dialog_title_sign_up_success), email + " has been Confirmed", true);
            } else {
                // User is not confirmed
                confirmSignUp(cognitoUserCodeDeliveryDetails);
            }
        }

        @Override
        public void onFailure(Exception exception) {

            btnRegister.setEnabled(true);
            btnRegister.setAlpha(1f);
            txtRegisterBtn.setText(R.string.btn_register);
            progressBar.setVisibility(View.GONE);
            arrowImage.setVisibility(View.VISIBLE);

//            closeWaitDialog();
            exception.printStackTrace();
            showDialogMessage(getString(R.string.dialog_title_sign_up_failed), AppHelper.formatException(exception), false);
        }
    };

    private void enableRegisterButton() {

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            btnRegister.setEnabled(false);
            btnRegister.setAlpha(0.5f);
        } else {
            btnRegister.setEnabled(true);
            btnRegister.setAlpha(1f);
        }
    }

    private void confirmSignUp(CognitoUserCodeDeliveryDetails cognitoUserCodeDeliveryDetails) {

        Intent intent = new Intent(getActivity(), SignUpConfirmActivity.class);
        intent.putExtra("source", "signup");
        intent.putExtra("name", email);
        intent.putExtra("password", password);
        intent.putExtra("destination", cognitoUserCodeDeliveryDetails.getDestination());
        intent.putExtra("deliveryMed", cognitoUserCodeDeliveryDetails.getDeliveryMedium());
        intent.putExtra("attribute", cognitoUserCodeDeliveryDetails.getAttributeName());
        getActivity().startActivityForResult(intent, 11);
    }

    private void showDialogMessage(String title, String body, final boolean exit) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(title).setMessage(body).setNeutralButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    userDialog.dismiss();
                    if (exit) {
                        exit(email);
                    }
                } catch (Exception e) {
                    if (exit) {
                        exit(email);
                    }
                }
            }
        });
        userDialog = builder.create();
        userDialog.show();
    }

    private void exit(String uname) {
        exit(uname, null);
    }

    private void exit(String uname, String password) {

        if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(password)) {
            // We have the user details, so sign in!
            AppHelper.getPool().getUser(email).getSessionInBackground(authenticationHandler);
        } else {
            // TODO
        }
    }

    private void getUserAuthentication(AuthenticationContinuation continuation, String username) {

        if (username != null) {
            email = username;
            AppHelper.setUser(username);
        }

        if (this.password == null) {

            etEmail.setText(username);
            password = etPassword.getText().toString();

            if (TextUtils.isEmpty(password)) {

                layoutPassword.setError(getString(R.string.error_password_empty));
                return;
            }
        }

        AuthenticationDetails authenticationDetails = new AuthenticationDetails(this.email, password, null);
        continuation.setAuthenticationDetails(authenticationDetails);
        continuation.continueTask();
    }

    private void showDialogMessage(String title, String body) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
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
            ApiManager.getInstance(getActivity().getApplicationContext()).getTokenAndUserId();
            ((MainActivity) getActivity()).launchProvisioningApp();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String username) {
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

//            hideLoginLoading();
            showDialogMessage(getString(R.string.dialog_title_login_failed), AppHelper.formatException(e));
        }

        @Override
        public void authenticationChallenge(ChallengeContinuation continuation) {

            // Nothing to do for this app.
            /*
             * For Custom authentication challenge, implement your logic to present challenge to the
             * user and pass the user's responses to the continuation.
             */
        }
    };
}
