package com.espressif.cloudapi;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * This class represents interface for all APIs.
 */
public interface ApiInterface {

    // Get User Id
    @GET(ApiClient.BASE_URL + "customer/users/{user_pool_id}?")
    Call<ResponseBody> getUserId(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Path("user_pool_id") String poolId, @Query("user_name") String userName);

    // Get Devices
    @GET(ApiClient.BASE_URL + "user/device/{user_pool_id}?")
    Call<ResponseBody> getDevicesForUser(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Path("user_pool_id") String poolId, @Query("userid") String userId);

    // Add Device
    @PUT(ApiClient.BASE_URL + "user/device/{user_pool_id}")
    Call<ResponseBody> addDevice(@Header(ApiClient.HEADER_AUTHORIZATION) String token, @Path("user_pool_id") String poolId, @Body AddDeviceRequest rawJsonString);
}
