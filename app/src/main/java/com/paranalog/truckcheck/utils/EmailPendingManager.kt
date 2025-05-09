package com.paranalog.truckcheck.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.models.Checklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class EmailPendingManager(private val context: Context) {
    private val TAG = "EmailPendingManager"
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    // Função para verificar e enviar checklists pendentes
    fun verificarEnviosPendentes(lifecycleOwner: LifecycleOwner) {
        val sharedPrefsManager = SharedPrefsManager(context)

        // Verificar se o envio automático está habilitado
        if (!sharedPrefsManager.getEnvioAutomaticoEmails()) {
            Log.d(TAG, "Envio automático de emails está desabilitado")
            return
        }

        lifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val emailSender = EmailSender(context)

                // Verificar conectividade
                if (!emailSender.isInternetDisponivel()) {
                    Log.d(TAG, "Sem conexão com a internet. Envios pendentes serão verificados mais tarde.")
                    return@launch
                }

                // Buscar checklists com PDF gerado mas email não enviado (status amarelo)
                val checklistsPendentes = withContext(Dispatchers.IO) {
                    db.checklistDao().getChecklistsComPdfSemEmail()
                }

                Log.d(TAG, "Encontrados ${checklistsPendentes.size} checklists pendentes de envio")

                // Processar cada checklist pendente
                checklistsPendentes.forEach { checklist ->
                    processarEnvioPendente(checklist, lifecycleOwner)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar envios pendentes: ${e.message}", e)
            }
        }
    }

    private fun processarEnvioPendente(checklist: Checklist, lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val emailSender = EmailSender(context)

                // Verificar se o arquivo PDF existe
                val pdfFile = checklist.pdfPath?.let { File(it) }
                if (pdfFile == null || !pdfFile.exists() || pdfFile.length() == 0L) {
                    Log.e(TAG, "PDF não encontrado ou inválido: ${checklist.pdfPath}")
                    return@launch
                }

                // Criar assunto e corpo do email
                val crtNum = checklist.crtMicDue ?: "S/N"
                val assunto = "CRT No. $crtNum | Motorista: ${checklist.motoristaName}"

                val corpo = "Segue em anexo o checklist de inspeção do veículo " +
                        "${checklist.placaCavalo} / ${checklist.placaCarreta} " +
                        "realizado em ${dateFormat.format(checklist.data)} às ${timeFormat.format(checklist.data)}."

                // Destinatários
                val destinatarios = listOf("juniorrafael@me.com")

                // Tentar enviar o email
                val enviado = emailSender.enviarEmail(destinatarios.first(), assunto, corpo, pdfFile)

                // Atualizar status no banco se enviado com sucesso
                if (enviado) {
                    withContext(Dispatchers.IO) {
                        val checklistAtualizado = checklist.copy(emailEnviado = true)
                        db.checklistDao().updateChecklist(checklistAtualizado)
                        Log.d(TAG, "Email enviado para checklist #${checklist.id} e status atualizado para VERDE")
                    }
                } else {
                    Log.e(TAG, "Falha ao enviar email para checklist #${checklist.id}. Será tentado novamente mais tarde.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao processar envio pendente: ${e.message}", e)
            }
        }
    }
}