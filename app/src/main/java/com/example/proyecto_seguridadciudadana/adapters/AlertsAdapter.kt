package com.example.proyecto_seguridadciudadana.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.proyecto_seguridadciudadana.R
import com.example.proyecto_seguridadciudadana.models.Alert
import com.google.firebase.Timestamp

class AlertsAdapter(
    private val alerts: List<Alert>,
    private val currentUserId: String,
    private val onValidateClick: (Alert, Boolean) -> Unit
) : RecyclerView.Adapter<AlertsAdapter.AlertViewHolder>() {

    inner class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtEmoji: TextView = view.findViewById(R.id.txtAlertEmoji)
        val txtSubtype: TextView = view.findViewById(R.id.txtAlertSubtype)
        val txtUser: TextView = view.findViewById(R.id.txtAlertUser)
        val txtTime: TextView = view.findViewById(R.id.txtAlertTime)
        val txtAddress: TextView = view.findViewById(R.id.txtAlertAddress)
        val txtValidationScore: TextView = view.findViewById(R.id.txtValidationScore)
        val txtValidationStatus: TextView = view.findViewById(R.id.txtValidationStatus)
        val layoutButtons: LinearLayout = view.findViewById(R.id.layoutValidationButtons)
        val btnValidateTrue: Button = view.findViewById(R.id.btnValidateTrue)
        val btnValidateFalse: Button = view.findViewById(R.id.btnValidateFalse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val alert = alerts[position]

        // Emoji según tipo
        holder.txtEmoji.text = when (alert.type) {
            "HOME_ACCIDENT" -> "🏠"
            "STREET_INCIDENT" -> "🚗"
            "CRIMINAL_ACTIVITY" -> "⚠️"
            else -> "🚨"
        }

        // Información básica
        holder.txtSubtype.text = alert.subtype
        holder.txtUser.text = "Reportado por: ${alert.userName}"
        holder.txtAddress.text = "📍 ${alert.address}"

        // Tiempo transcurrido
        val now = Timestamp.now()
        val minutesAgo = ((now.seconds - alert.createdAt.seconds) / 60).toInt()
        holder.txtTime.text = when {
            minutesAgo < 1 -> "Ahora"
            minutesAgo == 1 -> "1 min"
            minutesAgo < 60 -> "$minutesAgo min"
            else -> "${minutesAgo / 60}h"
        }

        // Score de validación
        val trueVotes = alert.validationScore.trueVotes
        val falseVotes = alert.validationScore.falseVotes
        holder.txtValidationScore.text = "✅ $trueVotes  |  ❌ $falseVotes"

        // Estado de validación
        val totalVotes = trueVotes + falseVotes
        val validationStatus = when {
            totalVotes == 0 -> {
                holder.txtValidationStatus.text = "PENDIENTE"
                holder.txtValidationStatus.setBackgroundResource(R.drawable.badge_pending_background)
                "pending"
            }
            trueVotes > falseVotes * 2 -> {
                holder.txtValidationStatus.text = "VERIFICADA ✓"
                holder.txtValidationStatus.setBackgroundColor(0xFF4CAF50.toInt())
                "verified"
            }
            falseVotes > trueVotes * 2 -> {
                holder.txtValidationStatus.text = "FALSA ✗"
                holder.txtValidationStatus.setBackgroundColor(0xFFF44336.toInt())
                "false"
            }
            else -> {
                holder.txtValidationStatus.text = "EN REVISIÓN"
                holder.txtValidationStatus.setBackgroundResource(R.drawable.badge_pending_background)
                "under_review"
            }
        }

        // Ocultar botones si es el creador de la alerta
        if (alert.userId == currentUserId) {
            holder.layoutButtons.visibility = View.GONE
        } else {
            holder.layoutButtons.visibility = View.VISIBLE

            holder.btnValidateTrue.setOnClickListener {
                onValidateClick(alert, true)
            }

            holder.btnValidateFalse.setOnClickListener {
                onValidateClick(alert, false)
            }
        }
    }

    override fun getItemCount(): Int = alerts.size
}