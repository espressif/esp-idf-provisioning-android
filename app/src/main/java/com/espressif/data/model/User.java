package com.espressif.data.model;

import java.util.HashMap;
import java.util.Map;

public class User {
    
    private String id;
    private String name;
    private String email;
    private String userType;
    private long lastLogin;
    private String patientId; // Nuevo campo para el ID único del paciente
    private String connectedPatientId; // ID del paciente al que está conectado (para familiares)

    public User() {
        // Constructor vacío requerido para Firebase
    }

    public User(String id, String name, String email, String userType) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.userType = userType;
        this.lastLogin = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    /**
     * Obtiene el ID único del paciente
     * @return El ID único, o null si no es un paciente o aún no se ha generado
     */
    public String getPatientId() {
        return patientId;
    }

    /**
     * Establece el ID único del paciente
     * @param patientId El nuevo ID único a asignar
     */
    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    /**
     * Obtiene el ID del paciente al que este familiar está conectado
     * @return El ID del paciente conectado, o null si no está conectado a ninguno
     */
    public String getConnectedPatientId() {
        return connectedPatientId;
    }

    /**
     * Establece el ID del paciente al que este familiar debe conectarse
     * @param connectedPatientId El ID del paciente
     */
    public void setConnectedPatientId(String connectedPatientId) {
        this.connectedPatientId = connectedPatientId;
    }

    /**
     * Verifica si este usuario es un paciente
     * @return true si es paciente, false en caso contrario
     */
    public boolean isPatient() {
        return userType != null && userType.equals("patient");
    }

    /**
     * Verifica si este usuario es un familiar conectado a un paciente
     * @return true si es familiar conectado, false en caso contrario
     */
    public boolean isConnectedFamily() {
        return userType != null && userType.equals("family") && connectedPatientId != null;
    }

    /**
     * Convierte el objeto a un Map para guardar en Firebase
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("email", email);
        map.put("userType", userType);
        map.put("lastLogin", lastLogin);
        
        // Solo incluir patientId si no es null
        if (patientId != null) {
            map.put("patientId", patientId);
        }
        
        // Solo incluir connectedPatientId si no es null
        if (connectedPatientId != null) {
            map.put("connectedPatientId", connectedPatientId);
        }
        
        return map;
    }
}