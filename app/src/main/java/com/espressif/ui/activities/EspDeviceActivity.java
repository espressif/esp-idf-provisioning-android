package com.espressif.ui.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.adapters.AttrParamAdapter;
import com.espressif.ui.adapters.DynamicParamAdapter;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.Param;

import java.util.ArrayList;

public class EspDeviceActivity extends AppCompatActivity {

    private static final int NODE_DETAILS_ACTIVITY_REQUEST = 10;

    private TextView tvTitle, tvBack, tvNoParam;
    private ImageView ivNodeInfo;
    private RecyclerView paramRecyclerView;
    private RecyclerView attrRecyclerView;
    private ProgressDialog progressDialog;
    private SwipeRefreshLayout swipeRefreshLayout;

    private EspDevice espDevice;
    private EspApplication espApp;
    private ApiManager apiManager;
    private DynamicParamAdapter paramAdapter;
    private AttrParamAdapter attrAdapter;
    private ArrayList<Param> paramList;
    private ArrayList<Param> attributeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_device);

        espApp = (EspApplication) getApplicationContext();
        apiManager = new ApiManager(getApplicationContext());
        espDevice = getIntent().getParcelableExtra(AppConstants.KEY_ESP_DEVICE);

        ArrayList<Param> espDeviceParams = espDevice.getParams();
        setParamList(espDeviceParams);

        initViews();

        showWaitDialog("Getting values...");
        getValues();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NODE_DETAILS_ACTIVITY_REQUEST && resultCode == RESULT_OK) {
            finish();
        }
    }

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener infoBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Intent intent = new Intent(EspDeviceActivity.this, NodeDetailsActivity.class);
            intent.putExtra(AppConstants.KEY_NODE_ID, espDevice.getNodeId());
            startActivityForResult(intent, NODE_DETAILS_ACTIVITY_REQUEST);
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.esp_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvNoParam = findViewById(R.id.tv_no_params);
        ivNodeInfo = findViewById(R.id.btn_info);

        tvTitle.setText(espDevice.getDeviceName());
        tvBack.setVisibility(View.VISIBLE);

        paramRecyclerView = findViewById(R.id.rv_dynamic_param_list);
        attrRecyclerView = findViewById(R.id.rv_static_param_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);

        tvBack.setOnClickListener(backButtonClickListener);
        ivNodeInfo.setOnClickListener(infoBtnClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        paramRecyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        LinearLayoutManager linearLayoutManager1 = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager1.setOrientation(RecyclerView.VERTICAL);
        attrRecyclerView.setLayoutManager(linearLayoutManager1); // set LayoutManager to RecyclerView

        paramAdapter = new DynamicParamAdapter(this, espDevice.getNodeId(), espDevice.getDeviceName(), paramList);
        paramRecyclerView.setAdapter(paramAdapter);

        attrAdapter = new AttrParamAdapter(this, espDevice.getNodeId(), espDevice.getDeviceName(), attributeList);
        attrRecyclerView.setAdapter(attrAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getValues();
            }
        });
    }

    private void getValues() {

        apiManager.getDynamicParamsValue(espDevice.getNodeId(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                Log.e("TAG", "Get values success");
                closeWaitDialog();
                swipeRefreshLayout.setRefreshing(false);
                ArrayList<EspDevice> devices = espApp.nodeMap.get(espDevice.getNodeId()).getDevices();

                for (int i = 0; i < devices.size(); i++) {

                    if (espDevice.getDeviceName().equals(devices.get(i).getDeviceName())) {

                        espDevice = devices.get(i);
                        ArrayList<Param> espDeviceParams = espDevice.getParams();
                        setParamList(espDeviceParams);
                        updateUi();
                        break;
                    }
                }
            }

            @Override
            public void onFailure(Exception exception) {

                exception.printStackTrace();
                closeWaitDialog();
                Toast.makeText(EspDeviceActivity.this, "Failed to get values", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setParamList(ArrayList<Param> paramArrayList) {

        if (paramList == null || attributeList == null) {

            paramList = new ArrayList<>();
            attributeList = new ArrayList<>();
        }
        paramList.clear();
        attributeList.clear();

        for (int i = 0; i < paramArrayList.size(); i++) {

            if (paramArrayList.get(i).isDynamicParam()) {

//                if (espDevice.getDeviceName().equalsIgnoreCase("Integers")) {
//
//                    if (paramArrayList.get(i).getName().equalsIgnoreCase("with-ui-writeonly")
//                            || paramArrayList.get(i).getName().equalsIgnoreCase("with-ui-bounds-readonly")) {
//                        paramList.add(paramArrayList.get(i));
//                    }
//
//                } else {
                paramList.add(paramArrayList.get(i));
//                }

            } else {
                attributeList.add(paramArrayList.get(i));
            }
        }
    }

    private void updateUi() {

        paramAdapter.updateList(paramList);
        paramAdapter.notifyDataSetChanged();

        attrAdapter.updateList(attributeList);
        attrAdapter.notifyDataSetChanged();

        if (paramList.size() <= 0 && attributeList.size() <= 0) {

            tvNoParam.setVisibility(View.VISIBLE);
            paramRecyclerView.setVisibility(View.GONE);
            attrRecyclerView.setVisibility(View.GONE);

        } else {

            tvNoParam.setVisibility(View.GONE);
            paramRecyclerView.setVisibility(View.VISIBLE);
            attrRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showWaitDialog(String message) {

        closeWaitDialog();

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
        }
        progressDialog.setTitle(message);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void closeWaitDialog() {
        try {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
