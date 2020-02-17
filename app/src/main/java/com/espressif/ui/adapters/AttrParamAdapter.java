package com.espressif.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.provision.R;
import com.espressif.ui.models.Param;

import java.util.ArrayList;

public class AttrParamAdapter extends RecyclerView.Adapter<AttrParamAdapter.MyViewHolder> {

    private final String TAG = AttrParamAdapter.class.getSimpleName();

    private Context context;
    private ArrayList<Param> params;

    public AttrParamAdapter(Context context, String nodeId, String deviceName, ArrayList<Param> deviceList) {
        this.context = context;
        this.params = deviceList;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);

        View v = layoutInflater.inflate(R.layout.item_attribute_param, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder myViewHolder, final int position) {

        Log.e(TAG, "onBindViewHolder for position : " + position);
        final Param param = params.get(position);
        Log.e(TAG, "param.getName : " + param.getName());
        Log.e(TAG, "param.getLabelValue : " + param.getLabelValue());

        myViewHolder.tvAttrName.setText(param.getName());
        myViewHolder.tvAttrValue.setText(param.getLabelValue());
    }

    @Override
    public int getItemCount() {
        return params.size();
    }

    public void updateList(ArrayList<Param> updatedDeviceList) {
        params = updatedDeviceList;
        notifyDataSetChanged();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvAttrName, tvAttrValue;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvAttrName = itemView.findViewById(R.id.tv_attr_name);
            tvAttrValue = itemView.findViewById(R.id.tv_attr_value);
        }
    }
}
