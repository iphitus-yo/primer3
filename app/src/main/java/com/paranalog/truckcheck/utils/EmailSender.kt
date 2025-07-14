package com.paranalog.truckcheck.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import kotlin.concurrent.thread

class EmailSender(private val context: Context) {
    private val TAG = "EmailSender"

    // Dados de configuração do email Zoho
    private val smtpHost = "smtppro.zoho.com"
    private val smtpPort = "465"  // Porta SSL
    private val fromEmail = "checklist@paranalog.com.br"
    private val password = "@LNTu3K*Rekf"

    // Verifica se há conexão com a internet
    fun isInternetDisponivel(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    // Envia um email com anexo - versão com múltiplas tentativas
    @WorkerThread
    fun enviarEmail(para: String, assunto: String, corpo: String, arquivo: File): Boolean {
        Log.d(TAG, "Iniciando envio de email para $para com assunto: $assunto")

        // Verificar se o arquivo existe
        if (!arquivo.exists()) {
            Log.e(TAG, "Arquivo não encontrado: ${arquivo.absolutePath}")
            return false
        }

        // Verificar tamanho do arquivo
        val tamanhoArquivo = arquivo.length()
        Log.d(TAG, "Tamanho do arquivo: ${tamanhoArquivo / 1024} KB")

        // Verificar conexão com a internet
        if (!isInternetDisponivel()) {
            Log.e(TAG, "Sem conexão com a internet")
            return false
        }

        // Número máximo de tentativas
        val maxTentativas = 3

        // Variável para controlar se o email foi enviado
        var enviado = false

        // Tentativas
        for (tentativa in 1..maxTentativas) {
            if (enviado) break

            try {
                Log.d(TAG, "Tentativa $tentativa de $maxTentativas - Configurando propriedades...")
                val props = Properties()
                props.put("mail.smtp.host", smtpHost)
                props.put("mail.smtp.port", smtpPort)
                props.put("mail.smtp.auth", "true")
                props.put("mail.smtp.socketFactory.port", smtpPort)
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                props.put("mail.smtp.socketFactory.fallback", "false")
                props.put("mail.smtp.connectiontimeout", "30000")  // Aumentado para 30 segundos
                props.put("mail.smtp.timeout", "30000")            // Aumentado para 30 segundos
                props.put("mail.smtp.writetimeout", "30000")       // Aumentado para 30 segundos
                props.put("mail.debug", "true")  // Para depuração

                Log.d(TAG, "Criando sessão de email...")
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(fromEmail, password)
                    }
                })

                Log.d(TAG, "Criando mensagem de email...")
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(fromEmail))
                message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(para)
                )
                message.subject = assunto

                Log.d(TAG, "Criando corpo da mensagem e anexo...")
                val messageBodyPart = MimeBodyPart()
                messageBodyPart.setText(corpo)

                val attachmentPart = MimeBodyPart()
                attachmentPart.attachFile(arquivo)

                Log.d(TAG, "Montando multipart...")
                val multipart = MimeMultipart()
                multipart.addBodyPart(messageBodyPart)
                multipart.addBodyPart(attachmentPart)
                message.setContent(multipart)

                // Usar uma thread separada com timeout para o envio
                val envioThread = thread {
                    try {
                        Log.d(TAG, "Tentativa $tentativa - Enviando email...")
                        Transport.send(message)
                        enviado = true
                        Log.d(TAG, "Email enviado com sucesso na tentativa $tentativa!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao enviar email na tentativa $tentativa: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Aguardar conclusão da thread com timeout
                try {
                    // Esperar até 25 segundos pelo envio
                    envioThread.join(25000)

                    // Se a thread ainda estiver viva após o timeout, ela será encerrada na próxima tentativa
                    if (envioThread.isAlive) {
                        Log.w(TAG, "Timeout na tentativa $tentativa de envio de email")
                    }
                } catch (e: InterruptedException) {
                    Log.e(TAG, "Thread de envio interrompida: ${e.message}")
                }

                // Se o email foi enviado, não são necessárias mais tentativas
                if (enviado) {
                    Log.d(TAG, "Email enviado com sucesso após $tentativa tentativas")
                    break
                } else if (tentativa < maxTentativas) {
                    // Esperar antes da próxima tentativa - aumentando o tempo a cada tentativa
                    val tempoEspera = tentativa * 2000L // 2s, 4s, 6s...
                    Log.d(TAG, "Aguardando ${tempoEspera}ms antes da próxima tentativa")
                    Thread.sleep(tempoEspera)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro geral na tentativa $tentativa: ${e.message}")
                e.printStackTrace()

                // Esperar antes da próxima tentativa
                if (tentativa < maxTentativas) {
                    val tempoEspera = tentativa * 2000L
                    Thread.sleep(tempoEspera)
                }
            }
        }

        return enviado
    }
}