package com.paranalog.truckcheck.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.models.Checklist
import kotlinx.coroutines.*
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
                // Limpar arquivos temporários antigos antes de começar
                limparArquivosTemporariosAntigos()

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

                // Processar em lotes de 3 por vez para não sobrecarregar
                checklistsPendentes.chunked(3).forEach { lote ->
                    supervisorScope {
                        lote.map { checklist ->
                            async {
                                processarEnvioPendente(checklist)
                            }
                        }.awaitAll()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar envios pendentes: ${e.message}", e)
            }
        }
    }

    private suspend fun processarEnvioPendente(checklist: Checklist) {
        try {
            // Timeout de 30 segundos por email
            withTimeoutOrNull(30000) {
                val db = AppDatabase.getDatabase(context)
                val emailSender = EmailSender(context)

                // Verificar se o arquivo PDF existe
                val pdfOriginal = checklist.pdfPath?.let { File(it) }
                if (pdfOriginal == null || !pdfOriginal.exists() || pdfOriginal.length() == 0L) {
                    Log.e(TAG, "PDF não encontrado ou inválido para checklist #${checklist.id}: ${checklist.pdfPath}")
                    return@withTimeoutOrNull
                }

                // Preparar arquivo para envio
                val arquivoParaEnviar = prepararArquivoParaEnvio(checklist, pdfOriginal)

                // Formatar o assunto do email
                val crtNum = checklist.crtMicDue ?: "S/N"
                val assunto = "CRT No. $crtNum | Motorista: ${checklist.motoristaName}"

                // Preparar corpo do email
                val corpo = prepararCorpoEmail(checklist)

                // Destinatários
                val destinatarios = listOf("checklist@paranalog.com.br")

                Log.d(TAG, "Enviando email para checklist #${checklist.id} com assunto: $assunto")

                // Tentar enviar o email
                val enviado = withContext(Dispatchers.IO) {
                    emailSender.enviarEmail(destinatarios.first(), assunto, corpo, arquivoParaEnviar)
                }

                // Remover arquivo temporário se não for o original
                if (arquivoParaEnviar != pdfOriginal) {
                    try {
                        arquivoParaEnviar.delete()
                        Log.d(TAG, "Arquivo temporário excluído: ${arquivoParaEnviar.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao excluir arquivo temporário: ${e.message}")
                    }
                }

                // Atualizar status no banco se enviado com sucesso
                if (enviado) {
                    withContext(Dispatchers.IO) {
                        // Usar método otimizado do DAO
                        db.checklistDao().marcarEmailComoEnviado(checklist.id)
                        Log.d(TAG, "Email enviado para checklist #${checklist.id} e status atualizado para VERDE")
                    }
                } else {
                    Log.e(TAG, "Falha ao enviar email para checklist #${checklist.id}. Será tentado novamente mais tarde.")
                }
            } ?: run {
                Log.w(TAG, "Timeout ao processar checklist #${checklist.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar checklist #${checklist.id}: ${e.message}", e)
        }
    }

    private fun prepararArquivoParaEnvio(checklist: Checklist, pdfOriginal: File): File {
        return try {
            // Criar novo nome para o arquivo com melhores informações
            val dataFormatada = SimpleDateFormat("yyyyMMdd", Locale("pt", "BR"))
                .format(checklist.data)

            // Parte do CRT para o nome do arquivo
            val crtParte = if (!checklist.crtMicDue.isNullOrBlank()) {
                "CRT${checklist.crtMicDue}_"
            } else {
                "SemCRT_"
            }

            // Obter iniciais ou parte do nome do motorista
            val iniciais = obterIniciaisNome(checklist.motoristaName ?: "")

            // Nome do arquivo: CRT_INICIAIS_PLACACAVALO_PLACACARRETA_DATA.pdf
            val novoNomeArquivo = "${crtParte}${iniciais}_${checklist.placaCavalo}_${checklist.placaCarreta}_$dataFormatada.pdf"

            // Criar cópia temporária do arquivo com o novo nome
            val tempDir = context.cacheDir
            val temp = File(tempDir, novoNomeArquivo)
            pdfOriginal.copyTo(temp, overwrite = true)
            Log.d(TAG, "Arquivo temporário criado: ${temp.absolutePath}")
            temp
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar arquivo temporário: ${e.message}", e)
            // Se não conseguir criar o arquivo temporário, usar o original
            pdfOriginal
        }
    }

    private fun prepararCorpoEmail(checklist: Checklist): String {
        return buildString {
            append("Checklist de inspeção veicular:\n\n")
            append("Data: ${dateFormat.format(checklist.data)}\n")
            append("Hora: ${timeFormat.format(checklist.data)}\n")
            append("Placas: ${checklist.placaCavalo} / ${checklist.placaCarreta}\n")
            append("Motorista: ${checklist.motoristaName}\n")

            if (!checklist.crtMicDue.isNullOrBlank()) {
                append("CRT/MIC/DUE: ${checklist.crtMicDue}\n")
            } else {
                append("CRT/MIC/DUE: Não aplicável (viagem sem carga)\n")
            }

            append("\nSegue em anexo o documento completo do checklist.")
        }
    }

    // Método para obter iniciais ou parte do nome
    private fun obterIniciaisNome(nomeCompleto: String): String {
        if (nomeCompleto.isBlank()) return "XXX"

        val partes = nomeCompleto.split(" ")

        // Se o nome tiver apenas uma palavra, usar os 3 primeiros caracteres
        if (partes.size == 1) {
            return partes[0].take(3).uppercase()
        }

        // Se tiver 2 ou mais partes, pegar a primeira letra de até 3 palavras
        return partes
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString("") { it.first().toString() }
            .uppercase()
    }

    // Limpar arquivos temporários antigos no cache
    private fun limparArquivosTemporariosAntigos() {
        try {
            val tempDir = context.cacheDir
            val agora = System.currentTimeMillis()
            val umDia = 24 * 60 * 60 * 1000L // 24 horas em milissegundos

            tempDir.listFiles()?.forEach { file ->
                // Verificar se é um arquivo PDF temporário nosso
                if (file.name.startsWith("CRT") && file.name.endsWith(".pdf")) {
                    // Se tem mais de 1 dia, deletar
                    if (agora - file.lastModified() > umDia) {
                        if (file.delete()) {
                            Log.d(TAG, "Arquivo temporário antigo removido: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar arquivos temporários: ${e.message}")
        }
    }
}