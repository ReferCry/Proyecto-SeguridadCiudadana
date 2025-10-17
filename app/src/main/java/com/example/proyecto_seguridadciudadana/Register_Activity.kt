package com.example.proyecto_seguridadciudadana

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class Register_Activity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var edtRegEmail: EditText
    private lateinit var edtRegPass: EditText
    private lateinit var edtRegPass2: EditText
    private lateinit var btnCrearCuenta: Button
    private lateinit var btnVolverLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        edtRegEmail    = findViewById(R.id.edtRegEmail)
        edtRegPass     = findViewById(R.id.edtRegPass)
        edtRegPass2    = findViewById(R.id.edtRegPass2)
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta)
        btnVolverLogin = findViewById(R.id.btnVolverLogin)

        btnCrearCuenta.setOnClickListener { createAccount() }
        btnVolverLogin.setOnClickListener { finish() } // vuelve a Login_Activity
    }

    private fun createAccount() {
        val email = edtRegEmail.text?.toString()?.trim().orEmpty()
        val pass  = edtRegPass.text?.toString().orEmpty()
        val pass2 = edtRegPass2.text?.toString().orEmpty()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtRegEmail.error = "Correo inválido"; edtRegEmail.requestFocus(); return
        }
        if (pass.length < 6) {
            edtRegPass.error = "Mínimo 6 caracteres"; edtRegPass.requestFocus(); return
        }
        if (pass != pass2) {
            edtRegPass2.error = "Las contraseñas no coinciden"; edtRegPass2.requestFocus(); return
        }

        btnCrearCuenta.isEnabled = false

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                btnCrearCuenta.isEnabled = true
                if (task.isSuccessful) {
                    Toast.makeText(this, "Cuenta creada. ¡Bienvenido!", Toast.LENGTH_SHORT).show()
                    // La sesión queda iniciada. Vamos a Home.
                    startActivity(android.content.Intent(this, Home_Activity::class.java))
                    finish()
                } else {
                    val msg = task.exception?.localizedMessage ?: "No se pudo crear la cuenta"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
    }
}
