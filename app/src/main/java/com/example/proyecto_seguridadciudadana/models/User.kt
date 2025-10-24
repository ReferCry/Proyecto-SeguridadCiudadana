package com.example.proyecto_seguridadciudadana.models

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val communities: List<String> = listOf("general"), // Comunidad por defecto
    val fcmToken: String = ""
) {
    constructor() : this("")
}