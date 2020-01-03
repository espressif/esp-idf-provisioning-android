package com.espressif.ui.activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.adapters.DynamicParamAdapter;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.EspDevice;

import java.util.ArrayList;

public class EspDeviceActivity extends AppCompatActivity {

    private Button btnRemove;
    private RecyclerView recyclerView;
    private ProgressDialog progressDialog;
    private SwipeRefreshLayout swipeRefreshLayout;

    private EspDevice espDevice;
    private EspApplication espApp;
    private ApiManager apiManager;
    private DynamicParamAdapter adapter;
    private ArrayList<Param> params;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_device);

        espApp = (EspApplication) getApplicationContext();
        apiManager = new ApiManager(getApplicationContext());
        espDevice = getIntent().getParcelableExtra(AppConstants.KEY_ESP_DEVICE);
        params = espDevice.getParams();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(espDevice.getDeviceName());
        setSupportActionBar(toolbar);

        initViews();

        showWaitDialog("Getting values...");
        getValues();
    }

    private View.OnClickListener removeDeviceBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            showWaitDialog("Removing device...");
            apiManager.removeDevice(espDevice.getNodeId(), new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    closeWaitDialog();
                    finish();
                }

                @Override
                public void onFailure(Exception exception) {
                    closeWaitDialog();
                    exception.printStackTrace();
                    Toast.makeText(EspDeviceActivity.this, "Failed to delete device", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private void initViews() {

        btnRemove = findViewById(R.id.btn_remove_device);
        recyclerView = findViewById(R.id.rv_dynamic_param_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);

        btnRemove.setOnClickListener(removeDeviceBtnClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        adapter = new DynamicParamAdapter(this, espDevice.getNodeId(), espDevice.getDeviceName(), params);
        recyclerView.setAdapter(adapter);

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
                        params = devices.get(i).getParams();
                        adapter.updateList(params);
                        adapter.notifyDataSetChanged();
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
