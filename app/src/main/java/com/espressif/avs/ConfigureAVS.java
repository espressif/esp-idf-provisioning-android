// Copyright 2018 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.espressif.avs;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.authorization.AuthCancellation;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeListener;
import com.amazon.identity.auth.device.api.authorization.AuthorizeRequest;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.authorization.ScopeFactory;
import com.amazon.identity.auth.device.api.workflow.RequestContext;
import com.espressif.provision.security.Security;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;

import avs.Avsconfig;

public class ConfigureAVS {
    public static final String PRODUCT_ID_KEY = "productId";
    public static final String PRODUCT_DSN_KEY = "productDSN";
    public static final String CODE_VERIFIER_KEY = "codeVerifier";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String AUTH_CODE_KEY = "authCode";
    public static final String REDIRECT_URI_KEY = "redirectUri";
    public static final String AVS_CONFIG_PATH = "avsconfig";
    public static final String AVS_CONFIG_UUID_KEY = "avsconfigUUID";
    private static final String TAG = "Espressif::" + ConfigureAVS.class.getSimpleName();
    private static final String DEVICE_SERIAL_NUMBER_KEY = "deviceSerialNumber";
    private static final String PRODUCT_INSTANCE_ATTRIBUTES_KEY = "productInstanceAttributes";
    private static final String ALEXA_SCOPE = "alexa:all";

    private Session session;
    private Security security;
    private Transport transport;

    public ConfigureAVS(Session session) {
        this.session = session;
        this.security = session.getSecurity();
        this.transport = session.getTransport();
    }

    public static void loginWithAmazon(Activity activity,
                                       String productId,
                                       String productDSN,
                                       final String codeVerifier,
                                       final AmazonLoginListener amazonLoginListener) {
        final RequestContext requestContext = RequestContext.create(activity);
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {

                if (activity.isDestroyed()) {
                    requestContext.onResume();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });

        requestContext.registerListener(new AuthorizeListener() {

            /* Authorization was completed successfully. */
            @Override
            public void onSuccess(AuthorizeResult result) {
                /* Your app is now authorized for the requested scopes */
                Log.d(TAG, "Client ID is " + result.getClientId());
                Log.d(TAG, "Authorization code is " + result.getAuthorizationCode());
                Log.d(TAG, "Redirect URI is " + result.getRedirectURI());
                if (amazonLoginListener != null) {
                    amazonLoginListener.LoginSucceeded(result.getClientId(),
                            result.getAuthorizationCode(),
                            result.getRedirectURI(),
                            codeVerifier);
                }
            }

            /* There was an error during the attempt to authorize the
            application. */
            @Override
            public void onError(AuthError ae) {
                Log.d(TAG, "Amazon Auth error :" + ae.toString());
                if (amazonLoginListener != null) {
                    amazonLoginListener.LoginFailed();
                }
            }

            /* Authorization was cancelled before it could be completed. */
            @Override
            public void onCancel(AuthCancellation cancellation) {
                Log.d(TAG, "Amazon Auth error :" + cancellation.getDescription());
                if (amazonLoginListener != null) {
                    amazonLoginListener.LoginFailed();
                }
            }
        });

        final JSONObject scopeData = new JSONObject();
        final JSONObject productInstanceAttributes = new JSONObject();

        try {
            productInstanceAttributes.put(DEVICE_SERIAL_NUMBER_KEY, productDSN);
            scopeData.put(PRODUCT_INSTANCE_ATTRIBUTES_KEY, productInstanceAttributes);
            scopeData.put("productID", productId);
            String codeChallenge = codeVerifier;
            AuthorizationManager.authorize(new AuthorizeRequest
                    .Builder(requestContext)
                    .addScope(ScopeFactory.scopeNamed(ALEXA_SCOPE, scopeData))
                    .forGrantType(AuthorizeRequest.GrantType.AUTHORIZATION_CODE)
                    .withProofKeyParameters(codeChallenge, "S256")
                    .build());
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            if (amazonLoginListener != null) {
                amazonLoginListener.LoginFailed();
            }
        }
    }

    public void configureAmazonLogin(String clientId,
                                     String authCode,
                                     String redirectUri,
                                     final ConfigureAVSActionListener actionListener) {

        if (this.session.isEstablished()) {

            byte[] message = createSetAVSConfigRequest(clientId,
                    authCode,
                    redirectUri);
            transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {
                @Override
                public void onSuccess(byte[] returnData) {
                    Avsconfig.AVSConfigStatus status = processSetAVSConfigResponse(returnData);
                    if (actionListener != null) {
                        actionListener.onComplete(status, null);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (actionListener != null) {
                        actionListener.onComplete(Avsconfig.AVSConfigStatus.InvalidParam, e);
                    }
                }
            });
        }
    }

    private byte[] createSetAVSConfigRequest(String clientId,
                                             String authCode,
                                             String redirectUri) {
        Avsconfig.CmdSetConfig configRequest = Avsconfig.CmdSetConfig.newBuilder()
                .setAuthCode(authCode)
                .setClientID(clientId)
                .setRedirectURI(redirectUri)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetConfig;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSetConfig(configRequest)
                .build();

        return this.security.encrypt(payload.toByteArray());
    }

    private Avsconfig.AVSConfigStatus processSetAVSConfigResponse(byte[] responseData) {
        byte[] decryptedData = this.security.decrypt(responseData);

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.UNRECOGNIZED;
        try {
            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetConfig response = Avsconfig.RespSetConfig.parseFrom(payload.toByteArray());
            status = response.getStatus();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    public interface AmazonLoginListener {
        void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier);

        void LoginFailed();
    }

    public interface ConfigureAVSActionListener {
        void onComplete(Avsconfig.AVSConfigStatus status, Exception e);
    }
}
