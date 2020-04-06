package com.espressif.cloudapi;

import com.espressif.AppConstants;
import com.google.gson.JsonObject;

import java.util.HashMap;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

/**
 * This class represents interface for all APIs.
 */
public interface ApiInterface {

    @FormUrlEncoded
    @POST(AppConstants.TOKEN_URL)
    Call<ResponseBody> loginWithGithub(@Header("Content-type") String contentType,
                                       @Field("grant_type") String grant_type,
                                       @Field("client_id") String client_id,
                                       @Field("code") String code,
                                       @Field("redirect_uri") String redirect_uri);

    // Get Supported Versions
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + "apiversions")
    Call<ResponseBody> getSupportedVersions();

    // Do login (for GitHub / Google login)
    @POST(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/login")
    Call<ResponseBody> getOAuthLoginToken(@Body HashMap<String, String> body);

    // Get Nodes
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes?node_details=true")
    Call<ResponseBody> getNodes(@Header(AppConstants.HEADER_AUTHORIZATION) String token);

    // Get Single Node
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes")
    Call<ResponseBody> getNode(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("node_id") String nodeId);

    // Get Nodes
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes")
    Call<ResponseBody> getDevicesForUser(@Header(AppConstants.HEADER_AUTHORIZATION) String token);

    // Get EspNode Detail
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/config")
    Call<ResponseBody> getDevicesFromNodeId(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);

    // Add Device
    @PUT(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> addDevice(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Get Add device request status
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> getAddNodeRequestStatus(@Header(AppConstants.HEADER_AUTHORIZATION) String token,
                                               @Query("request_id") String requestId, @Query("user_request") boolean userReq);

    // Get dynamic param value
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/params")
    Call<ResponseBody> getParamValue(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);

    // Update dynamic param value
    @PUT(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/params")
    Call<ResponseBody> updateParamValue(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId, @Body JsonObject body);

    // Remove Device
    @PUT(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/mapping")
    Call<ResponseBody> removeDevice(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Body DeviceOperationRequest rawJsonString);

    // Get Online / Offline Status of Devices
    @GET(AppConstants.BASE_URL + AppConstants.PATH_SEPARATOR + AppConstants.CURRENT_VERSION + "/user/nodes/status")
    Call<ResponseBody> getOnlineOfflineStatus(@Header(AppConstants.HEADER_AUTHORIZATION) String token, @Query("nodeid") String nodeId);
}
