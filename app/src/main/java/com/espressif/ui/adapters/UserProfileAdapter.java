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

public class UserProfileAdapter extends RecyclerView.Adapter<UserProfileAdapter.MyViewHolder> {

    private Context context;
    private ArrayList<String> nodeInfoList;
    private ArrayList<String> nodeInfoValueList;
    private View.OnClickListener mOnItemClickListener;

    public UserProfileAdapter(Context context, ArrayList<String> nodeInfoList, ArrayList<String> nodeValueList) {
        this.context = context;
        this.nodeInfoList = nodeInfoList;
        this.nodeInfoValueList = nodeValueList;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_user_profile, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

        if (nodeInfoList.get(position).equals("Email") && !TextUtils.isEmpty(nodeInfoValueList.get(position))) {

            myViewHolder.tvNodeInfoLabel.setText(nodeInfoList.get(position));
            myViewHolder.tvNodeInfoValue.setText(nodeInfoValueList.get(position));

        } else if (nodeInfoList.get(position).equals(context.getString(R.string.title_activity_change_password))) {

            myViewHolder.tvNodeInfoLabel.setVisibility(View.GONE);
            myViewHolder.tvNodeInfoValue.setText(context.getString(R.string.title_activity_change_password));
            myViewHolder.tvNodeInfoValue.setTextColor(context.getResources().getColor(R.color.colorPrimary));

        } else if (nodeInfoList.get(position).equals("Logout")) {

            myViewHolder.tvNodeInfoLabel.setVisibility(View.GONE);
            myViewHolder.tvNodeInfoValue.setText("Logout");
            myViewHolder.tvNodeInfoValue.setTextColor(context.getResources().getColor(R.color.color_orange));
        }
    }

    @Override
    public int getItemCount() {
        return nodeInfoList.size();
    }

    public void setOnItemClickListener(View.OnClickListener itemClickListener) {
        mOnItemClickListener = itemClickListener;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvNodeInfoLabel, tvNodeInfoValue;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            tvNodeInfoLabel = itemView.findViewById(R.id.tv_node_label);
            tvNodeInfoValue = itemView.findViewById(R.id.tv_node_value);
            itemView.setTag(this);
            itemView.setOnClickListener(mOnItemClickListener);
        }
    }
}
