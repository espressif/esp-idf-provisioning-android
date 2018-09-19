# ESP-IDF Provisioning - Android

ESP-IDF consists of a provisioning mechanism, which is used to provide network credentials and/or custom data to an ESP32 device.
This repository contains the source code for the companion Android app for this provisioning mechanism.

This is licensed under Apache 2.0. The complete license for the same can be found in the LICENSE file.

## Configuring Build

There are multiple build options. It is a combination of 3 options - transport, security and release type.

- Transports 
 - WiFi
 - BLE
- Security
 - 0 (no security)
 - 1 (security as per IDF docs for provisioning)
- Release type
 - Debug
 - Release

So you can pick a build type as `bleSec1Debug`. Note that except for Release type, if you change any of the above options, you will have to change the corresponding firmware on ESP32.

# Resources

* Documentation for the latest version: https://docs.espressif.com/projects/esp-idf/. This documentation is built from the [docs directory](docs) of this repository.

* The [esp32.com forum](https://esp32.com/) is a place to ask questions and find community resources.

* [Check the Issues section on github](https://github.com/espressif/esp-idf/issues) if you find a bug or have a feature request. Please check existing Issues before opening a new one.

* If you're interested in contributing to ESP-IDF, please check the [Contributions Guide](https://docs.espressif.com/projects/esp-idf/en/latest/contribute/index.html).
