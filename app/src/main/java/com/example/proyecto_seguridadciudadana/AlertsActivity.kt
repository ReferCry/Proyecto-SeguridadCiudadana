package com.example.proyecto_seguridadciudadana

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.proyecto_seguridadciudadana.adapters.AlertsAdapter
import com.example.proyecto_seguridadciudadana.models.Alert
import com.example.proyecto_seguridadciudadana.repository.FirebaseRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AlertsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnBack: FloatingActionButton
    private lateinit var adapter: AlertsAdapter

    private val repository = FirebaseRepository()
    private val auth = FirebaseAuth.getInstance()

    private val alertsList = mutableListOf<Alert>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        // Inicializar vistas
        recyclerView = findViewById(R.id.recyclerViewAlerts)
        progressBar = findViewById(R.id.progressBarAlerts)
        btnBack = findViewById(R.id.btnBackFromAlerts)

        // Configurar RecyclerView
        setupRecyclerView()

        // Botón volver
        btnBack.setOnClickListener { finish() }

        // Cargar alertas
        loadAlerts()
    }

    private fun setupRecyclerView() {
        adapter = AlertsAdapter(
            alerts = alertsList,
            currentUserId = auth.currentUser?.uid ?: "",
            onValidateClick = { alert, isTrue ->
                validateAlert(alert, isTrue)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AlertsActivity)
            adapter = this@AlertsActivity.adapter
        }
    }

    private fun loadAlerts() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // Obtener alertas activas
                val alerts = repository.getActiveAlerts("general")

                // Filtrar alertas no expiradas
                val now = Timestamp.now()
                val activeAlerts = alerts.filter { alert ->
                    alert.expiresAt.seconds > now.seconds && alert.status == "active"
                }

                // Ordenar por más recientes primero
                val sortedAlerts = activeAlerts.sortedByDescending { it.createdAt.seconds }

                // Actualizar lista
                alertsList.clear()
                alertsList.addAll(sortedAlerts)
                adapter.notifyDataSetChanged()

                showLoading(false)

                if (sortedAlerts.isEmpty()) {
                    Toast.makeText(
                        this@AlertsActivity,
                        "No hay alertas activas",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                showLoading(false)
                Log.e("AlertsActivity", "Error al cargar alertas: ${e.message}")
                Toast.makeText(
                    this@AlertsActivity,
                    "Error al cargar alertas",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun validateAlert(alert: Alert, isTrue: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Verificar si es el creador de la alerta
        if (alert.userId == currentUserId) {
            Toast.makeText(
                this,
                "No puedes validar tu propia alerta",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Confirmar validación
        val message = if (isTrue) {
            "¿Confirmas que esta alerta es VERDADERA?"
        } else {
            "¿Confirmas que esta alerta es FALSA?"
        }

        AlertDialog.Builder(this)
            .setTitle("Validar Alerta")
            .setMessage(message)
            .setPositiveButton("Sí") { _, _ ->
                submitValidation(alert, isTrue)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun submitValidation(alert: Alert, isTrue: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            try {
                // Verificar si ya validó esta alerta
                val hasValidated = repository.hasUserValidated(alert.id, currentUserId)

                if (hasValidated) {
                    Toast.makeText(
                        this@AlertsActivity,
                        "Ya validaste esta alerta anteriormente",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Registrar validación
                val success = repository.validateAlert(alert.id, currentUserId, isTrue)

                if (success) {
                    Toast.makeText(
                        this@AlertsActivity,
                        "Validación registrada correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Recargar alertas para ver cambios
                    loadAlerts()
                } else {
                    Toast.makeText(
                        this@AlertsActivity,
                        "Error al validar la alerta",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("AlertsActivity", "Error al validar: ${e.message}")
                Toast.makeText(
                    this@AlertsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}