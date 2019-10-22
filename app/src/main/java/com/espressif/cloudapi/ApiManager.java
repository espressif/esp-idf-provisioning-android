package com.espressif.cloudapi;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.provision.R;
import com.espressif.ui.user_module.AppHelper;
import com.espressif.ui.user_module.EspDevice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiManager {

    private static final String TAG = ApiManager.class.getSimpleName();

    public static String userName = "";
    public static String userId = "";

    private Context context;
    private ApiInterface apiInterface;

    public ApiManager(Context context) {
        this.context = context;
        apiInterface = ApiClient.getClient().create(ApiInterface.class);
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

        apiInterface.getUserId(authToken, context.getString(R.string.user_pool_id), email)

                .enqueue(new Callback<ResponseBody>() {

                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                        Log.d(TAG, "onResponse code  : " + response.code());

                        if (response.isSuccessful()) {

                            if (response.body() != null) {

                                try {
                                    String jsonResponse = response.body().string();
                                    Log.e(TAG, "onResponse Success : " + jsonResponse);

                                    JSONObject data = new JSONObject(jsonResponse);
                                    userId = data.getString("user_id");
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
    public void getDevices(final ApiResponseListener listener) {

        Log.d(TAG, "Get Devices");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        apiInterface.getDevicesForUser(authToken, context.getString(R.string.user_pool_id), userId).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "onResponse code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {

                        try {

                            String jsonResponse = response.body().string();
                            Log.e(TAG, "onResponse Success : " + jsonResponse);

                            JSONArray jsonArray = new JSONArray(jsonResponse);
                            ArrayList<EspDevice> deviceList = new ArrayList<>();

                            for (int i = 0; i < jsonArray.length(); i++) {

                                JSONObject jsonObject = jsonArray.getJSONObject(i);

                                EspDevice device = new EspDevice();
                                device.setDeviceId(jsonObject.getString("device_id"));
                                device.setDeviceName(jsonObject.getString("name"));
                                device.setFwVersion(jsonObject.getString("fw_version"));

                                String status = jsonObject.getString("status");
                                if (!TextUtils.isEmpty(status) && status.equalsIgnoreCase("online")) {
                                    device.setOnline(true);
                                }

                                deviceList.add(device);
                            }

                            Bundle bundle = new Bundle();
                            bundle.putParcelableArrayList("devices", deviceList);

                            Log.d(TAG, "Device list : " + deviceList);
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
     * @param deviceId  Device Id.
     * @param secretKey Generated Secret Key.
     * @param listener  Listener to send success or failure.
     */
    public void addDevice(String deviceId, String secretKey, final ApiResponseListener listener) {

        Log.d(TAG, "Add Device");
        String authToken = AppHelper.getCurrSession().getIdToken().getJWTToken();
        Log.d(TAG, "Auth token : " + authToken);

        AddDeviceRequest req = new AddDeviceRequest();
        req.setUserId(userId);
        req.setDeviceId(deviceId);
        req.setSecretKey(secretKey);
        req.setOperation("add");

        apiInterface.addDevice(authToken, context.getString(R.string.user_pool_id), req).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.e(TAG, "Add device response code : " + response.code());

                if (response.isSuccessful()) {

                    if (response.body() != null) {
                        try {
                            Log.e(TAG, "Response : " + response.body().string());
                            listener.onSuccess(null);
                        } catch (IOException e) {
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
}
