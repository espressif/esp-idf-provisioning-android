package com.espressif.data.source.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.espressif.AppConstants;

public class SharedPreferencesHelper {

    private static SharedPreferencesHelper instance;
    private final SharedPreferences preferences;

    private SharedPreferencesHelper(Context context) {
        preferences = context.getSharedPreferences(AppConstants.PREF_NAME_USER, Context.MODE_PRIVATE);
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
        preferences.edit().putString(AppConstants.KEY_USER_TYPE, userType).apply();
    }

    public String getUserType() {
        return preferences.getString(AppConstants.KEY_USER_TYPE, null);
    }

    /**
     * Métodos para estado de login
     */
    public void setLoggedIn(boolean isLoggedIn) {
        preferences.edit().putBoolean(AppConstants.KEY_IS_LOGGED_IN, isLoggedIn).apply();
    }

    public boolean isLoggedIn() {
        return preferences.getBoolean(AppConstants.KEY_IS_LOGGED_IN, false);
    }

    /**
     * Métodos para provisioning
     */
    public void setCompletedProvisioning(boolean hasCompleted) {
        preferences.edit().putBoolean(AppConstants.KEY_HAS_COMPLETED_PROVISIONING, hasCompleted).apply();
    }

    public boolean hasCompletedProvisioning() {
        return preferences.getBoolean(AppConstants.KEY_HAS_COMPLETED_PROVISIONING, false);
    }

    /**
     * Métodos para información del usuario
     */
    public void saveUserInfo(String name, String email) {
        SharedPreferences.Editor editor = preferences.edit();
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
     * Limpiar datos de usuario (logout)
     */
    public void clearUserData() {
        preferences.edit()
            .remove(AppConstants.KEY_IS_LOGGED_IN)
            .remove("user_name")
            .remove("user_email")
            .apply();
    }

    /**
     * Acceso directo al objeto SharedPreferences
     */
    public SharedPreferences getPreferences() {
        return preferences;
    }
}