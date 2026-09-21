package com.example.aarohiai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

data class TelemetryData(
    val level: Float = 0f,
    val motorRunning: Boolean = false,
    val mainsAvailable: Boolean = false,
    val autoMode: Boolean = false
)

class AarohiMqttManager(
    private val context: Context,
    private val onConnectionChanged: (String) -> Unit,
    private val onTelemetryReceived: (TelemetryData) -> Unit,
    private val onNotificationReceived: (String) -> Unit,
    private val onDeviceStatusChanged: (Boolean) -> Unit = {}
) {

    companion object {
        private const val TAG = "AarohiMqtt"

        // SharedPreferences
        private const val PREFS_NAME = "aarohi_mqtt_prefs"
        private const val KEY_CLIENT_ID = "mqtt_client_id"
        private const val KEY_BROKER_URI = "mqtt_broker_uri"
        private const val KEY_USERNAME = "mqtt_username"
        private const val KEY_PASSWORD = "mqtt_password"
        private const val KEY_VERIFIED = "mqtt_is_verified"

        // Topics
        private const val TOPIC_COMMAND       = "aarohi/cmd"
        private const val TOPIC_TELEMETRY     = "aarohi/telemetry"
        private const val TOPIC_NOTIFICATION  = "aarohi/notification"
        private const val TOPIC_STATUS        = "aarohi/status"
        private const val TOPIC_DEVICE_STATUS = "aarohi/device"

        private const val QOS = 1

        private const val DIGICERT_GLOBAL_ROOT_G2_PEM = """-----BEGIN CERTIFICATE-----
MIIDjjCCAnagAwIBAgIQAzrx5qcRqaC7KGSxHQn65TANBgkqhkiG9w0BAQsFADBh
MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3
d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBH
MjAeFw0xMzA4MDExMjAwMDBaFw0zODAxMTUxMjAwMDBaMGExCzAJBgNVBAYTAlVT
MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3dy5kaWdpY2VydC5j
b20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IEcyMIIBIjANBgkqhkiG
9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN9RlUyfuI
2/Ou8jqJkTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx
1x7e/dfgy5SDN67sH0NO3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQ
q2EGnI/yuum06ZIya7XzV+hdG82MHauVBJVJ8zUtluNJbd134/tJS7SsVQepj5Wz
tCO7TG1F8PapspUwtP1MVYwnSlcUfIKdzXOS0xZKBgyMUNGPHgm+F6HmIcr9g+UQ
vIOlCsRnKPZzFBQ9RnbDhxSJITRNrw9FDKZJobq7nMWxM4MphQIDAQABo0IwQDAP
BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBhjAdBgNVHQ4EFgQUTiJUIBiV
5uNu5g/6+rkS7QYXjzkwDQYJKoZIhvcNAQELBQADggEBAGBnKJRvDkhj6zHd6mcY
1Yl9PMWLSn/pvtsrF9+wX3N3KjITOYFnQoQj8kVnNeyIv/iPsGEMNKSuIEyExtv4
NeF22d+mQrvHRAiGfzZ0JFrabA0UWTW98kndth/Jsw1HKj2ZL7tcu7XUIOGZX1NG
Fdtom/DzMNU+MeKNhJ7jitralj41E6Vf8PlwUHBHQRFXGU7Aj64GxJUTFy8bJZ91
8rGOmaFvE7FBcf6IKshPECBV1/MUReXgRPTqh5Uykw7+U0b6LJ3/iyK5S9kJRaTe
pLiaWN0bfVKfjllDiIGknibVb63dDcY3fe0Dkhvld1927jyNxF1WW6LZZm6zNTfl
MrY=
-----END CERTIFICATE-----"""

        fun generateUUID(): String = UUID.randomUUID().toString()

        fun createUUID(prefix: String = "Aro_", length: Int = 12): String {
            return prefix + UUID.randomUUID().toString().replace("-", "").take(length)
        }
    }

    private fun getCustomSocketFactory(): SSLSocketFactory? {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val caInput = ByteArrayInputStream(DIGICERT_GLOBAL_ROOT_G2_PEM.toByteArray(Charsets.UTF_8))
            val ca = caInput.use { cf.generateCertificate(it) as X509Certificate }

            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("digicert_root_g2", ca)
            }

            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, tmf.trustManagers, null)
            }

            sslContext.socketFactory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom SSLSocketFactory", e)
            null
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedBrokerUri(): String = prefs.getString(KEY_BROKER_URI, "") ?: ""
    fun getSavedUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
    fun getSavedPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun isServerVerified(): Boolean {
        val uri = getSavedBrokerUri()
        val user = getSavedUsername()
        val pass = getSavedPassword()
        val verified = prefs.getBoolean(KEY_VERIFIED, false)
        return verified && uri.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    }

    fun saveCredentials(uri: String, user: String, pass: String) {
        prefs.edit {
            putString(KEY_BROKER_URI, uri.trim())
            putString(KEY_USERNAME, user.trim())
            putString(KEY_PASSWORD, pass.trim())
            putBoolean(KEY_VERIFIED, true)
        }
    }

    private val clientId: String = getOrCreateClientId()

    private fun getOrCreateClientId(): String {
        val existing = prefs.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val newId = "Aro_" + UUID.randomUUID().toString().replace("-", "").take(12)
        prefs.edit { putString(KEY_CLIENT_ID, newId) }
        return newId
    }

    private var mqttClient: MqttClient? = null
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isConnected = false

    // ==================== TEST / VERIFY CONNECTION ====================
    fun testConnection(
        uri: String,
        user: String,
        pass: String,
        onResult: (Boolean, String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    val tempClientId = "Verify_" + UUID.randomUUID().toString().replace("-", "").take(8)
                    val testClient = MqttClient(uri.trim(), tempClientId, MemoryPersistence())
                    val options = MqttConnectOptions().apply {
                        userName = user.trim()
                        password = pass.trim().toCharArray()
                        connectionTimeout = 5
                        keepAliveInterval = 30
                        isCleanSession = true
                        val trimmedUri = uri.trim().lowercase()
                        if (trimmedUri.startsWith("ssl://") || trimmedUri.startsWith("tls://") || trimmedUri.startsWith("wss://")) {
                            getCustomSocketFactory()?.let { socketFactory = it }
                        }
                    }
                    testClient.connect(options)
                    if (testClient.isConnected) {
                        testClient.disconnect()
                        testClient.close()
                        withContext(Dispatchers.Main) {
                            saveCredentials(uri, user, pass)
                            onResult(true, "Verified / Connected successfully!")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Connection failed. Please check Broker URI, Username, or Password.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Test connection failed", e)
                    withContext(Dispatchers.Main) {
                        onResult(false, "Connection Error: ${e.message ?: "Invalid broker configuration"}")
                    }
                }
            }
        }
    }

    fun getTargetMac(): String {
        val appPrefs = context.getSharedPreferences("aarohi_prefs", Context.MODE_PRIVATE)
        return appPrefs.getString("device_mac", "") ?: ""
    }

    // ==================== CONNECT ====================
    fun connect() {
        val activeUri = getSavedBrokerUri()
        val activeUser = getSavedUsername()
        val activePass = getSavedPassword()

        if (activeUri.isBlank() || activeUser.isBlank() || activePass.isBlank() || !isServerVerified()) {
            Log.w(TAG, "No valid MQTT credentials configured. Connection blocked.")
            onConnectionChanged("Disconnected")
            return
        }

        if (isConnected) {
            onConnectionChanged("Connected")
            return
        }

        onConnectionChanged("Connecting...")

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    mqttClient?.let {
                        try {
                            if (it.isConnected) it.disconnect()
                        } catch (_: Exception) {}
                    }

                    Log.d(TAG, "Connecting to $activeUri with clientId: $clientId")

                    mqttClient = MqttClient(
                        activeUri,
                        clientId,
                        MemoryPersistence()
                    )

                    mqttClient?.setCallback(
                        object : MqttCallbackExtended {
                            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.IO) {
                                        isConnected = true
                                        Log.d(TAG, "MQTT Connected: $serverURI (reconnect=$reconnect)")
                                        subscribeToTopics()
                                        withContext(Dispatchers.Main) { onConnectionChanged("Connected") }
                                    }
                                }
                            }

                            override fun connectionLost(cause: Throwable?) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.IO) {
                                        isConnected = false
                                        Log.e(TAG, "MQTT Connection lost", cause)
                                        withContext(Dispatchers.Main) {
                                            onConnectionChanged("Disconnected")
                                        }
                                    }
                                }
                            }

                            override fun messageArrived(topic: String?, message: MqttMessage?) {
                                if (message == null) return
                                val payload = message.payload.toString(Charsets.UTF_8)
                                Log.d(TAG, "RX [$topic] $payload")
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(Dispatchers.IO) {
                                        handleMessage(topic ?: "", payload)
                                    }
                                }
                            }

                            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                                Log.d(TAG, "Message delivered")
                            }
                        }
                    )

                    val options = MqttConnectOptions().apply {
                        userName = activeUser
                        password = activePass.toCharArray()
                        isCleanSession = false
                        isAutomaticReconnect = true
                        connectionTimeout = 5
                        keepAliveInterval = 60
                        mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                        val lowerUri = activeUri.trim().lowercase()
                        if (lowerUri.startsWith("ssl://") || lowerUri.startsWith("tls://") || lowerUri.startsWith("wss://")) {
                            getCustomSocketFactory()?.let { socketFactory = it }
                        }
                    }

                    mqttClient?.connect(options)
                    Log.d(TAG, "MQTT connection successful")
                } catch (e: Exception) {
                    isConnected = false
                    Log.e(TAG, "MQTT connection failed", e)
                    withContext(Dispatchers.Main) {
                        onConnectionChanged("Disconnected")
                        onNotificationReceived("MQTT connection failed.")
                    }
                }
            }
        }
    }

    // ==================== SUBSCRIBE ====================
    private fun subscribeToTopics() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    mqttClient?.subscribe(TOPIC_TELEMETRY, QOS)
                    mqttClient?.subscribe(TOPIC_NOTIFICATION, QOS)
                    mqttClient?.subscribe(TOPIC_STATUS, QOS)
                    mqttClient?.subscribe(TOPIC_DEVICE_STATUS, QOS)

                    Log.d(TAG, "Subscribed: telemetry, notification, status, device")
                    publishCommand("STATUS")

                } catch (e: Exception) {
                    Log.e(TAG, "MQTT subscribe failed", e)
                }
            }
        }
    }

    @Volatile
    private var pendingVerificationMac: String? = null
    private var verificationCallback: ((Boolean, String) -> Unit)? = null
    private var verificationTimeoutRunnable: Runnable? = null

    // ==================== DEVICE HANDSHAKE VERIFICATION ====================
    fun verifyDevice(mac: String, onResult: (Boolean, String) -> Unit) {
        val trimmedMac = mac.trim()
        if (!isConnected || mqttClient?.isConnected != true) {
            onResult(false, "MQTT Server is not connected. Please verify broker connection first.")
            return
        }

        verificationTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        pendingVerificationMac = trimmedMac
        verificationCallback = onResult

        val timeoutRunnable = Runnable {
            if (pendingVerificationMac != null) {
                Log.w(TAG, "Device verification timed out for MAC: $trimmedMac")
                val cb = verificationCallback
                pendingVerificationMac = null
                verificationCallback = null
                cb?.invoke(false, "Device Verification Failed! ❌ ESP32 not responding.")
            }
        }
        verificationTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, 6000)

        Log.d(TAG, "Sending VERIFY command for MAC: $trimmedMac")
        publishCommand("VERIFY:$trimmedMac")
    }

    // ==================== MESSAGE HANDLER ====================
    private suspend fun handleMessage(topic: String, payload: String) {
        withContext(Dispatchers.IO) {
            try {
                val text = payload.trim()
                if (text.contains("VERIFIED_OK")) {
                    Log.d(TAG, "ESP32 Handshake SUCCESS → VERIFIED_OK received for $topic")
                    withContext(Dispatchers.Main) {
                        onDeviceStatusChanged(true)
                        onNotificationReceived("ESP32 Device Handshake Verified!")

                        verificationTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        verificationTimeoutRunnable = null
                        val cb = verificationCallback
                        pendingVerificationMac = null
                        verificationCallback = null
                        cb?.invoke(true, "Device Verified & Paired Successfully! ✅")
                    }
                }

                when (topic) {
                    TOPIC_DEVICE_STATUS -> {
                        when (text) {
                            "ESP32_ONLINE" -> {
                                Log.d(TAG, "ESP32 DEVICE → ONLINE")
                                withContext(Dispatchers.Main) { onDeviceStatusChanged(true) }
                            }
                            "ESP32_OFFLINE" -> {
                                Log.d(TAG, "ESP32 DEVICE → OFFLINE")
                                withContext(Dispatchers.Main) { onDeviceStatusChanged(false) }
                            }
                            else -> {
                                if (!text.contains("VERIFIED_OK")) {
                                    Log.w(TAG, "Unknown device status: $payload")
                                }
                            }
                        }
                    }

                    TOPIC_TELEMETRY -> {
                        val telemetry = parseTelemetry(payload)
                        if (telemetry != null) {
                            withContext(Dispatchers.Main) { onTelemetryReceived(telemetry) }
                        } else {
                            Log.w(TAG, "Telemetry parse failed: $payload")
                        }
                    }

                    TOPIC_STATUS -> {
                        val telemetry = parseTelemetry(payload)
                        if (telemetry != null) {
                            withContext(Dispatchers.Main) { onTelemetryReceived(telemetry) }
                        } else if (!text.contains("VERIFIED_OK")) {
                            withContext(Dispatchers.Main) { onNotificationReceived(payload) }
                        }
                    }

                    TOPIC_NOTIFICATION -> {
                        val telemetry = parseTelemetry(payload)
                        if (telemetry != null) {
                            withContext(Dispatchers.Main) { onTelemetryReceived(telemetry) }
                        } else if (!text.contains("VERIFIED_OK")) {
                            withContext(Dispatchers.Main) { onNotificationReceived(payload) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Message handling error", e)
            }
        }
    }

    // ==================== TELEMETRY PARSER ====================
    private fun parseTelemetry(payload: String): TelemetryData? {
        val text = payload.trim()

        try {
            if (text.startsWith("{") && text.endsWith("}")) {
                val json = JsonParser.parseString(text).asJsonObject
                val level = json.get("level")?.asFloat ?: return null
                val motor = json.get("motorRunning")?.asBoolean ?: false
                val mains = json.get("mainsAvailable")?.asBoolean ?: false
                val auto  = json.get("autoMode")?.asBoolean ?: false

                return TelemetryData(level, motor, mains, auto)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not JSON telemetry")
        }

        if (!text.contains("STATUS") || !text.contains("Level:")) {
            return null
        }

        return try {
            val levelRegex = Regex("""Level:\s*([0-9]+(?:\.[0-9]+)?)%""")
            val levelMatch = levelRegex.find(text) ?: return null
            val level = levelMatch.groupValues[1].toFloatOrNull() ?: return null

            val motorLine = Regex("""Motor:\s*(.*)""").find(text)?.groupValues?.get(1) ?: ""
            val autoLine  = Regex("""Auto:\s*(.*)""").find(text)?.groupValues?.get(1) ?: ""
            val lightLine = Regex("""Light:\s*(.*)""").find(text)?.groupValues?.get(1) ?: ""

            TelemetryData(
                level = level,
                motorRunning = motorLine.contains("Running", ignoreCase = true),
                mainsAvailable = lightLine.contains("ON", ignoreCase = true),
                autoMode = autoLine.contains("ON", ignoreCase = true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Text telemetry parse error", e)
            null
        }
    }

    // ==================== PUBLISH ====================
    fun publishCommand(command: String) {
        publish(TOPIC_COMMAND, command)
    }

    fun publish(topic: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                val client = mqttClient
                if (client == null || !client.isConnected) {
                    Log.w(TAG, "Cannot publish. MQTT not connected.")
                    withContext(Dispatchers.Main) { onNotificationReceived("MQTT not connected.") }
                    return@withContext
                }

                try {
                    val mqttMessage = MqttMessage(
                        message.toByteArray(Charsets.UTF_8)
                    ).apply {
                        qos = QOS
                        isRetained = false
                    }

                    client.publish(topic, mqttMessage)
                    Log.d(TAG, "TX [$topic] $message")

                } catch (e: Exception) {
                    Log.e(TAG, "MQTT publish failed", e)
                    withContext(Dispatchers.Main) { onNotificationReceived("MQTT command failed.") }
                }
            }
        }
    }

    // ==================== DISCONNECT ====================
    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        verificationTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                        verificationTimeoutRunnable = null
                        pendingVerificationMac = null
                        verificationCallback = null
                    }

                    mqttClient?.let { client ->
                        if (client.isConnected) {
                            client.disconnect()
                            Log.d(TAG, "MQTT disconnected")
                        }
                        client.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MQTT disconnect error", e)
                } finally {
                    mqttClient = null
                    isConnected = false
                    withContext(Dispatchers.Main) { onConnectionChanged("Disconnected") }
                }
            }
        }
    }
}

// ==================== GLOBAL UUID GENERATOR HELPERS ====================
fun generateUUID(): String = java.util.UUID.randomUUID().toString()

fun createUUID(prefix: String = "Aro_", length: Int = 12): String {
    return prefix + java.util.UUID.randomUUID().toString().replace("-", "").take(length)
}
