package com.espressif.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.Utils;
import com.espressif.ui.activities.MainActivity;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.ForgotPasswordActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginFragment extends Fragment {

    private static final String TAG = LoginFragment.class.getSimpleName();

    private EditText etEmail;
    private TextInputEditText etPassword;
    private TextInputLayout layoutPassword;
    private CardView btnLogin, btnLoginWithGitHub;
    private TextView txtLoginBtn, txtLoginWithGitHubBtn;
    private ImageView arrowImageLogin, imageLoginWithGitHub;
    private ContentLoadingProgressBar progressBarLogin, progressBarLoginGitHub;
    private TextView tvForgotPassword;
    private TextView linkDoc, linkPrivacy, linkTerms;

    private String email, password;

    public LoginFragment() {
        // Required empty public constructor
    }

    public static LoginFragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_login, container, false);
        init(root);
        findCurrent();
        return root;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e(TAG, "ON ACTIVITY RESULT");
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent activityIntent = getActivity().getIntent();

        if (activityIntent.getData() != null && activityIntent.getData().toString().contains(AppConstants.REDIRECT_URI)) {

            showGitHubLoginLoading();
            Log.e(TAG, "Data : " + activityIntent.getData().toString());
            String code = activityIntent.getData().toString().replace(AppConstants.REDIRECT_URI, "");
            code = code.replace("?code=", "");
            Log.e(TAG, "Code : " + code);

            ApiManager apiManager = ApiManager.getInstance(getActivity().getApplicationContext());
            apiManager.loginGithub(code, new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    hideGitHubLoginLoading();
                    ((MainActivity) getActivity()).launchProvisioningApp();
                }

                @Override
                public void onFailure(Exception exception) {
                    hideGitHubLoginLoading();
                    Toast.makeText(getActivity(), "Fail to login with GitHub", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            Log.e(TAG, "Data is null");
        }
    }

    private void init(View view) {

        btnLogin = view.findViewById(R.id.btn_login);
        btnLoginWithGitHub = view.findViewById(R.id.btn_login_with_github);

        txtLoginBtn = btnLogin.findViewById(R.id.text_btn);
        arrowImageLogin = btnLogin.findViewById(R.id.iv_arrow);
        progressBarLogin = btnLogin.findViewById(R.id.progress_indicator);

        txtLoginWithGitHubBtn = btnLoginWithGitHub.findViewById(R.id.text_btn);
        imageLoginWithGitHub = btnLoginWithGitHub.findViewById(R.id.iv_arrow);
        progressBarLoginGitHub = btnLoginWithGitHub.findViewById(R.id.progress_indicator);

        txtLoginBtn.setText(R.string.btn_login);
        txtLoginWithGitHubBtn.setVisibility(View.GONE);
        imageLoginWithGitHub.setImageResource(R.drawable.ic_github);
        LinearLayout ll = btnLoginWithGitHub.findViewById(R.id.layout_btn);
        ll.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));

        etEmail = view.findViewById(R.id.et_email);
        layoutPassword = view.findViewById(R.id.layout_password);
        etPassword = view.findViewById(R.id.et_password);
        tvForgotPassword = view.findViewById(R.id.tv_forgot_password);
        linkDoc = view.findViewById(R.id.tv_documentation);
        linkPrivacy = view.findViewById(R.id.tv_privacy);
        linkTerms = view.findViewById(R.id.tv_terms_condition);
        linkDoc.setMovementMethod(LinkMovementMethod.getInstance());
        linkPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
        linkTerms.setMovementMethod(LinkMovementMethod.getInstance());

        btnLogin.setOnClickListener(loginBtnClickListener);
        btnLoginWithGitHub.setOnClickListener(githubLoginBtnClickListener);
        tvForgotPassword.setOnClickListener(forgotPasswordClickListener);

        etPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                if (actionId == EditorInfo.IME_ACTION_GO) {
                    signInUser();
                }
                return false;
            }
        });
    }

    private void findCurrent() {

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        ApiManager.isGitHubLogin = sharedPreferences.getBoolean(AppConstants.KEY_IS_GITHUB_LOGIN, false);

        if (ApiManager.isGitHubLogin) {

            showGitHubLoginLoading();
            ApiManager apiManager = ApiManager.getInstance(getActivity().getApplicationContext());

            if (apiManager.isTokenExpired()) {

                apiManager.getNewToken();
            } else {
                hideGitHubLoginLoading();
                ((MainActivity) getActivity()).launchProvisioningApp();
            }

        } else {

            CognitoUser user = AppHelper.getPool().getCurrentUser();
            email = user.getUserId();

            if (email != null) {
                AppHelper.setUser(email);
                etEmail.setText(user.getUserId());
            }
        }
    }

    private void signInUser() {

        email = etEmail.getText().toString();
        password = etPassword.getText().toString();

        etEmail.setError(null);
        layoutPassword.setError(null);

        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;
        } else if (!Utils.isValidEmail(email)) {

            etEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        AppHelper.setUser(email);

        if (TextUtils.isEmpty(password)) {

            layoutPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        showLoginLoading();
        ((MainActivity) getActivity()).signInUser(email, password);
    }

    public void updateUi(String newUserEmail, String newUserPassword) {

        etEmail.setText(newUserEmail);
        etPassword.setText(newUserPassword);
        email = etEmail.getText().toString();
        password = etPassword.getText().toString();
        etEmail.setError(null);
        layoutPassword.setError(null);

        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;
        } else if (!Utils.isValidEmail(email)) {

            etEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        AppHelper.setUser(email);

        if (TextUtils.isEmpty(password)) {

            layoutPassword.setError(getString(R.string.error_password_empty));
            return;
        }

        showLoginLoading();
        ((MainActivity) getActivity()).signInUser(email, password);
    }

    public void showLoginLoading() {

        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.5f);
        txtLoginBtn.setText(R.string.btn_signing_in);
        progressBarLogin.setVisibility(View.VISIBLE);
        arrowImageLogin.setVisibility(View.GONE);
    }

    public void hideLoginLoading() {

        btnLogin.setEnabled(true);
        btnLogin.setAlpha(1f);
        txtLoginBtn.setText(R.string.btn_login);
        progressBarLogin.setVisibility(View.GONE);
        arrowImageLogin.setVisibility(View.VISIBLE);
    }

    public void showGitHubLoginLoading() {

        btnLoginWithGitHub.setEnabled(false);
        btnLoginWithGitHub.setAlpha(0.5f);
        progressBarLoginGitHub.setVisibility(View.VISIBLE);
    }

    public void hideGitHubLoginLoading() {

        btnLoginWithGitHub.setEnabled(true);
        btnLoginWithGitHub.setAlpha(1f);
        progressBarLoginGitHub.setVisibility(View.GONE);
    }

    View.OnClickListener loginBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            signInUser();
        }
    };

    View.OnClickListener githubLoginBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

//            Uri uri = Uri.parse(AppConstants.GITHUB_PROD);
            Uri uri = Uri.parse(AppConstants.GITHUB_STAGING);
            Intent openURL = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(openURL);
        }
    };

    View.OnClickListener forgotPasswordClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Intent intent = new Intent(getActivity(), ForgotPasswordActivity.class);
            startActivity(intent);
        }
    };
}
