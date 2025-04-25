package com.espressif.ui.utils;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.util.Log;

/**
 * Administrador centralizado de observadores para controlar su ciclo de vida
 */
public class ObserverManager {
    private static final String TAG = "ObserverManager";
    
    // Mapa que contiene todos los observers registrados por identificador
    private final Map<String, List<ObserverRegistration<?>>> observerRegistrations = new HashMap<>();
    
    /**
     * Clase que representa un registro de observer
     */
    private static class ObserverRegistration<T> {
        final LiveData<T> liveData;
        final Observer<T> observer;
        final String id;
        
        ObserverRegistration(LiveData<T> liveData, Observer<T> observer, String id) {
            this.liveData = liveData;
            this.observer = observer;
            this.id = id;
        }
    }
    
    /**
     * Registra un nuevo observer
     * @param owner Propietario del ciclo de vida
     * @param liveData LiveData a observar
     * @param observer Observer que procesará los datos
     * @param id Identificador único para este observer
     * @param <T> Tipo de datos del LiveData
     */
    public <T> void observe(LifecycleOwner owner, LiveData<T> liveData, Observer<T> observer, String id) {
        if (owner == null || liveData == null || observer == null) {
            Log.e(TAG, "No se puede registrar observer: algún parámetro es nulo");
            return;
        }
        
        // Registrar el observer en el LiveData
        liveData.observe(owner, observer);
        
        // Almacenar el registro
        ObserverRegistration<T> registration = new ObserverRegistration<>(liveData, observer, id);
        List<ObserverRegistration<?>> registrations = observerRegistrations.get(id);
        
        if (registrations == null) {
            registrations = new ArrayList<>();
            observerRegistrations.put(id, registrations);
        }
        
        registrations.add(registration);
        Log.d(TAG, "Observer registrado con ID: " + id);
    }
    
    /**
     * Observa permanentemente (sin ciclo de vida)
     * ADVERTENCIA: Usar con cuidado para evitar fugas de memoria
     */
    public <T> void observeForever(LiveData<T> liveData, Observer<T> observer, String id) {
        if (liveData == null || observer == null) {
            Log.e(TAG, "No se puede registrar observer: algún parámetro es nulo");
            return;
        }
        
        // Registrar el observer sin ciclo de vida
        liveData.observeForever(observer);
        
        // Almacenar el registro
        ObserverRegistration<T> registration = new ObserverRegistration<>(liveData, observer, id);
        List<ObserverRegistration<?>> registrations = observerRegistrations.get(id);
        
        if (registrations == null) {
            registrations = new ArrayList<>();
            observerRegistrations.put(id, registrations);
        }
        
        registrations.add(registration);
        Log.d(TAG, "Observer permanente registrado con ID: " + id);
    }
    
    /**
     * Remueve todos los observadores asociados a un ID
     * @param id Identificador del grupo de observadores
     */
    @SuppressWarnings("unchecked")
    public void removeObservers(String id) {
        List<ObserverRegistration<?>> registrations = observerRegistrations.get(id);
        
        if (registrations != null) {
            for (ObserverRegistration<?> registration : registrations) {
                try {
                    // Necesitamos este cast para llamar a removeObserver con el tipo correcto
                    ((LiveData<Object>) registration.liveData).removeObserver(
                            (Observer<Object>) registration.observer);
                    Log.d(TAG, "Observer removido: " + id);
                } catch (Exception e) {
                    Log.e(TAG, "Error al remover observer: " + e.getMessage());
                }
            }
            registrations.clear();
        }
        
        observerRegistrations.remove(id);
    }
    
    /**
     * Remueve todos los observadores registrados
     */
    public void removeAllObservers() {
        for (String id : new ArrayList<>(observerRegistrations.keySet())) {
            removeObservers(id);
        }
        observerRegistrations.clear();
        Log.d(TAG, "Todos los observers han sido removidos");
    }
}