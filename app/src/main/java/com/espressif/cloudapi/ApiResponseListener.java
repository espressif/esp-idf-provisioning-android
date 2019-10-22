package com.espressif.cloudapi;

import android.os.Bundle;

/**
 * This response listener is used to psss received response from ApiManager to calling class.
 */
public interface ApiResponseListener {

    void onSuccess(Bundle data);

    void onFailure(Exception exception);
}
