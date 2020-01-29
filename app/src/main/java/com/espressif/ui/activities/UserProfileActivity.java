package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUser;
import com.espressif.cloudapi.ApiManager;
import com.espressif.provision.R;
import com.espressif.ui.adapters.UserProfileAdapter;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.ChangePasswordActivity;

import java.util.ArrayList;

public class UserProfileActivity extends AppCompatActivity {

    private UserProfileAdapter nodeDetailsAdapter;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;

    private TextView tvTitle, tvBack, tvCancel;
    private RecyclerView nodeInfoRecyclerView;
    private AlertDialog userDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_details);

        nodeInfoList = new ArrayList<>();
        nodeInfoValueList = new ArrayList<>();
        setNodeInfo();
        initViews();
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.e("TAG", "On Back button click");
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

        nodeInfoRecyclerView = findViewById(R.id.rv_node_details_list);
        findViewById(R.id.iv_provisioning).setVisibility(View.GONE);
        findViewById(R.id.btn_remove).setVisibility(View.GONE);
        findViewById(R.id.tv_txt_remove).setVisibility(View.GONE);

        tvBack.setOnClickListener(backButtonClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        nodeInfoRecyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        nodeDetailsAdapter = new UserProfileAdapter(this, nodeInfoList, nodeInfoValueList);
        nodeInfoRecyclerView.setAdapter(nodeDetailsAdapter);
        nodeDetailsAdapter.setOnItemClickListener(onItemClickListener);
    }

    private void setNodeInfo() {

        nodeInfoList.add("Email");
        nodeInfoList.add(getString(R.string.title_activity_change_password));
        nodeInfoList.add("Logout");

        nodeInfoValueList.add(ApiManager.userName);
    }

    private View.OnClickListener onItemClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {

            RecyclerView.ViewHolder viewHolder = (RecyclerView.ViewHolder) view.getTag();
            int position = viewHolder.getAdapterPosition();
            String str = nodeInfoList.get(position);

            if (str.equals(getString(R.string.title_activity_change_password))) {

                startActivity(new Intent(UserProfileActivity.this, ChangePasswordActivity.class));

            } else if (str.equals("Logout")) {

                String username = AppHelper.getCurrUser();
                Log.e("TAG", "User name : " + username);
                CognitoUser user = AppHelper.getPool().getUser(username);
                user.signOut();
                Intent loginActivity = new Intent(getApplicationContext(), MainActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(loginActivity);
                finish();
            }
        }
    };


    private void showLoading() {

//        btnRemoveDevice.setEnabled(false);
//        btnRemoveDevice.setAlpha(0.5f);
//        txtRemoveDeviceBtn.setText(R.string.btn_removing);
//        progressBar.setVisibility(View.VISIBLE);
//        removeDeviceImage.setVisibility(View.GONE);
    }

    public void hideLoading() {

//        btnRemoveDevice.setEnabled(true);
//        btnRemoveDevice.setAlpha(1f);
//        txtRemoveDeviceBtn.setText(R.string.btn_remove);
//        progressBar.setVisibility(View.GONE);
//        removeDeviceImage.setVisibility(View.VISIBLE);
    }

    private void confirmForRemoveNode() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure to delete this node?");

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
//                removeDevice();
            }
        });

        builder.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        userDialog = builder.create();
        userDialog.show();
    }
}

