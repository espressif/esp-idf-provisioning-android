package com.espressif.ui.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.ui.activities.provision_activities.EspMainActivity;
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.mediwatch.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.espressif.data.model.User;
import com.espressif.data.repository.UserRepository;

import java.util.Arrays;
import java.util.List;

import android.app.ProgressDialog;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private SharedPreferences preferences;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private AlertDialog activeDialog;

    private static final int RC_SIGN_IN = 123;

    private UserRepository userRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Verificar Google Play Services primero
        if (!checkGooglePlayServices()) {
            Toast.makeText(this, "Google Play Services no está disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Inicializar repositorio
        userRepository = UserRepository.getInstance(this);
        
        // Inicializar SharedPreferences
        preferences = getSharedPreferences(AppConstants.PREF_NAME_USER, MODE_PRIVATE);
        
        // Configurar Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Registrar el ActivityResultLauncher
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        Log.e(TAG, "Resultado nulo de Google Sign In");
                        showUserTypeDialog();
                    }
                });
        
        // Verificar si el usuario ya está autenticado
        checkUserStatus();
    }

    /**
     * Verifica el estado del usuario y dirige al flujo correspondiente
     */
    private void checkUserStatus() {
        String userType = userRepository.getUserType();
        boolean isLoggedIn = userRepository.isUserLoggedIn();
        boolean hasCompletedProvisioning = userRepository.hasCompletedProvisioning();

        if (userType == null) {
            // Primera vez - mostrar diálogo de selección
            showUserTypeDialog();
        } else if (userType.equals(AppConstants.USER_TYPE_PATIENT) && !isLoggedIn) {
            // Es paciente pero no ha iniciado sesión
            showGoogleSignInDialog();
        } else if (userType.equals(AppConstants.USER_TYPE_FAMILY) && 
                  userRepository.getConnectedPatientId() == null) {
            // Es familiar pero no se ha conectado con ningún paciente
            showFamilyConnectionDialog();
        } else {
            // Usuario ya configurado, verificar si hay dispositivo conectado
            checkDeviceConnection();
        }
    }

    /**
     * Verifica si hay un dispositivo ESP32 conectado antes de decidir qué pantalla mostrar
     */
    private void checkDeviceConnection() {
        // Mostrar un diálogo de progreso
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.device_search_message));
        progressDialog.setTitle(getString(R.string.device_search_title));
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Verificar si hay dispositivo conectado
        DeviceConnectionChecker deviceChecker = new DeviceConnectionChecker(this);
        deviceChecker.checkConnection(new DeviceConnectionChecker.ConnectionCheckListener() {
            @Override
            public void onConnectionCheckResult(boolean isConnected) {
                runOnUiThread(() -> {
                    // Cerrar el diálogo de progreso
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    if (isConnected) {
                        // ¡Genial! Un dispositivo está conectado, vamos directo al MainActivity
                        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // No hay dispositivo conectado, mostrar la pantalla de provisioning
                        startEspMainActivity();
                    }
                    
                    // Liberar recursos
                    deviceChecker.release();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    // Cerrar el diálogo de progreso
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    
                    // Mostrar error específico de conexión de dispositivo
                    showErrorDialog(errorMessage);
                    
                    // Liberar recursos
                    deviceChecker.release();
                });
            }
        });
    }

    /**
     * Muestra un diálogo de error para problemas de conexión de dispositivo
     */
    private void showErrorDialog(String errorMessage) {
        String title = "Error de conexión";
        String message = "No se pudo conectar con el dispositivo. Por favor verifica que esté encendido e intenta nuevamente.";
        
        // Registrar el error para depuración
        Log.e(TAG, "Error de conexión de dispositivo: " + errorMessage);
        
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Reintentar", (dialog, which) -> {
                checkDeviceConnection();
            })
            .setNegativeButton("Cancelar", (dialog, which) -> {
                // Ir al flujo de provisioning de todos modos
                startEspMainActivity();
            })
            .show();
    }

    /**
     * Categoriza y muestra errores relacionados con la verificación de ID de paciente
     */
    private void handlePatientIdVerificationError(ProgressDialog progressDialog, String errorMessage) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        
        // Registrar el error original para depuración
        Log.e(TAG, "Error original al verificar ID: " + errorMessage);
        
        // Categorizar el error
        String messageToShow;
        String dialogTitle;
        
        // Categoría 1: ID no encontrado
        if (errorMessage.contains("No se encontró") || 
            errorMessage.contains("No se encontraron") ||
            errorMessage.contains("no existe") ||
            errorMessage.contains("inválido") ||
            errorMessage.contains("invalid")) {
            dialogTitle = "ID no reconocido";
            messageToShow = "No se pudo reconocer el ID ingresado. Verifica que el ID esté escrito correctamente.";
        } 
        // Categoría 2: Problemas de conexión
        else if (errorMessage.contains("Firebase") || 
                 errorMessage.contains("conexión") ||
                 errorMessage.contains("network") ||
                 errorMessage.contains("red") ||
                 errorMessage.contains("timeout") ||
                 errorMessage.contains("inicializado")) {
            dialogTitle = "Error de conexión";
            messageToShow = "Error de conexión con el servidor. Por favor verifica tu conexión a Internet e intenta nuevamente.";
        } 
        // Categoría 3: Problemas de permisos
        else if (errorMessage.contains("permiso") || 
                 errorMessage.contains("denegado") ||
                 errorMessage.contains("access") || 
                 errorMessage.contains("denied")) {
            dialogTitle = "Acceso denegado";
            messageToShow = "No tienes permisos para acceder a este paciente. Contacta al administrador.";
        } 
        // Categoría 4: Otros errores
        else {
            dialogTitle = "Error desconocido";
            messageToShow = "Ha ocurrido un error inesperado. Por favor intenta nuevamente.";
            
            // En modo desarrollo, mostrar el error específico para ayudar a depurar
            // En producción, comentar esta línea
            messageToShow += "\n\nDetalle técnico: " + errorMessage;
        }
        
        // Mostrar diálogo con el mensaje adecuado y opción para reintentar
        AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this)
            .setTitle(dialogTitle)
            .setMessage(messageToShow)
            .setPositiveButton("Intentar de nuevo", (dialog, which) -> {
                showFamilyConnectionDialog();
            })
            .setNegativeButton("Cancelar", (dialog, which) -> {
                showUserTypeDialog();
            });
            
        builder.show();
    }

    /**
     * Muestra el diálogo para seleccionar el tipo de usuario
     */
    private void showUserTypeDialog() {
        // Cerrar cualquier diálogo previo que pueda estar abierto
        closeActiveDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_user_type, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        activeDialog = builder.create();

        Button btnPatient = dialogView.findViewById(R.id.btn_patient);
        Button btnFamily = dialogView.findViewById(R.id.btn_family);

        btnPatient.setOnClickListener(v -> {
            // Guardar preferencia
            saveUserType(AppConstants.USER_TYPE_PATIENT);
            activeDialog.dismiss();
            // Mostrar diálogo de inicio de sesión con Google
            showGoogleSignInDialog();
        });

        btnFamily.setOnClickListener(v -> {
            // Guardar preferencia
            saveUserType(AppConstants.USER_TYPE_FAMILY);
            activeDialog.dismiss();
            // Para familiares, pedir el ID del paciente
            showFamilyConnectionDialog();
        });

        // Mostrar el diálogo
        if (!isFinishing() && !isDestroyed()) {
            activeDialog.show();
        }
    }

    /**
     * Guarda el tipo de usuario
     */
    private void saveUserType(String userType) {
        userRepository.saveUserType(userType);
    }

    /**
     * Muestra el diálogo para introducir el ID del paciente (para familiares)
     */
    private void showFamilyConnectionDialog() {
        // Cerrar cualquier diálogo previo que pueda estar abierto
        closeActiveDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_family_connection, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        EditText etPatientId = dialogView.findViewById(R.id.et_patient_id);
        Button btnConnect = dialogView.findViewById(R.id.btn_connect);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        
        btnConnect.setOnClickListener(v -> {
            String patientId = etPatientId.getText().toString().trim().toUpperCase();
            
            if (patientId.isEmpty()) {
                etPatientId.setError("Por favor ingresa el ID del paciente");
                return;
            }
            
            // Verificar el ID con el diálogo de progreso
            verifyPatientIdWithProgress(patientId);
        });
        
        btnCancel.setOnClickListener(v -> {
            activeDialog.dismiss();
            showUserTypeDialog();
        });
        
        activeDialog = builder.create();
        
        if (!isFinishing() && !isDestroyed()) {
            activeDialog.show();
        }
    }

    /**
     * Verifica un ID de paciente mostrando un diálogo de progreso
     */
    private void verifyPatientIdWithProgress(String patientId) {
        // Mostrar progreso
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Verificando ID...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Verificar si el ID existe en la base de datos
        userRepository.verifyPatientId(patientId, new UserRepository.PatientVerificationCallback() {
            @Override
            public void onVerified(User patient) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    activeDialog.dismiss();
                    
                    // Guardar datos del paciente conectado
                    userRepository.saveConnectedPatientId(patientId);
                    
                    if (patient.getName() != null) {
                        userRepository.saveConnectedPatientName(patient.getName());
                    }
                    
                    if (patient.getEmail() != null) {
                        userRepository.saveConnectedPatientEmail(patient.getEmail());
                    }
                    
                    Toast.makeText(SplashActivity.this, 
                              "Conectado exitosamente con " + patient.getName(), 
                              Toast.LENGTH_SHORT).show();
                              
                    // Continuar con provisioning
                    checkDeviceConnection();
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    handlePatientIdVerificationError(progressDialog, errorMessage);
                });
            }
        });
    }

    /**
     * Muestra el diálogo para iniciar sesión con Google
     */
    private void showGoogleSignInDialog() {
        // Cerrar cualquier diálogo previo que pueda estar abierto
        closeActiveDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_google_sign_in, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        activeDialog = builder.create();

        Button btnGoogleSignIn = dialogView.findViewById(R.id.btn_google_sign_in);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        btnGoogleSignIn.setOnClickListener(v -> {
            activeDialog.dismiss();
            // Iniciar proceso de Google Sign-In
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        btnCancel.setOnClickListener(v -> {
            activeDialog.dismiss();
            // Si cancela, volvemos al diálogo de selección de tipo
            showUserTypeDialog();
        });

        // Mostrar el diálogo
        if (!isFinishing() && !isDestroyed()) {
            activeDialog.show();
        }
    }

    /**
     * Maneja el resultado del inicio de sesión con Google
     */
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account == null) {
                Log.e(TAG, "Google Sign In account is null");
                showUserTypeDialog();
                return;
            }
            
            Log.d(TAG, "Google Sign In successful, account: " + account.getEmail());

            // Usar el repositorio para iniciar sesión
            userRepository.signInWithGoogle(account, new UserRepository.AuthCallback() {
                @Override
                public void onSuccess(User user) {
                    // Si es un paciente nuevo, mostrar su ID único
                    if (AppConstants.USER_TYPE_PATIENT.equals(user.getUserType()) && 
                        user.getPatientId() != null) {
                        
                        // Mostrar el ID al paciente para que pueda compartirlo
                        runOnUiThread(() -> {
                            showPatientIdDialog(user.getPatientId());
                        });
                    } else {
                        // Continuar con el flujo normal
                        runOnUiThread(() -> {
                            Log.d(TAG, "Inicio de sesión exitoso para: " + user.getName());
                            Toast.makeText(SplashActivity.this, "Bienvenido " + user.getName(), 
                                        Toast.LENGTH_SHORT).show();
                            checkDeviceConnection();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "Error en inicio de sesión: " + errorMessage);
                        Toast.makeText(SplashActivity.this, "Error: " + errorMessage, 
                                    Toast.LENGTH_SHORT).show();
                        showUserTypeDialog();
                    });
                }
            });

        } catch (ApiException e) {
            Log.e(TAG, "Google Sign In failed: " + e.getStatusCode(), e);
            showUserTypeDialog();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in Google Sign In", e);
            showUserTypeDialog();
        }
    }

    /**
     * Muestra un diálogo con el ID único del paciente
     */
    private void showPatientIdDialog(String patientId) {
        // Cerrar cualquier diálogo previo
        closeActiveDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_patient_id, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        TextView tvPatientId = dialogView.findViewById(R.id.tv_patient_id);
        Button btnCopy = dialogView.findViewById(R.id.btn_copy_id);
        Button btnContinue = dialogView.findViewById(R.id.btn_continue);
        
        tvPatientId.setText(patientId);
        
        btnCopy.setOnClickListener(v -> {
            // Copiar ID al portapapeles
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("ID de Paciente", patientId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "ID copiado al portapapeles", Toast.LENGTH_SHORT).show();
        });
        
        btnContinue.setOnClickListener(v -> {
            activeDialog.dismiss();
            checkDeviceConnection();
        });
        
        activeDialog = builder.create();
        
        if (!isFinishing() && !isDestroyed()) {
            activeDialog.show();
        }
    }

    /**
     * Inicia la actividad de provisioning
     */
    private void startEspMainActivity() {
        Intent intent = new Intent(this, EspMainActivity.class);
        startActivity(intent);
        finish(); // Cerrar esta actividad
    }

    /**
     * Cierra cualquier diálogo activo
     */
    private void closeActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error al cerrar diálogo: " + e.getMessage());
            }
        }
    }

    /**
     * Verifica la disponibilidad de Google Play Services
     */
    private boolean checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, 1)
                        .show();
            } else {
                Log.e(TAG, "Este dispositivo no es compatible con Google Play Services");
                Toast.makeText(this, "Este dispositivo no es compatible con Google Play Services", 
                            Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeActiveDialog();
    }

    @Override
    protected void onDestroy() {
        closeActiveDialog();
        super.onDestroy();
    }
}