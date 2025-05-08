package com.espressif.ui.activities.mqtt_activities;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

import com.espressif.AppConstants;

public class MqttHandler {
    private static final String TAG = "MqttHandler";
    private final MqttClient client;
    private final MqttConnectOptions options;
    private final Context context;
    private final MqttCallback externalCallback;

    public MqttHandler(Context context, MqttCallback callback) {
        this.context = context;
        this.externalCallback = callback;

        try {
            String clientId = MqttClient.generateClientId();
            this.client = new MqttClient(AppConstants.MQTT_BROKER_URL, clientId, null);

            // Configuramos el callback para reenviar todos los mensajes al callback externo
            this.client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    if (externalCallback != null) {
                        externalCallback.connectionLost(cause);
                    }
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Mensaje recibido en tópico " + topic + ": " + payload);

                    // Reenviar el mensaje al callback externo
                    if (externalCallback != null) {
                        externalCallback.messageArrived(topic, message);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    if (externalCallback != null) {
                        externalCallback.deliveryComplete(token);
                    }
                }
            });

            this.options = new MqttConnectOptions();
            this.options.setCleanSession(true);
            this.options.setConnectionTimeout(30);
            this.options.setKeepAliveInterval(60);
            this.options.setAutomaticReconnect(true);

            // Configurar usuario y contraseña
            this.options.setUserName(AppConstants.MQTT_USER);
            this.options.setPassword(AppConstants.MQTT_PASSWORD.toCharArray());

            // Configurar TLS/SSL con el certificado de CA
            this.options.setSocketFactory(getSSLSocketFactory());
        } catch (MqttException e) {
            Log.e(TAG, "Error initializing MQTT client", e);
            throw new RuntimeException("Could not initialize MQTT client", e);
        }
    }

    private SSLSocketFactory getSSLSocketFactory() {
        try {
            // Cargar el certificado de CA desde res/raw
            InputStream caInput = context.getResources().openRawResource(
                    context.getResources().getIdentifier("ca", "raw", context.getPackageName()));

            // Crear un KeyStore para almacenar el certificado de CA
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null); // Inicializar el KeyStore vacío
            keyStore.setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(caInput));

            // Crear un TrustManagerFactory con el KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Crear un contexto SSL con el TrustManagerFactory
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            return sslContext.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "Error configurando SSL", e);
            throw new RuntimeException("Error configurando SSL", e);
        }
    }

    public void connect() throws MqttException {
        if (!client.isConnected()) {
            client.connect(options);
        }
    }

    public void disconnect() {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error disconnecting MQTT client", e);
        }
    }

    public void subscribe(String topic, int qos) throws MqttException {
        if (client.isConnected()) {
            client.subscribe(topic, qos);
        }
    }

    public void publishMessage(String topic, String message) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
        mqttMessage.setQos(1);
        client.publish(topic, mqttMessage);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}