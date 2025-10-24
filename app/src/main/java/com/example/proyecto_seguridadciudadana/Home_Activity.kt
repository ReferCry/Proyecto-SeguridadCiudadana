package com.example.proyecto_seguridadciudadana

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.proyecto_seguridadciudadana.models.Alert
import com.example.proyecto_seguridadciudadana.models.User
import com.example.proyecto_seguridadciudadana.repository.FirebaseRepository
import com.example.proyecto_seguridadciudadana.services.LocationService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class Home_Activity : AppCompatActivity() {

    private lateinit var btnSOS: Button
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtWelcome: TextView

    private val auth = FirebaseAuth.getInstance()
    private val repository = FirebaseRepository()
    private lateinit var locationService: LocationService

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Verificar autenticación
        if (auth.currentUser == null) {
            startActivity(Intent(this, Login_Activity::class.java))
            finish()
            return
        }

        // Inicializar servicios
        locationService = LocationService(this)

        // Inicializar vistas
        btnSOS = findViewById(R.id.btnSOS)
        bottomNav = findViewById(R.id.bottomNavigation)
        progressBar = findViewById(R.id.progressBar)
        txtWelcome = findViewById(R.id.txtWelcome)

        setupSOSButton()
        setupBottomNavigation()
        createUserIfNeeded()
        updateWelcomeMessage()

        // Configurar FCM
        requestNotificationPermission()
        setupFCMToken()
    }

    private fun createUserIfNeeded() {
        lifecycleScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            val userEmail = auth.currentUser?.email ?: ""
            val userName = auth.currentUser?.displayName ?: "Usuario"

            try {
                // Verificar si el usuario ya existe
                val existingUser = repository.getUser(userId)

                if (existingUser == null) {
                    // Usuario no existe, crear uno nuevo
                    Log.d("Firestore", "Creando nuevo usuario: $userName")

                    val newUser = User(
                        id = userId,
                        email = userEmail,
                        name = userName,
                        communities = listOf("general"),
                        fcmToken = "" // Se actualizará con setupFCMToken()
                    )

                    val success = repository.createOrUpdateUser(newUser)

                    if (success) {
                        Log.d("Firestore", "✅ Usuario creado exitosamente")
                        Toast.makeText(
                            this@Home_Activity,
                            "¡Bienvenido a SafeZone!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Log.e("Firestore", "❌ Error al crear usuario")
                    }
                } else {
                    Log.d("Firestore", "Usuario ${existingUser.name} ya existe")
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error al verificar usuario: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun updateWelcomeMessage() {
        val userName = auth.currentUser?.displayName ?: "Usuario"
        txtWelcome.text = "¡Hola $userName, mantente seguro!"
    }

    /**
     * Obtiene el token FCM y lo guarda en Firestore
     */
    private fun setupFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Error al obtener token", task.exception)
                return@addOnCompleteListener
            }

            // Obtener el token
            val token = task.result
            Log.d("FCM", "Token FCM: $token")

            // Guardar en Firestore
            saveTokenToFirestore(token)
        }
    }

    /**
     * Guarda el token FCM en el documento del usuario en Firestore
     */
    private fun saveTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance(
                    FirebaseApp.getInstance(),
                    "safe-zone"
                )

                firestore.collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM", "Token guardado exitosamente")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM", "Error al guardar token: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e("FCM", "Excepción al guardar token: ${e.message}")
            }
        }
    }

    /**
     * Solicita permiso de notificaciones para Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun setupSOSButton() {
        btnSOS.setOnClickListener {
            if (checkLocationPermission()) {
                showIncidentTypeDialog()
            } else {
                requestLocationPermission()
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    Log.d("Navigation", "Home seleccionado")
                    true
                }
                R.id.nav_map -> {
                    Log.d("Navigation", "Mapa seleccionado - Abriendo MapActivity")
                    try {
                        val intent = Intent(this, MapActivity::class.java)
                        startActivity(intent)
                        Log.d("Navigation", "Intent de MapActivity lanzado correctamente")
                    } catch (e: Exception) {
                        Log.e("Navigation", "Error al abrir MapActivity: ${e.message}")
                        Toast.makeText(this, "Error al abrir el mapa: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    true
                }
                R.id.nav_communities -> {
                    Toast.makeText(this, "Comunidades - En desarrollo", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_news -> {
                    // 🆕 Abrir AlertsActivity (Feed de alertas)
                    Log.d("Navigation", "Noticias/Alertas seleccionado - Abriendo AlertsActivity")
                    try {
                        val intent = Intent(this, AlertsActivity::class.java)
                        startActivity(intent)
                        Log.d("Navigation", "Intent de AlertsActivity lanzado correctamente")
                    } catch (e: Exception) {
                        Log.e("Navigation", "Error al abrir AlertsActivity: ${e.message}")
                        Toast.makeText(this, "Error al abrir alertas: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showIncidentTypeDialog() {
        val categories = arrayOf(
            "🏠 En el hogar",
            "🚗 En las calles",
            "⚠️ Atentados"
        )

        AlertDialog.Builder(this)
            .setTitle("Selecciona el tipo de incidente")
            .setItems(categories) { _, which ->
                when (which) {
                    0 -> showHomeIncidentDialog()
                    1 -> showStreetIncidentDialog()
                    2 -> showCriminalIncidentDialog()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showHomeIncidentDialog() {
        val incidents = arrayOf("Corte", "Caída", "Fuga de gas", "Incendio doméstico")
        showSubtypeDialog("Incidente en el hogar", incidents, "HOME_ACCIDENT")
    }

    private fun showStreetIncidentDialog() {
        val incidents = arrayOf("Accidente de tránsito", "Fuego en edificio", "Inundación", "Derrumbe")
        showSubtypeDialog("Incidente en la calle", incidents, "STREET_INCIDENT")
    }

    private fun showCriminalIncidentDialog() {
        val incidents = arrayOf("Robo", "Secuestro", "Extorsión", "Agresión")
        showSubtypeDialog("Actividad criminal", incidents, "CRIMINAL_ACTIVITY")
    }

    private fun showSubtypeDialog(title: String, subtypes: Array<String>, category: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(subtypes) { _, which ->
                sendAlert(category, subtypes[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendAlert(category: String, subtype: String) {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val locationResult = locationService.getCurrentLocation()
                if (locationResult == null) {
                    showLoading(false)
                    Toast.makeText(this@Home_Activity, "No se pudo obtener la ubicación", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val userId = auth.currentUser?.uid ?: return@launch
                val userName = auth.currentUser?.displayName ?: "Usuario"

                val alert = Alert(
                    userId = userId,
                    userName = userName,
                    type = category,
                    subtype = subtype,
                    location = locationResult.geoPoint,
                    address = locationResult.address,
                    createdAt = Timestamp.now(),
                    expiresAt = Timestamp(Timestamp.now().seconds + 1800, 0)
                )

                val success = repository.sendAlert(alert)
                showLoading(false)

                if (success) {
                    showSuccessDialog(subtype)
                } else {
                    Toast.makeText(this@Home_Activity, "Error al enviar la alerta", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@Home_Activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSuccessDialog(subtype: String) {
        AlertDialog.Builder(this)
            .setTitle("✅ ¡Alerta enviada!")
            .setMessage("Tu alerta de '$subtype' ha sido enviada exitosamente.\n\nTodos los miembros de tu comunidad han sido notificados.")
            .setPositiveButton("Entendido", null)
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSOS.isEnabled = !show
        bottomNav.isEnabled = !show
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showIncidentTypeDialog()
                } else {
                    Toast.makeText(this, "Se requiere ubicación para enviar alertas", Toast.LENGTH_LONG).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("FCM", "Permiso de notificaciones concedido")
                    setupFCMToken()
                } else {
                    Toast.makeText(this, "Las notificaciones están deshabilitadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}