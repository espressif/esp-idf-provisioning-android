package com.espressif.data.source.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.espressif.AppConstants;

public class SharedPreferencesHelper {

    private static SharedPreferencesHelper instance;
    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;

    private SharedPreferencesHelper(Context context) {
        preferences = context.getSharedPreferences(AppConstants.PREF_NAME_USER, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    public static synchronized SharedPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPreferencesHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Métodos para userType
     */
    public void saveUserType(String userType) {
        editor.putString(AppConstants.KEY_USER_TYPE, userType);
        editor.apply();
    }

    public String getUserType() {
        return preferences.getString(AppConstants.KEY_USER_TYPE, null);
    }

    /**
     * Métodos para estado de login
     */
    public void setLoggedIn(boolean isLoggedIn) {
        editor.putBoolean(AppConstants.KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return preferences.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false);
    }

    /**
     * Métodos para provisioning
     */
    public void setCompletedProvisioning(boolean hasCompleted) {
        editor.putBoolean(AppConstants.KEY_HAS_COMPLETED_PROVISIONING, hasCompleted);
        editor.apply();
    }

    public boolean hasCompletedProvisioning() {
        return preferences.getBoolean(AppConstants.KEY_HAS_COMPLETED_PROVISIONING, false);
    }

    /**
     * Métodos para información del usuario
     */
    public void saveUserInfo(String name, String email) {
        if (name != null) {
            editor.putString("user_name", name);
        }
        if (email != null) {
            editor.putString("user_email", email);
        }
        editor.apply();
    }

    public String getUserName() {
        return preferences.getString("user_name", "Usuario");
    }

    public String getUserEmail() {
        return preferences.getString("user_email", "");
    }

    /**
     * Métodos para IDs de paciente
     */
    
    /**
     * Guarda el ID único del paciente (para usuarios tipo paciente)
     * @param patientId ID único del paciente
     */
    public void savePatientId(String patientId) {
        editor.putString(AppConstants.KEY_PATIENT_ID, patientId);
        editor.apply();
    }

    /**
     * Obtiene el ID único del paciente
     * @return El ID del paciente o null si no está disponible
     */
    public String getPatientId() {
        return preferences.getString(AppConstants.KEY_PATIENT_ID, null);
    }

    /**
     * Guarda el ID del paciente al que está conectado un familiar
     * @param patientId ID del paciente al que se conectará
     */
    public void saveConnectedPatientId(String patientId) {
        editor.putString(AppConstants.KEY_CONNECTED_PATIENT_ID, patientId);
        editor.apply();
    }

    /**
     * Obtiene el ID del paciente al que está conectado un familiar
     * @return ID del paciente conectado o null si no hay conexión
     */
    public String getConnectedPatientId() {
        return preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_ID, null);
    }

    /**
     * Guarda el email del paciente conectado (información adicional)
     */
    public void saveConnectedPatientEmail(String email) {
        editor.putString(AppConstants.KEY_CONNECTED_PATIENT_EMAIL, email);
        editor.apply();
    }

    /**
     * Obtiene el email del paciente conectado
     */
    public String getConnectedPatientEmail() {
        return preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_EMAIL, null);
    }

    /**
     * Guarda el nombre del paciente conectado
     */
    public void saveConnectedPatientName(String name) {
        editor.putString(AppConstants.KEY_CONNECTED_PATIENT_NAME, name);
        editor.apply();
    }

    /**
     * Obtiene el nombre del paciente conectado
     */
    public String getConnectedPatientName() {
        return preferences.getString(AppConstants.KEY_CONNECTED_PATIENT_NAME, "Paciente");
    }

    /**
     * Limpiar datos de usuario (logout)
     */
    public void clearUserData() {
        editor.remove(AppConstants.KEY_IS_LOGGED_IN)
            .remove("user_name")
            .remove("user_email")
            .remove(AppConstants.KEY_PATIENT_ID)
            .remove(AppConstants.KEY_CONNECTED_PATIENT_ID)
            .remove(AppConstants.KEY_CONNECTED_PATIENT_EMAIL)
            .remove(AppConstants.KEY_CONNECTED_PATIENT_NAME)
            .apply();
    }

    /**
     * Acceso directo al objeto SharedPreferences
     */
    public SharedPreferences getPreferences() {
        return preferences;
    }
}