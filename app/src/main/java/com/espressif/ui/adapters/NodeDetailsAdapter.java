package com.espressif.ui.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.provision.R;

import java.util.ArrayList;

public class NodeDetailsAdapter extends RecyclerView.Adapter<NodeDetailsAdapter.MyViewHolder> {

    private Context context;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;

    public NodeDetailsAdapter(Context context, ArrayList<String> nodeInfoList, ArrayList<String> nodeValueList) {
        this.context = context;
        this.nodeInfoList = nodeInfoList;
        this.nodeInfoValueList = nodeValueList;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_node_info, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

//        MyViewHolder myViewHolder = (MyViewHolder) holder;

        // set the data in items
        myViewHolder.tvNodeInfoLabel.setText(nodeInfoList.get(position));

        if (!TextUtils.isEmpty(nodeInfoValueList.get(position))) {

            myViewHolder.tvNodeInfoValue.setText(nodeInfoValueList.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return nodeInfoList.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvNodeInfoLabel, tvNodeInfoValue;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvNodeInfoLabel = itemView.findViewById(R.id.tv_node_label);
            tvNodeInfoValue = itemView.findViewById(R.id.tv_node_value);
        }
    }
}
