package com.espressif.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.provision.R;
import com.espressif.ui.adapters.UserProfileAdapter;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.ChangePasswordActivity;

import java.util.ArrayList;

public class UserProfileActivity extends AppCompatActivity {

    private UserProfileAdapter termsInfoAdapter;
    private ArrayList<String> termsInfoList;

    private TextView tvTitle, tvBack, tvCancel;
    private TextView tvAppVersion;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        overridePendingTransition(R.anim.enter_anim, R.anim.exit_anim);

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        initViews();
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_user_profile);
        tvBack.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.GONE);

        RecyclerView userInfoView = findViewById(R.id.rv_user_info);
        RecyclerView termsInfoView = findViewById(R.id.rv_terms);
        CardView logoutView = findViewById(R.id.card_view_logout);
        tvAppVersion = findViewById(R.id.tv_app_version);

        String version = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String appVersion = "App Version - v" + version;
        tvAppVersion.setText(appVersion);

        tvBack.setOnClickListener(backButtonClickListener);
        logoutView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (!ApiManager.isOAuthLogin) {
                    String username = AppHelper.getCurrUser();
                    Log.e("TAG", "User name : " + username);
                    CognitoUser user = AppHelper.getPool().getUser(username);
                    user.signOut();
                }

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.apply();

                Intent loginActivity = new Intent(getApplicationContext(), MainActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginActivity);
                finish();
            }
        });

        LinearLayoutManager llm1 = new LinearLayoutManager(getApplicationContext());
        llm1.setOrientation(RecyclerView.VERTICAL);
        userInfoView.setLayoutManager(llm1); // set LayoutManager to RecyclerView

        LinearLayoutManager llm2 = new LinearLayoutManager(getApplicationContext());
        llm2.setOrientation(RecyclerView.VERTICAL);
        termsInfoView.setLayoutManager(llm2); // set LayoutManager to RecyclerView

        ArrayList<String> userInfoList = new ArrayList<>();
        userInfoList.add(getString(R.string.hint_email));
        ArrayList<String> userInfoValues = new ArrayList<>();
        userInfoValues.add(sharedPreferences.getString(AppConstants.KEY_EMAIL, ""));
        UserProfileAdapter userInfoAdapter = new UserProfileAdapter(this, userInfoList, userInfoValues, true);
        userInfoView.setAdapter(userInfoAdapter);

        termsInfoList = new ArrayList<>();

        if (!ApiManager.isOAuthLogin) {
            termsInfoList.add(getString(R.string.title_activity_change_password));
        }
        termsInfoList.add(getString(R.string.documentation));
        termsInfoList.add(getString(R.string.privacy_policy));
        termsInfoList.add(getString(R.string.terms_of_use));
        termsInfoAdapter = new UserProfileAdapter(this, termsInfoList, null, false);
        termsInfoView.setAdapter(termsInfoAdapter);
        termsInfoAdapter.setOnItemClickListener(onItemClickListener);
    }

    private View.OnClickListener onItemClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {

            RecyclerView.ViewHolder viewHolder = (RecyclerView.ViewHolder) view.getTag();
            int position = viewHolder.getAdapterPosition();
            String str = termsInfoList.get(position);

            if (str.equals(getString(R.string.title_activity_change_password))) {

                startActivity(new Intent(UserProfileActivity.this, ChangePasswordActivity.class));

            } else if (str.equals(getString(R.string.documentation))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.DOCUMENTATION_URL));
                startActivity(openURL);

            } else if (str.equals(getString(R.string.privacy_policy))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.PRIVACY_URL));
                startActivity(openURL);

            } else if (str.equals(getString(R.string.terms_of_use))) {

                Intent openURL = new Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.TERMS_URL));
                startActivity(openURL);
            }
        }
    };
}
