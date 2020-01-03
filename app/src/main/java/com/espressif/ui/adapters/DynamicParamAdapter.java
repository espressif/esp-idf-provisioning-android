package com.espressif.ui.adapters;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.models.Param;
import com.warkiz.tickseekbar.OnSeekChangeListener;
import com.warkiz.tickseekbar.SeekParams;
import com.warkiz.tickseekbar.TickSeekBar;

import java.util.ArrayList;
import java.util.HashMap;

public class DynamicParamAdapter extends RecyclerView.Adapter<DynamicParamAdapter.MyViewHolder> {

    private final String TAG = DynamicParamAdapter.class.getSimpleName();

    private Context context;
    private ArrayList<Param> params;
    private ApiManager apiManager;
    private String nodeId, deviceName;

    public DynamicParamAdapter(Context context, String nodeId, String deviceName, ArrayList<Param> deviceList) {
        this.context = context;
        this.nodeId = nodeId;
        this.deviceName = deviceName;
        this.params = deviceList;
        apiManager = new ApiManager(context.getApplicationContext());
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_dynamic_param, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, final int position) {

        Log.e(TAG, "onBindViewHolder");
        final Param param = params.get(position);
        Log.e(TAG, "param.getName : " + param.getName());
        Log.e(TAG, "param.getLabelValue : " + param.getLabelValue());

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

            myViewHolder.rvUiTypeSlider.setVisibility(View.VISIBLE);
            myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);

            int sliderValue = param.getSliderValue();
            Log.e(TAG, "Value : " + sliderValue);

            myViewHolder.tvSliderName.setText(param.getName());
            int max = param.getMaxBounds();
            int min = param.getMinBounds();
            Log.e(TAG, "Min value : " + min);
            Log.e(TAG, "Max value : " + max);

            myViewHolder.slider.setMax(max);
            myViewHolder.slider.setMin(min);
            myViewHolder.slider.setTickCount(2);
            myViewHolder.slider.setProgress(sliderValue);

            if (param.getProperties().contains("write")) {

                myViewHolder.slider.setOnSeekChangeListener(new OnSeekChangeListener() {

                    @Override
                    public void onSeeking(SeekParams seekParams) {
                    }

                    @Override
                    public void onStartTrackingTouch(TickSeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(TickSeekBar seekBar) {

                        int progress = seekBar.getProgress();
                        int finalProgress = progress;

                        Log.e(TAG, "onStopTrackingTouch - " + finalProgress);

                        HashMap<String, Object> body = new HashMap<>();
                        String key = deviceName + "." + param.getName();
                        body.put(key, finalProgress);

                        apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {

                            }

                            @Override
                            public void onFailure(Exception exception) {

                            }
                        });
                    }
                });
            } else {

                myViewHolder.slider.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
            }


        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

            myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);
            myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
            myViewHolder.rvUiTypeSwitch.setVisibility(View.VISIBLE);

            myViewHolder.tvSwitchName.setText(param.getName());
            myViewHolder.toggleSwitch.setChecked(param.getSwitchStatus());

            if (param.getProperties().contains("write")) {

                myViewHolder.toggleSwitch.setVisibility(View.VISIBLE);
                myViewHolder.tvSwitchStatus.setVisibility(View.GONE);

                myViewHolder.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                        HashMap<String, Object> body = new HashMap<>();
                        String key = deviceName + "." + param.getName();
                        body.put(key, isChecked);

                        apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                            @Override
                            public void onSuccess(Bundle data) {

                            }

                            @Override
                            public void onFailure(Exception exception) {

                            }
                        });
                    }
                });

            } else {

                myViewHolder.toggleSwitch.setVisibility(View.GONE);
                myViewHolder.tvSwitchStatus.setVisibility(View.VISIBLE);

                if (param.getSwitchStatus()) {

                    myViewHolder.tvSwitchStatus.setText(R.string.text_on);
                    myViewHolder.tvSwitchStatus.setTextColor(context.getResources().getColor(R.color.color_text_on));

                } else {
                    myViewHolder.tvSwitchStatus.setText(R.string.text_off);
                    myViewHolder.tvSwitchStatus.setTextColor(context.getResources().getColor(R.color.color_text_off));
                }
            }

        } else {

            if (param.getDataType().equalsIgnoreCase("bool")) {

                myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);
                myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
                myViewHolder.rvUiTypeSwitch.setVisibility(View.VISIBLE);

                myViewHolder.tvSwitchName.setText(param.getName());
                myViewHolder.toggleSwitch.setChecked(param.getSwitchStatus());

                if (param.getProperties().contains("write")) {

                    myViewHolder.toggleSwitch.setVisibility(View.VISIBLE);
                    myViewHolder.tvSwitchStatus.setVisibility(View.GONE);

                    myViewHolder.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                            HashMap<String, Object> body = new HashMap<>();
                            String key = deviceName + "." + param.getName();
                            body.put(key, isChecked);

                            apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                }

                                @Override
                                public void onFailure(Exception exception) {

                                }
                            });
                        }
                    });

                } else {

                    myViewHolder.toggleSwitch.setVisibility(View.GONE);
                    myViewHolder.tvSwitchStatus.setVisibility(View.VISIBLE);

                    if (param.getSwitchStatus()) {

                        myViewHolder.tvSwitchStatus.setText(R.string.text_on);
                        myViewHolder.tvSwitchStatus.setTextColor(context.getResources().getColor(R.color.color_text_on));

                    } else {
                        myViewHolder.tvSwitchStatus.setText(R.string.text_off);
                        myViewHolder.tvSwitchStatus.setTextColor(context.getResources().getColor(R.color.color_text_off));
                    }
                }

            } else {

                myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
                myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);
                myViewHolder.rvUiTypeLabel.setVisibility(View.VISIBLE);
                myViewHolder.tvLabelName.setText(param.getName());
                myViewHolder.tvLabelValue.setText(param.getLabelValue());
            }
        }
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
        TickSeekBar slider;
        Switch toggleSwitch;
        TextView tvSliderName, tvSwitchName, tvSwitchStatus, tvLabelName, tvLabelValue;
        RelativeLayout rvUiTypeSlider, rvUiTypeSwitch, rvUiTypeLabel;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            slider = itemView.findViewById(R.id.card_slider);
            toggleSwitch = itemView.findViewById(R.id.card_switch);
            tvSliderName = itemView.findViewById(R.id.slider_name);
            tvSwitchName = itemView.findViewById(R.id.switch_name);
            tvSwitchStatus = itemView.findViewById(R.id.tv_switch_status);
            tvLabelName = itemView.findViewById(R.id.label_name);
            tvLabelValue = itemView.findViewById(R.id.label_value);
            rvUiTypeSlider = itemView.findViewById(R.id.rl_card_slider);
            rvUiTypeSwitch = itemView.findViewById(R.id.rl_card_switch);
            rvUiTypeLabel = itemView.findViewById(R.id.rl_card_label);
        }
    }
}
// AWS console access
// Share document of AWS concepts
