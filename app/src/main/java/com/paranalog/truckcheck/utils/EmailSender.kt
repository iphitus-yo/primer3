package com.paranalog.truckcheck.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailSender(private val context: Context) {

    companion object {
        private const val TAG = "EmailSender"
        // Configurações de e-mail - em uma aplicação real, essas informações estariam
        // em um lugar mais seguro ou seriam obtidas de um serviço backend
        private const val EMAIL = "juniorrafael@icloud.com" // Seu email
        private const val PASSWORD = "clla-edit-okpw-pxbu" // Sua senha de app
        private const val HOST = "smtp.mail.me.com"
        private const val PORT = "587"
    }

    fun isInternetDisponivel(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    // Função para testar a conexão SMTP
    suspend fun testarConexaoSMTP(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando teste de conexão SMTP")
                val props = Properties().apply {
                    put("mail.smtp.host", HOST)
                    put("mail.smtp.port", PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.debug", "true") // Para debug
                }

                Log.d(TAG, "Criando sessão")
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(EMAIL, PASSWORD)
                    }
                })

                // Tentar obter transporte
                Log.d(TAG, "Tentando obter transporte")
                val transport = session.getTransport("smtp")
                Log.d(TAG, "Conectando ao servidor: $HOST")
                transport.connect(HOST, EMAIL, PASSWORD)
                Log.d(TAG, "Conexão estabelecida, fechando")
                transport.close()

                "Conexão bem-sucedida"
            } catch (e: Exception) {
                Log.e(TAG, "Falha na conexão: ${e.message}", e)
                "Falha na conexão: ${e.message}"
            }
        }
    }

    // Função original mantida para compatibilidade
    suspend fun enviarEmail(destinatario: String, assunto: String, corpo: String, anexo: File): Boolean {
        return enviarEmail(listOf(destinatario), assunto, corpo, anexo)
    }

    // Nova função que aceita lista de destinatários
    suspend fun enviarEmail(destinatarios: List<String>, assunto: String, corpo: String, anexo: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando envio de email para: ${destinatarios.joinToString()}")
                Log.d(TAG, "Assunto: $assunto")
                Log.d(TAG, "Anexo: ${anexo.absolutePath} (${anexo.length()} bytes)")

                val props = Properties().apply {
                    put("mail.smtp.host", HOST)
                    put("mail.smtp.port", PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.debug", "true") // Habilitar logs detalhados
                }

                Log.d(TAG, "Criando sessão de email")
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(EMAIL, PASSWORD)
                    }
                })

                Log.d(TAG, "Criando mensagem")
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(EMAIL))

                    // Adicionar todos os destinatários
                    for (destinatario in destinatarios) {
                        Log.d(TAG, "Adicionando destinatário: $destinatario")
                        addRecipient(Message.RecipientType.TO, InternetAddress(destinatario))
                    }

                    setSubject(assunto)
                }

                // Corpo do e-mail
                Log.d(TAG, "Criando parte do corpo da mensagem")
                val messageBodyPart = MimeBodyPart().apply {
                    setText(corpo)
                }

                // Verificar se o anexo existe
                if (!anexo.exists()) {
                    Log.e(TAG, "Arquivo de anexo não existe: ${anexo.absolutePath}")
                    return@withContext false
                }

                // Anexo
                Log.d(TAG, "Criando parte do anexo")
                val attachPart = MimeBodyPart().apply {
                    val source = FileDataSource(anexo)
                    dataHandler = DataHandler(source)
                    fileName = anexo.name
                }

                // Montar a mensagem com corpo e anexo
                Log.d(TAG, "Montando partes da mensagem")
                val multipart = MimeMultipart().apply {
                    addBodyPart(messageBodyPart)
                    addBodyPart(attachPart)
                }

                message.setContent(multipart)

                // Enviar a mensagem
                Log.d(TAG, "Enviando mensagem...")
                Transport.send(message)
                Log.d(TAG, "Mensagem enviada com sucesso!")

                true
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao enviar email: ${e.message}", e)
                e.printStackTrace()
                false
            }
        }
    }
}