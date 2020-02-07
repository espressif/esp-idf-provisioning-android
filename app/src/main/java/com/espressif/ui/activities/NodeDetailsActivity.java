package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.adapters.NodeDetailsAdapter;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;

import java.util.ArrayList;

public class NodeDetailsActivity extends AppCompatActivity {

    private EspNode node;
    private EspApplication espApp;
    private ApiManager apiManager;
    private NodeDetailsAdapter nodeDetailsAdapter;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnRemoveDevice;
    private TextView txtRemoveDeviceBtn;
    private ImageView removeDeviceImage;
    private RecyclerView nodeInfoRecyclerView;
    private ContentLoadingProgressBar progressBar;
    private AlertDialog userDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_details);

        nodeInfoList = new ArrayList<>();
        nodeInfoValueList = new ArrayList<>();
        espApp = (EspApplication) getApplicationContext();
        apiManager = new ApiManager(getApplicationContext());
        String nodeId = getIntent().getStringExtra(AppConstants.KEY_NODE_ID);
        node = espApp.nodeMap.get(nodeId);
        setNodeInfo();

        initViews();
    }

    private View.OnClickListener removeDeviceBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            confirmForRemoveNode();
        }
    };

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

        tvTitle.setText(R.string.title_activity_node_details);
        tvBack.setVisibility(View.VISIBLE);
        tvCancel.setVisibility(View.GONE);

        nodeInfoRecyclerView = findViewById(R.id.rv_node_details_list);
        btnRemoveDevice = findViewById(R.id.btn_remove);
        txtRemoveDeviceBtn = findViewById(R.id.text_btn);
        removeDeviceImage = findViewById(R.id.iv_remove);
        progressBar = findViewById(R.id.progress_indicator);

        tvBack.setOnClickListener(backButtonClickListener);
        btnRemoveDevice.setOnClickListener(removeDeviceBtnClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(RecyclerView.VERTICAL);
        nodeInfoRecyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        nodeDetailsAdapter = new NodeDetailsAdapter(this, nodeInfoList, nodeInfoValueList);
        nodeInfoRecyclerView.setAdapter(nodeDetailsAdapter);
    }

    private void setNodeInfo() {

        nodeInfoList.add(getString(R.string.node_id));
        nodeInfoList.add(getString(R.string.node_name));
        nodeInfoList.add(getString(R.string.node_type));
        nodeInfoList.add(getString(R.string.node_fw_version));
        nodeInfoList.add(getString(R.string.node_config_version));

        nodeInfoValueList.add(node.getNodeId());
        nodeInfoValueList.add(node.getNodeName());
        nodeInfoValueList.add(node.getNodeType());
        nodeInfoValueList.add(node.getFwVersion());
        nodeInfoValueList.add(node.getConfigVersion());

        ArrayList<Param> attributes = node.getAttributes();

        if (attributes != null && attributes.size() > 0) {

            for (int i = 0; i < attributes.size(); i++) {

                Param param = attributes.get(i);
                nodeInfoList.add(param.getName());
                nodeInfoValueList.add(param.getLabelValue());
            }
        }
    }

    private void showLoading() {

        btnRemoveDevice.setEnabled(false);
        btnRemoveDevice.setAlpha(0.5f);
        txtRemoveDeviceBtn.setText(R.string.btn_removing);
        progressBar.setVisibility(View.VISIBLE);
        removeDeviceImage.setVisibility(View.GONE);
    }

    public void hideLoading() {

        btnRemoveDevice.setEnabled(true);
        btnRemoveDevice.setAlpha(1f);
        txtRemoveDeviceBtn.setText(R.string.btn_remove);
        progressBar.setVisibility(View.GONE);
        removeDeviceImage.setVisibility(View.VISIBLE);
    }

    private void confirmForRemoveNode() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure to delete this node?");

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
                removeDevice();
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

    private void removeDevice() {

        showLoading();
        apiManager.removeDevice(node.getNodeId(), new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                hideLoading();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Exception exception) {
                hideLoading();
                exception.printStackTrace();
                Toast.makeText(NodeDetailsActivity.this, "Failed to delete device", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
