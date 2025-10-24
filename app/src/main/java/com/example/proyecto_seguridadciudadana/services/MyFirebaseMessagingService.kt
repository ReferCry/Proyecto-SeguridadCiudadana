package com.example.proyecto_seguridadciudadana.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.proyecto_seguridadciudadana.Home_Activity
import com.example.proyecto_seguridadciudadana.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "safezone_alerts"
        private const val CHANNEL_NAME = "Alertas de Emergencia"
    }

    /**
     * Este método se llama cuando llega una notificación mientras la app está en primer plano
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Mensaje recibido de: ${message.from}")

        // Extraer datos de la notificación
        message.notification?.let {
            Log.d(TAG, "Título: ${it.title}")
            Log.d(TAG, "Cuerpo: ${it.body}")

            // Mostrar la notificación
            sendNotification(it.title ?: "Alerta", it.body ?: "Nueva alerta en tu comunidad")
        }

        // También puedes recibir datos personalizados
        message.data.isNotEmpty().let {
            Log.d(TAG, "Datos: ${message.data}")
            // Aquí puedes extraer datos como alertId, tipo, etc.
        }
    }

    /**
     * Este método se llama cuando se genera un nuevo token FCM
     * (Primera vez que instalas la app, o cuando cambias de dispositivo)
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: $token")

        // Guardar el token en Firestore
        saveTokenToFirestore(token)
    }

    /**
     * Guarda el token en Firestore para poder enviar notificaciones después
     */
    private fun saveTokenToFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val firestore = FirebaseFirestore.getInstance()

        // Actualizar el token en el documento del usuario
        firestore.collection("users")
            .document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "Token guardado en Firestore")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar token: ${e.message}")
            }
    }

    /**
     * Muestra la notificación en la barra de estado del teléfono
     */
    private fun sendNotification(title: String, messageBody: String) {
        // Intent para abrir la app cuando tocan la notificación
        val intent = Intent(this, Home_Activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Sonido de notificación por defecto
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Construir la notificación
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // 🔴 Crearemos este ícono después
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // Se cierra al tocarla
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Alta prioridad
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Para Android 8.0+ necesitamos crear un canal de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alertas de emergencia"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Mostrar la notificación
        notificationManager.notify(0, notificationBuilder.build())
    }
}