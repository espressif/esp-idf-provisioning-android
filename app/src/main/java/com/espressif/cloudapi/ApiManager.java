package com.espressif.cloudapi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.auth0.android.jwt.Claim;
import com.auth0.android.jwt.DecodeException;
import com.auth0.android.jwt.JWT;
import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.provision.R;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiManager {

    private static final String TAG = ApiManager.class.getSimpleName();

    private static final int REQ_STATUS_TIME = 5000;

    public static boolean isOAuthLogin;
    public static String userName = "";
    public static String userId = "";
    private static String idToken = "";
    private static String accessToken = "";
    private static String refreshToken = "";
    public static HashMap<String, String> requestIds = new HashMap<>(); // Map of node id and request id.

    private Context context;
    private EspApplication espApp;
    private Handler handler;
    private ApiInterface apiInterface;
    private static ArrayList<String> nodeIds = new ArrayList<>();

    private static ApiManager apiManager;

    public static ApiManager getInstance(Context context) {

        if (apiManager == null) {
            apiManager = new ApiManager(context);
        }
        return apiManager;
    }

    private ApiManager(Context context) {
        this.context = context;
        handler = new Handler();
        espApp = (EspApplication) context.getApplicationContext();
        apiInterface = ApiClient.getClient(context).create(ApiInterface.class);
    }

    public void getOAuthToken(String code, final ApiResponseListener listener) {

        try {
            apiInterface.loginWithGithub("application/x-www-form-urlencoded",
                    "authorization_code", context.getString(R.string.client_id), code,
                    AppConstants.REDIRECT_URI).enqueue(new Callback<ResponseBody>() {

                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                    Log.d(TAG, "Request : " + call.request().toString());
                    Log.d(TAG, "onResponse code  : " + response.code());
                    try {
                        if (response.isSuccessful()) {

                            String jsonResponse = response.body().string();
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            idToken = jsonObject.getString("id_token");
                            accessToken = jsonObject.getString("access_token");
                            refreshToken = jsonObject.getString("refresh_token");
                            isOAuthLogin = true;

                            SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(AppConstants.KEY_ID_TOKEN, idToken);
                            editor.putString(AppConstants.KEY_ACCESS_TOKEN, accessToken);
                            editor.putString(AppConstants.KEY_REFRESH_TOKEN, refreshToken);
                            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, true);
                            editor.apply();

                            setTokenAndUserId();
                            listener.onSuccess(null);

                        } else {
                            listener.onFailure(new RuntimeException("Failed to login"));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.onFailure(e);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        listener.onFailure(e);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    t.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            listener.onFailure(e);
        }
    }

    public void setTokenAndUserId() {

        SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        userName = sharedPreferences.getString(AppConstants.KEY_EMAIL, "");
        idToken = sharedPreferences.getString(AppConstants.KEY_ID_TOKEN, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        refreshToken = sharedPreferences.getString(AppConstants.KEY_REFRESH_TOKEN, "");
        isOAuthLogin = sharedPreferences.getBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);

        if (isOAuthLogin) {

            JWT jwt = null;
            try {
                jwt = new JWT(idToken);
            } catch (DecodeException e) {
                e.printStackTrace();
            }

            Claim claimUserName = jwt.getClaim("cognito:username");
            userId = claimUserName.asString();
            Claim claimEmail = jwt.getClaim("email");
            String email = claimEmail.asString();
            userName = email;

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_EMAIL, email);
            editor.apply();

        } else {

            JWT jwt = null;
            try {
                jwt = new JWT(idToken);
            } catch (DecodeException e) {
                e.printStackTrace();
            }

            Claim claimUserId = jwt.getClaim("custom:user_id");
            userId = claimUserId.asString();
        }
        Log.d(TAG, "=======================>>>>>>>>>>>>>>>>>>> GOT USER ID : " + userId);
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
                                listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                            }

                        } else {
                            listener.onFailure(new RuntimeException("Failed to get Supported Versions"));
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(TAG, "Error in receiving Supported Versions");
                        t.printStackTrace();
                        listener.onFailure(new Exception(t));
                    }
                });
    }

    public void getNodes(final ApiResponseListener listener) {

        Log.d(TAG, "Get Nodes");
        Log.d(TAG, "User Id : " + userId);
        Log.d(TAG, "Auth token : " + accessToken);

        apiInterface.getNodes(accessToken).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "onResponse code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            } else {
//                                espApp.nodeMap.clear();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray("node_details");
                            nodeIds.clear();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString("id");
                                        Log.e(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject("config");
                                        if (configJson != null) {

                                            espNode.setConfigVersion(configJson.optString("config_version"));

                                            JSONObject infoObj = configJson.optJSONObject("info");

                                            if (infoObj != null) {
                                                espNode.setNodeName(infoObj.optString("name"));
                                                espNode.setFwVersion(infoObj.optString("fw_version"));
                                                espNode.setNodeType(infoObj.optString("type"));
                                            } else {
                                                Log.e(TAG, "Info object is null");
                                            }
                                            espNode.setOnline(true);

                                            JSONArray devicesJsonArray = configJson.optJSONArray("devices");
                                            ArrayList<EspDevice> devices = new ArrayList<>();

                                            if (devicesJsonArray != null) {

                                                for (int i = 0; i < devicesJsonArray.length(); i++) {

                                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                                                    EspDevice device = new EspDevice(nodeId);
                                                    device.setDeviceName(deviceObj.optString("name"));
                                                    device.setDeviceType(deviceObj.optString("type"));
                                                    device.setPrimaryParamName(deviceObj.optString("primary"));

                                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
                                                    ArrayList<Param> params = new ArrayList<>();

                                                    if (paramsJson != null) {

                                                        for (int j = 0; j < paramsJson.length(); j++) {

                                                            JSONObject paraObj = paramsJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(paraObj.optString("name"));
                                                            param.setParamType(paraObj.optString("type"));
                                                            param.setDataType(paraObj.optString("data_type"));
                                                            param.setUiType(paraObj.optString("ui_type"));
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
                                                            Param param = new Param();
                                                            param.setName(attrObj.optString("name"));
                                                            param.setDataType(attrObj.optString("data_type"));
                                                            param.setLabelValue(attrObj.optString("value"));
                                                            params.add(param);
                                                        }
                                                    }

                                                    device.setParams(params);
                                                    devices.add(device);
                                                }
                                            }

                                            espNode.setDevices(devices);

                                            JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
                                            ArrayList<Param> nodeAttributes = new ArrayList<>();

                                            if (nodeAttributesJson != null) {

                                                for (int j = 0; j < nodeAttributesJson.length(); j++) {

                                                    JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                                                    Param param = new Param();
                                                    param.setName(attrObj.optString("name"));
                                                    param.setLabelValue(attrObj.optString("value"));
                                                    nodeAttributes.add(param);
                                                }
                                            }

                                            espNode.setAttributes(nodeAttributes);

                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params
                                        JSONObject paramsJson = nodeJson.optJSONObject("params");
                                        if (paramsJson != null) {

                                            ArrayList<EspDevice> devices = espNode.getDevices();

                                            for (int i = 0; i < devices.size(); i++) {

                                                ArrayList<Param> params = devices.get(i).getParams();
                                                String deviceName = devices.get(i).getDeviceName();
                                                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                                                if (deviceJson != null) {

                                                    for (int j = 0; j < params.size(); j++) {

                                                        Param param = params.get(j);
                                                        String key = param.getName();

                                                        if (!param.isDynamicParam()) {
                                                            continue;
                                                        }

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
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject("status");

                                        if (statusJson != null) {

                                            JSONObject connectivityObject = statusJson.optJSONObject("connectivity");

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean("connected");
                                                long timestamp = connectivityObject.optLong("timestamp");
                                                espNode.setTimeStampOfStatus(timestamp);

                                                if (espNode.isOnline() != nodeStatus) {
                                                    espNode.setOnline(nodeStatus);
                                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                                }
                                            } else {
                                                Log.e(TAG, "Connectivity object is null");
                                            }
                                        }
                                    }
                                }
                            }

                            Iterator<Map.Entry<String, EspNode>> itr = espApp.nodeMap.entrySet().iterator();

                            // iterate and remove items simultaneously
                            while (itr.hasNext()) {

                                Map.Entry<String, EspNode> entry = itr.next();
                                String key = entry.getKey();

                                if (!nodeIds.contains(key)) {
                                    itr.remove();
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

    public void getNodeDetails(String nodeId, final ApiResponseListener listener) {

        Log.d(TAG, "Get Node Details");
        Log.d(TAG, "User Id : " + userId);
        Log.d(TAG, "Auth token : " + accessToken);

        apiInterface.getNode(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "onResponse code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            if (espApp.nodeMap == null) {
                                espApp.nodeMap = new HashMap<>();
                            } else {
//                                espApp.nodeMap.clear();
                            }

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);
                            JSONArray nodeJsonArray = jsonObject.optJSONArray("node_details");
                            nodeIds.clear();

                            if (nodeJsonArray != null) {

                                for (int nodeIndex = 0; nodeIndex < nodeJsonArray.length(); nodeIndex++) {

                                    JSONObject nodeJson = nodeJsonArray.optJSONObject(nodeIndex);

                                    if (nodeJson != null) {

                                        // Node ID
                                        String nodeId = nodeJson.optString("id");
                                        Log.e(TAG, "Node id : " + nodeId);
                                        nodeIds.add(nodeId);
                                        EspNode espNode;

                                        if (espApp.nodeMap.get(nodeId) != null) {
                                            espNode = espApp.nodeMap.get(nodeId);
                                        } else {
                                            espNode = new EspNode(nodeId);
                                        }

                                        // Node Config
                                        JSONObject configJson = nodeJson.optJSONObject("config");
                                        if (configJson != null) {

                                            espNode.setConfigVersion(configJson.optString("config_version"));

                                            JSONObject infoObj = configJson.optJSONObject("info");

                                            if (infoObj != null) {
                                                espNode.setNodeName(infoObj.optString("name"));
                                                espNode.setFwVersion(infoObj.optString("fw_version"));
                                                espNode.setNodeType(infoObj.optString("type"));
                                            } else {
                                                Log.e(TAG, "Info object is null");
                                            }
                                            espNode.setOnline(true);

                                            JSONArray devicesJsonArray = configJson.optJSONArray("devices");
                                            ArrayList<EspDevice> devices = new ArrayList<>();

                                            if (devicesJsonArray != null) {

                                                for (int i = 0; i < devicesJsonArray.length(); i++) {

                                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
                                                    EspDevice device = new EspDevice(nodeId);
                                                    device.setDeviceName(deviceObj.optString("name"));
                                                    device.setDeviceType(deviceObj.optString("type"));
                                                    device.setPrimaryParamName(deviceObj.optString("primary"));

                                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
                                                    ArrayList<Param> params = new ArrayList<>();

                                                    if (paramsJson != null) {

                                                        for (int j = 0; j < paramsJson.length(); j++) {

                                                            JSONObject paraObj = paramsJson.optJSONObject(j);
                                                            Param param = new Param();
                                                            param.setName(paraObj.optString("name"));
                                                            param.setParamType(paraObj.optString("type"));
                                                            param.setDataType(paraObj.optString("data_type"));
                                                            param.setUiType(paraObj.optString("ui_type"));
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
                                                            Param param = new Param();
                                                            param.setName(attrObj.optString("name"));
                                                            param.setDataType(attrObj.optString("data_type"));
                                                            param.setLabelValue(attrObj.optString("value"));
                                                            params.add(param);
                                                        }
                                                    }

                                                    device.setParams(params);
                                                    devices.add(device);
                                                }
                                            }

                                            espNode.setDevices(devices);

                                            JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
                                            ArrayList<Param> nodeAttributes = new ArrayList<>();

                                            if (nodeAttributesJson != null) {

                                                for (int j = 0; j < nodeAttributesJson.length(); j++) {

                                                    JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
                                                    Param param = new Param();
                                                    param.setName(attrObj.optString("name"));
                                                    param.setLabelValue(attrObj.optString("value"));
                                                    nodeAttributes.add(param);
                                                }
                                            }

                                            espNode.setAttributes(nodeAttributes);

                                            espApp.nodeMap.put(nodeId, espNode);
                                        }

                                        // Node Params
                                        JSONObject paramsJson = nodeJson.optJSONObject("params");
                                        if (paramsJson != null) {

                                            ArrayList<EspDevice> devices = espNode.getDevices();

                                            for (int i = 0; i < devices.size(); i++) {

                                                ArrayList<Param> params = devices.get(i).getParams();
                                                String deviceName = devices.get(i).getDeviceName();
                                                JSONObject deviceJson = paramsJson.optJSONObject(deviceName);

                                                if (deviceJson != null) {

                                                    for (int j = 0; j < params.size(); j++) {

                                                        Param param = params.get(j);
                                                        String key = param.getName();

                                                        if (!param.isDynamicParam()) {
                                                            continue;
                                                        }

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
                                        }

                                        // Node Status
                                        JSONObject statusJson = nodeJson.optJSONObject("status");

                                        if (statusJson != null) {

                                            JSONObject connectivityObject = statusJson.optJSONObject("connectivity");

                                            if (connectivityObject != null) {

                                                boolean nodeStatus = connectivityObject.optBoolean("connected");
                                                long timestamp = connectivityObject.optLong("timestamp");
                                                espNode.setTimeStampOfStatus(timestamp);

                                                if (espNode.isOnline() != nodeStatus) {
                                                    espNode.setOnline(nodeStatus);
                                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
                                                }
                                            } else {
                                                Log.e(TAG, "Connectivity object is null");
                                            }
                                        }
                                    }
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

    /**
     * This method is used to send request for add device (Associate device with user).
     *
     * @param nodeId    Device Id.
     * @param secretKey Generated Secret Key.
     * @param listener  Listener to send success or failure.
     */
    public void addDevice(final String nodeId, String secretKey, final ApiResponseListener listener) {

        Log.d(TAG, "Add Device");
        Log.d(TAG, "Auth token : " + accessToken);
        Log.d(TAG, "nodeId : " + nodeId);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setNodeId(nodeId);
        req.setSecretKey(secretKey);
        req.setOperation("add");

        apiInterface.addDevice(accessToken, req).enqueue(new Callback<ResponseBody>() {

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
        Log.d(TAG, "Auth token : " + accessToken);

        DeviceOperationRequest req = new DeviceOperationRequest();
        req.setNodeId(nodeId);
        req.setOperation("remove");

        apiInterface.removeDevice(accessToken, req).enqueue(new Callback<ResponseBody>() {

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

    public void getParamsValues(final String nodeId, final ApiResponseListener listener) {

        Log.e(TAG, "Get Param values");

        apiInterface.getParamValue(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "Get Params Value response code : " + response.code());
                Log.d(TAG, "Request : " + call.request().toString());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);
                            JSONObject jsonObject = new JSONObject(jsonResponse);

                            EspNode node = espApp.nodeMap.get(nodeId);

                            if (node != null) {

                                ArrayList<EspDevice> devices = node.getDevices();

                                for (int i = 0; i < devices.size(); i++) {

                                    ArrayList<Param> params = devices.get(i).getParams();
                                    String deviceName = devices.get(i).getDeviceName();
                                    JSONObject deviceJson = jsonObject.optJSONObject(deviceName);

                                    if (deviceJson != null) {

                                        for (int j = 0; j < params.size(); j++) {

                                            Param param = params.get(j);
                                            String key = param.getName();

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

        Log.d(TAG, "Updating param values");

        try {
            apiInterface.updateParamValue(accessToken, nodeId, body).enqueue(new Callback<ResponseBody>() {

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

    private void getAddNodeRequestStatus(final String nodeId, String requestId) {

        Log.d(TAG, "getAddDeviceRequestStatus");

        apiInterface.getAddNodeRequestStatus(accessToken, requestId, true).enqueue(new Callback<ResponseBody>() {

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
                            } else if (!TextUtils.isEmpty(reqStatus) && reqStatus.equals("timedout")) {

                                requestIds.remove(nodeId);

                                // Send event for update UI
                                if (requestIds.size() == 0) {
                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
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
                    getAddNodeRequestStatus(nodeId, requestId);
                }
                getNodeReqStatus();
            } else {
                Log.i(TAG, "No request id is available to check status");
                handler.removeCallbacks(getRequestStatusTask);
            }
        }
    };

    public boolean isTokenExpired() {

        Log.d(TAG, "Check isTokenExpired");

        SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        idToken = sharedPreferences.getString(AppConstants.KEY_ID_TOKEN, "");
        accessToken = sharedPreferences.getString(AppConstants.KEY_ACCESS_TOKEN, "");
        refreshToken = sharedPreferences.getString(AppConstants.KEY_REFRESH_TOKEN, "");

        Log.d(TAG, "Auth token : " + accessToken);

        JWT jwt = null;
        try {
            jwt = new JWT(accessToken);
        } catch (DecodeException e) {
            e.printStackTrace();
        }

        Date expiresAt = jwt.getExpiresAt();
        Calendar calendar = Calendar.getInstance();
        Date currentTIme = calendar.getTime();
        Log.e(TAG, "Expire at : " + expiresAt);
        Log.e(TAG, "Current time : " + currentTIme);

        if (currentTIme.after(expiresAt)) {
            Log.e(TAG, "Token has expired");
            return true;
        } else {
            Log.e(TAG, "Token has not expired");
            return false;
        }
    }

    public void getNewToken() {

        if (isOAuthLogin) {
            getNewTokenForOAuth();
        } else {
            AppHelper.getPool().getUser(userName).getSessionInBackground(authenticationHandler);
        }
    }

    private void getNewTokenForOAuth() {

        HashMap<String, String> body = new HashMap<>();
        Log.e(TAG, "USER NAME : " + userId);
        Log.e(TAG, "Refresh token : " + refreshToken);
        body.put("user_name", userId);
        body.put("refreshtoken", refreshToken);

        apiInterface.getOAuthLoginToken(body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Request : " + call.request().toString());
                Log.d(TAG, "onResponse code  : " + response.code());
                try {
                    if (response.isSuccessful()) {

                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        idToken = jsonObject.getString("idtoken");
                        accessToken = jsonObject.getString("accesstoken");
                        refreshToken = jsonObject.getString("refreshtoken");
                        isOAuthLogin = true;

                        SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(AppConstants.KEY_ID_TOKEN, idToken);
                        editor.putString(AppConstants.KEY_ACCESS_TOKEN, accessToken);
                        editor.putString(AppConstants.KEY_REFRESH_TOKEN, refreshToken);
                        editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, true);
                        editor.apply();

                        setTokenAndUserId();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private Runnable stopRequestStatusPollingTask = new Runnable() {

        @Override
        public void run() {
            requestIds.clear();
            Log.d(TAG, "Stopped Polling Task");
            handler.removeCallbacks(getRequestStatusTask);
            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_ADD_DEVICE_TIME_OUT));
        }
    };

    public void cancelRequestStatusPollingTask() {

        handler.removeCallbacks(stopRequestStatusPollingTask);
    }

    AuthenticationHandler authenticationHandler = new AuthenticationHandler() {

        @Override
        public void onSuccess(CognitoUserSession cognitoUserSession, CognitoDevice newDevice) {

            Log.d(TAG, " -- Auth Success");
            Log.d(TAG, "Username : " + cognitoUserSession.getUsername());
            Log.d(TAG, "IdToken : " + cognitoUserSession.getIdToken().getJWTToken());
            Log.d(TAG, "AccessToken : " + cognitoUserSession.getAccessToken().getJWTToken());
            Log.d(TAG, "RefreshToken : " + cognitoUserSession.getRefreshToken().getToken());

            AppHelper.setCurrSession(cognitoUserSession);
            SharedPreferences sharedPreferences = context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_ID_TOKEN, cognitoUserSession.getIdToken().getJWTToken());
            editor.putString(AppConstants.KEY_ACCESS_TOKEN, cognitoUserSession.getAccessToken().getJWTToken());
            editor.putString(AppConstants.KEY_REFRESH_TOKEN, cognitoUserSession.getRefreshToken().getToken());
            editor.putBoolean(AppConstants.KEY_IS_OAUTH_LOGIN, false);
            editor.apply();

            AppHelper.newDevice(newDevice);
            setTokenAndUserId();
        }

        @Override
        public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String userId) {
            Log.e(TAG, "getAuthenticationDetails ");
        }

        @Override
        public void getMFACode(MultiFactorAuthenticationContinuation continuation) {
            Log.e(TAG, "getMFACode ");
        }

        @Override
        public void authenticationChallenge(ChallengeContinuation continuation) {
            Log.e(TAG, "authenticationChallenge ");
        }

        @Override
        public void onFailure(Exception exception) {
            Log.e(TAG, "onFailure ");
            exception.printStackTrace();

            // TODO
            // Do Signout
        }
    };


    /**
     * This method is used to get all devices for the user.
     *
     * @param listener Listener to send success or failure.
     */
//    public void getAllDevices(final ApiResponseListener listener) {
//
//        Log.d(TAG, "Get Devices");
//        Log.d(TAG, "User Id : " + userId);
//        Log.d(TAG, "Auth token : " + accessToken);
//
//        apiInterface.getDevicesForUser(accessToken).enqueue(new Callback<ResponseBody>() {
//
//            @Override
//            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//
//                Log.e(TAG, "onResponse code : " + response.code());
//
//                if (response.isSuccessful()) {
//
//                    if (response.body() != null) {
//
//                        try {
//
//                            if (espApp.nodeMap == null) {
//                                espApp.nodeMap = new HashMap<>();
//                            } else {
////                                espApp.nodeMap.clear();
//                            }
//
//                            String jsonResponse = response.body().string();
//                            Log.e(TAG, "onResponse Success : " + jsonResponse);
//                            JSONObject jsonObject = new JSONObject(jsonResponse);
//                            JSONArray jsonArray = jsonObject.optJSONArray("nodes");
//                            nodeIds.clear();
//
//                            for (int i = 0; i < jsonArray.length(); i++) {
//
//                                String nodeId = jsonArray.optString(i);
//                                Log.e(TAG, "EspNode id : " + nodeId);
//
//                                if (espApp.nodeMap.get(nodeId) != null) {
//                                    EspNode node = espApp.nodeMap.get(nodeId);
//                                    espApp.nodeMap.put(nodeId, node);
//                                } else {
//                                    espApp.nodeMap.put(nodeId, null);
//                                }
//                                nodeIds.add(nodeId);
//                            }
//
//                            // FIXME Concurrent modification exception
//                            for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {
//
//                                String key = entry.getKey();
//                                if (!nodeIds.contains(key)) {
//                                    espApp.nodeMap.remove(key);
//                                }
//                            }
//
//                            if (espApp.nodeMap.size() != 0) {
//                                getNextNodeDetail(listener);
//                            } else {
//                                listener.onSuccess(null);
//                            }
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                            listener.onFailure(e);
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                            listener.onFailure(e);
//                        }
//                    } else {
//                        Log.e(TAG, "Response received : null");
//                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
//                    }
//                } else {
//
//                    if (response.body() != null) {
//
//                        try {
//                            String jsonResponse = response.body().string();
//                            Log.e(TAG, "onResponse Success : " + jsonResponse);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    listener.onFailure(new RuntimeException("Failed to get User device mapping"));
//                }
//            }
//
//            @Override
//            public void onFailure(Call<ResponseBody> call, Throwable t) {
//                t.printStackTrace();
//                listener.onFailure(new Exception(t));
//            }
//        });
//    }

//    private void getNextNodeDetail(final ApiResponseListener listener) {
//
//        if (isAllNodeConfigDone()) {
//            listener.onSuccess(null);
//        } else {
//            getNodeConfigFromNodeId(nodeIds.get(0), new ApiResponseListener() {
//
//                @Override
//                public void onSuccess(Bundle data) {
//
//                    if (isAllNodeConfigDone()) {
//                        listener.onSuccess(null);
//                    } else {
//                        getNextNodeDetail(listener);
//                    }
//                }
//
//                @Override
//                public void onFailure(Exception exception) {
//                    // TODO fix the issue
//                    exception.printStackTrace();
////                        listener.onFailure(exception);
//                    getNextNodeDetail(listener);
//                }
//            });
//        }
//    }

//    private boolean isAllNodeConfigDone() {
//
//        if (nodeIds != null && nodeIds.size() > 0) {
//            return false;
//        }
//        return true;
//    }

//    private void getNodeConfigFromNodeId(final String nodeId, final ApiResponseListener listener) {
//
//        Log.d(TAG, "Get getNodeConfigFromNodeId : " + nodeId);
//        Log.d(TAG, "Auth token : " + accessToken);
//
//        apiInterface.getDevicesFromNodeId(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {
//
//            @Override
//            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//
//                Log.d(TAG, "Request : " + call.request().toString());
//                Log.e(TAG, "onResponse code : " + response.code());
//                nodeIds.remove(nodeId);
//
//                if (response.isSuccessful()) {
//
//                    if (response.body() != null) {
//
//                        try {
//
//                            String jsonResponse = response.body().string();
//                            Log.e(TAG, "onResponse Success : " + jsonResponse);
//
//                            JSONObject jsonObject = new JSONObject(jsonResponse);
//                            EspNode espNode;
//                            if (espApp.nodeMap.get(nodeId) != null) {
//                                espNode = espApp.nodeMap.get(nodeId);
//                            } else {
//                                espNode = new EspNode(nodeId);
//                            }
//                            espNode.setConfigVersion(jsonObject.optString("config_version"));
//
//                            JSONObject infoObj = jsonObject.optJSONObject("info");
//                            espNode.setNodeName(infoObj.optString("name"));
//                            espNode.setFwVersion(infoObj.optString("fw_version"));
//                            espNode.setNodeType(infoObj.optString("type"));
//                            espNode.setOnline(true);
//
//                            JSONArray devicesJsonArray = jsonObject.optJSONArray("devices");
//                            ArrayList<EspDevice> devices = new ArrayList<>();
//
//                            if (devicesJsonArray != null) {
//
//                                for (int i = 0; i < devicesJsonArray.length(); i++) {
//
//                                    JSONObject deviceObj = devicesJsonArray.optJSONObject(i);
//                                    EspDevice device = new EspDevice(nodeId);
//                                    device.setDeviceName(deviceObj.optString("name"));
//                                    device.setDeviceType(deviceObj.optString("type"));
//                                    device.setPrimaryParamName(deviceObj.optString("primary"));
//
//                                    JSONArray paramsJson = deviceObj.optJSONArray("params");
//                                    ArrayList<Param> params = new ArrayList<>();
//
//                                    if (paramsJson != null) {
//
//                                        for (int j = 0; j < paramsJson.length(); j++) {
//
//                                            JSONObject paraObj = paramsJson.optJSONObject(j);
//                                            Param param = new Param();
//                                            param.setName(paraObj.optString("name"));
//                                            param.setParamType(paraObj.optString("type"));
//                                            param.setDataType(paraObj.optString("data_type"));
//                                            param.setUiType(paraObj.optString("ui_type"));
//                                            param.setDynamicParam(true);
//                                            params.add(param);
//
//                                            JSONArray propertiesJson = paraObj.optJSONArray("properties");
//                                            ArrayList<String> properties = new ArrayList<>();
//
//                                            if (propertiesJson != null) {
//                                                for (int k = 0; k < propertiesJson.length(); k++) {
//
//                                                    properties.add(propertiesJson.optString(k));
//                                                }
//                                            }
//                                            param.setProperties(properties);
//
//                                            JSONObject boundsJson = paraObj.optJSONObject("bounds");
//
//                                            if (boundsJson != null) {
//                                                param.setMaxBounds(boundsJson.optInt("max"));
//                                                param.setMinBounds(boundsJson.optInt("min"));
//                                            }
//                                        }
//                                    }
//
//                                    JSONArray attributesJson = deviceObj.optJSONArray("attributes");
//
//                                    if (attributesJson != null) {
//
//                                        for (int j = 0; j < attributesJson.length(); j++) {
//
//                                            JSONObject attrObj = attributesJson.optJSONObject(j);
//                                            Param param = new Param();
//                                            param.setName(attrObj.optString("name"));
//                                            param.setDataType(attrObj.optString("data_type"));
//                                            param.setLabelValue(attrObj.optString("value"));
//                                            params.add(param);
//                                        }
//                                    }
//
//                                    device.setParams(params);
//                                    devices.add(device);
//                                }
//                            }
//
//                            espNode.setDevices(devices);
//
//                            JSONArray nodeAttributesJson = infoObj.optJSONArray("attributes");
//                            ArrayList<Param> nodeAttributes = new ArrayList<>();
//
//                            if (nodeAttributesJson != null) {
//
//                                for (int j = 0; j < nodeAttributesJson.length(); j++) {
//
//                                    JSONObject attrObj = nodeAttributesJson.optJSONObject(j);
//                                    Param param = new Param();
//                                    param.setName(attrObj.optString("name"));
//                                    param.setLabelValue(attrObj.optString("value"));
//                                    nodeAttributes.add(param);
//                                }
//                            }
//
//                            espNode.setAttributes(nodeAttributes);
//
//                            espApp.nodeMap.put(nodeId, espNode);
//                            getOnlineOfflineStatus(nodeId);
//
//                            listener.onSuccess(null);
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                            listener.onFailure(e);
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                            listener.onFailure(e);
//                        }
//                    } else {
//                        Log.e(TAG, "Response received : null");
////                        listener.onFailure(new RuntimeException("Failed to get User device mapping"));
//                    }
//
//                } else {
//
//                    if (response.body() != null) {
//
//                        try {
//                            String jsonResponse = response.body().string();
//                            Log.e(TAG, "onResponse failure : " + jsonResponse);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    listener.onFailure(new RuntimeException("Failed to get Node config"));
//                }
//            }
//
//            @Override
//            public void onFailure(Call<ResponseBody> call, Throwable t) {
//                t.printStackTrace();
//                // TODO
////                listener.onFailure(new Exception(t));
//            }
//        });
//    }

//    private void getOnlineOfflineStatus(final String nodeId) {
//
//        Log.d(TAG, "getOnlineOfflineStatus");
//
//        apiInterface.getOnlineOfflineStatus(accessToken, nodeId).enqueue(new Callback<ResponseBody>() {
//
//            @Override
//            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//
//                Log.d(TAG, "Request : " + call.request().toString());
//                Log.d(TAG, "EspNode status response code : " + response.code());
//
//                if (response.isSuccessful()) {
//
//                    if (response.body() != null) {
//
//                        try {
//
//                            String jsonResponse = response.body().string();
//                            Log.e(TAG, "onResponse Success : " + jsonResponse);
//                            JSONObject jsonObject = new JSONObject(jsonResponse);
//                            JSONObject connectivityObject = jsonObject.optJSONObject("connectivity");
//
//                            if (connectivityObject != null) {
//
//                                boolean nodeStatus = connectivityObject.optBoolean("connected");
//
//                                for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {
//
//                                    String key = entry.getKey();
//                                    EspNode node = entry.getValue();
//
//                                    if (nodeId.equalsIgnoreCase(key)) {
//
//                                        long timestamp = connectivityObject.optLong("timestamp");
//                                        node.setTimeStampOfStatus(timestamp);
//
//                                        if (node.isOnline() != nodeStatus) {
//                                            node.setOnline(nodeStatus);
//                                            EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
//                                        }
//                                    }
//                                }
//                            } else {
//                                Log.e(TAG, "Connectivity object is null");
//                            }
//
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    } else {
//                        Log.e(TAG, "Get node status failed");
//                        boolean nodeStatus = false;
//                        for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {
//
//                            String key = entry.getKey();
//                            EspNode node = entry.getValue();
//
//                            if (nodeId.equalsIgnoreCase(key)) {
//
//                                if (node.isOnline() != nodeStatus) {
//                                    node.setOnline(nodeStatus);
//                                    EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
//                                }
//                            }
//                        }
//                    }
//
//                } else {
//
//                    boolean nodeStatus = false;
//                    for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {
//
//                        String key = entry.getKey();
//                        EspNode node = entry.getValue();
//
//                        if (nodeId.equalsIgnoreCase(key)) {
//
//                            if (node.isOnline() != nodeStatus) {
//                                node.setOnline(nodeStatus);
//                                EventBus.getDefault().post(new UpdateEvent(AppConstants.UpdateEventType.EVENT_DEVICE_STATUS_UPDATE));
//                            }
//                        }
//                    }
//                    Log.e(TAG, "Get node status failed");
//                }
//            }
//
//            @Override
//            public void onFailure(Call<ResponseBody> call, Throwable t) {
//                t.printStackTrace();
//            }
//        });
//    }


//    private Runnable getNodeStatusTask = new Runnable() {
//
//        @Override
//        public void run() {
//
//            getAllNodeStatus();
//        }
//    };

//    public void getNodeStatus() {
//
//        cancelGetNodeStatusTask();
//        getAllNodeStatus();
//    }
//

//    public void cancelGetNodeStatusTask() {
//
//        handler.removeCallbacks(getNodeStatusTask);
//    }

//    private void getAllNodeStatus() {
//
//        Set<String> nodeIdList = espApp.nodeMap.keySet();
//
//        for (String nodeId : nodeIdList) {
//            getOnlineOfflineStatus(nodeId);
//        }
////        handler.postDelayed(getNodeStatusTask, 10000);
//    }

}
