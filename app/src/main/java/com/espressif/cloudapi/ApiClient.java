package com.espressif.cloudapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * This class represents Retrofit Client to call REST APIs.
 */
public class ApiClient {

    private static Retrofit retrofit = null;

    public static final String BASE_URL = "https://61h45uifta.execute-api.us-east-1.amazonaws.com/demo/";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    static Retrofit getClient() {

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit;
    }
}
