package com.espressif.data.repository;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.data.model.User;
import com.espressif.data.source.local.SharedPreferencesHelper;
import com.espressif.data.source.remote.FirebaseDataSource;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

        // Si es un paciente, generar un ID único
        if (AppConstants.USER_TYPE_PATIENT.equals(userType)) {
            generateUniquePatientIdAndSave(userId, userData, user, callback);
        } else {
            // Para usuarios que no son pacientes, guardar inmediatamente
            completeUserSave(userId, userData, user, callback);
        }
    }

    /**
     * Genera un ID único para el paciente y lo guarda
     */
    private void generateUniquePatientIdAndSave(String userId, Map<String, Object> userData, 
                                               FirebaseUser user, AuthCallback callback) {
        // Verificar si ya tiene un ID
        firebaseDataSource.getUserData(userId, new FirebaseDataSource.DataCallback() {
            @Override
            public void onSuccess(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.child("patientId").exists()) {
                    // Ya tiene ID, usar el existente
                    String existingId = dataSnapshot.child("patientId").getValue(String.class);
                    userData.put("patientId", existingId);
                    completeUserSave(userId, userData, user, callback);
                } else {
                    // No tiene ID, generar uno nuevo
                    attemptPatientIdGeneration(userId, userData, user, callback);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Error al verificar, generar nuevo ID
                attemptPatientIdGeneration(userId, userData, user, callback);
            }
        });
    }

    /**
     * Intenta generar un ID único de paciente
     */
    private void attemptPatientIdGeneration(String userId, Map<String, Object> userData, 
                                           FirebaseUser user, AuthCallback callback) {
        String candidateId = generatePatientIdCandidate();
        
        // Verificar si el ID ya existe
        DatabaseReference patientIdsRef = FirebaseDatabase.getInstance().getReference("patient_ids");
        patientIdsRef.child(candidateId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // ID ya usado, intentar con otro
                    attemptPatientIdGeneration(userId, userData, user, callback);
                } else {
                    // ID disponible, guardarlo
                    userData.put("patientId", candidateId);
                    
                    // Registrar en la tabla de IDs de pacientes
                    Map<String, Object> patientIdData = new HashMap<>();
                    patientIdData.put("userId", userId);
                    patientIdData.put("email", user.getEmail());
                    patientIdData.put("timestamp", ServerValue.TIMESTAMP);
                    
                    patientIdsRef.child(candidateId).setValue(patientIdData)
                        .addOnSuccessListener(aVoid -> {
                            // ID guardado correctamente, continuar con el resto de datos
                            completeUserSave(userId, userData, user, callback);
                        })
                        .addOnFailureListener(e -> {
                            // Error al guardar ID, continuar sin él
                            Log.e(TAG, "Error al guardar ID de paciente: " + e.getMessage());
                            userData.remove("patientId");
                            completeUserSave(userId, userData, user, callback);
                        });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Error al verificar ID, continuar sin generar uno nuevo
                Log.e(TAG, "Error al verificar ID: " + error.getMessage());
                completeUserSave(userId, userData, user, callback);
            }
        });
    }

    /**
     * Completa el proceso de guardado del usuario
     */
    private void completeUserSave(String userId, Map<String, Object> userData, 
                                 FirebaseUser firebaseUser, AuthCallback callback) {
        // Guardar datos en Firebase
        firebaseDataSource.saveUserData(userId, userData, new FirebaseDataSource.DatabaseCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Datos de usuario guardados correctamente");
                
                // Guardar patientId en preferencias si existe
                if (userData.containsKey("patientId")) {
                    preferencesHelper.savePatientId((String) userData.get("patientId"));
                }
                
                completeSignIn(firebaseUser.getDisplayName(), callback);
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Error al guardar datos: " + errorMessage);
                // Continuar de todos modos
                completeSignIn(firebaseUser.getDisplayName(), callback);
            }
        });
    }

    /**
     * Genera un candidato para ID de paciente
     * @return Un ID alfanumérico único de 6 caracteres
     */
    private String generatePatientIdCandidate() {
        // Usar solo caracteres que no se confundan entre sí
        String allowedChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(allowedChars.length());
            sb.append(allowedChars.charAt(index));
        }
        
        return sb.toString();
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
        
        // Crear usuario sin ID único de paciente
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
        
        // Si hay un patientId guardado, asignarlo al usuario
        String patientId = preferencesHelper.getPatientId();
        if (patientId != null) {
            user.setPatientId(patientId);
        }
        
        // Si hay un connectedPatientId guardado, asignarlo al usuario
        String connectedPatientId = preferencesHelper.getConnectedPatientId();
        if (connectedPatientId != null) {
            user.setConnectedPatientId(connectedPatientId);
        }
        
        callback.onSuccess(user);
    }

    /**
     * Verifica si un ID de paciente es válido y retorna el usuario asociado
     */
    public void verifyPatientId(String patientId, PatientVerificationCallback callback) {
        Log.d(TAG, "Iniciando verificación para ID: " + patientId);
        
        if (!firebaseDataSource.isInitialized()) {
            Log.e(TAG, "Firebase no está inicializado");
            callback.onError("Firebase no está inicializado");
            return;
        }
        
        // Convertir a mayúsculas para asegurar consistencia
        patientId = patientId.toUpperCase().trim();
        
        // Verificar si el ID existe
        Log.d(TAG, "Consultando ID de paciente: " + patientId);
        
        final String finalPatientId = patientId; // Para usar en lambda
        DatabaseReference patientIdsRef = FirebaseDatabase.getInstance().getReference("patient_ids");
        patientIdsRef.child(patientId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "ID encontrado: " + finalPatientId);
                    // ID existe, obtener datos del paciente
                    String userId = snapshot.child("userId").getValue(String.class);
                    Log.d(TAG, "userId asociado: " + userId);
                    
                    if (userId != null) {
                        // Obtener datos completos del paciente
                        firebaseDataSource.getUserData(userId, new FirebaseDataSource.DataCallback() {
                            @Override
                            public void onSuccess(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    Log.d(TAG, "Datos de usuario encontrados para userId: " + userId);
                                    // Construir objeto User
                                    User patient = new User();
                                    patient.setId(userId);
                                    
                                    String name = dataSnapshot.child("name").getValue(String.class);
                                    patient.setName(name != null ? name : "Paciente");
                                    
                                    String email = dataSnapshot.child("email").getValue(String.class);
                                    patient.setEmail(email);
                                    
                                    patient.setUserType(AppConstants.USER_TYPE_PATIENT);
                                    patient.setPatientId(finalPatientId);
                                    
                                    // Guardar conexión para este familiar
                                    preferencesHelper.saveConnectedPatientId(finalPatientId);
                                    
                                    Log.d(TAG, "Verificación exitosa para paciente: " + name);
                                    callback.onVerified(patient);
                                } else {
                                    Log.e(TAG, "No se encontraron datos para el userId: " + userId);
                                    callback.onError("No se encontraron datos del paciente asociado a este ID");
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Log.e(TAG, "Error al obtener datos del paciente: " + errorMessage);
                                callback.onError("Error al obtener datos: " + errorMessage);
                            }
                        });
                    } else {
                        Log.e(TAG, "El ID existe pero no tiene userId asociado: " + finalPatientId);
                        callback.onError("ID de paciente inválido (sin usuario asociado)");
                    }
                } else {
                    Log.e(TAG, "No se encontró el ID: " + finalPatientId);
                    callback.onError("No se encontró el ID de paciente: " + finalPatientId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error en la consulta: " + error.getMessage());
                callback.onError("Error al verificar ID: " + error.getMessage());
            }
        });
    }

    /**
     * Actualiza un objeto User en la base de datos
     */
    public void updateUser(User user) {
        if (!firebaseDataSource.isInitialized() || user.getId() == null) {
            return;
        }
        
        firebaseDataSource.saveUserData(user.getId(), user.toMap(), 
            new FirebaseDataSource.DatabaseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Usuario actualizado correctamente");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "Error al actualizar usuario: " + errorMessage);
                }
            });
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
     * Guarda el ID de paciente conectado para usuarios familiares
     */
    public void saveConnectedPatientId(String patientId) {
        preferencesHelper.saveConnectedPatientId(patientId);
    }

    /**
     * Obtiene el ID de paciente conectado para usuarios familiares
     */
    public String getConnectedPatientId() {
        return preferencesHelper.getConnectedPatientId();
    }

    /**
     * Guarda el nombre del paciente conectado
     */
    public void saveConnectedPatientName(String name) {
        preferencesHelper.saveConnectedPatientName(name);
    }

    /**
     * Guarda el email del paciente conectado
     */
    public void saveConnectedPatientEmail(String email) {
        preferencesHelper.saveConnectedPatientEmail(email);
    }

    /**
     * Obtiene el nombre del paciente conectado
     */
    public String getConnectedPatientName() {
        return preferencesHelper.getConnectedPatientName();
    }

    /**
     * Obtiene el email del paciente conectado
     */
    public String getConnectedPatientEmail() {
        return preferencesHelper.getConnectedPatientEmail();
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
        
        User user = new User(id, name, email, userType);
        
        // Agregar IDs específicos si están disponibles
        String patientId = preferencesHelper.getPatientId();
        if (patientId != null) {
            user.setPatientId(patientId);
        }
        
        String connectedPatientId = preferencesHelper.getConnectedPatientId();
        if (connectedPatientId != null) {
            user.setConnectedPatientId(connectedPatientId);
        }
        
        return user;
    }

    /**
     * Interface de callback para autenticación
     */
    public interface AuthCallback {
        void onSuccess(User user);
        void onError(String errorMessage);
    }
    
    /**
     * Interface para la verificación del ID del paciente
     */
    public interface PatientVerificationCallback {
        void onVerified(User patient);
        void onError(String errorMessage);
    }
}