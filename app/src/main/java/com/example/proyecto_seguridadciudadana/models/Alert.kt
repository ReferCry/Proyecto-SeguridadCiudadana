package com.example.proyecto_seguridadciudadana.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

data class Alert(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val communityId: String = "general", // Por ahora usaremos una comunidad general
    val type: String = "",
    val subtype: String = "",
    val location: GeoPoint? = null,
    val address: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp = Timestamp(Timestamp.now().seconds + 1800, 0), // 30 minutos
    val status: String = "active",
    val validationStatus: String = "pending",
    val validationScore: ValidationScore = ValidationScore()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("")
}

data class ValidationScore(
    val trueVotes: Int = 0,
    val falseVotes: Int = 0
)