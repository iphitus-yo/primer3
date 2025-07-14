package com.paranalog.truckcheck.utils

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import com.paranalog.truckcheck.R

class AppUpdater(private val context: Context) {
    private val TAG = "AppUpdater"

    // URL onde você hospedará um arquivo JSON com as informações de versão
    private val UPDATE_INFO_URL = "https://paranalog.com.br/wp-content/uploads/truckcheck_info.json"

    // URL onde a APK está hospedada
    private val APK_DOWNLOAD_URL = "https://paranalog.com.br/wp-content/uploads/truckcheck.apk"

    private var downloadID: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    // Referência para animação
    private var animatedDrawable: Any? = null

    // Shared Preferences para armazenar informações de instalação pendente
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("app_updater_prefs", Context.MODE_PRIVATE)
    }

    // Verifica se há atualização disponível
    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verificando atualizações em: $UPDATE_INFO_URL")
                val url = URL(UPDATE_INFO_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "TruckCheck App/${getAppVersion()}")
                connection.connect()

                val responseCode = connection.responseCode
                Log.d(TAG, "Código de resposta HTTP: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Resposta recebida: $response")

                    // Analisar JSON
                    val json = JSONObject(response)
                    val latestVersion = json.getString("version")
                    val updateMessage = json.getString("message")
                    val forceUpdate = json.getBoolean("forceUpdate")

                    // Obter versão atual
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentVersion = pInfo.versionName

                    Log.d(TAG, "Versão atual: $currentVersion, Versão mais recente: $latestVersion")

                    // Comparar versões usando comparação semântica
                    val versionComparison = compareVersions(currentVersion, latestVersion)

                    when {
                        versionComparison < 0 -> {
                            // Versão atual é menor que a versão do servidor - atualização disponível
                            Log.d(TAG, "Atualização disponível! Versão atual ($currentVersion) < Versão servidor ($latestVersion)")
                            return@withContext UpdateInfo(
                                currentVersion = currentVersion,
                                latestVersion = latestVersion,
                                updateAvailable = true,
                                updateMessage = updateMessage,
                                forceUpdate = forceUpdate
                            )
                        }
                        versionComparison > 0 -> {
                            // Versão atual é maior que a versão do servidor - não atualizar
                            Log.d(TAG, "Versão atual ($currentVersion) é mais recente que a do servidor ($latestVersion) - não atualizando")
                        }
                        else -> {
                            // Versões são iguais
                            Log.d(TAG, "Aplicativo já está na versão mais recente ($currentVersion)")
                        }
                    }
                } else {
                    Log.e(TAG, "Erro na resposta HTTP: $responseCode")
                }

                // Se não houver atualização ou ocorrer erro
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar atualizações: ${e.message}", e)
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    // Compara versões semânticas (ex: 1.0.10 vs 1.0.9)
    private fun compareVersions(currentVersion: String, latestVersion: String): Int {
        try {
            val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val latest = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(current.size, latest.size)

            for (i in 0 until maxLength) {
                val currentPart = current.getOrNull(i) ?: 0
                val latestPart = latest.getOrNull(i) ?: 0

                when {
                    currentPart < latestPart -> return -1 // Versão atual é menor
                    currentPart > latestPart -> return 1  // Versão atual é maior
                }
            }

            return 0 // Versões são iguais
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao comparar versões: ${e.message}")
            // Em caso de erro, assumir que são diferentes para permitir verificação manual
            return if (currentVersion != latestVersion) -1 else 0
        }
    }

    // Obter versão do app para User-Agent
    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "1.0"
        }
    }

    // Método para verificar se o arquivo APK é válido
    private fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 10000) { // APK válido deve ter pelo menos 10KB
            return false
        }

        try {
            // Tentar ler o arquivo como um ZIP (APKs são arquivos ZIP)
            val zipFile = java.util.zip.ZipFile(file)
            zipFile.close()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar APK: ${e.message}")
            return false
        }
    }

    // Verificação de conectividade
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    // Método para verificar se a Activity ainda está ativa
    private fun isActivityActive(): Boolean {
        return when (context) {
            is Activity -> !context.isFinishing && !context.isDestroyed
            else -> true // Para ApplicationContext sempre retorna true
        }
    }

    // Método seguro para mostrar diálogos
    private fun showDialogSafely(dialogBuilder: () -> AlertDialog.Builder) {
        try {
            if (isActivityActive()) {
                dialogBuilder().show()
            } else {
                Log.w(TAG, "Activity não está ativa, não é possível mostrar diálogo")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context.applicationContext, "Download concluído. Verifique as notificações.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mostrar diálogo: ${e.message}")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context.applicationContext, "Erro ao mostrar diálogo de instalação", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Método de download silencioso sem diálogos desnecessários
    fun downloadUpdateSilent(onComplete: (Boolean) -> Unit) {
        Thread {
            try {
                Log.d(TAG, "Iniciando download silencioso")

                if (!isNetworkAvailable()) {
                    Log.e(TAG, "Sem conexão com internet")
                    onComplete(false)
                    return@Thread
                }

                val outputFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "truckcheck.apk"
                )

                // Download manual direto sem diálogos
                downloadManualSilent(APK_DOWNLOAD_URL, outputFile) { success ->
                    if (success) {
                        Log.d(TAG, "Download silencioso concluído, iniciando instalação")
                        // Instalar automaticamente sem muitos diálogos
                        installAPKSilent(outputFile)
                        onComplete(true)
                    } else {
                        Log.e(TAG, "Falha no download silencioso")
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no download silencioso: ${e.message}", e)
                onComplete(false)
            }
        }.start()
    }

    // Download manual sem interface gráfica
    private fun downloadManualSilent(urlString: String, outputFile: File, onComplete: (Boolean) -> Unit) {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            Log.d(TAG, "Iniciando download de $urlString")

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "TruckCheck App/${getAppVersion()}")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Connection", "close")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Erro HTTP: ${connection.responseCode}")
                onComplete(false)
                return
            }

            input = BufferedInputStream(connection.inputStream)
            output = FileOutputStream(outputFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                output.write(data, 0, count)
            }

            output.flush()
            Log.d(TAG, "Download concluído: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            if (outputFile.exists() && outputFile.length() > 0 && isValidApkFile(outputFile)) {
                onComplete(true)
            } else {
                Log.e(TAG, "APK inválido após download")
                onComplete(false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download: ${e.message}", e)
            onComplete(false)
        } finally {
            try {
                output?.close()
                input?.close()
                connection?.disconnect()
            } catch (e: IOException) {
                Log.e(TAG, "Erro ao fechar conexões: ${e.message}")
            }
        }
    }

    // Instalação automática com mínimo de diálogos
    private fun installAPKSilent(file: File) {
        try {
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "Arquivo APK inválido")
                return
            }

            Log.d(TAG, "Iniciando instalação automática")

            // Instalar diretamente sem perguntar sobre desinstalar versão anterior
            val intent = Intent(Intent.ACTION_VIEW)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.d(TAG, "Intent de instalação iniciado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao instalar APK: ${e.message}", e)

            // Se falhar, mostrar apenas um toast
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context.applicationContext,
                    "Erro na instalação. Tente manualmente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Método alternativo de download direto (sem usar DownloadManager) - ATUALIZADO PARA SER MAIS SILENCIOSO
    fun downloadUpdateDirect() {
        downloadUpdateSilent { success ->
            if (!success) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context.applicationContext,
                        "Falha no download. Tente novamente mais tarde.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Verificar se há uma instalação pendente após reinstalação
    fun checkPendingInstallation() {
        val pendingApkPath = sharedPreferences.getString("pending_apk_path", null)
        if (pendingApkPath != null) {
            val file = File(pendingApkPath)
            if (file.exists()) {
                // Perguntar ao usuário se deseja completar a instalação
                showDialogSafely {
                    AlertDialog.Builder(context)
                        .setTitle("Concluir Atualização")
                        .setMessage("Detectamos uma atualização pendente. Deseja instalá-la agora?")
                        .setPositiveButton("Sim") { _, _ ->
                            proceedWithInstallation(file)
                            // Limpar o caminho pendente após a instalação
                            sharedPreferences.edit().remove("pending_apk_path").apply()
                        }
                        .setNegativeButton("Não") { _, _ ->
                            // Limpar o caminho pendente se o usuário recusar
                            sharedPreferences.edit().remove("pending_apk_path").apply()
                        }
                        .setCancelable(false)
                }
            } else {
                // Se o arquivo não existir mais, limpar a preferência
                sharedPreferences.edit().remove("pending_apk_path").apply()
            }
        }
    }

    // Função para continuar com a instalação - CORRIGIDA
    private fun proceedWithInstallation(file: File) {
        showDialogSafely {
            AlertDialog.Builder(context)
                .setTitle("Instalação da Atualização")
                .setMessage("Se aparecer uma mensagem sobre 'Fontes desconhecidas', toque em 'Configurações' e ative a opção 'Permitir desta fonte', depois volte e toque em 'Instalar'.")
                .setPositiveButton("Entendi") { _, _ ->
                    // Continuar com a instalação
                    Log.d(TAG, "Iniciando instalação do APK: ${file.absolutePath} (${file.length()} bytes)")
                    try {
                        // Verificar novamente se o arquivo existe e é válido
                        if (!file.exists() || file.length() == 0L) {
                            Log.e(TAG, "Arquivo não encontrado ou inválido antes da instalação")
                            Toast.makeText(context.applicationContext, "Arquivo de instalação não encontrado", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }

                        // Verificar se é um APK válido
                        if (!isValidApkFile(file)) {
                            Log.e(TAG, "Arquivo não é um APK válido antes da instalação")
                            Toast.makeText(context.applicationContext, "Arquivo de instalação inválido", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }

                        val intent = Intent(Intent.ACTION_VIEW)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Para Android 7.0 e superior, usar FileProvider
                            val providerAuth = "${context.packageName}.fileprovider"
                            Log.d(TAG, "Autoridade do FileProvider: $providerAuth")

                            try {
                                val apkUri = FileProvider.getUriForFile(
                                    context,
                                    providerAuth,
                                    file
                                )
                                Log.d(TAG, "Usando FileProvider, URI: $apkUri")
                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao criar URI com FileProvider: ${e.message}", e)
                                Toast.makeText(context.applicationContext, "Erro ao preparar instalação: ${e.message}", Toast.LENGTH_LONG).show()
                                return@setPositiveButton
                            }
                        } else {
                            // Para versões mais antigas do Android
                            Log.d(TAG, "Android anterior a 7.0, usando URI direto")
                            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                        }

                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                            Log.d(TAG, "Intent para instalação iniciado com sucesso")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao iniciar a intent de instalação: ${e.message}", e)
                            Toast.makeText(context.applicationContext, "Erro ao iniciar instalação: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro geral ao instalar: ${e.message}", e)
                        Toast.makeText(context.applicationContext, "Erro na instalação: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setCancelable(false)
        }
    }

    // Método para usar com o botão de download manual
    fun initiateDirectDownload() {
        downloadUpdateDirect()
    }

    // Classe para armazenar informações de atualização
    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val updateAvailable: Boolean,
        val updateMessage: String,
        val forceUpdate: Boolean
    )
}