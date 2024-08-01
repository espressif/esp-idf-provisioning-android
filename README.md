# Provisioning Library  

Provisioning library provides a mechanism to send network credentials and/or custom data to ESP32 (or its variants like S2, S3, C3, etc.) or ESP8266 devices.

This repository contains the source code for the companion Android app for this provisioning mechanism.
To get this app please clone this repository using the below command:
```
 git clone https://github.com/espressif/esp-idf-provisioning-android.git
```

- [Features](#features)  
- [Requirements](#requirements)  
- [How to include](#how-to-include)  
- [Usage](#using-ESPProvision)  
  - [****Introduction****](#introduction)  
  - [****Getting ESPDevice****](#getting-ESPDevice)  
  - [****Provisioning****](#provisioning)  
  - [****Other Configuration****](#other-configuration)
- [License](#license)  
  
## Features  
  
- [x] Search for available BLE devices.  
- [x] Scan device QR code to provide reference to ESP device.  
- [x] Create reference of ESPDevice manually.  
- [x] Data Encryption  
- [x] Data transmission through BLE and SoftAP.  
- [x] Scan for available Wi-Fi networks. 
- [x] Provision device.  
- [x] Scan for available Wi-Fi networks.  
- [x] Support for exchanging custom data.
- [x] Support for security version 2.
  
## Requirements  
  
- Supports Android 8.0 (API level 26) and above.  

##  How to include  
  
 Add this in your root `build.gradle` at the end of repositories:
 ```
 allprojects {
	 repositories {
		 ...
		 maven { url 'https://jitpack.io' }
	 }
 }
 ```
And add a dependency code to your  app module's  `build.gradle`  file. 
```  
 implementation 'com.github.espressif:esp-idf-provisioning-android:lib-2.2.1'
```

## Using Provisioning Library
 ## Introduction    
 Provisioning library provides a simpler mechanism to communicate with an ESP-32, ESP32-S2 and ESP8266 devices. It gives an efficient search and scan model to listen and return devices which are in provisioning mode. It embeds security protocol and allow for safe transmission of data by doing end to end encryption. It supports BLE and SoftAP as mode of transmission which are configurable at runtime. Its primarily use is to provide home network credentials to a device and ensure device connectivity status is returned to the application.    
    
 ## Getting ESPDevice  
   
`ESPDevice` object is virtual representation of ESP-32/ESP32-S2/ESP8266 devices. It provides interface to interact with devices directly in a simpler manner.
`ESPProvisionManager` is a singleton class that encompasses APIs for searching ESP devices using BLE or SoftAP transport. Once app has received `ESPDevice` instance, app can maintain it for other API calls or it can receive same `ESPDevice` instance by calling API `getEspDevice()` of `ESPProvisionManager` class.

 `ESPDevice` instances can be obtained from two ways as described following : 
 
 ### QR Code Scan 
 Device information can be extracted from scanning valid QR code. API returns single `ESPDevice` instance on success. It supports both SoftAP and BLE.
 If your device does not have QR code, you can use any online QR code generator.
QR code payload is a JSON string representing a dictionary with key value pairs listed in the table below. An example payload :
`{"ver":"v1","name":"PROV_CE03C0","pop":"abcd1234","transport":"softap"}`

Payload information : 

| Key       	| Detail                             	| Values                                  	| Required                                                            	|
|-----------	|------------------------------------	|-----------------------------------------	|---------------------------------------------------------------------	|
| ver       	| Version of the QR code.            	| Currently, it must be v1.               	| Yes                                                                 	|
| name      	| Name of the device.                	| PROV_XXXXXX                             	| Yes                                                                 	|
| pop       	| Proof of possession.               	| POP value of the device like abcd1234   	| Optional. Considered empty string if not available in QR code data. 	|
| transport 	| Wi-Fi provisioning transport type. 	| It can be softap or ble.                	| Yes                                                                 	|
| security  	| Security for device communication. 	| It can be 0, 1 or 2 int value.            | Optional. Considered Sec2 if not available in QR code data.         	|
| password  	| Password of SoftAP device.         	| Password to connect with SoftAP device. 	| Optional                                                            	|

In provisioning library, there are two options for QR code scanning API. 
 
 1. User of this API decides the camera preview layer frame by providing `CameraSourcePreview` as parameter.

	Include `CameraSourcePreview` in your layout file.
	```xml
	<com.espressif.provisioning.CameraSourcePreview
		android:id="@+id/preview"
		android:layout_width="match_parent"
		android:layout_height="match_parent"
		/>
	```
	Provide this `CameraSourcePreview` instance to below API.
	```java
	 ESPProvisionManager.getInstance(context).scanQRCode(Activity activityContext, CameraSourcePreview cameraSourcePreview, QRCodeScanListener qrCodeScanListener)
	  ``` 

 2. User of this API needs to provide `CodeScanner`. For providing `CodeScanner' following things to be added in app.
	
	Add dependency in your `app/build.gradle`.
	```
	implementation 'com.budiyev.android:code-scanner:2.1.0'
	```
	
	Include `CodeScannerView` in your layout file.
	```xml
	<com.budiyev.android.codescanner.CodeScannerView  
		android:id="@+id/scanner_view"
		android:layout_width="match_parent"
		android:layout_height="match_parent"
		app:autoFocusButtonColor="@android:color/white"
		app:autoFocusButtonVisible="false"
		app:flashButtonColor="@android:color/white"
		app:flashButtonVisible="false"
		app:frameColor="@android:color/white"
		app:frameCornersSize="50dp"
		app:frameCornersRadius="0dp"
		app:frameAspectRatioWidth="1"
		app:frameAspectRatioHeight="1"
		app:frameSize="0.75"
		app:frameThickness="2dp"
		app:maskColor="#77000000"/>
	```
	Provide this `CodeScanner` instance to below API.
	```java
	ESPProvisionManager.getInstance(context).scanQRCode(CodeScanner codeScanner, QRCodeScanListener qrCodeScanListener)
	  ``` 
  
  ### Manually Create ESPDevice
 `ESPDevice` can be also created by passing necessary parameters as argument of below function. 
 ```java    
ESPProvisionManager.getInstance(context).createESPDevice(TransportType transportType, SecurityType securityType);  
  ```    
    
  This will return `ESPDevice` with given transport and security type.
  For manually creating `ESPDevice` flow, after creating `ESPDevice` instance, app also needs to call connect API as described below.
  
 1. For BLE Transport :
	For BLE transport type,  library will need BluetoothDevice to connect with actual device. To get BluetoothDevice app can call search API or also app can use own BLE scanning. Library supports searching of BLE devices which are currently in provisioning mode. It returns list of devices that are discoverable and matches the parameter criteria.  This API will return BluetoothDevice objects for the devices found in BLE scan with given prefix. 
	 ```java
	 ESPProvisionManager.getInstance(context).searchBleEspDevices(String prefix, BleScanListener bleScannerListener)
	 ```
	 After user select BLE device, app can call connect API. Primary service UUID will also require to call connect API. App can get that from `ScanResult`. 
	 ```java
	 espDevice.connectBLEDevice(BluetoothDevice bluetoothDevice, String primaryServiceUuid)
	 ```
	    
  2. For SoftAP Transport : 
	For SoftAP transport type, app can call connect API to connect with the device. 
	 ```java
	 espDevice.connectWiFiDevice()
	 ```
For both transport app can listen device connected / disconnected events by registering for events.


After device connection, app needs to get Proof of Possession from user, if device has pop capability.
App needs to set proof of possession for the device.

```java
espDevice.setProofOfPossession(pop)
```

For security version 2, app needs to provide username as shown below :

```java
espDevice.setUserName(username)
```

	
## Provisioning  
  
The main feature of Provisioning library is to provision ESP devices. Once we get instance of `ESPDevice` from above APIs we need to establish session with the device before we can transmit/receive data from it. After receiving device connected event, app can get device capabilities and also set Proof of possession if device has pop capability.  
  
After that application can proceed to scan list of available networks visible to device. This list can be used to give option to the user to choose network of their own choice.
  
```java  
  
espDevice.scanNetworks(final WiFiScanListener wifiScanListener); 
  
```  
  
User can choose to apply Wi-Fi settings from the above list or choose other Wi-Fi network to provision the device.  
  
```java  
  
espDevice.provision(final String ssid, final String passphrase, final ProvisionListener provisionListener);  
  
```  
  
  ## Other Configuration

#### Enable / Disable QR code support
QR code support can be enable/disable by setting true/false value of `isQrCodeSupported` filed available in `app/build.gradle`.

## License  
  

    Copyright 2020 Espressif Systems (Shanghai) PTE LTD  
     
    Licensed under the Apache License, Version 2.0 (the "License");  
    you may not use this file except in compliance with the License.  
    You may obtain a copy of the License at  
     
        http://www.apache.org/licenses/LICENSE-2.0  
     
    Unless required by applicable law or agreed to in writing, software  
    distributed under the License is distributed on an "AS IS" BASIS,  
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  
    See the License for the specific language governing permissions and  
    limitations under the License.
