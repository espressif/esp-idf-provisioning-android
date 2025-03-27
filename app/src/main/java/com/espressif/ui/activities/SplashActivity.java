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

// Añadir estos imports
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.FirebaseApp;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private SharedPreferences preferences;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    // Añadir este campo
    private FirebaseAuth firebaseAuth;
    private DatabaseReference mDatabase; // Añadir esta línea

    // Añadir esta variable de clase para rastrear el diálogo activo
    private AlertDialog activeDialog;

    private static final int RC_SIGN_IN = 123; // Código de solicitud para FirebaseUI

    // Modificar el método onCreate

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
        
        // Inicializar SharedPreferences
        preferences = getSharedPreferences(AppConstants.PREF_NAME_USER, MODE_PRIVATE);
        
        // Configurar Google Sign-In - sin try-catch aquí
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Inicializar Firebase - sin try-catch aquí
        firebaseAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        
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
        String userType = preferences.getString(AppConstants.KEY_USER_TYPE, null);
        boolean isLoggedIn = preferences.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false);
        boolean hasCompletedProvisioning = preferences.getBoolean(AppConstants.KEY_HAS_COMPLETED_PROVISIONING, false);

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
        
        // Configurar la cancelación personalizada
        builder.setCancelable(false);
        
        activeDialog = builder.create();
        
        // Resto del código para configurar el diálogo...
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // No permite cerrar al tocar fuera

        Button btnPatient = dialogView.findViewById(R.id.btn_patient);
        Button btnFamily = dialogView.findViewById(R.id.btn_family);

        btnPatient.setOnClickListener(v -> {
            // Guardar preferencia
            saveUserType(AppConstants.USER_TYPE_PATIENT);
            dialog.dismiss();
            // Mostrar diálogo de inicio de sesión con Google
            showGoogleSignInDialog();
        });

        btnFamily.setOnClickListener(v -> {
            // Guardar preferencia
            saveUserType(AppConstants.USER_TYPE_FAMILY);
            dialog.dismiss();
            // Para familiares, ir directamente a provisioning
            startEspMainActivity();
        });

        // Mostrar el diálogo
        if (!isFinishing() && !isDestroyed()) {
            activeDialog.show();
        }
    }

    /**
     * Guarda el tipo de usuario en SharedPreferences
     */
    private void saveUserType(String userType) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(AppConstants.KEY_USER_TYPE, userType);
        editor.apply();
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
        
        // Resto del código para configurar el diálogo...
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false); // No permite cerrar al tocar fuera

        Button btnGoogleSignIn = dialogView.findViewById(R.id.btn_google_sign_in);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        btnGoogleSignIn.setOnClickListener(v -> {
            dialog.dismiss();
            // Iniciar proceso de Google Sign-In
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
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
    @SuppressWarnings("deprecation") // Suprimir advertencia por API obsoleta
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Google Sign In successful, account: " + account.getEmail());
            
            // Autenticar con Firebase
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Autenticación con Firebase exitosa
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            if (user != null) {
                                createUserInDatabase(user);
                            } else {
                                Log.e(TAG, "Usuario Firebase es null después de autenticación exitosa");
                                startEspMainActivity();
                            }
                        } else {
                            Log.e(TAG, "Error en autenticación con Firebase", task.getException());
                            // A pesar del error, continuar
                            startEspMainActivity();
                        }
                    });
            
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign In failed: " + e.getStatusCode(), e);
            showUserTypeDialog(); // Volver a mostrar el diálogo
        }
    }

    // Añadir este método para guardar el usuario en la base de datos
    private void createUserInDatabase(FirebaseUser user) {
        if (user != null) {
            try {
                String userId = user.getUid();
                
                // Crear un objeto con los datos del usuario que quieres guardar
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", user.getEmail());
                userData.put("name", user.getDisplayName());
                userData.put("userType", preferences.getString(AppConstants.KEY_USER_TYPE, "unknown"));
                userData.put("lastLogin", ServerValue.TIMESTAMP);
                
                // Guardar en la base de datos
                mDatabase.child("users").child(userId).setValue(userData)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Usuario guardado en la base de datos");
                                
                                // Guardar estado de inicio de sesión
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putBoolean(AppConstants.KEY_IS_LOGGED_IN, true);
                                editor.apply();
                                
                                // Inicio de sesión exitoso
                                String displayName = user.getDisplayName() != null ? user.getDisplayName() : "Usuario";
                                Toast.makeText(this, "Bienvenido " + displayName, Toast.LENGTH_SHORT).show();
                                
                                // Ir a pantalla de provisioning
                                startEspMainActivity();
                            } else {
                                Log.w(TAG, "Error al guardar usuario en la base de datos", task.getException());
                                // A pesar del error, continuar con el flujo
                                startEspMainActivity();
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error en createUserInDatabase: " + e.getMessage(), e);
                // A pesar del error, continuar con el flujo
                startEspMainActivity();
            }
        } else {
            // Si user es null, simplemente continuar
            startEspMainActivity();
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
     * Maneja el resultado del inicio de sesión con Google de forma segura
     */
    private void handleGoogleSignInResultSafely(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Google Sign In successful, account: " + (account != null ? account.getEmail() : "null"));
            
            // Bypass Firebase temporalmente para evitar errores
            directSignIn(account);
            
        } catch (ApiException e) {
            // Error en inicio de sesión con Google
            Log.w(TAG, "Google Sign In failed, code: " + e.getStatusCode() + ", message: " + e.getMessage(), e);
            Toast.makeText(this, "Error al iniciar sesión con Google. Usando modo alternativo.", Toast.LENGTH_SHORT).show();
            bypassGoogleSignIn(); // Usar método alternativo
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in Google Sign In: " + e.getMessage(), e);
            bypassGoogleSignIn(); // Usar método alternativo
        }
    }

    /**
     * Realiza un inicio de sesión directo intentando usar Firebase si es posible
     */
    private void directSignIn(GoogleSignInAccount account) {
        try {
            // Verificar si Firebase Auth está inicializado
            if (firebaseAuth == null) {
                Log.e(TAG, "FirebaseAuth es null, no se puede autenticar con Firebase");
                completeSignIn(account != null ? account.getDisplayName() : null);
                return;
            }
            
            // Intentar autenticar con Firebase si tenemos un token
            if (account != null && account.getIdToken() != null) {
                Log.d(TAG, "Intentando autenticar con Firebase usando ID token");
                
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                firebaseAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, task -> {
                            // El resto permanece igual...
                            if (task.isSuccessful()) {
                                // Autenticación exitosa
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                Log.d(TAG, "Autenticación con Firebase exitosa: " + 
                                      (user != null ? user.getEmail() : "null"));
                                
                                // Guardar en base de datos
                                if (user != null) {
                                    saveUserToDatabase(user);
                                } else {
                                    // Si no hay usuario aun con éxito, usar bypass
                                    completeSignIn(account.getDisplayName());
                                }
                            } else {
                                // Error de autenticación, registrar y continuar con bypass
                                Exception exception = task.getException();
                                Log.e(TAG, "Error en autenticación con Firebase: " + 
                                      (exception != null ? exception.getMessage() : "desconocido"), 
                                      exception);
                                
                                // Usar el bypass
                                completeSignIn(account.getDisplayName());
                            }
                        });
            } else {
                // No tenemos token o cuenta, usar bypass
                Log.d(TAG, "No hay ID token disponible, usando bypass");
                completeSignIn(account != null ? account.getDisplayName() : null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en directSignIn: " + e.getMessage(), e);
            completeSignIn(account != null ? account.getDisplayName() : null);
        }
    }

    /**
     * Guarda el usuario en la base de datos de Firebase
     */
    private void saveUserToDatabase(FirebaseUser user) {
        try {
            String userId = user.getUid();
            
            // Crear un objeto con los datos del usuario
            Map<String, Object> userData = new HashMap<>();
            userData.put("email", user.getEmail());
            userData.put("name", user.getDisplayName());
            userData.put("userType", preferences.getString(AppConstants.KEY_USER_TYPE, "unknown"));
            userData.put("lastLogin", ServerValue.TIMESTAMP);
            
            // Guardar en la base de datos
            mDatabase.child("users").child(userId).setValue(userData)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Usuario guardado correctamente en la base de datos");
                        } else {
                            Log.e(TAG, "Error al guardar usuario en base de datos", task.getException());
                        }
                        
                        // Completar el inicio de sesión independientemente del resultado
                        completeSignIn(user.getDisplayName());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error en saveUserToDatabase: " + e.getMessage(), e);
            completeSignIn(user.getDisplayName());
        }
    }

    /**
     * Completa el proceso de inicio de sesión
     */
    private void completeSignIn(String displayName) {
        // Guardar estado de inicio de sesión
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(AppConstants.KEY_IS_LOGGED_IN, true);
        editor.apply();
        
        // Nombre a mostrar
        String name = displayName != null ? displayName : "Usuario";
        
        Log.d(TAG, "Sesión completada para: " + name);
        Toast.makeText(this, "Bienvenido " + name, Toast.LENGTH_SHORT).show();
        
        // Ir a la actividad principal
        startEspMainActivity();
    }

    /**
     * Método para saltarse completamente el inicio de sesión en caso de error
     */
    private void bypassGoogleSignIn() {
        try {
            // Guardar estado de inicio de sesión de todos modos
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(AppConstants.KEY_IS_LOGGED_IN, true);
            editor.apply();
            
            Log.d(TAG, "Usando bypass para inicio de sesión");
            Toast.makeText(this, "Inicio de sesión alternativo aplicado", Toast.LENGTH_SHORT).show();
            
            // Ir a la actividad principal
            startEspMainActivity();
        } catch (Exception e) {
            Log.e(TAG, "Error incluso en bypass: " + e.getMessage(), e);
            // Último recurso: ir directamente a la actividad principal
            Intent intent = new Intent(this, EspMainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    // Añadir este método para cerrar el diálogo activo
    private void closeActiveDialog() {
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error al cerrar diálogo: " + e.getMessage());
            }
        }
    }

    // Añadir este método
    private void startFirebaseUIAuth() {
        // Cerrar cualquier diálogo activo
        closeActiveDialog();
        
        // Configurar proveedores
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.GoogleBuilder().build());

        // Crear y lanzar el intent de inicio de sesión
        Intent signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setLogo(R.mipmap.ic_launcher) // Logo de la app
                .setTheme(R.style.AppTheme) // Tema de la app
                .build();
        
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    // Añadir o modificar onActivityResult para manejar el resultado
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Usuario autenticado correctamente
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    // Guardar en la base de datos
                    saveUserToDatabase(user);
                } else {
                    // Raro pero podría ocurrir
                    Log.e(TAG, "Usuario autenticado pero FirebaseUser es null");
                    completeSignIn(null);
                }
            } else {
                // Error o cancelación del inicio de sesión
                if (response == null) {
                    Log.d(TAG, "Inicio de sesión cancelado por el usuario");
                    Toast.makeText(this, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show();
                    showUserTypeDialog(); // Volver a mostrar diálogo
                } else {
                    Log.e(TAG, "Error en inicio de sesión: " + response.getError());
                    Toast.makeText(this, "Error al iniciar sesión", Toast.LENGTH_SHORT).show();
                    showUserTypeDialog(); // Volver a mostrar diálogo
                }
            }
        }
    }

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

    // Sobreescribir onPause para cerrar diálogos
    @Override
    protected void onPause() {
        super.onPause();
        closeActiveDialog();
    }

    // Sobreescribir onDestroy para cerrar diálogos
    @Override
    protected void onDestroy() {
        closeActiveDialog();
        super.onDestroy();
    }
}