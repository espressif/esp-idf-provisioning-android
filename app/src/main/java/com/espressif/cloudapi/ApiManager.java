package com.espressif.cloudapi;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.UpdateEvent;
import com.espressif.ui.user_module.AppHelper;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiManager {

    private static final String TAG = ApiManager.class.getSimpleName();

    private static final int REQ_STATUS_TIME = 5000;
    private static final int TIME_OUT = 60000;

    public static String userName = "";
    public static String userId = "";
    public static HashMap<String, String> requestIds = new HashMap<>(); // Map of node id and request id.

    private Context context;
    private EspApplication espApp;
    private Handler handler;
    private ApiInterface apiInterface;
    private static ArrayList<String> nodeIds = new ArrayList<>();

    public ApiManager(Context context) {
        this.context = context;
        handler = new Handler();
        espApp = (EspApplication) context.getApplicationContext();
        apiInterface = ApiClient.getClient().create(ApiInterface.class);
    }

    /**
     * This method is used to get user id from user name.
     *
     * @param listener Listener to send success or failure.
     */
    public void getSupportedVersions(final ApiResponseListener listener) {

        Log.d(TAG, "Get Supported Versions");

        apiInterface.getSupportedVersions()

                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                        Log.d(TAG, "Request : " + call.request().toString());
                        Log.d(TAG, "onResponse code  : " + response.code());

                        if (response.isSuccessful()) {

                            if (response.body() != null) {

                                try {
                                    String jsonResponse = response.body().string();
                                    Log.e(TAG, "onResponse Success : " + jsonResponse);

                                    JSONObject jsonObject = new JSONObject(jsonResponse);
                                    JSONArray jsonArray = jsonObject.optJSONArray("supported_versions");
                                    ArrayList<String> supportedVersions = new ArrayList<>();

                                    for (int i = 0; i < jsonArray.length(); i++) {

                                        String version = jsonArray.optString(i);
                                        Log.e(TAG, "Supported Version : " + version);
                                        supportedVersions.add(version);
                                    }

                                    String additionalInfoMsg = jsonObject.optString("additional_info");
                                    Bundle bundle = new Bundle();
                                    bundle.putString("additional_info", additionalInfoMsg);
                                    bundle.putStringArrayList("supported_versions", supportedVersions);
                                    listener.onSuccess(bundle);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                }
                            } else {
                                Log.e(TAG, "Response received : null");
                                listener.onFailure(new RuntimeException("Failed to get User ID"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get User ID"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e("TAG", "Error in receiving User id");
                        t.printStackTrace();
                        listener.onFailure(new Exception(t));
                    }
                });
    }

    /**
     * This method is used to get user id from user name.
     *
     * @param email    User name.
     * @param listener Listener to send success or failure.
     */
    public void getUserId(final String email, final ApiResponseListener listener) {

        Log.d(TAG, "Get User Id for user name : " + email);
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        apiInterface.getUserId(authToken, email)

                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                        Log.d(TAG, "Request : " + call.request().toString());
                        Log.d(TAG, "onResponse code  : " + response.code());

                        if (response.isSuccessful()) {

                            if (response.body() != null) {

                                try {
                                    String jsonResponse = response.body().string();
                                    Log.e(TAG, "onResponse Success : " + jsonResponse);

                                    JSONObject data = new JSONObject(jsonResponse);
                                    userId = data.optString("user_id");
                                    Log.e(TAG, "User id : " + userId);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    listener.onFailure(e);
                                }
                            } else {
                                Log.e(TAG, "Response received : null");
                                listener.onFailure(new RuntimeException("Failed to get User ID"));
                            }

                            Bundle bundle = new Bundle();
                            bundle.putString("user_id", userId);
                            listener.onSuccess(bundle);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get User ID"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e("TAG", "Error in receiving User id");
                        t.printStackTrace();
                        listener.onFailure(new Exception(t));
                    }
                });
    }

    /**
     * This method is used to get all devices for the user.
     *
     * @param listener Listener to send success or failure.
     */
    public void getAllDevices(final ApiResponseListener listener) {

        Log.d(TAG, "Get Devices");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        apiInterface.getDevicesForUser(authToken, userId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "onResponse code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            } else {
                                espApp.nodeMap.clear();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray jsonArray = jsonObject.optJSONArray("nodes");
                            nodeIds.clear();

                            for (int i = 0; i < jsonArray.length(); i++) {

                                String nodeId = jsonArray.optString(i);
                                Log.e(TAG, "EspNode id : " + nodeId);
                                espApp.nodeMap.put(nodeId, null);
                                nodeIds.add(nodeId);
                            }

                            if (espApp.nodeMap.size() != 0) {
                                getNextNodeDetail(listener);
                            } else {
                                listener.onSuccess(null);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        Log.e(TAG, "Response received : null");
                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                    }
                } else {

                    if (response.body() != null) {

                        try {
                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    private void getNextNodeDetail(final ApiResponseListener listener) {

        if (isAllNodeConfigDone()) {
            listener.onSuccess(null);
        } else {
            getNodeConfigFromNodeId(nodeIds.get(0), new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    if (isAllNodeConfigDone()) {
                        listener.onSuccess(null);
                    } else {
                        getNextNodeDetail(listener);
                    }
                }

                @Override
                public void onFailure(Exception exception) {
                    exception.printStackTrace();
//                        listener.onFailure(exception);
                    getNextNodeDetail(listener);
                }
            });
        }
    }

    private boolean isAllNodeConfigDone() {

        if (nodeIds != null && nodeIds.size() > 0) {
            return false;
        }
        return true;
    }

    private void getNodeConfigFromNodeId(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get getNodeConfigFromNodeId : " + nodeId);
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        apiInterface.getDevicesFromNodeId(authToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Request : " + call.request().toString());
                Log.e(TAG, "onResponse code : " + response.code());
                nodeIds.remove(nodeId);

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);

                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            EspNode espNode = new EspNode(nodeId);
                            espNode.setConfigVersion(jsonObject.optString("config_version"));

                            JSONObject infoObj = jsonObject.optJSONObject("info");
                            espNode.setNodeName(infoObj.optString("name"));
                            espNode.setFwVersion(infoObj.optString("fw_version"));
                            espNode.setNodeType(infoObj.optString("type"));

                            JSONArray devicesJsonArray = jsonObject.optJSONArray("devices");
                            ArrayList<EspDevice> devices = new ArrayList<>();

                            if (devicesJsonArray != null) {

                                for (int i = 0; i < devicesJsonArray.length(); i++) {

                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                                    EspDevice device = new EspDevice(nodeId);
                                    Log.e(TAG, "Device name : " + deviceObj.optString("name"));
                                    Log.e(TAG, "Device type : " + deviceObj.optString("type"));
                                    device.setDeviceName(deviceObj.optString("name"));
                                    device.setDeviceType(deviceObj.optString("type"));

                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
                                    ArrayList<Param> params = new ArrayList<>();

                                    if (paramsJson != null) {

                                        for (int j = 0; j < paramsJson.length(); j++) {

                                            JSONObject paraObj = paramsJson.optJSONObject(j);
                                            Param param = new Param();
                                            Log.e(TAG, "================= Received name" + paraObj.optString("name"));
                                            param.setName(paraObj.optString("name"));
                                            param.setDataType(paraObj.optString("data_type"));
                                            param.setUiType(paraObj.optString("ui-type"));
                                            param.setDynamicParam(true);
                                            params.add(param);

                                            JSONArray propertiesJson = paraObj.optJSONArray("properties");
                                            ArrayList<String> properties = new ArrayList<>();

                                            if (propertiesJson != null) {
                                                for (int k = 0; k < propertiesJson.length(); k++) {

                                                    properties.add(propertiesJson.optString(k));
                                                }
                                            }
                                            param.setProperties(properties);

                                            JSONObject boundsJson = paraObj.optJSONObject("bounds");

                                            if (boundsJson != null) {
                                                param.setMaxBounds(boundsJson.optInt("max"));
                                                param.setMinBounds(boundsJson.optInt("min"));
                                            }
                                        }
                                    }

                                    JSONArray attributesJson = deviceObj.optJSONArray("attributes");

                                    if (attributesJson != null) {

                                        for (int j = 0; j < attributesJson.length(); j++) {

                                            JSONObject attrObj = attributesJson.optJSONObject(j);
                                            Log.e(TAG, "Label : " + attrObj.optString("name"));
                                            Param param = new Param();
                                            param.setName(attrObj.optString("name"));
                                            param.setDataType(attrObj.optString("data_type"));
                                            param.setLabelValue(attrObj.optString("value"));
                                            Log.e(TAG, "Value : " + param.getLabelValue());
                                            params.add(param);
                                        }
                                    }

                                    device.setParams(params);
                                    devices.add(device);
                                }
                            }

                            espNode.setDevices(devices);
                            espApp.nodeMap.put(nodeId, espNode);
//
//                            Bundle bundle = new Bundle();
//                            bundle.putParcelableArrayList("nodes", nodeList);
//
//                            Log.d(TAG, "Device list : " + nodeList);
                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        Log.e(TAG, "Response received : null");
//                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
                    }

                } else {

                    if (response.body() != null) {

                        try {
                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse failure : " + jsonResponse);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    listener.onFailure(new RuntimeException("Failed to get Node config"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                // TODO
//                listener.onFailure(new Exception(t));
            }
        });
    }

    /**
     * This method is used to send request for add device (Associate device with user).
     *
     * @param nodeId    Device Id.
     * @param secretKey Generated Secret Key.
     * @param listener  Listener to send success or failure.
     */
    public void addDevice(final String nodeId, String secretKey, final ApiResponseListener listener) {

        Log.d(TAG, "Add Device");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);
        Log.d(TAG, "nodeId : " + nodeId);
