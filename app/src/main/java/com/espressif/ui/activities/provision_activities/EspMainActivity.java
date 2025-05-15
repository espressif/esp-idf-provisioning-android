// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.activities.provision_activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.espressif.AppConstants;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.mediwatch.BuildConfig;
import com.espressif.mediwatch.R;
import com.espressif.ui.activities.MainActivity; // ← AÑADE ESTA IMPORTACIÓN
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.ui.dialogs.ProgressDialogFragment;
import com.espressif.ui.viewmodels.MqttViewModel;
import com.google.android.material.button.MaterialButton;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = EspMainActivity.class.getSimpleName();

    // Request codes
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private ESPProvisionManager provisionManager;
    private MaterialButton btnAddDevice;
    private ImageView ivEsp;
    private SharedPreferences sharedPreferences;
    private String deviceType;
    private MaterialButton btnFindDevice;
    private DeviceConnectionChecker deviceChecker;
    private ProgressDialogFragment progressDialog;
    private boolean isWaitingForBluetoothActivation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initViews();

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        provisionManager = ESPProvisionManager.getInstance(getApplicationContext());
        
        // Mostrar mensaje informativo al usuario
        new AlertDialog.Builder(this)
                .setTitle("Configuración de dispositivo")
                .setMessage("No se ha detectado ningún dispositivo MEDIWATCH en la red. Puedes configurar un nuevo dispositivo o buscar uno existente.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        deviceType = sharedPreferences.getString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT);
        if (deviceType.equals("wifi")) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT);
            editor.apply();
        }

        deviceType = sharedPreferences.getString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT);
        if (deviceType.equals(AppConstants.DEVICE_TYPE_BLE)) {
            ivEsp.setImageResource(R.drawable.ic_esp_ble);
        } else if (deviceType.equals(AppConstants.DEVICE_TYPE_SOFTAP)) {
            ivEsp.setImageResource(R.drawable.ic_esp_softap);
        } else {
            ivEsp.setImageResource(R.drawable.ic_esp);
        }
        
        // Verificar si estamos esperando la activación manual de Bluetooth
        if (isWaitingForBluetoothActivation) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null && bluetoothManager.getAdapter().isEnabled()) {
                isWaitingForBluetoothActivation = false;
                // Solo continuar si los permisos están concedidos
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                     checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) {
                    startProvisioningFlow();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        if (BuildConfig.isSettingsAllowed) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.menu_settings, menu);
            return true;
        } else {
            menu.clear();
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_LOCATION) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (isLocationEnabled()) {
                    addDeviceClick();
                }
            }
        }

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth is turned ON, you can provision device now.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        // Manejo de permisos de Bluetooth
        if (requestCode == 3) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                // Intentar activar Bluetooth nuevamente
                addDeviceClick();
            } else {
                Toast.makeText(this, "Los permisos de Bluetooth son necesarios para provisionar el dispositivo", 
                              Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initViews() {

        ivEsp = findViewById(R.id.iv_esp);

        // Inicializar el botón de provisioning (asegúrate de usar el ID correcto)
        btnAddDevice = findViewById(R.id.btn_provision_device);
        btnAddDevice.setOnClickListener(addDeviceBtnClickListener);

        // Inicializar el botón de búsqueda de dispositivos
        btnFindDevice = findViewById(R.id.btn_find_device);
        btnFindDevice.setOnClickListener(findDeviceBtnClickListener);

        TextView tvAppVersion = findViewById(R.id.tv_app_version);

        String version = "";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String appVersion = getString(R.string.app_version) + " - v" + version;
        tvAppVersion.setText(appVersion);
    }

    View.OnClickListener addDeviceBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (!isLocationEnabled()) {
                    askForLocation();
                    return;
                }
            }
            addDeviceClick();
        }
    };

    View.OnClickListener findDeviceBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            searchForDevice();
        }
    };

    private void addDeviceClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!isLocationEnabled()) {
                askForLocation();
                return;
            }
        }

        if (BuildConfig.isQrCodeSupported) {
            gotoQrCodeActivity();
        } else {
            if (deviceType.equals(AppConstants.DEVICE_TYPE_BLE) || deviceType.equals(AppConstants.DEVICE_TYPE_BOTH)) {
                // Primero verificamos si tenemos los permisos necesarios en Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        // Solicitar permisos
                        requestPermissions(new String[]{
                                android.Manifest.permission.BLUETOOTH_SCAN, 
                                android.Manifest.permission.BLUETOOTH_CONNECT
                        }, 3);
                        return;
                    }
                }

                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter bleAdapter = bluetoothManager.getAdapter();

                if (!bleAdapter.isEnabled()) {
                    try {
                        // Intentamos usar el método tradicional para todas las versiones
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    } catch (SecurityException e) {
                        // Si falla (puede ocurrir en Android 12+ incluso con permisos concedidos), 
                        // usamos el método alternativo con dialog
                        Log.e(TAG, "Error activando Bluetooth: " + e.getMessage());
                        
                        // Mostrar diálogo más amigable
                        new AlertDialog.Builder(this)
                            .setTitle("Activar Bluetooth")
                            .setMessage("Para conectarte al dispositivo MEDIWATCH, es necesario activar el Bluetooth.")
                            .setPositiveButton("Activar", (dialog, which) -> {
                                isWaitingForBluetoothActivation = true;
                                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                    }
                } else {
                    startProvisioningFlow();
                }
            } else {
                startProvisioningFlow();
            }
        }
    }

    private void startProvisioningFlow() {

        deviceType = sharedPreferences.getString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT);
        final boolean isSec1 = sharedPreferences.getBoolean(AppConstants.KEY_SECURITY_TYPE, true);
        Log.d(TAG, "Device Types : " + deviceType);
        Log.d(TAG, "isSec1 : " + isSec1);
        int securityType = 0;
        if (isSec1) {
            securityType = 1;
        }

        if (deviceType.equals(AppConstants.DEVICE_TYPE_BLE)) {

            if (isSec1) {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
            } else {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_0);
            }
            goToBLEProvisionLandingActivity(securityType);

        } else if (deviceType.equals(AppConstants.DEVICE_TYPE_SOFTAP)) {

            if (isSec1) {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
            } else {
                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
            }
            goToWiFiProvisionLandingActivity(securityType);

        } else {

            final String[] deviceTypes = {"BLE", "SoftAP"};
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setTitle(R.string.dialog_msg_device_selection);
            final int finalSecurityType = securityType;
            builder.setItems(deviceTypes, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int position) {

                    switch (position) {
                        case 0:

                            if (isSec1) {
                                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
                            } else {
                                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_0);
                            }
                            goToBLEProvisionLandingActivity(finalSecurityType);
                            break;

                        case 1:

                            if (isSec1) {
                                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_1);
                            } else {
                                provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_SOFTAP, ESPConstants.SecurityType.SECURITY_0);
                            }
                            goToWiFiProvisionLandingActivity(finalSecurityType);
                            break;
                    }
                    dialog.dismiss();
                }
            });
            builder.show();
        }
    }

    private void askForLocation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_gps);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION);
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private boolean isLocationEnabled() {

        boolean gps_enabled = false;
        boolean network_enabled = false;
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        Log.d(TAG, "GPS Enabled : " + gps_enabled + " , Network Enabled : " + network_enabled);

        boolean result = gps_enabled || network_enabled;
        return result;
    }

    private void gotoQrCodeActivity() {
        Intent intent = new Intent(EspMainActivity.this, AddDeviceActivity.class);
        startActivity(intent);
    }

    private void goToBLEProvisionLandingActivity(int securityType) {

        Intent intent = new Intent(EspMainActivity.this, BLEProvisionLanding.class);
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType);
        startActivity(intent);
    }

    private void goToWiFiProvisionLandingActivity(int securityType) {

        Intent intent = new Intent(EspMainActivity.this, ProvisionLanding.class);
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType);
        startActivity(intent);
    }

    private void searchForDevice() {
        // Mostrar diálogo de progreso
        showProgressDialog(getString(R.string.device_search_title),
                getString(R.string.device_search_message));

        // Inicializar el DeviceConnectionChecker
        deviceChecker = new DeviceConnectionChecker(this);

        // Comprobar conexión del dispositivo
        deviceChecker.checkConnection(new DeviceConnectionChecker.ConnectionCheckListener() {
            @Override
            public void onConnectionCheckResult(boolean isConnected) {
                runOnUiThread(() -> {
                    // Cerrar diálogo de progreso
                    dismissProgressDialog();

                    if (isConnected) {
                        // Mostrar mensaje de éxito
                        Toast.makeText(EspMainActivity.this,
                                getString(R.string.device_found),
                                Toast.LENGTH_SHORT).show();

                        // Guardar estado de provisioning
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(AppConstants.KEY_IS_PROVISIONED, true);
                        editor.apply();

                        // Ir a la pantalla principal
                        Intent intent = new Intent(EspMainActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish(); // Cerrar esta actividad para no volver a ella con el botón atrás
                    } else {
                        // Mostrar mensaje de error
                        showErrorDialog(getString(R.string.no_device_found),
                                "No se detectó ningún dispositivo ESP32 en la red. " +
                                        "Asegúrate de que el dispositivo esté encendido y conectado a WiFi.");
                    }

                    // Liberar recursos
                    if (deviceChecker != null) {
                        deviceChecker.release();
                        deviceChecker = null;
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    // Cerrar diálogo de progreso
                    dismissProgressDialog();

                    // Mostrar mensaje de error
                    showErrorDialog(getString(R.string.device_search_error), errorMessage);

                    // Liberar recursos
                    if (deviceChecker != null) {
                        deviceChecker.release();
                        deviceChecker = null;
                    }
                });
            }
        });
    }

    private void showProgressDialog(String title, String message) {
        FragmentManager fm = getSupportFragmentManager();

        // Cerrar diálogo existente si hay alguno
        if (progressDialog != null && progressDialog.isAdded()) {
            progressDialog.dismiss();
        }

        // Crear y mostrar nuevo diálogo
        progressDialog = ProgressDialogFragment.newInstance(title, message);
        progressDialog.setOnCancelListener(dialog -> {
            // Cancelar búsqueda si el usuario cierra el diálogo
            if (deviceChecker != null) {
                deviceChecker.release();
                deviceChecker = null;
            }
        });
        progressDialog.show(fm, "progress_dialog");
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isAdded()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    private void showSuccessDialog() {
        // Obtener el nombre del dispositivo ESP32
        String rawDeviceName = provisionManager.getEspDevice().getDeviceName();
        
        // Formatear el nombre para asegurar formato PROV_XXXXXX
        String deviceName;
        if (rawDeviceName != null && !rawDeviceName.isEmpty()) {
            if (rawDeviceName.startsWith("PROV_")) {
                deviceName = rawDeviceName;
            } else {
                // Extraer los últimos 6 caracteres o usar todo el string si es más corto
                String suffix = rawDeviceName.length() > 6 
                    ? rawDeviceName.substring(rawDeviceName.length() - 6) 
                    : rawDeviceName;
                // Convertir a mayúsculas y añadir prefijo PROV_
                deviceName = "PROV_" + suffix.toUpperCase();
            }
        } else {
            // Generar un ID aleatorio si no hay nombre
            deviceName = "PROV_" + String.format("%06X", (int)(Math.random() * 0xFFFFFF));
        }

        Log.d(TAG, "Nombre original: " + rawDeviceName);
        Log.d(TAG, "Nombre formateado: " + deviceName);

        // Guardar el nombre formateado
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(AppConstants.KEY_DEVICE_NAME, deviceName);
        editor.apply();

        // Propagar el cambio al MqttViewModel
        MqttViewModel mqttViewModel = new ViewModelProvider(this).get(MqttViewModel.class);
        mqttViewModel.updateDeviceName(deviceName);

        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.device_found))
            .setMessage("¡Se ha encontrado un dispositivo ESP32 en la red! " +
                    "Ya puedes comenzar a monitorearlo.\n" +
                    "ID del dispositivo: " + deviceName)
            .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                navigateToMainActivity();
            })
            .show();
    }

    /**
     * Navega a la pantalla principal de la aplicación
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(EspMainActivity.this, MainActivity.class);
        // Agregar banderas para limpiar la pila de actividades anteriores
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Finalizar esta actividad para que no quede en segundo plano
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Asegurarse de liberar los recursos MQTT
        if (deviceChecker != null) {
            deviceChecker.release();
            deviceChecker = null;
        }
    }
}
