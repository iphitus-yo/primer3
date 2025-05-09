package com.paranalog.truckcheck.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.utils.EmailSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ChecklistSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override suspend fun doWork(): Result {
        return try {
            // Verificar se há conexão com a internet
            val emailSender = EmailSender(applicationContext)
            if (!emailSender.isInternetDisponivel()) {
                return Result.retry()
            }

            // Buscar checklists não enviados
            val db = AppDatabase.getDatabase(applicationContext)
            val checklists = withContext(Dispatchers.IO) {
                db.checklistDao().getChecklistsNaoEnviados()
            }

            if (checklists.isEmpty()) {
                // Nada para sincronizar
                return Result.success()
            }

            // Enviar cada checklist
            var sucessos = 0
            for (checklist in checklists) {
                // Verificar se o PDF existe
                val pdfPath = checklist.pdfPath
                if (pdfPath != null) {
                    val pdfFile = File(pdfPath)
                    if (pdfFile.exists()) {
                        // Enviar e-mail
                        val destinatario = "juniorrafael@icloud.com" // E-mail fixo conforme especificação
                        val assunto = "Checklist de Inspeção - ${checklist.placaCavalo} - ${dateFormat.format(checklist.data)}"
                        val corpo = "Segue em anexo o checklist de inspeção do veículo ${checklist.placaCavalo} / ${checklist.placaCarreta} realizado em ${dateFormat.format(checklist.data)} às ${timeFormat.format(checklist.data)}."

                        val enviado = emailSender.enviarEmail(destinatario, assunto, corpo, pdfFile)
                        if (enviado) {
                            // Marcar como enviado no banco de dados
                            withContext(Dispatchers.IO) {
                                db.checklistDao().marcarComoEnviado(checklist.id, pdfPath)
                            }
                            sucessos++
                        }
                    }
                }
            }

            // Retornar sucesso se pelo menos um checklist foi enviado
            if (sucessos > 0) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}