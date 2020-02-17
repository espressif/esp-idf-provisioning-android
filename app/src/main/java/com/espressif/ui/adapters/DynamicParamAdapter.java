package com.espressif.ui.adapters;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.R;
import com.espressif.ui.models.Param;
import com.google.gson.JsonObject;
import com.warkiz.tickseekbar.OnSeekChangeListener;
import com.warkiz.tickseekbar.SeekParams;
import com.warkiz.tickseekbar.TickSeekBar;

import java.util.ArrayList;

public class DynamicParamAdapter extends RecyclerView.Adapter<DynamicParamAdapter.MyViewHolder> {

    private final String TAG = DynamicParamAdapter.class.getSimpleName();

    private Activity context;
    private ArrayList<Param> params;
    private ApiManager apiManager;
    private String nodeId, deviceName;

    public DynamicParamAdapter(Activity context, String nodeId, String deviceName, ArrayList<Param> deviceList) {
        this.context = context;
        this.nodeId = nodeId;
        this.deviceName = deviceName;
        this.params = deviceList;
        apiManager = ApiManager.getInstance(context.getApplicationContext());
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
    public void onBindViewHolder(@NonNull final MyViewHolder myViewHolder, final int position) {

//        Log.e(TAG, "onBindViewHolder for position : " + position);
        final Param param = params.get(position);
        Log.e(TAG, "param.getName : " + param.getName() + " and param.getLabelValue : " + param.getLabelValue());

        if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")
                    || dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double"))) {

                int max = param.getMaxBounds();
                int min = param.getMinBounds();

                if ((min < max)) {

                    displaySlider(myViewHolder, param, position);
                } else {
                    displayLabel(myViewHolder, param, position);
                }
            }

        } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

            String dataType = param.getDataType();

