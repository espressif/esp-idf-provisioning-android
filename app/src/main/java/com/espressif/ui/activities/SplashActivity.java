package com.espressif.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.AppConstants;
import com.espressif.ui.activities.provision_activities.EspMainActivity;
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
        } else {
            // Usuario ya configurado, verificar si ha completado el provisioning
            if (!hasCompletedProvisioning) {
                // Redirigir a EspMainActivity para provisioning
                startEspMainActivity();
            } else {
                // En el futuro, redirigir a MainActivity con Bottom Navigation
                // Por ahora, vamos a EspMain
                startEspMainActivity();
            }
        }
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
            // Para familiares, ir directamente a provisioning
            startEspMainActivity();
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
                    Log.d(TAG, "Inicio de sesión exitoso para: " + user.getName());
                    Toast.makeText(SplashActivity.this, "Bienvenido " + user.getName(), Toast.LENGTH_SHORT).show();
                    startEspMainActivity();
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error en inicio de sesión: " + errorMessage);
                    Toast.makeText(SplashActivity.this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    showUserTypeDialog();
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