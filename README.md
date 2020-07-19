# ESP-IDF Provisioning - Android: AVS

ESP-IDF consists of a provisioning mechanism, which is used to provide network credentials and/or custom data to an ESP32 device.
This repository contains the source code for the companion Android app for this provisioning mechanism.

This is licensed under Apache 2.0. The complete license for the same can be found in the LICENSE file.

## Setup

To build this app, you will need a development machine, with Android Studio installed.
As we are building a mobile application that will provision network credentials as well AVS credentials, app will need the following details

1. Keystore certificate information 
2. API key from LWA

**NOTE**
- These are mandatory requirements and provisioning of Alexa on ESP32 will **not** work unless these 2 values have been configured in this Android project.
- Change `applicationId` in `app/build.gradle` file to develop and release your own Android app,  

#### Keystore certificate information  
Generate certificate for app using given steps in [this link](https://developer.android.com/studio/publish/app-signing#generate-key).  
Fill information of keystore certificate in `app/build.gradle` file in `signingConfigs` block.
```
signingConfigs {
	config {
		keyAlias 'nameofcert'
		storeFile file('path to cert')
		storePassword 'password'
		keyPassword 'password'
	}
}
```
Please find instructions on how to generate a Product ID from Alexa's developer console [here](https://github.com/alexa/avs-device-sdk/wiki/Create-Security-Profile).

#### API key from Login With Amazon (LWA)
As we are creating an Alexa product, we need to register as a developer with Amazon and use their `Login With Amazon` service for signing up users (even during testing). The complete documentation for understanding LWA can be found [here](https://developer.amazon.com/docs/login-with-amazon/documentation-overview.html).

The above link tells you how to - 
1. Download and use the LWA SDK for Android
2. Configure settings in your developer account.

Now you will have to follow the steps listed on [this](https://developer.amazon.com/docs/login-with-amazon/android-docs.html#getting-started) to do setup of LWA for Android.

We have already setup step 1 from above i.e. the LWA SDK for Android is a part of this respository. As you can see on this page, there are a total of 7 steps - of which you will have to do only Step 3. We highly recommend that you go through the other steps to understand some of the things that have already been done for you as part of this open source project.  

Here are the steps to perform "Step-3 (Register for Login with Amazon)" and generate API Key.

- Go to [Alexa console](https://developer.amazon.com/alexa/console/avs/home).
- Go to Product details -> Security profile
- Select Android / Kindle Tab.
- Enter API key name, package name (applicationId), MD5 and SHA256 signature of the above generated keystore certificate which will be used to sign your app.
- Click "GENERATE KEY".

Please input this value in `app/src/main/assets/api_key.txt` file of android app source code.

## Configuring Build

There are multiple build options. It is a combination of 2 options along with one mandatory `Avs` field -  security and release type.

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
