package com.example.proyecto_seguridadciudadana.repository

import com.example.proyecto_seguridadciudadana.models.Alert
import com.example.proyecto_seguridadciudadana.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance(
        FirebaseApp.getInstance(),
        "safe-zone"
    )

    // Obtener usuario actual
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Obtener datos del usuario
    suspend fun getUser(userId: String): User? {
        return try {
            firestore.collection("users")
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Crear o actualizar usuario
    suspend fun createOrUpdateUser(user: User): Boolean {
        return try {
            firestore.collection("users")
                .document(user.id)
                .set(user)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Enviar alerta
    suspend fun sendAlert(alert: Alert): Boolean {
        return try {
            firestore.collection("alerts")
                .add(alert)
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Obtener alertas activas
    suspend fun getActiveAlerts(communityId: String = "general"): List<Alert> {
        return try {
            firestore.collection("alerts")
                .whereEqualTo("communityId", communityId)
                .whereEqualTo("status", "active")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Alert::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 🆕 Verificar si el usuario ya validó una alerta
    suspend fun hasUserValidated(alertId: String, userId: String): Boolean {
        return try {
            val doc = firestore.collection("alerts")
                .document(alertId)
                .collection("validators")
                .document(userId)
                .get()
                .await()

            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    // 🆕 Validar una alerta
    suspend fun validateAlert(alertId: String, userId: String, isTrue: Boolean): Boolean {
        return try {
            // 1. Registrar la validación del usuario
            firestore.collection("alerts")
                .document(alertId)
                .collection("validators")
                .document(userId)
                .set(mapOf(
                    "isTrue" to isTrue,
                    "timestamp" to FieldValue.serverTimestamp()
                ))
                .await()

            // 2. Actualizar el score de validación
            val fieldToIncrement = if (isTrue) "validationScore.trueVotes" else "validationScore.falseVotes"

            firestore.collection("alerts")
                .document(alertId)
                .update(fieldToIncrement, FieldValue.increment(1))
                .await()

            // 3. Obtener la alerta actualizada para verificar el estado
            val alert = firestore.collection("alerts")
                .document(alertId)
                .get()
                .await()
                .toObject(Alert::class.java)

            alert?.let {
                val trueVotes = it.validationScore.trueVotes
                val falseVotes = it.validationScore.falseVotes

                // Actualizar estado si es necesario
                val newStatus = when {
                    falseVotes > trueVotes * 2 && falseVotes >= 3 -> {
                        // Marcar como falsa si tiene muchos votos falsos
                        firestore.collection("alerts")
                            .document(alertId)
                            .update(
                                "validationStatus", "false",
                                "status", "inactive"
                            )
                            .await()
                        "false"
                    }
                    trueVotes > falseVotes * 2 && trueVotes >= 3 -> {
                        // Marcar como verificada si tiene muchos votos verdaderos
                        firestore.collection("alerts")
                            .document(alertId)
                            .update("validationStatus", "verified")
                            .await()
                        "verified"
                    }
                    else -> null
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}