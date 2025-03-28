package com.espressif;

import android.app.Application;
import android.util.Log;

import com.espressif.data.source.remote.FirebaseDataSource;

public class MediwatchApplication extends Application {
    
    private static final String TAG = "MediwatchApp";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Configurar reporte de errores no capturados
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            Log.e(TAG, "Error no capturado: " + ex.getMessage(), ex);
        });
        
        // Inicializar Firebase
        boolean success = FirebaseDataSource.getInstance().initialize(this);
        Log.d(TAG, "Inicialización de Firebase: " + (success ? "Exitosa" : "Fallida"));
    }
}