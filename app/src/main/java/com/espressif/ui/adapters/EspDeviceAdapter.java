package com.espressif.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.espressif.provision.R;
import com.espressif.ui.user_module.EspDevice;

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
        View v = layoutInflater.inflate(R.layout.item_esp_device, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

        EspDevice device = deviceList.get(position);
//        MyViewHolder myViewHolder = (MyViewHolder) holder;

        // set the data in items
        Log.e("TAG", "Device name : " + device.getDeviceName());
        myViewHolder.tvDeviceName.setText(device.getDeviceName());

        if (device.isOnline()) {
            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_status_online);
        } else {
            myViewHolder.ivDeviceStatus.setImageResource(R.drawable.ic_status_offline);
        }

        // implement setOnClickListener event on item view.
//        holder.itemView.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
        // display a toast with person name on item click
//                Toast.makeText(context, personNames.get(position), Toast.LENGTH_SHORT).show();
//            }
//        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public void updateList(ArrayList<EspDevice> updatedDeviceList) {
        deviceList = updatedDeviceList;
//        notifyDataSetChanged();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvDeviceName;
        ImageView ivDeviceStatus;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvDeviceName = itemView.findViewById(R.id.tv_device_name);
            ivDeviceStatus = itemView.findViewById(R.id.iv_device_status);
        }
    }
}
