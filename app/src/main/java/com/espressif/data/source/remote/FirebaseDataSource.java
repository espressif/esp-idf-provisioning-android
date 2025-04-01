package com.espressif.data.source.remote;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

public class FirebaseDataSource {

    private static final String TAG = "FirebaseDataSource";
    private static FirebaseDataSource instance;

    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private boolean isInitialized = false;

    private FirebaseDataSource() {
        // Constructor privado para patrón singleton
    }

    public static synchronized FirebaseDataSource getInstance() {
        if (instance == null) {
            instance = new FirebaseDataSource();
        }
        return instance;
    }

    /**
     * Inicializa Firebase
     */
    public void initialize(Context context) {
        try {
            FirebaseAuth.getInstance();
            firebaseAuth = FirebaseAuth.getInstance();
            databaseReference = FirebaseDatabase.getInstance().getReference();
            isInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar Firebase: " + e.getMessage());
            isInitialized = false;
        }
    }

    /**
     * Verifica si Firebase está inicializado
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Iniciar sesión con Google
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        callback.onSuccess(user);
                    } else {
                        callback.onError(task.getException() != null ? 
                                       task.getException().getMessage() : "Error desconocido");
                    }
                });
    }

    /**
     * Guarda datos del usuario
     */
    public void saveUserData(String userId, Map<String, Object> userData, DatabaseCallback callback) {
        databaseReference.child("users").child(userId).updateChildren(userData)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Obtiene datos del usuario
     */
    public void getUserData(String userId, DataCallback callback) {
        databaseReference.child("users").child(userId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        callback.onSuccess(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        callback.onError(databaseError.getMessage());
                    }
                });
    }

    /**
     * Cierra la sesión actual
     */
    public void signOut() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
    }

    /**
     * Obtiene el usuario actual
     */
    public FirebaseUser getCurrentUser() {
        return firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
    }

    /**
     * Interfaz para callbacks de autenticación
     */
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String errorMessage);
    }

    /**
     * Interfaz para callbacks de base de datos
     */
    public interface DatabaseCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /**
     * Interfaz para callbacks de consulta de datos
     */
    public interface DataCallback {
        void onSuccess(DataSnapshot dataSnapshot);
        void onError(String errorMessage);
    }
}