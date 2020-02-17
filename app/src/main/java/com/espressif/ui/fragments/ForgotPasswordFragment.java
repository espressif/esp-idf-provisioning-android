package com.espressif.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;

import com.espressif.provision.R;
import com.espressif.ui.Utils;
import com.espressif.ui.user_module.ForgotPasswordActivity;

public class ForgotPasswordFragment extends Fragment {

    private EditText etEmail;
    private CardView btnResetPassword;
    private TextView txtResetPasswordBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;
    private String email;

    public ForgotPasswordFragment() {
        // Required empty public constructor
    }

    public static ForgotPasswordFragment newInstance() {
        return new ForgotPasswordFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_forgot_password, container, false);
        Bundle extras = getArguments();
        init(rootView, extras);
        return rootView;
    }

    private View.OnClickListener resetPasswordBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            resetPassword();
        }
    };

    private void init(View view, Bundle extras) {

        etEmail = view.findViewById(R.id.et_email);
        btnResetPassword = view.findViewById(R.id.btn_reset_password);
        txtResetPasswordBtn = view.findViewById(R.id.text_btn);
        arrowImage = view.findViewById(R.id.iv_arrow);
        progressBar = view.findViewById(R.id.progress_indicator);

        if (extras != null) {
            if (extras.containsKey("email")) {
                this.email = extras.getString("email");
                etEmail.setText(email);
            }
        }

        txtResetPasswordBtn.setText(R.string.btn_reset_password);
        btnResetPassword.setOnClickListener(resetPasswordBtnClickListener);
    }

    private void resetPassword() {

        email = etEmail.getText().toString();
        if (TextUtils.isEmpty(email)) {

            etEmail.setError(getString(R.string.error_email_empty));
            return;

        } else if (!Utils.isValidEmail(email)) {

            etEmail.setError(getString(R.string.error_invalid_email));
            return;
        }

        showLoading();
        ((ForgotPasswordActivity) getActivity()).forgotPassword(email);
    }

    private void showLoading() {

        btnResetPassword.setEnabled(false);
        btnResetPassword.setAlpha(0.5f);
        txtResetPasswordBtn.setText(R.string.btn_resetting_password);
        progressBar.setVisibility(View.VISIBLE);
        arrowImage.setVisibility(View.GONE);
    }

    public void hideLoading() {

        btnResetPassword.setEnabled(true);
        btnResetPassword.setAlpha(1f);
        txtResetPasswordBtn.setText(R.string.btn_reset_password);
        progressBar.setVisibility(View.GONE);
        arrowImage.setVisibility(View.VISIBLE);
    }
}
