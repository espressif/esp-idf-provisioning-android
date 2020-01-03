package com.espressif.cloudapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * This class represents Retrofit Client to call REST APIs.
 */
public class ApiClient {

    private static Retrofit retrofit = null;

    public static final String BASE_URL = "https://sxeznlpg30.execute-api.us-east-1.amazonaws.com/testing";
    public static final String CURRENT_VERSION = "v1";
    public static final String PATH_SEPARATOR = "/";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    static Retrofit getClient() {

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL + PATH_SEPARATOR + CURRENT_VERSION + PATH_SEPARATOR)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit;
    }
}
