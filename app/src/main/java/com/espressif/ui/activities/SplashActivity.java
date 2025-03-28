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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private SharedPreferences preferences;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference mDatabase; 

    private AlertDialog activeDialog;

    private static final int RC_SIGN_IN = 123;

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
        
        // Inicializar Firebase explícitamente
        try {
            // Verificar si Firebase ya está inicializado
            List<FirebaseApp> firebaseApps = FirebaseApp.getApps(this);
            if (firebaseApps.isEmpty()) {
                // Si no hay apps de Firebase inicializadas, inicializar
                FirebaseApp.initializeApp(this);
                Log.d(TAG, "Firebase inicializado explícitamente");
            } else {
                Log.d(TAG, "Firebase ya estaba inicializado. Apps: " + firebaseApps.size());
            }
            
            // Obtener instancias después de la inicialización
            firebaseAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();
            
            if (firebaseAuth != null) {
                Log.d(TAG, "FirebaseAuth inicializado correctamente");
            } else {
                Log.e(TAG, "FirebaseAuth es null después de la inicialización");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar Firebase: " + e.getMessage(), e);
            // Continuar de todos modos, utilizaremos el flujo alternativo
            firebaseAuth = null;
        }
        
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

            // Intentar inicializar Firebase nuevamente si es necesario
            if (firebaseAuth == null) {
                try {
                    FirebaseApp.initializeApp(this);
                    firebaseAuth = FirebaseAuth.getInstance();
                    mDatabase = FirebaseDatabase.getInstance().getReference();
                    Log.d(TAG, "Reintento de inicialización de Firebase: " + 
                          (firebaseAuth != null ? "exitoso" : "fallido"));
                } catch (Exception e) {
                    Log.e(TAG, "Error en reintento de inicialización de Firebase", e);
                }
            }

            // Verificar si Firebase Auth está disponible
            if (firebaseAuth == null) {
                Log.e(TAG, "FirebaseAuth is null, skipping Firebase authentication");
                directSignInWithoutFirebase(account);
                return;
            }

            // Use Firebase Authentication
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToDatabase(user);
                        } else {
                            Log.e(TAG, "Firebase user is null after successful authentication");
                            directSignInWithoutFirebase(account);
                        }
                    } else {
                        Log.e(TAG, "Firebase authentication failed", task.getException());
                        directSignInWithoutFirebase(account);
                    }
                });

        } catch (ApiException e) {
            Log.e(TAG, "Google Sign In failed: " + e.getStatusCode(), e);
            showUserTypeDialog();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in Google Sign In", e);
            directSignInWithoutFirebase(null);
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
     * Guarda la información del usuario en la base de datos
     */
    private void saveUserToDatabase(FirebaseUser user) {
        try {
            String userId = user.getUid();
            
            // Crear mapa con datos del usuario
            Map<String, Object> userData = new HashMap<>();
            userData.put("email", user.getEmail());
            userData.put("name", user.getDisplayName());
            userData.put("userType", preferences.getString(AppConstants.KEY_USER_TYPE, "unknown"));
            userData.put("lastLogin", ServerValue.TIMESTAMP);
            
            // Guardar datos en Firebase
            mDatabase.child("users").child(userId).setValue(userData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User data saved successfully");
                        completeSignIn(user.getDisplayName());
                    } else {
                        Log.e(TAG, "Failed to save user data", task.getException());
                        // Continuar de todos modos
                        completeSignIn(user.getDisplayName());
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Error saving user to database", e);
            // Continuar de todos modos
            completeSignIn(user.getDisplayName());
        }
    }

    /**
     * Método alternativo para iniciar sesión sin Firebase
     */
    private void directSignInWithoutFirebase(GoogleSignInAccount account) {
        String displayName = (account != null) ? account.getDisplayName() : "Usuario";
        String email = (account != null) ? account.getEmail() : "";
        
        // Guardar información básica en SharedPreferences
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(AppConstants.KEY_IS_LOGGED_IN, true);
        if (email != null && !email.isEmpty()) {
            editor.putString("user_email", email);
        }
        if (displayName != null && !displayName.isEmpty()) {
            editor.putString("user_name", displayName);
        }
        editor.apply();
        
        Log.d(TAG, "Direct sign-in without Firebase completed");
        Toast.makeText(this, "Bienvenido " + displayName, Toast.LENGTH_SHORT).show();
        
        // Continuar con el flujo normal
        startEspMainActivity();
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
     * Inicia la autenticación usando FirebaseUI
     */
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

    /**
     * Maneja el resultado de FirebaseUI
     */
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