package com.paranalog.truckcheck.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.utils.SharedPrefsManager
import com.paranalog.truckcheck.activities.MainActivity


class SplashActivity : AppCompatActivity() {

    private val SPLASH_DURATION = 1000L // 2 segundos
    private lateinit var sharedPrefsManager: SharedPrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sharedPrefsManager = SharedPrefsManager(this)

        // Configurar a navegação após o tempo do splash
        Handler(Looper.getMainLooper()).postDelayed({
            navegarParaProximaTela()
        }, SPLASH_DURATION)
    }

    private fun navegarParaProximaTela() {
        // Verificar se o usuário está logado
        val destino = if (sharedPrefsManager.isLoggedIn()) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }

        // Iniciar a atividade de destino
        startActivity(Intent(this, destino))
        finish() // Encerrar a SplashActivity
    }
}