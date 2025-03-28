package com.espressif.data.source.remote;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.Map;

public class FirebaseDataSource {

    private static final String TAG = "FirebaseDataSource";
    private static FirebaseDataSource instance;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private boolean isInitialized = false;

    private FirebaseDataSource() {
        // Constructor privado para Singleton
    }

    public static synchronized FirebaseDataSource getInstance() {
        if (instance == null) {
            instance = new FirebaseDataSource();
        }
        return instance;
    }

    /**
     * Inicializa Firebase en la aplicación
     */
    public boolean initialize(Context context) {
        try {
            // Verificar si Firebase ya está inicializado
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context);
                Log.d(TAG, "Firebase inicializado explícitamente");
            } else {
                Log.d(TAG, "Firebase ya estaba inicializado");
            }

            // Obtener instancias
            firebaseAuth = FirebaseAuth.getInstance();
            databaseReference = FirebaseDatabase.getInstance().getReference();

            isInitialized = (firebaseAuth != null);
            Log.d(TAG, "Estado de inicialización de Firebase: " + (isInitialized ? "Exitoso" : "Fallido"));
            
            return isInitialized;
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar Firebase: " + e.getMessage(), e);
            isInitialized = false;
            return false;
        }
    }

    /**
     * Verifica si Firebase está inicializado
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Autentica al usuario con Google
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        if (!isInitialized) {
            callback.onError("Firebase no está inicializado");
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        callback.onSuccess(user);
                    } else {
                        callback.onError("Usuario autenticado pero FirebaseUser es null");
                    }
                } else {
                    callback.onError("Error en la autenticación: " + 
                        (task.getException() != null ? task.getException().getMessage() : "Desconocido"));
                }
            });
    }

    /**
     * Guarda datos del usuario en la base de datos
     */
    public void saveUserData(String userId, Map<String, Object> userData, DatabaseCallback callback) {
        if (!isInitialized) {
            callback.onError("Firebase no está inicializado");
            return;
        }

        databaseReference.child("users").child(userId).setValue(userData)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Error al guardar datos: " + 
                        (task.getException() != null ? task.getException().getMessage() : "Desconocido"));
                }
            });
    }

    /**
     * Obtiene el usuario actualmente autenticado
     */
    public FirebaseUser getCurrentUser() {
        return isInitialized ? firebaseAuth.getCurrentUser() : null;
    }

    /**
     * Cierra la sesión del usuario
     */
    public void signOut() {
        if (isInitialized) {
            firebaseAuth.signOut();
        }
    }

    /**
     * Obtiene la referencia a la base de datos
     */
    public DatabaseReference getDatabaseReference() {
        return databaseReference;
    }

    /**
     * Obtiene la referencia a la autenticación
     */
    public FirebaseAuth getFirebaseAuth() {
        return firebaseAuth;
    }

    /**
     * Actualiza el timestamp de último login
     */
    public void updateLastLogin(String userId, DatabaseCallback callback) {
        Map<String, Object> updates = Map.of("lastLogin", ServerValue.TIMESTAMP);
        databaseReference.child("users").child(userId).updateChildren(updates)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Error al actualizar último login");
                }
            });
    }

    /**
     * Interfaces de callback
     */
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String errorMessage);
    }

    public interface DatabaseCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
}