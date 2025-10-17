package com.example.proyecto_seguridadciudadana

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class Login_Activity : AppCompatActivity() {

    // Firebase
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // Facebook
    private lateinit var callbackManager: CallbackManager

    // Google
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Resultado del intent de Google
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { signInTask ->
                if (signInTask.isSuccessful) {
                    Toast.makeText(this, "Login con Google correcto", Toast.LENGTH_SHORT).show()
                    goToHome()
                } else {
                    val msg = signInTask.exception?.localizedMessage ?: "No se pudo iniciar con Google"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Error de Google: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Vistas
    private lateinit var root: ConstraintLayout
    private lateinit var edtCorreo: EditText
    private lateinit var btnContinuar: Button
    private lateinit var btnGoogle: LinearLayout
    private lateinit var btnFacebook: LinearLayout

    // Campo de contraseña que se creará dinámicamente al pulsar "Continuar"
    private var edtPassword: EditText? = null
    private var pidiendoPassword: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Inset handling (igual que tenías)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Referencias a vistas del XML existente
        root = findViewById(R.id.main)
        edtCorreo = findViewById(R.id.edtCorreo)
        btnContinuar = findViewById(R.id.btnContinuarRegistro)
        btnGoogle = findViewById(R.id.btnGoogle)
        btnFacebook = findViewById(R.id.btnFacebook)

        // --- FACEBOOK ---
        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                auth.signInWithCredential(credential).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this@Login_Activity, "Login con Facebook correcto", Toast.LENGTH_SHORT).show()
                        goToHome()
                    } else {
                        val msg = task.exception?.localizedMessage ?: "No se pudo iniciar con Facebook"
                        Toast.makeText(this@Login_Activity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            override fun onCancel() {
                Toast.makeText(this@Login_Activity, "Inicio de sesión cancelado", Toast.LENGTH_SHORT).show()
            }
            override fun onError(error: FacebookException) {
                Toast.makeText(this@Login_Activity, "Error: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })

        btnFacebook.setOnClickListener {
            // Permisos básicos
            LoginManager.getInstance().logInWithReadPermissions(
                this@Login_Activity,
                listOf("email", "public_profile")
            )
        }

        // --- GOOGLE ---
        btnGoogle.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                // default_web_client_id viene de google-services.json (ya configurado)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            val googleClient = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(googleClient.signInIntent)
        }

        // --- EMAIL + PASSWORD ---
        btnContinuar.setOnClickListener {
            if (!pidiendoPassword) {
                // Primera pulsación: validar correo y mostrar campo de contraseña debajo
                val email = edtCorreo.text?.toString()?.trim().orEmpty()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    edtCorreo.error = "Correo inválido"
                    edtCorreo.requestFocus()
                    return@setOnClickListener
                }
                agregarCampoPasswordDebajoDeCorreo()
                btnContinuar.text = "Entrar"
                pidiendoPassword = true
            } else {
                // Segunda pulsación: intentar login con correo/contraseña
                val email = edtCorreo.text?.toString()?.trim().orEmpty()
                val pass = edtPassword?.text?.toString().orEmpty()

                if (pass.length < 6) {
                    edtPassword?.error = "Mínimo 6 caracteres"
                    edtPassword?.requestFocus()
                    return@setOnClickListener
                }

                setHabilitado(false)
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    setHabilitado(true)
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Inicio de sesión correcto", Toast.LENGTH_SHORT).show()
                        goToHome()
                    } else {
                        val msg = task.exception?.localizedMessage ?: "No se pudo iniciar sesión"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Si más adelante agregas un botón/Texto "Registrarse", aquí harías:
        // findViewById<View>(R.id.btnIrRegistro).setOnClickListener {
        //     startActivity(Intent(this, Register_Activity::class.java))
        // }
    }

    /**
     * Crea dinámicamente un EditText de contraseña debajo de edtCorreo y mueve el botón "Continuar"
     * para que quede debajo de la nueva contraseña, conservando tu diseño ConstraintLayout.
     */
    private fun agregarCampoPasswordDebajoDeCorreo() {
        if (edtPassword != null) return // ya existe

        val passId = View.generateViewId()
        val nuevo = EditText(this).apply {
            id = passId
            hint = "Ingresa tu contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setEms(10)
        }

        // Añadimos al ConstraintLayout
        root.addView(nuevo)

        // Ancho match-constraint (0dp) con márgenes y constraints
        val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
            startToStart = ConstraintSet.PARENT_ID
            endToEnd = ConstraintSet.PARENT_ID
            topToBottom = edtCorreo.id
            // mismos márgenes que edtCorreo para que se vea alineado
            marginStart = dp(16)
            marginEnd = dp(16)
            topMargin = dp(12)
        }
        nuevo.layoutParams = lp

        // Ahora movemos el botón "Continuar" para que quede debajo del nuevo campo
        (btnContinuar.layoutParams as? LayoutParams)?.let { params ->
            params.topToBottom = passId
            params.topMargin = dp(20)
            btnContinuar.layoutParams = params
        }

        edtPassword = nuevo
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun goToHome() {
        startActivity(Intent(this, Home_Activity::class.java))
        finish()
    }

    private fun setHabilitado(enabled: Boolean) {
        btnContinuar.isEnabled = enabled
        btnGoogle.isEnabled = enabled
        btnFacebook.isEnabled = enabled
        edtCorreo.isEnabled = enabled
        edtPassword?.isEnabled = enabled
    }

    // Requerido por el SDK de Facebook para recibir su resultado
    @Deprecated("Facebook SDK aún usa este callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::callbackManager.isInitialized) {
            callbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }
}
