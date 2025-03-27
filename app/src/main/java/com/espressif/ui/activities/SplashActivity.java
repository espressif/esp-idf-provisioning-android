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

import java.util.HashMap;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private SharedPreferences preferences;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    // Añadir este campo
    private FirebaseAuth firebaseAuth;
    private DatabaseReference mDatabase; // Añadir esta línea

    // Reemplazar el método onCreate con una versión con mejor manejo de errores

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_splash);
            
            // Inicializar SharedPreferences
            preferences = getSharedPreferences(AppConstants.PREF_NAME_USER, MODE_PRIVATE);
            
            try {
                // Inicializar Firebase Auth y Database - Envuelto en try-catch por seguridad
                firebaseAuth = FirebaseAuth.getInstance();
                mDatabase = FirebaseDatabase.getInstance().getReference(); // Añadir esta línea
                Log.d("MediwatchApp", "Firebase inicializado correctamente");
            } catch (Exception e) {
                Log.e(TAG, "Error al inicializar Firebase: " + e.getMessage(), e);
                // Continuar aún con el error para no bloquear la app
            }
            
            try {
                // Configurar Google Sign-In con manejo de errores
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail() // Primero solo pedir email para simplificar
                        .build();
                googleSignInClient = GoogleSignIn.getClient(this, gso);
            } catch (Exception e) {
                Log.e(TAG, "Error al configurar Google Sign-In: " + e.getMessage(), e);
                // Continuar aún con el error
            }
            
            // Configurar el ActivityResultLauncher para Google Sign-In
            googleSignInLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        try {
                            Log.d(TAG, "Google Sign In result code: " + result.getResultCode());
                            
                            // Intentar procesar el resultado independientemente del código
                            if (result.getData() != null) {
                                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                                handleGoogleSignInResultSafely(task);
                            } else {
                                Log.e(TAG, "Google Sign In data is null");
                                Toast.makeText(this, "No se recibieron datos del inicio de sesión", Toast.LENGTH_SHORT).show();
                                bypassGoogleSignIn(); // Usar método alternativo
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando resultado de Google Sign In: " + e.getMessage(), e);
                            Toast.makeText(this, "Error en proceso de inicio de sesión", Toast.LENGTH_SHORT).show();
                            bypassGoogleSignIn(); // Usar método alternativo
                        }
                    });
            
            // Retrasar un poco para mostrar la pantalla splash
            new Handler().postDelayed(this::checkUserStatus, 1000);
            
        } catch (Exception e) {
            // Capturar cualquier error durante la inicialización
            Log.e(TAG, "Error crítico en onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error al iniciar la aplicación: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Si hay un error crítico, simplemente ir a la actividad principal tras un breve retraso
            new Handler().postDelayed(() -> {
                startEspMainActivity();
            }, 2000);
        }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_user_type, null);
        builder.setView(dialogView);
        
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

        dialog.show();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_google_sign_in, null);
        builder.setView(dialogView);
        
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

        dialog.show();
    }

    /**
     * Maneja el resultado del inicio de sesión con Google
     */
    @SuppressWarnings("deprecation") // Suprimir advertencia por API obsoleta
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            
            // Ahora autenticar con Firebase
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Autenticación con Firebase exitosa
                            FirebaseUser user = firebaseAuth.getCurrentUser();
                            
                            // Guardar usuario en la base de datos (añadir esta parte)
                            createUserInDatabase(user);
                            
                        } else {
                            // Error en la autenticación con Firebase
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(SplashActivity.this, "Error al autenticar con Firebase.",
                                    Toast.LENGTH_SHORT).show();
                            showGoogleSignInDialog(); // Volver a mostrar el diálogo
                        }
                    });
            
        } catch (ApiException e) {
            // Error en inicio de sesión con Google
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Toast.makeText(this, "Error al iniciar sesión. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show();
            showGoogleSignInDialog(); // Volver a mostrar el diálogo
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
            // Primero intentar autenticar con Firebase
            if (account != null && account.getIdToken() != null) {
                Log.d(TAG, "Intentando autenticar con Firebase usando ID token");
                
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                firebaseAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, task -> {
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
            completeSignIn(null); // Si algo falla, usar el método más simple
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
}