//        Log.d(TAG, "userId : " + userId);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setUserId(userId);
        req.setNodeId(nodeId);
        req.setSecretKey(secretKey);
        req.setOperation("add");

        apiInterface.addDevice(authToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "Add device response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqId = jsonObject.optString("request_id");
                            requestIds.put(nodeId, reqId);
                            handler.post(getRequestStatusTask);
                            handler.removeCallbacks(stopRequestStatusPollingTask);
                            handler.postDelayed(stopRequestStatusPollingTask, TIME_OUT);
                            Bundle data = new Bundle();
                            data.putString("request_id", reqId);
                            listener.onSuccess(data);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to add device"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to add device"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    /**
     * This method is used to send request for remove device.
     *
     * @param nodeId   Device Id.
     * @param listener Listener to send success or failure.
     */
    public void removeDevice(final String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Remove Device");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setUserId(userId);
        req.setNodeId(nodeId);
        req.setOperation("remove");

        apiInterface.removeDevice(authToken, req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "Remove device response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to remove device"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to remove device"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void getDynamicParamsValue(final String nodeId, final ApiResponseListener listener) {

        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();

        apiInterface.getParamValue(authToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "Get Dynamic Params Value response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);

                            EspNode node = espApp.nodeMap.get(nodeId);
                            ArrayList<EspDevice> devices = node.getDevices();

                            for (int i = 0; i < devices.size(); i++) {

                                ArrayList<Param> params = devices.get(i).getParams();
                                String deviceName = devices.get(i).getDeviceName();
                                JSONObject deviceJson = jsonObject.optJSONObject(deviceName);

                                if (deviceJson != null) {

                                    Log.e(TAG, "JSON IS NOT NULL for device name : " + deviceName);

                                    for (int j = 0; j < params.size(); j++) {

                                        Param param = params.get(j);
                                        String key = param.getName();
                                        Log.e(TAG, "=============== Key : " + key);

                                        if (jsonResponse.contains(key)) {

                                            String dataType = param.getDataType();

                                            if (AppConstants.UI_TYPE_SLIDER.equalsIgnoreCase(param.getUiType())) {

                                                String labelValue = "";

                                                if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                    int value = deviceJson.optInt(key);
                                                    labelValue = String.valueOf(value);
                                                    param.setLabelValue(labelValue);
                                                    param.setSliderValue(value);

                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                    double value = deviceJson.optDouble(key);
                                                    labelValue = String.valueOf(value);
                                                    param.setLabelValue(labelValue);
                                                    param.setSliderValue(value);

                                                } else {

                                                    labelValue = deviceJson.optString(key);
                                                    param.setLabelValue(labelValue);
                                                }

                                            } else if (AppConstants.UI_TYPE_TOGGLE.equalsIgnoreCase(param.getUiType())) {

                                                boolean value = deviceJson.optBoolean(key);
                                                param.setSwitchStatus(value);

                                            } else {

                                                String labelValue = "";

                                                if (dataType.equalsIgnoreCase("bool") || dataType.equalsIgnoreCase("boolean")) {

                                                    boolean value = deviceJson.optBoolean(key);
//                                                param.setSwitchStatus(value);
                                                    if (value) {
                                                        param.setLabelValue("true");
                                                    } else {
                                                        param.setLabelValue("false");
                                                    }

                                                } else if (dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")) {

                                                    int value = deviceJson.optInt(key);
                                                    labelValue = String.valueOf(value);
                                                    param.setLabelValue(labelValue);

                                                } else if (dataType.equalsIgnoreCase("float") || dataType.equalsIgnoreCase("double")) {

                                                    double value = deviceJson.optDouble(key);
                                                    labelValue = String.valueOf(value);
                                                    param.setLabelValue(labelValue);

                                                } else {

                                                    labelValue = deviceJson.optString(key);
                                                    param.setLabelValue(labelValue);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "DEVICE JSON IS NULL");
                                }
                            }

                            listener.onSuccess(null);

                        } catch (IOException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            listener.onFailure(e);
                        }
                    } else {
                        listener.onFailure(new RuntimeException("Failed to Get Dynamic Params"));
                    }

                } else {
                    listener.onFailure(new RuntimeException("Failed to Get Dynamic Params"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onFailure(new Exception(t));
            }
        });
    }

    public void setDynamicParamValue(final String nodeId, JsonObject body, final ApiResponseListener listener) {

        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();

        try {
            apiInterface.updateParamValue(authToken, nodeId, body).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                    Log.e(TAG, "Update Dynamic Params Value response code : " + response.code());
                    Log.d(TAG, "Request : " + call.request().toString());

                    if (response.isSuccessful()) {

                        if (response.body() != null) {

                            try {

                                String jsonResponse = response.body().string();
                                Log.e(TAG, "onResponse Success : " + jsonResponse);
                                JSONObject jsonObject = new JSONObject(jsonResponse);

                                // TODO
                                listener.onSuccess(null);

                            } catch (IOException e) {
                                e.printStackTrace();
                                listener.onFailure(e);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                listener.onFailure(e);
                            }
                        } else {
                            listener.onFailure(new RuntimeException("Failed to update dynamic param"));
                        }

                    } else {
                        listener.onFailure(new RuntimeException("Failed to update dynamic param"));
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    t.printStackTrace();
                    listener.onFailure(new Exception(t));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getOnlineOfflineStatus(final String nodeId) {

        Log.d(TAG, "getOnlineOfflineStatus");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();

        apiInterface.getOnlineOfflineStatus(authToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Request : " + call.request().toString());
                Log.d(TAG, "EspNode mapping status response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            boolean nodeStatus = jsonObject.optBoolean("connected");

                            for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

                                String key = entry.getKey();
                                EspNode node = entry.getValue();

                                if (nodeId.equalsIgnoreCase(key)) {

                                    if (node.isOnline() != nodeStatus) {
                                        node.setOnline(nodeStatus);
                                        EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                    }
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(TAG, "Get node mapping status failed");
                    }

                } else {
                    Log.e(TAG, "Get node mapping status failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void getAddNodeRequestStatus(String userId, final String nodeId, String requestId) {

        Log.d(TAG, "getAddDeviceRequestStatus");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();

        apiInterface.getAddNodeRequestStatus(authToken, userId, requestId, true).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Request : " + call.request().toString());
                Log.d(TAG, "EspNode mapping status response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {
                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            String reqStatus = jsonObject.optString("request_status");

                            if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals("confirmed")) {
                                requestIds.remove(nodeId);

                                // Send event for update UI
                                if (requestIds.size() == 0) {
                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_ADDED));
                                }
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(TAG, "Get node mapping status failed");
                    }

                } else {
                    Log.e(TAG, "Get node mapping status failed");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private void getNodeReqStatus() {

        handler.postDelayed(getRequestStatusTask, REQ_STATUS_TIME);
    }

    private Runnable getRequestStatusTask = new Runnable() {

        @Override
        public void run() {

            if (requestIds.size() > 0) {

                for (String key : requestIds.keySet()) {

                    String nodeId = key;
                    String requestId = requestIds.get(nodeId);
                    getAddNodeRequestStatus(userId, nodeId, requestId);
                }
                getNodeReqStatus();
            } else {
                Log.i(TAG, "No request id is available to check status");
                handler.removeCallbacks(getRequestStatusTask);
                handler.removeCallbacks(stopRequestStatusPollingTask);
            }
        }
    };

    private Runnable stopRequestStatusPollingTask = new Runnable() {

        @Override
        public void run() {
            requestIds.clear();
            Log.d(TAG, "Stopped Polling Task");
            handler.removeCallbacks(getRequestStatusTask);
            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
        }
    };

    private Runnable getNodeStatusTask = new Runnable() {

        @Override
        public void run() {

            getAllNodeStatus();
        }
    };

    public void getNodeStatus() {

        cancelGetNodeStatusTask();
        getAllNodeStatus();
    }

    public void cancelGetNodeStatusTask() {

        handler.removeCallbacks(getNodeStatusTask);
    }

    private void getAllNodeStatus() {

        Set<String> nodeIdList = espApp.nodeMap.keySet();

        for (String nodeId : nodeIdList) {
            getOnlineOfflineStatus(nodeId);
        }
        handler.postDelayed(getNodeStatusTask, 10000);
    }
}
