package com.paranalog.truckcheck.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.utils.AppUpdater
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private val SPLASH_DURATION = 800L
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var appUpdater: AppUpdater
    private val TAG = "SplashActivity"
    private var updateInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        sharedPrefsManager = SharedPrefsManager(this)
        appUpdater = AppUpdater(this)

        // Configurar animações de entrada
        setupAnimations()

        // Exibir a versão atual do aplicativo
        displayAppVersion()

        // Inicializar status de carregamento
        updateLoadingStatus("INICIALIZANDO...")

        // Verificar se há instalação pendente primeiro
        appUpdater.checkPendingInstallation()

        // Verificar atualizações antes de continuar para a próxima tela
        checkForUpdates()
    }

    private fun setupAnimations() {
        try {
            // Animar entrada do container do logo
            val logoContainer = findViewById<LinearLayout>(R.id.logoContainer)
            logoContainer.alpha = 0f
            logoContainer.translationY = 60f

            logoContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(1200)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Animar logo com escala
            val logoCard = findViewById<CardView>(R.id.logoCard)
            logoCard.scaleX = 0.7f
            logoCard.scaleY = 0.7f

            logoCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setStartDelay(300)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Animar texto principal
            val titleText = findViewById<TextView>(R.id.titleText)
            titleText.alpha = 0f
            titleText.translationY = 30f

            titleText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(600)
                .setInterpolator(DecelerateInterpolator())
                .start()

            // Animar slogan
            val sloganText = findViewById<TextView>(R.id.sloganText)
            sloganText.alpha = 0f
            sloganText.translationY = 20f

            sloganText.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(800)
                .setInterpolator(DecelerateInterpolator())
                .start()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao configurar animações: ${e.message}", e)
        }
    }

    private fun displayAppVersion() {
        try {
            val versionTextView = findViewById<TextView>(R.id.versionTextView)
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            versionTextView.text = "v$version"

            // Animar versão
            versionTextView.alpha = 0f
            versionTextView.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(1000)
                .start()

            Log.d(TAG, "Versão do app exibida: $version")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exibir versão: ${e.message}", e)
        }
    }

    private fun updateLoadingStatus(status: String) {
        try {
            val statusText = findViewById<TextView>(R.id.statusText)
            statusText.text = status

            // Animação de pulso no texto de status
            statusText.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(200)
                .withEndAction {
                    statusText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar status: ${e.message}", e)
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                updateLoadingStatus("VERIFICANDO ATUALIZAÇÕES...")
                Log.d(TAG, "Verificando atualizações...")
                val updateInfo = appUpdater.checkForUpdates()

                if (updateInfo != null && updateInfo.updateAvailable) {
                    Log.d(TAG, "Atualização disponível: ${updateInfo.latestVersion}")
                    updateLoadingStatus("ATUALIZAÇÃO DISPONÍVEL")
                    showUpdateDialog(updateInfo)
                } else {
                    Log.d(TAG, "Nenhuma atualização disponível")
                    updateLoadingStatus("CARREGANDO APLICATIVO...")
                    continueSplash()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar atualizações: ${e.message}", e)
                updateLoadingStatus("CARREGANDO APLICATIVO...")
                continueSplash()
            }
        }
    }

    private fun showUpdateDialog(updateInfo: AppUpdater.UpdateInfo) {
        if (isFinishing || isDestroyed) return

        val builder = AlertDialog.Builder(this)
            .setTitle("Atualização Disponível")
            .setMessage("Nova versão ${updateInfo.latestVersion} disponível.\n\n${updateInfo.updateMessage}\n\nO processo será rápido e automático.")
            .setPositiveButton("Atualizar Agora") { dialog, _ ->
                Log.d(TAG, "Iniciando processo de atualização")
                dialog.dismiss()
                updateInProgress = true
                startUpdateProcess()
            }

        if (!updateInfo.forceUpdate) {
            builder.setNegativeButton("Depois") { dialog, _ ->
                Log.d(TAG, "Usuário adiou atualização")
                dialog.dismiss()
                updateLoadingStatus("CARREGANDO APLICATIVO...")
                continueSplash()
            }
        } else {
            builder.setCancelable(false)
        }

        try {
            builder.show()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mostrar diálogo: ${e.message}", e)
            continueSplash()
        }
    }

    private fun startUpdateProcess() {
        // Atualizar status e versão para mostrar que está baixando
        updateLoadingStatus("BAIXANDO ATUALIZAÇÃO...")

        try {
            val versionTextView = findViewById<TextView>(R.id.versionTextView)
            versionTextView.text = "Baixando..."

            // Manter barra de progresso ativa
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.isIndeterminate = true

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar interface: ${e.message}")
        }

        // Iniciar download direto e silencioso
        appUpdater.downloadUpdateSilent { success ->
            runOnUiThread {
                if (success) {
                    Log.d(TAG, "Download concluído com sucesso")
                    updateLoadingStatus("INSTALANDO ATUALIZAÇÃO...")

                    try {
                        val versionTextView = findViewById<TextView>(R.id.versionTextView)
                        versionTextView.text = "Instalando..."
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao atualizar texto: ${e.message}")
                    }
                    // A instalação será iniciada automaticamente pelo AppUpdater
                } else {
                    Log.e(TAG, "Falha no download")
                    showErrorAndContinue()
                }
            }
        }
    }

    private fun showErrorAndContinue() {
        if (isFinishing || isDestroyed) return

        // Restaurar estado original
        updateLoadingStatus("ERRO NA ATUALIZAÇÃO")

        try {
            val versionTextView = findViewById<TextView>(R.id.versionTextView)
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionTextView.text = "v${pInfo.versionName}"

            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.isIndeterminate = true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao restaurar interface: ${e.message}")
        }

        AlertDialog.Builder(this)
            .setTitle("Erro na Atualização")
            .setMessage("Não foi possível atualizar o aplicativo. Você pode continuar usando a versão atual.")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                updateInProgress = false
                updateLoadingStatus("CARREGANDO APLICATIVO...")
                continueSplash()
            }
            .setCancelable(false)
            .show()
    }

    private fun continueSplash() {
        if (!updateInProgress && !isFinishing && !isDestroyed) {
            Handler(Looper.getMainLooper()).postDelayed({
                navegarParaProximaTela()
            }, SPLASH_DURATION)
        }
    }

    private fun navegarParaProximaTela() {
        if (isFinishing || isDestroyed) return

        try {
            val destino = if (sharedPrefsManager.isLoggedIn()) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }

            Log.d(TAG, "Navegando para: ${destino.simpleName}")
            startActivity(Intent(this, destino))
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao navegar: ${e.message}", e)
            try {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } catch (ex: Exception) {
                Log.e(TAG, "Erro crítico: ${ex.message}", ex)
            }
        }
    }

    override fun onBackPressed() {
        if (updateInProgress) {
            // Durante atualização, não permitir voltar
            Log.d(TAG, "Atualização em progresso, bloqueando botão voltar")
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
        Log.d(TAG, "SplashActivity finalizada")
    }
}