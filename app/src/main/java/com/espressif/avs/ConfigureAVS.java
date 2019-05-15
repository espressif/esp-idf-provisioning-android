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

//    private Session session;
//    private Security security;
//    private Transport transport;
//
//    public ConfigureAVS(Session session) {
//        this.session = session;
//        this.security = session.getSecurity();
//        this.transport = session.getTransport();
//    }
}
