# ESP-IDF Provisioning - Android: AVS

ESP-IDF consists of a provisioning mechanism, which is used to provide network credentials and/or custom data to an ESP32 device.
This repository contains the source code for the companion Android app for this provisioning mechanism.

This is licensed under Apache 2.0. The complete license for the same can be found in the LICENSE file.

## Setup

To build this app, you will need a development machine, with Android Studio installed.

### Get Alexa credentials

As we are building a mobile application that will provision network credentials as well AVS credentials, we will need the following details

- Product ID
- API key from LWA

**NOTE** - these are mandatory requirements and provisioning of Alexa on ESP32 will **not** work unless these 2 values have been configured in this Android project.

#### Product ID
In the `build.gradle` file (for the app module), add product ID under the buildTypes configuration. Please find instructions on how to generate a Product ID from Alexa's developer console [here](https://github.com/alexa/avs-device-sdk/wiki/Create-Security-Profile).

#### API key from Login With Amazon (LWA)
As we are creating an Alexa product, we need to register as a developer with Amazon and use their `Login With Amazon` service for signing up users (even during testing). The complmete documentation for understanding LWA can be found [here](https://developer.amazon.com/docs/login-with-amazon/documentation-overview.html).

The above link tells you how to -

1. download and use the LWA SDK for Android 
2. configure settings in your developer account.

We have already setup step 1 from above i.e. the LWA SDK for Android is a part of this respository. Now you will have to follow the steps listed on [this](https://developer.amazon.com/docs/login-with-amazon/install-sdk-android.html) page to configure settings. As you can see on this page, there are a total of 7 steps - of which you will have to do only Step 3. We highly recommend that you go through the other steps to understand some of the things that have already been done for you as part of this open source project.

After you have completed everything that's required as part of [Step 3](https://developer.amazon.com/docs/login-with-amazon/register-android.html), you should have in your possession an API key. Please input this value in `api_key.txt`.

## Configuring Build

There are multiple build options. It is a combination of 3 options along with one mandatory `Avs` field - transport, security and release type.

- Transports 
  - WiFi
  - BLE
- Security
  - 0 (no security)
  - 1 (security as per IDF docs for provisioning)
- Avs
- Release type
  - Debug
  - Release

So you can pick a build type as `bleSec1AvsDebug`. Note that except for Release type, if you change any of the above options, you will have to change the corresponding firmware on ESP32.

# Resources

* Documentation for the latest version of IDF: https://docs.espressif.com/projects/esp-idf/.

* The [esp32.com forum](https://esp32.com/) is a place to ask questions and find community resources.

* [Check the Issues section on github](https://github.com/espressif/esp-idf-provisioning-android/issues) if you find a bug or have a feature request. Please check existing Issues before opening a new one.

* If you're interested in contributing to ESP-IDF, please check the [Contributions Guide](https://docs.espressif.com/projects/esp-idf/en/latest/contribute/index.html).
