package com.espressif.ui.adapters;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.provision.R;
import com.espressif.ui.activities.EspDeviceActivity;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.EspNode;

import java.util.ArrayList;

public class EspDeviceAdapter extends RecyclerView.Adapter<EspDeviceAdapter.MyViewHolder> {

    private Context context;
    private ArrayList<EspDevice> deviceList;

    public EspDeviceAdapter(Context context, ArrayList<EspDevice> deviceList) {
        this.context = context;
        this.deviceList = deviceList;
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
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

        final EspDevice device = deviceList.get(position);
//        MyViewHolder myViewHolder = (MyViewHolder) holder;

        // set the data in items
        Log.e("TAG", "Device name : " + device.getDeviceName());
        Log.e("TAG", "Device type : " + device.getDeviceType());
        myViewHolder.tvDeviceName.setText(device.getDeviceName());

        if (TextUtils.isEmpty(device.getDeviceType())) {

            myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);

        } else {

            if (AppConstants.ESP_DEVICE_BULB.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_light_bulb);

            } else if (AppConstants.ESP_DEVICE_SWITCH.equals(device.getDeviceType())) {

                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device_switch);

            } else {
                myViewHolder.ivDevice.setImageResource(R.drawable.ic_device);
            }
        }

        EspApplication espApp = (EspApplication) context.getApplicationContext();
        EspNode node = espApp.nodeMap.get(device.getNodeId());

        if (node != null && !node.isOnline()) {

            myViewHolder.llOffline.setVisibility(View.VISIBLE);
            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_device_offline);

        } else {

            myViewHolder.llOffline.setVisibility(View.INVISIBLE);
            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_device_on);
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
        TextView tvDeviceName;
        ImageView ivDevice, ivDeviceStatus;
        LinearLayout llOffline;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            ivDevice = itemView.findViewById(R.id.iv_device);
            llOffline = itemView.findViewById(R.id.ll_offline);
            ivDeviceStatus = itemView.findViewById(R.id.iv_on_off);
        }
    }
}
