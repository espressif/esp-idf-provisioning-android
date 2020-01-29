package com.espressif.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
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
    private ContentLoadingProgressBar progressBarLogin;
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

    private void init(View view) {

        btnLogin = view.findViewById(R.id.btn_login);
        btnLoginWithGitHub = view.findViewById(R.id.btn_login_with_github);

        txtLoginBtn = btnLogin.findViewById(R.id.text_btn);
        arrowImageLogin = btnLogin.findViewById(R.id.iv_arrow);
        progressBarLogin = btnLogin.findViewById(R.id.progress_indicator);

        txtLoginWithGitHubBtn = btnLoginWithGitHub.findViewById(R.id.text_btn);
        imageLoginWithGitHub = btnLoginWithGitHub.findViewById(R.id.iv_arrow);

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

        CognitoUser user = AppHelper.getPool().getCurrentUser();
        email = user.getUserId();

        if (email != null) {
            AppHelper.setUser(email);
            etEmail.setText(user.getUserId());
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

    View.OnClickListener loginBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            signInUser();
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
