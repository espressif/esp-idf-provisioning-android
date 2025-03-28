package com.espressif.data.repository;

import android.content.Context;
import android.util.Log;

import com.espressif.data.model.User;
import com.espressif.data.source.local.SharedPreferencesHelper;
import com.espressif.data.source.remote.FirebaseDataSource;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {

    private static final String TAG = "UserRepository";
    private static UserRepository instance;

    private final FirebaseDataSource firebaseDataSource;
    private final SharedPreferencesHelper preferencesHelper;

    private UserRepository(Context context) {
        this.firebaseDataSource = FirebaseDataSource.getInstance();
        this.preferencesHelper = SharedPreferencesHelper.getInstance(context);
        // Inicializar Firebase
        firebaseDataSource.initialize(context);
    }

    public static synchronized UserRepository getInstance(Context context) {
        if (instance == null) {
            instance = new UserRepository(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Autentica al usuario con Google
     */
    public void signInWithGoogle(GoogleSignInAccount account, AuthCallback callback) {
        if (account == null) {
            callback.onError("Cuenta de Google es null");
            return;
        }

        if (!firebaseDataSource.isInitialized()) {
            // Firebase no está disponible, usar flujo alternativo
            Log.d(TAG, "Firebase no está inicializado, usando flujo alternativo");
            directSignInWithoutFirebase(account, callback);
            return;
        }

        // Intentar autenticar con Firebase
        firebaseDataSource.signInWithGoogle(account.getIdToken(), new FirebaseDataSource.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                // Guardar datos en Firebase
                saveUserToDatabase(user, callback);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error en autenticación con Firebase: " + errorMessage);
                // Usar flujo alternativo
                directSignInWithoutFirebase(account, callback);
            }
        });
    }

    /**
     * Guarda información del usuario en la base de datos
     */
    private void saveUserToDatabase(FirebaseUser user, AuthCallback callback) {
        String userId = user.getUid();
        String userType = preferencesHelper.getUserType();

        // Crear mapa con datos del usuario
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("name", user.getDisplayName());
        userData.put("userType", userType != null ? userType : "unknown");
        userData.put("lastLogin", ServerValue.TIMESTAMP);

        // Guardar datos en Firebase
        firebaseDataSource.saveUserData(userId, userData, new FirebaseDataSource.DatabaseCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Datos de usuario guardados correctamente");
                completeSignIn(user.getDisplayName(), callback);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al guardar datos: " + errorMessage);
                // Continuar de todos modos
                completeSignIn(user.getDisplayName(), callback);
            }
        });
    }

    /**
     * Método alternativo sin Firebase
     */
    private void directSignInWithoutFirebase(GoogleSignInAccount account, AuthCallback callback) {
        String displayName = account.getDisplayName();
        String email = account.getEmail();

        // Guardar en preferencias
        preferencesHelper.setLoggedIn(true);
        preferencesHelper.saveUserInfo(displayName, email);

        Log.d(TAG, "Inicio de sesión directo completado para: " + displayName);
        
        User user = new User(null, displayName, email, preferencesHelper.getUserType());
        callback.onSuccess(user);
    }

    /**
     * Finaliza el proceso de inicio de sesión
     */
    private void completeSignIn(String displayName, AuthCallback callback) {
        preferencesHelper.setLoggedIn(true);
        
        User user = new User(
            null, 
            displayName, 
            preferencesHelper.getUserEmail(),
            preferencesHelper.getUserType()
        );
        
        callback.onSuccess(user);
    }

    /**
     * Verifica el estado de autenticación del usuario
     */
    public boolean isUserLoggedIn() {
        return preferencesHelper.isLoggedIn();
    }

    /**
     * Obtiene el tipo de usuario
     */
    public String getUserType() {
        return preferencesHelper.getUserType();
    }

    /**
     * Guarda el tipo de usuario
     */
    public void saveUserType(String userType) {
        preferencesHelper.saveUserType(userType);
    }

    /**
     * Verifica si ha completado el provisioning
     */
    public boolean hasCompletedProvisioning() {
        return preferencesHelper.hasCompletedProvisioning();
    }

    /**
     * Establece el estado de provisioning
     */
    public void setCompletedProvisioning(boolean hasCompleted) {
        preferencesHelper.setCompletedProvisioning(hasCompleted);
    }

    /**
     * Cierra la sesión del usuario
     */
    public void signOut() {
        if (firebaseDataSource.isInitialized()) {
            firebaseDataSource.signOut();
        }
        preferencesHelper.clearUserData();
    }

    /**
     * Obtiene el nombre del usuario
     */
    public String getUserName() {
        if (firebaseDataSource.isInitialized()) {
            FirebaseUser user = firebaseDataSource.getCurrentUser();
            if (user != null && user.getDisplayName() != null) {
                return user.getDisplayName();
            }
        }
        return preferencesHelper.getUserName();
    }

    /**
     * Obtiene el usuario actual
     */
    public User getCurrentUser() {
        String name = preferencesHelper.getUserName();
        String email = preferencesHelper.getUserEmail();
        String userType = preferencesHelper.getUserType();
        String id = null;
        
        if (firebaseDataSource.isInitialized()) {
            FirebaseUser firebaseUser = firebaseDataSource.getCurrentUser();
            if (firebaseUser != null) {
                id = firebaseUser.getUid();
                if (firebaseUser.getDisplayName() != null) {
                    name = firebaseUser.getDisplayName();
                }
                if (firebaseUser.getEmail() != null) {
                    email = firebaseUser.getEmail();
                }
            }
        }
        
        return new User(id, name, email, userType);
    }

    /**
     * Interface de callback para autenticación
     */
    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String errorMessage);
    }
}