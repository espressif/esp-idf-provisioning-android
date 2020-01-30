package com.espressif.cloudapi;

import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * This class represents interface for all APIs.
 */
public interface ApiInterface {

    // Get Supported Versions
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + "apiversions")
    Call<ResponseBody> getSupportedVersions();

    // Get User Id
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user")
    Call<ResponseBody> getUserId(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("user_name") String userName);

    // Get Nodes
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes")
    Call<ResponseBody> getDevicesForUser(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("userid") String userId);

    // Get EspNode Detail
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/config")
    Call<ResponseBody> getDevicesFromNodeId(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);

    // Add Device
    @PUT(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> addDevice(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Get Add device request status
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> getAddNodeRequestStatus(@Header(ApiClient.HEADER_AUTHORIZATION) String token,
                                               @Query("userid") String userid,
                                               @Query("request_id") String requestId, @Query("user_request") boolean userReq);

    // Get dynamic param value
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/params")
    Call<ResponseBody> getParamValue(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);

    // Update dynamic param value
    @PUT(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/params")
    Call<ResponseBody> updateParamValue(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId, @Body JsonObject body);

    // Remove Device
    @PUT(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> removeDevice(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Get Online / Offline Status of Devices
    @GET(ApiClient.BASE_URL + ApiClient.PATH_SEPARATOR + ApiClient.CURRENT_VERSION + "/user/nodes/status")
    Call<ResponseBody> getOnlineOfflineStatus(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);
}
