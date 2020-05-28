
# Provisioning Library  
  
Provisioning library provides mechanism to send network credentials and/or custom data to an ESP32 or ESP32-S2 devices.  
This repository contains the source code for the companion Android app for this provisioning mechanism.
  
- [Features](#features)  
- [Requirements](#requirements)  
- [How to include](#how-to-include)  
- [Usage](#using-ESPProvision)  
  - [****Introduction****](#introduction)  
  - [****Getting ESPDevice****](#getting-ESPDevice)  
  - [****Provisioning****](#provisioning)  
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
  
## Requirements  
  
- Supports Android 7.0 (API level 24) and above.  

##  How to include  
  
  Add it in your root build.gradle at the end of repositories:
  

    allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

And add a dependency code to your  app module's  `build.gradle`  file.  

    implementation 'com.github.espressif:esp-idf-provisioning-android:lib-2.0'
  
## Using Provisioning Library  
  
## Introduction  
  
Provisioning library provides a simpler mechanism to communicate with an ESP-32 and ESP32-S2 devices. It gives an efficient search and scan model to listen and return devices which are in provisioning mode. It embeds security protocol and allow for safe transmission of data by doing end to end encryption. It supports BLE and SoftAP as mode of transmission which are configurable at runtime. Its primarily use is to provide home network credentials to a device and ensure device connectivity status is returned to the application.  
  
  
## Getting ESPDevice  
  
`ESPDevice` object is virtual representation of ESP-32/ESP32-S2 devices. It provides interface to interact with devices directly in a simpler manner. `ESPProvisionManager` is a singleton class that encompasses APIs for searching ESP devices using BLE or SoftAP transport.
 `ESPDevice` instances can be obtained from two ways as described following :   

   ### Scan  
  
Device information can be extracted from scanning valid QR code. User of this API decides the camera preview layer frame by providing `SurfaceView` as parameter. It return single `ESPDevice` instance on success. Supports both SoftAP and BLE.  
  
```java  
  
ESPProvisionManager.getInstance(context).scanQRCode(Activity activityContext, SurfaceView surfaceView, QRCodeScanListener qrCodeScanListener)
  
```  
### Create  
  
`ESPDevice` can be also created by passing necessary parameters as argument of below function.  
  
```java  
  
ESPProvisionManager.getInstance(context).createESPDevice(TransportType transportType, SecurityType securityType);
  
```  
  
  > This will return ESPDevice with given transport and security type.

 1. For BLE Transport
	> For BLE transport type,  library will need BluetoothDevice to connect with actual device. To get BluetoothDevice app can call search API or also app can use own BLE scanning. Library supports searching of BLE devices which are currently in provisioning mode. It returns list of devices that are discoverable and matches the parameter criteria.  This API will return BluetoothDevice objects for the devices found in BLE scan with given prefix.
	> After user select BLE device, app can call connect API.
	  
```java  
  
ESPProvisionManager.getInstance(context).searchBleEspDevices(String prefix, BleScanListener bleScannerListener)  
  
```  
	
> After user select BLE device, app can call connect API.

```java  
  
espDevice.connectBLEDevice(BluetoothDevice bluetoothDevice, String primaryServiceUuid)
  
``` 

	
 2. For SoftAP Transport

  > For SoftAP transport type, app can call connect API to connect with the device.

```java  
  
espDevice.connectWiFiDevice()  
  
```  
  
> For both transport app can listen device connected / disconnected events by resigtering for events.
  
## Provisioning  
  
The main feature of Provisioning library is to provision ESP devices. Once we get instance of `ESPDevice` from above APIs we need to establish session with the device before we can transmit/receive data from it. After receiving device connected event, app can get device capabilities and also set Proof of possesion if device has pop capability.  
  
After that application can proceed to scan list of available networks visible to device. This list can be used to give option to the user to choose network of their own choice.  
  
```java  
  
espDevice.scanNetworks(final WiFiScanListener wifiScanListener); 
  
```  
  
User can choose to apply Wi-Fi settings from the above list or choose other Wi-Fi network to provision the device.  
  
```java  
  
espDevice.provision(final String ssid, final String passphrase, final ProvisionListener provisionListener);  
  
```  
  
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
