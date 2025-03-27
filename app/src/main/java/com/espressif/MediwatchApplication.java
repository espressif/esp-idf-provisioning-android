package com.espressif;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class MediwatchApplication extends Application {

    private static final String TAG = "MediwatchApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        initializeFirebase();
    }
    
    private void initializeFirebase() {
        try {
            // Inicializar Firebase
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase inicializado correctamente en Application");
            
            // Habilitar persistencia offline para la base de datos (opcional)
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            
            // Verificar que FirebaseAuth se inicialice correctamente
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth != null) {
                Log.d(TAG, "FirebaseAuth inicializado correctamente en Application");
            } else {
                Log.e(TAG, "FirebaseAuth.getInstance() devolvió null en Application");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar Firebase en Application: " + e.getMessage(), e);
        }
    }
}