            if (!TextUtils.isEmpty(dataType) && (dataType.equalsIgnoreCase("bool")
                    || dataType.equalsIgnoreCase("boolean"))) {

                displayToggle(myViewHolder, param);

            } else {
                displayLabel(myViewHolder, param, position);
            }

        } else {
            displayLabel(myViewHolder, param, position);
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

    private void displaySlider(final MyViewHolder myViewHolder, final Param param, final int position) {

        Log.d(TAG, "Deep : UI Type :" + param.getUiType() + " for position :" + position);

        myViewHolder.rvUiTypeSlider.setVisibility(View.VISIBLE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);
        myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);

        double sliderValue = param.getSliderValue();
        myViewHolder.tvSliderName.setText(param.getName());
        float max = param.getMaxBounds();
        float min = param.getMinBounds();
        String dataType = param.getDataType();

        if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

            myViewHolder.intSlider.setVisibility(View.VISIBLE);
            myViewHolder.floatSlider.setVisibility(View.GONE);

            myViewHolder.intSlider.setMax(max);
            myViewHolder.intSlider.setMin(min);
            myViewHolder.intSlider.setTickCount(2);

            if (sliderValue < min) {

                myViewHolder.intSlider.setProgress(min);

            } else if (sliderValue > max) {

                myViewHolder.intSlider.setProgress(max);

            } else {
                myViewHolder.intSlider.setProgress((int) sliderValue);
            }

            Log.e(TAG, "=================== Param : " + param.getName() + " properties : " + param.getProperties().toString());

            if (param.getProperties().contains("write")) {

                Log.e(TAG, "=================== Param : " + param.getName() + " CONTAINS WRITE");

                myViewHolder.intSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

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

                        JsonObject jsonParam = new JsonObject();
                        JsonObject body = new JsonObject();

                        jsonParam.addProperty(param.getName(), finalProgress);
                        body.add(deviceName, jsonParam);

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

                Log.e(TAG, "=================== Param : " + param.getName() + " Read only");
                myViewHolder.intSlider.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        Log.e(TAG, "============= Disabled Slider for position : " + position);
                        return true;
                    }
                });
            }

        } else {

            myViewHolder.intSlider.setVisibility(View.GONE);
            myViewHolder.floatSlider.setVisibility(View.VISIBLE);

            myViewHolder.floatSlider.setMax(max);
            myViewHolder.floatSlider.setMin(min);
            myViewHolder.floatSlider.setTickCount(2);

            if (sliderValue < min) {

                myViewHolder.floatSlider.setProgress(min);

            } else if (sliderValue > max) {

                myViewHolder.floatSlider.setProgress(max);

            } else {
                myViewHolder.floatSlider.setProgress((float) sliderValue);
            }

            Log.e(TAG, "FLOAT Slider value : " + (float) sliderValue);
            Log.e(TAG, "FLOAT Slider value : " + myViewHolder.floatSlider.getProgressFloat());

            if (param.getProperties().contains("write")) {

                myViewHolder.floatSlider.setOnSeekChangeListener(new OnSeekChangeListener() {

                    @Override
                    public void onSeeking(SeekParams seekParams) {
                    }

                    @Override
                    public void onStartTrackingTouch(TickSeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(TickSeekBar seekBar) {

                        float progress = seekBar.getProgressFloat();
                        float finalProgress = progress;

                        Log.e(TAG, "onStopTrackingTouch - " + finalProgress);

                        JsonObject jsonParam = new JsonObject();
                        JsonObject body = new JsonObject();

                        jsonParam.addProperty(param.getName(), finalProgress);
                        body.add(deviceName, jsonParam);

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

                myViewHolder.floatSlider.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
            }
        }
    }

    private void displayToggle(final MyViewHolder myViewHolder, final Param param) {

        myViewHolder.rvUiTypeLabel.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.VISIBLE);

        myViewHolder.tvSwitchName.setText(param.getName());
        myViewHolder.tvSwitchStatus.setVisibility(View.VISIBLE);

        if (param.getSwitchStatus()) {

            myViewHolder.tvSwitchStatus.setText(R.string.text_on);

        } else {
            myViewHolder.tvSwitchStatus.setText(R.string.text_off);
        }

        if (param.getProperties().contains("write")) {

            myViewHolder.toggleSwitch.setVisibility(View.VISIBLE);
            myViewHolder.toggleSwitch.setChecked(param.getSwitchStatus());

            myViewHolder.toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (isChecked) {

                        myViewHolder.tvSwitchStatus.setText(R.string.text_on);

                    } else {
                        myViewHolder.tvSwitchStatus.setText(R.string.text_off);
                    }

                    JsonObject jsonParam = new JsonObject();
                    JsonObject body = new JsonObject();

                    jsonParam.addProperty(param.getName(), isChecked);
                    body.add(deviceName, jsonParam);

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
        }
    }

    private void displayLabel(final MyViewHolder myViewHolder, final Param param, final int position) {

        myViewHolder.rvUiTypeSlider.setVisibility(View.GONE);
        myViewHolder.rvUiTypeSwitch.setVisibility(View.GONE);
        myViewHolder.rvUiTypeLabel.setVisibility(View.VISIBLE);

        myViewHolder.tvLabelName.setText(param.getName());
        myViewHolder.tvLabelValue.setText(param.getLabelValue());

        if (param.getProperties().contains("write")) {

            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
            myViewHolder.btnEdit.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    askForNewValue(myViewHolder, param, position);
                }
            });

        } else {

            myViewHolder.btnEdit.setVisibility(View.GONE);
        }
    }

    private void askForNewValue(final MyViewHolder myViewHolder, final Param param, final int position) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = context.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        builder.setView(dialogView);

        final EditText etAttribute = dialogView.findViewById(R.id.et_attr_value);

        if (!TextUtils.isEmpty(param.getDataType())) {

            String dataType = param.getDataType();

            if (dataType.equalsIgnoreCase("int")
                    || dataType.equalsIgnoreCase("integer")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER);

            } else if (dataType.equalsIgnoreCase("float")
                    || dataType.equalsIgnoreCase("double")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            } else if (dataType.equalsIgnoreCase("string")) {

                etAttribute.setInputType(InputType.TYPE_CLASS_TEXT);
            }
        }

        if (!TextUtils.isEmpty(param.getLabelValue())) {
            etAttribute.setText(param.getLabelValue());
            etAttribute.setSelection(etAttribute.getText().length());
            etAttribute.requestFocus();
        }

        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String dataType = param.getDataType();
                final String value = etAttribute.getText().toString();

                JsonObject jsonParam = new JsonObject();
                JsonObject body = new JsonObject();

                if (dataType.equalsIgnoreCase("bool")
                        || dataType.equalsIgnoreCase("boolean")) {

                    if (!TextUtils.isEmpty(value)) {

                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                                || value.equalsIgnoreCase("0") || value.equalsIgnoreCase("1")) {

                            boolean isOn = false;

                            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1")) {
                                isOn = true;
                            }

                            myViewHolder.btnEdit.setVisibility(View.GONE);
                            myViewHolder.progressBar.setVisibility(View.VISIBLE);

                            jsonParam.addProperty(param.getName(), isOn);
                            body.add(deviceName, jsonParam);

                            final boolean finalIsOn = isOn;
                            apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                    myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    myViewHolder.progressBar.setVisibility(View.GONE);

                                    if (finalIsOn) {

                                        myViewHolder.tvLabelValue.setText("true");
                                        params.get(position).setLabelValue("true");

                                    } else {
                                        myViewHolder.tvLabelValue.setText("false");
                                        params.get(position).setLabelValue("false");
                                    }
                                }

                                @Override
                                public void onFailure(Exception exception) {

                                    myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                                    myViewHolder.progressBar.setVisibility(View.GONE);
                                    myViewHolder.tvLabelValue.setText(param.getLabelValue());
                                }
                            });

                        } else {

                            dialog.dismiss();
                            Toast.makeText(context, "Please enter valid value", Toast.LENGTH_SHORT).show();
                        }
                    } else {

                        dialog.dismiss();
                        Toast.makeText(context, "Please enter valid value", Toast.LENGTH_SHORT).show();
                    }

                } else if (dataType.equalsIgnoreCase("int")
                        || dataType.equalsIgnoreCase("integer")) {

                    int newValue = Integer.valueOf(value);
                    jsonParam.addProperty(param.getName(), newValue);
                    body.add(deviceName, jsonParam);

                    apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(value);
                            params.get(position).setLabelValue(value);
                        }

                        @Override
                        public void onFailure(Exception exception) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(param.getLabelValue());
                        }
                    });

                } else if (dataType.equalsIgnoreCase("float")
                        || dataType.equalsIgnoreCase("double")) {

                    float newValue = Float.valueOf(value);
                    jsonParam.addProperty(param.getName(), newValue);
                    body.add(deviceName, jsonParam);

                    apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(value);
                            params.get(position).setLabelValue(value);
                        }

                        @Override
                        public void onFailure(Exception exception) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(param.getLabelValue());
                        }
                    });

                } else {

                    jsonParam.addProperty(param.getName(), value);
                    body.add(deviceName, jsonParam);

                    apiManager.setDynamicParamValue(nodeId, body, new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(value);
                            params.get(position).setLabelValue(value);
                        }

                        @Override
                        public void onFailure(Exception exception) {

                            myViewHolder.btnEdit.setVisibility(View.VISIBLE);
                            myViewHolder.progressBar.setVisibility(View.GONE);
                            myViewHolder.tvLabelValue.setText(param.getLabelValue());
                        }
                    });
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TickSeekBar intSlider, floatSlider;
        SwitchCompat toggleSwitch;
        TextView tvSliderName, tvSwitchName, tvSwitchStatus, tvLabelName, tvLabelValue;
        RelativeLayout rvUiTypeSlider, rvUiTypeSwitch, rvUiTypeLabel;
        TextView btnEdit;
        ContentLoadingProgressBar progressBar;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            intSlider = itemView.findViewById(R.id.card_int_slider);
            floatSlider = itemView.findViewById(R.id.card_float_slider);
            toggleSwitch = itemView.findViewById(R.id.card_switch);
            tvSliderName = itemView.findViewById(R.id.slider_name);
            tvSwitchName = itemView.findViewById(R.id.switch_name);
            tvSwitchStatus = itemView.findViewById(R.id.tv_switch_status);
            tvLabelName = itemView.findViewById(R.id.tv_label_name);
            tvLabelValue = itemView.findViewById(R.id.tv_label_value);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            progressBar = itemView.findViewById(R.id.progress_indicator);
            rvUiTypeSlider = itemView.findViewById(R.id.rl_card_slider);
            rvUiTypeSwitch = itemView.findViewById(R.id.rl_card_switch);
            rvUiTypeLabel = itemView.findViewById(R.id.rl_card_label);
        }
    }
}
