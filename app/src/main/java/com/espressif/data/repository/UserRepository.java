package com.espressif.data.repository;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.data.model.User;
import com.espressif.data.source.local.SharedPreferencesHelper;
import com.espressif.data.source.remote.FirebaseDataSource;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
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

    // Referencias a la base de datos
    private final DatabaseReference patientsRef;
    private final DatabaseReference authMappingsRef;

    private UserRepository(Context context) {
        this.firebaseDataSource = FirebaseDataSource.getInstance();
        this.preferencesHelper = SharedPreferencesHelper.getInstance(context);
        
        // Inicializar Firebase
        firebaseDataSource.initialize(context);
        
        // Inicializar referencias de Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        patientsRef = database.getReference("patients");
        authMappingsRef = database.getReference("auth_mappings");
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
                // Verificar si es un usuario existente (paciente o familiar)
                checkExistingUser(user, callback);
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
     * Verifica si el usuario ya existe en la base de datos
     */
    private void checkExistingUser(FirebaseUser firebaseUser, AuthCallback callback) {
        String userId = firebaseUser.getUid();
        
        // Verificar en auth_mappings si existe un patientId asociado
        authMappingsRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Usuario existente, obtener su patientId asociado
                    String patientId = snapshot.getValue(String.class);
                    
                    if (patientId != null) {
                        // Es un paciente existente, cargar sus datos
                        loadPatientData(userId, patientId, firebaseUser, callback);
                    } else {
                        // Mapeo existe pero sin valor válido, tratar como usuario nuevo
                        createNewUser(firebaseUser, callback);
                    }
                } else {
                    // Usuario nuevo, crear según su tipo
                    createNewUser(firebaseUser, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error al verificar usuario existente: " + error.getMessage());
                createNewUser(firebaseUser, callback);
            }
        });
    }
    
    /**
     * Carga los datos de un paciente existente
     */
    private void loadPatientData(String userId, String patientId, FirebaseUser firebaseUser, AuthCallback callback) {
        patientsRef.child(patientId).child("basic_info").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Crear objeto User con los datos
                    User user = new User();
                    user.setId(userId);
                    user.setPatientId(patientId);
                    
                    String name = snapshot.child("name").getValue(String.class);
                    user.setName(name != null ? name : firebaseUser.getDisplayName());
                    
                    String email = snapshot.child("email").getValue(String.class);
                    user.setEmail(email != null ? email : firebaseUser.getEmail());
                    
                    String userType = snapshot.child("userType").getValue(String.class);
                    user.setUserType(userType != null ? userType : AppConstants.USER_TYPE_PATIENT);
                    
                    // Guardar en preferencias
                    saveUserPreferences(user);
                    
                    callback.onSuccess(user);
                } else {
                    // Datos incompletos, reparar y continuar
                    repairPatientData(userId, patientId, firebaseUser, callback);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error al cargar datos del paciente: " + error.getMessage());
                // Continuar con datos básicos
                User user = createBasicUser(userId, firebaseUser);
                user.setPatientId(patientId);
                saveUserPreferences(user);
                callback.onSuccess(user);
            }
        });
    }
    
    /**
     * Repara datos incompletos de un paciente
     */
    private void repairPatientData(String userId, String patientId, FirebaseUser firebaseUser, AuthCallback callback) {
        // Crear datos básicos para el paciente
        Map<String, Object> basicInfo = new HashMap<>();
        basicInfo.put("userId", userId);
        basicInfo.put("name", firebaseUser.getDisplayName());
        basicInfo.put("email", firebaseUser.getEmail());
        basicInfo.put("userType", AppConstants.USER_TYPE_PATIENT);
        basicInfo.put("createdAt", ServerValue.TIMESTAMP);
        
        // Guardar en patients/[patientId]/basic_info
        patientsRef.child(patientId).child("basic_info").updateChildren(basicInfo)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Datos de paciente reparados para patientId: " + patientId);
                
                // Crear usuario con datos básicos
                User user = createBasicUser(userId, firebaseUser);
                user.setPatientId(patientId);
                saveUserPreferences(user);
                callback.onSuccess(user);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error al reparar datos del paciente: " + e.getMessage());
                
                // Continuar con datos básicos de todos modos
                User user = createBasicUser(userId, firebaseUser);
                user.setPatientId(patientId);
                saveUserPreferences(user);
                callback.onSuccess(user);
            });
    }
    
    /**
     * Crea un nuevo usuario según el tipo seleccionado
     */
    private void createNewUser(FirebaseUser firebaseUser, AuthCallback callback) {
        String userType = preferencesHelper.getUserType();
        String userId = firebaseUser.getUid();
        
        if (AppConstants.USER_TYPE_PATIENT.equals(userType)) {
            // Crear nuevo paciente
            createNewPatient(userId, firebaseUser, callback);
        } else if (AppConstants.USER_TYPE_FAMILY.equals(userType)) {
            // Para familiar, primero debe conectarse a un paciente
            User user = createBasicUser(userId, firebaseUser);
            saveUserPreferences(user);
            callback.onSuccess(user);
        } else {
            // Tipo desconocido, usar datos básicos
            User user = createBasicUser(userId, firebaseUser);
            saveUserPreferences(user);
            callback.onSuccess(user);
        }
    }
    
    /**
     * Crea un nuevo paciente con ID único
     */
    private void createNewPatient(String userId, FirebaseUser firebaseUser, AuthCallback callback) {
        generateUniquePatientId(candidateId -> {
            if (candidateId != null) {
                // Crear estructura básica del paciente
                Map<String, Object> basicInfo = new HashMap<>();
                basicInfo.put("userId", userId);
                basicInfo.put("name", firebaseUser.getDisplayName());
                basicInfo.put("email", firebaseUser.getEmail());
                basicInfo.put("userType", AppConstants.USER_TYPE_PATIENT);
                basicInfo.put("createdAt", ServerValue.TIMESTAMP);
                
                // Guardar en patients/[patientId]/basic_info
                patientsRef.child(candidateId).child("basic_info").setValue(basicInfo)
                    .addOnSuccessListener(aVoid -> {
                        // Guardar mapeo de userId a patientId
                        authMappingsRef.child(userId).setValue(candidateId)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "Nuevo paciente creado con ID: " + candidateId);
                                
                                // Crear usuario y guardarlo en preferencias
                                User user = createBasicUser(userId, firebaseUser);
                                user.setPatientId(candidateId);
                                user.setUserType(AppConstants.USER_TYPE_PATIENT);
                                saveUserPreferences(user);
                                preferencesHelper.savePatientId(candidateId);
                                
                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error al crear mapeo de auth: " + e.getMessage());
                                // Continuar con el usuario de todos modos
                                User user = createBasicUser(userId, firebaseUser);
                                user.setPatientId(candidateId);
                                user.setUserType(AppConstants.USER_TYPE_PATIENT);
                                saveUserPreferences(user);
                                callback.onSuccess(user);
                            });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al crear paciente: " + e.getMessage());
                        // Continuar con datos básicos
                        User user = createBasicUser(userId, firebaseUser);
                        saveUserPreferences(user);
                        callback.onSuccess(user);
                    });
            } else {
                // No se pudo generar un ID único
                Log.e(TAG, "No se pudo generar un ID único para el paciente");
                User user = createBasicUser(userId, firebaseUser);
                saveUserPreferences(user);
                callback.onSuccess(user);
            }
        });
    }
    
    /**
     * Genera un ID único para el paciente
     */
    private void generateUniquePatientId(PatientIdCallback callback) {
        String candidateId = generatePatientIdCandidate();
        patientsRef.child(candidateId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // ID ya existe, intentar otro
                    generateUniquePatientId(callback);
                } else {
                    // ID disponible
                    callback.onIdGenerated(candidateId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error al verificar ID candidato: " + error.getMessage());
                callback.onIdGenerated(null);
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
     * Crea un objeto de usuario básico a partir de FirebaseUser
     */
    private User createBasicUser(String userId, FirebaseUser firebaseUser) {
        User user = new User();
        user.setId(userId);
        user.setName(firebaseUser.getDisplayName());
        user.setEmail(firebaseUser.getEmail());
        user.setUserType(preferencesHelper.getUserType());
        return user;
    }
    
    /**
     * Guarda la información del usuario en las preferencias locales
     */
    private void saveUserPreferences(User user) {
        Log.d(TAG, "Guardando preferencias de usuario: " + user.getName() + ", tipo: " + user.getUserType());
        preferencesHelper.setLoggedIn(true);
        preferencesHelper.saveUserInfo(user.getName(), user.getEmail());
        preferencesHelper.saveUserType(user.getUserType());
        
        if (user.getPatientId() != null) {
            Log.d(TAG, "Guardando ID de paciente: " + user.getPatientId());
            preferencesHelper.savePatientId(user.getPatientId());
        }
        
        if (user.getConnectedPatientId() != null) {
            Log.d(TAG, "Guardando ID de paciente conectado: " + user.getConnectedPatientId());
            preferencesHelper.saveConnectedPatientId(user.getConnectedPatientId());
        }
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
        final String finalPatientId = patientId;
        
        // Verificar si el paciente existe directamente
        patientsRef.child(patientId).child("basic_info").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Paciente encontrado, extraer información
                    String userId = snapshot.child("userId").getValue(String.class);
                    String name = snapshot.child("name").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    
                    // Crear objeto usuario
                    User patient = new User();
                    patient.setId(userId);
                    patient.setName(name != null ? name : "Paciente " + finalPatientId);
                    patient.setEmail(email);
                    patient.setUserType(AppConstants.USER_TYPE_PATIENT);
                    patient.setPatientId(finalPatientId);
                    
                    // Guardar los datos del paciente en preferencias
                    preferencesHelper.saveConnectedPatientId(finalPatientId);
                    if (name != null) preferencesHelper.saveConnectedPatientName(name);
                    if (email != null) preferencesHelper.saveConnectedPatientEmail(email);
                    
                    // Registrar al usuario actual como familiar
                    registerAsFamilyMember(finalPatientId);
                    
                    Log.d(TAG, "Verificación exitosa para paciente: " + patient.getName());
                    callback.onVerified(patient);
                } else {
                    Log.e(TAG, "No se encontró el paciente: " + finalPatientId);
                    callback.onError("No se encontró el paciente con ID: " + finalPatientId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error al verificar paciente: " + error.getMessage());
                callback.onError("Error al verificar ID: " + error.getMessage());
            }
        });
    }
    
    /**
     * Registra al usuario actual como familiar de un paciente
     */
    private void registerAsFamilyMember(String patientId) {
        FirebaseUser currentUser = firebaseDataSource.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No hay usuario autenticado para registrar como familiar");
            return;
        }
        
        String familyUserId = currentUser.getUid();
        
        // Crear datos del familiar
        Map<String, Object> familyData = new HashMap<>();
        familyData.put("userId", familyUserId);
        familyData.put("name", currentUser.getDisplayName());
        familyData.put("email", currentUser.getEmail());
        familyData.put("joinedAt", ServerValue.TIMESTAMP);
        familyData.put("userType", AppConstants.USER_TYPE_FAMILY);
        
        // Guardar en patients/[patientId]/family_members/[userId]
        patientsRef.child(patientId)
                .child("family_members")
                .child(familyUserId)
                .updateChildren(familyData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Usuario registrado como familiar para el paciente: " + patientId);
                    
                    // También actualizar las preferencias locales
                    preferencesHelper.saveUserType(AppConstants.USER_TYPE_FAMILY);
                    preferencesHelper.saveConnectedPatientId(patientId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al registrar como familiar: " + e.getMessage());
                });
    }

    // Resto de métodos auxiliares y de utilidad
    
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
     * Verifica si el usuario ha iniciado sesión
     * @return true si el usuario ha iniciado sesión, false en caso contrario
     */
    public boolean isUserLoggedIn() {
        // Verificar PRIMERO en Firebase Auth
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean firebaseLoggedIn = (currentUser != null);
        
        // Verificar el valor almacenado en SharedPreferences
        boolean prefsLoggedIn = preferencesHelper.isLoggedIn();
        
        Log.d(TAG, "Estado de login - Firebase: " + firebaseLoggedIn + 
               ", SharedPreferences: " + prefsLoggedIn);
        
        // Si hay inconsistencia, actualizar SharedPreferences para que coincida con Firebase
        if (firebaseLoggedIn != prefsLoggedIn) {
            preferencesHelper.setLoggedIn(firebaseLoggedIn);
            Log.d(TAG, "Corrigiendo inconsistencia de login en SharedPreferences");
        }
        
        // Devolver el estado real de Firebase
        return firebaseLoggedIn;
    }

    /**
     * Establece el estado de inicio de sesión del usuario
     * @param isLoggedIn true si el usuario está logueado, false en caso contrario
     */
    public void setLoggedIn(boolean isLoggedIn) {
        Log.d(TAG, "Estableciendo estado de login: " + isLoggedIn);
        preferencesHelper.setLoggedIn(isLoggedIn);
    }

    /**
     * Obtiene el ID del paciente conectado
     */
    public String getConnectedPatientId() {
        return preferencesHelper.getConnectedPatientId();
    }

    /**
     * Obtiene el tipo de usuario
     */
    public String getUserType() {
        String userType = preferencesHelper.getUserType();
        Log.d(TAG, "Obteniendo tipo de usuario desde repositorio: " + userType);
        return userType;
    }

    /**
     * Guarda el tipo de usuario
     */
    public void saveUserType(String userType) {
        preferencesHelper.saveUserType(userType);
    }

    /**
     * Guarda el ID del paciente conectado
     */
    public void saveConnectedPatientId(String patientId) {
        preferencesHelper.saveConnectedPatientId(patientId);
    }

    /**
     * Guarda el nombre del paciente conectado
     */
    public void saveConnectedPatientName(String name) {
        preferencesHelper.saveConnectedPatientName(name);
    }

    /**
     * Guarda el correo del paciente conectado
     */
    public void saveConnectedPatientEmail(String email) {
        preferencesHelper.saveConnectedPatientEmail(email);
    }

    /**
     * Verifica si el usuario ha completado el proceso de provisioning
     */
    public boolean hasCompletedProvisioning() {
        boolean completed = preferencesHelper.hasCompletedProvisioning();
        Log.d(TAG, "Verificando provisioning completado desde repositorio: " + completed);
        return completed;
    }

    /**
     * Verifica si el usuario ha completado el proceso completo de onboarding
     * @return true si el usuario ha completado el flujo de onboarding, false en caso contrario
     */
    public boolean hasCompletedOnboarding() {
        // Verificar doble flag - debe tener tanto sesión en Firebase como el flag de onboarding
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean firebaseLoggedIn = (currentUser != null);
        boolean onboardingCompleted = preferencesHelper.getBoolean(AppConstants.KEY_ONBOARDING_COMPLETED, false);
        
        Log.d(TAG, "Verificando onboarding - Firebase Auth: " + firebaseLoggedIn + 
               ", Onboarding completado: " + onboardingCompleted);
        
        // Solo está completo si ambas condiciones son verdaderas
        return firebaseLoggedIn && onboardingCompleted;
    }

    /**
     * Marca que el usuario ha completado el proceso de onboarding
     * @param completed true si el onboarding está completo
     */
    public void setOnboardingCompleted(boolean completed) {
        Log.d(TAG, "Estableciendo estado de onboarding: " + completed);
        preferencesHelper.putBoolean(AppConstants.KEY_ONBOARDING_COMPLETED, completed);
    }

    /**
     * Realiza un restablecimiento completo de la aplicación
     * Este método debe llamarse al desinstalar/reinstalar o cuando
     * se desee volver al estado inicial
     */
    public void resetAppState() {
        Log.d(TAG, "Realizando reset completo de la aplicación");
        
        // 1. Cerrar sesión en Firebase
        if (firebaseDataSource.isInitialized()) {
            firebaseDataSource.signOut();
        }
        
        // 2. Borrar todos los datos de preferencias
        preferencesHelper.clearAllData();
        
        // 3. Borrar específicamente los flags clave
        preferencesHelper.putBoolean(AppConstants.KEY_ONBOARDING_COMPLETED, false);
        preferencesHelper.setLoggedIn(false);
        preferencesHelper.saveUserType(null);
        
    }

    /**
     * Obtiene el ID del paciente seleccionado actualmente
     * @return El ID del paciente seleccionado o null si no hay ninguno
     */
    public String getSelectedPatientId() {
        // Cache temporal para evitar múltiples llamadas
        String cachedId = null;
        
        // Estrategia 1: Usuario es paciente
        if (AppConstants.USER_TYPE_PATIENT.equals(getUserType())) {
            cachedId = preferencesHelper.getPatientId();
            if (isValidPatientId(cachedId)) {
                Log.d(TAG, "✓ ID obtenido de paciente directo: " + cachedId);
                return cachedId;
            }
        }
        
        // Estrategia 2: Usuario es familiar
        cachedId = preferencesHelper.getConnectedPatientId();
        if (isValidPatientId(cachedId)) {
            Log.d(TAG, "✓ ID obtenido de paciente conectado: " + cachedId);
            return cachedId;
        }
        
        // Estrategia 3: Firebase Auth
        if (firebaseDataSource.isInitialized()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String firebaseId = getUserPatientIdFromFirebase(user.getUid());
                if (isValidPatientId(firebaseId)) {
                    // Guardar en preferencias para futuros accesos
                    preferencesHelper.savePatientId(firebaseId);
                    Log.d(TAG, "✓ ID recuperado de Firebase: " + firebaseId);
                    return firebaseId;
                }
            }
        }
        
        Log.e(TAG, "✗ No se pudo obtener un ID de paciente válido");
        return null;
    }

    private boolean isValidPatientId(String id) {
        if (id == null || id.isEmpty() || "current_user_id".equals(id)) {
            return false;
        }
        
        // Validación adicional: debe tener el formato correcto (6 caracteres alfanuméricos)
        return id.matches("[A-Z0-9]{6}");
    }

    private String getUserPatientIdFromFirebase(String userId) {
        // Intentar obtener el ID mapeado en Firebase
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("auth_mappings")
                .child(userId);
        
        final String[] patientId = {null};
        
        // Realizar consulta sincrónica (esto es seguro solo en background)
        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                patientId[0] = task.getResult().getValue(String.class);
            }
        });
        
        return patientId[0];
    }

    /**
     * Método de conveniencia para acceder a las preferencias directamente
     * @return El objeto SharedPreferencesHelper
     */
    public SharedPreferencesHelper getPreferencesHelper() {
        return preferencesHelper;
    }

    // Interfaces adicionales
    
    /**
     * Interface para generación de ID de paciente
     */
    private interface PatientIdCallback {
        void onIdGenerated(String patientId);
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