package com.espressif.cloudapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * This class represents Retrofit Client to call REST APIs.
 */
public class ApiClient {

    private static Retrofit retrofit = null;

    public static final String BASE_URL = "https://qp2pvsorac.execute-api.us-east-1.amazonaws.com/azoteq/";
    public static final String HEADER_AUTHORIZATION = "Authorization";

    static Retrofit getClient() {

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit;
    }

}
