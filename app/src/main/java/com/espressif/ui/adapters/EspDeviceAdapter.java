package com.espressif.ui.adapters;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.activities.EspDeviceActivity;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class EspDeviceAdapter extends RecyclerView.Adapter<EspDeviceAdapter.MyViewHolder> {

    private Context context;
    private ApiManager apiManager;
    private ArrayList<EspDevice> deviceList;

    public EspDeviceAdapter(Context context, ArrayList<EspDevice> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
        apiManager = ApiManager.getInstance(context.getApplicationContext());
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_esp_new_device, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder myViewHolder, final int position) {

        final EspDevice device = deviceList.get(position);
        EspApplication espApp = (EspApplication) context.getApplicationContext();
        EspNode node = espApp.nodeMap.get(device.getNodeId());

        // set the data in items
        Log.e("TAG", "Device name : " + device.getDeviceName());
        Log.e("TAG", "Device Primary Param : " + device.getPrimaryParamName());

        boolean isParamTypeNameAvailable = false;

        for (int i = 0; i < device.getParams().size(); i++) {

            Param p = device.getParams().get(i);
            if (p != null && p.getParamType() != null && p.getParamType().equals(AppConstants.PARAM_TYPE_NAME)) {
                isParamTypeNameAvailable = true;
                myViewHolder.tvDeviceName.setText(p.getLabelValue());
                break;
            }
        }

        if (!isParamTypeNameAvailable) {
            myViewHolder.tvDeviceName.setText(device.getDeviceName());
        }

        if (TextUtils.isEmpty(device.getDeviceType())) {

            myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);

        } else {

            if (AppConstants.ESP_DEVICE_BULB.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_bulb);

            } else if (AppConstants.ESP_DEVICE_BULB_CCT.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_bulb_cct);

            } else if (AppConstants.ESP_DEVICE_BULB_RGB.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_bulb_rgb);

            } else if (AppConstants.ESP_DEVICE_SWITCH.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_switch);

            } else if (AppConstants.ESP_DEVICE_LOCK.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_lock);

            } else if (AppConstants.ESP_DEVICE_THERMOSTAT.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_thermostat);

            } else if (AppConstants.ESP_DEVICE_FAN.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_fan);

            } else if (AppConstants.ESP_DEVICE_SENSOR.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);

            } else if (AppConstants.ESP_DEVICE_TEMP_SENSOR.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);

            } else {
                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);
            }
        }

        if (!TextUtils.isEmpty(device.getPrimaryParamName())) {

            String paramName = device.getPrimaryParamName();
            boolean isParamFound = false;
            int paramIndex = -1;

            for (int i = 0; i < device.getParams().size(); i++) {

                Param p = device.getParams().get(i);
                if (p != null && paramName.equals(p.getName())) {
                    isParamFound = true;
                    paramIndex = i;
                    break;
                }
            }

            if (isParamFound) {

                final Param param = device.getParams().get(paramIndex);
                String dataType = param.getDataType();

                if (TextUtils.isEmpty(dataType)) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
                    myViewHolder.tvStringValue.setVisibility(View.GONE);
                    Log.e("TAG", "tvStringValue.setVisibility(View.GONE) 1");

                } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.VISIBLE);
                    myViewHolder.tvStringValue.setVisibility(View.GONE);
                    Log.e("TAG", "tvStringValue.setVisibility(View.GONE) 2");

                    final boolean isOn = param.getSwitchStatus();

                    if (isOn) {
                        myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                    } else {
                        myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                    }

                    if (param.getProperties().contains("write")) {

                        myViewHolder.ivDeviceStatus.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {

                                final boolean status = param.getSwitchStatus();
                                Log.e("TAG", "VALUE : " + status);

                                if (status) {
                                    myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                                } else {
                                    myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                                }

                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), !status);
                                body.add(device.getDeviceName(), jsonParam);

                                apiManager.setDynamicParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {
                                        param.setSwitchStatus(!status);
                                        Log.e("TAG", "VALUE updated : " + param.getSwitchStatus());
                                    }

                                    @Override
                                    public void onFailure(Exception exception) {
                                    }
                                });
                            }
                        });
                    }

                } else if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.VISIBLE);
                    myViewHolder.tvStringValue.setVisibility(View.GONE);
                    Log.e("TAG", "tvStringValue.setVisibility(View.GONE) 3");

                    String value = param.getLabelValue();
                    boolean isOn = false;

                    if (value != null && value.equalsIgnoreCase("true")) {
                        isOn = true;
                    }

                    if (isOn) {
                        myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                    } else {
                        myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                    }

                    if (param.getProperties().contains("write")) {

                        myViewHolder.ivDeviceStatus.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View v) {

                                String value = param.getLabelValue();
                                Log.e("TAG", "VALUE : " + value);
                                boolean finalIsOn = false;

                                if (value != null && value.equalsIgnoreCase("true")) {
                                    finalIsOn = true;
                                }

                                JsonObject jsonParam = new JsonObject();
                                JsonObject body = new JsonObject();

                                jsonParam.addProperty(param.getName(), !finalIsOn);
                                body.add(device.getDeviceName(), jsonParam);

                                final boolean finalIsOn1 = finalIsOn;
                                apiManager.setDynamicParamValue(device.getNodeId(), body, new ApiResponseListener() {

                                    @Override
                                    public void onSuccess(Bundle data) {

                                        if (finalIsOn1) {
                                            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_off);
                                            param.setLabelValue("false");
                                            Log.e("TAG", "VALUE updated : " + param.getLabelValue());
                                        } else {
                                            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_on);
                                            param.setLabelValue("true");
                                            Log.e("TAG", "VALUE updated : " + param.getLabelValue());
                                        }
                                    }

                                    @Override
                                    public void onFailure(Exception exception) {
                                        Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }

                } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
                    myViewHolder.tvStringValue.setVisibility(View.VISIBLE);
                    Log.e("TAG", "tvStringValue.setVisibility(View.VISIBLE) 4");
                    Log.e("TAG", "Value : " + param.getLabelValue());
                    myViewHolder.tvStringValue.setText(param.getLabelValue());

                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
                    myViewHolder.tvStringValue.setVisibility(View.VISIBLE);
                    myViewHolder.tvStringValue.setText(param.getLabelValue());
                    Log.e("TAG", "tvStringValue.setVisibility(View.VISIBLE) 5");

                } else if (dataType.equalsIgnoreCase("string")) {

                    myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
                    myViewHolder.tvStringValue.setVisibility(View.VISIBLE);
                    myViewHolder.tvStringValue.setText(param.getLabelValue());
                    Log.e("TAG", "tvStringValue.setVisibility(View.VISIBLE) 6");
                }
            } else {

                myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
                myViewHolder.tvStringValue.setVisibility(View.GONE);
                Log.e("TAG", "tvStringValue.setVisibility(View.GONE) 7");
            }
        } else {

            myViewHolder.ivDeviceStatus.setVisibility(View.GONE);
            myViewHolder.tvStringValue.setVisibility(View.GONE);
            Log.e("TAG", "tvStringValue.setVisibility(View.GONE) 8");
        }

        if (node != null && !node.isOnline()) {

            myViewHolder.llOffline.setVisibility(View.VISIBLE);
            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_output_disable);
            myViewHolder.ivDeviceStatus.setOnClickListener(null);

        } else {

            myViewHolder.llOffline.setVisibility(View.INVISIBLE);
        }

        // implement setOnClickListener event on item view.
        myViewHolder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, EspDeviceActivity.class);
                intent.putExtra(AppConstants.KEY_ESP_DEVICE, device);
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public void updateList(ArrayList<EspDevice> updatedDeviceList) {
        deviceList = updatedDeviceList;
        notifyDataSetChanged();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvDeviceName, tvStringValue;
        ImageView ivDevice, ivDeviceStatus;
        LinearLayout llOffline;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            ivDevice = itemView.findViewById(R.id.iv_device);
            llOffline = itemView.findViewById(R.id.ll_offline);
            ivDeviceStatus = itemView.findViewById(R.id.iv_on_off);
            tvStringValue = itemView.findViewById(R.id.tv_string);
        }
    }
}
