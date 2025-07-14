package com.paranalog.truckcheck.adapters

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.models.Checklist
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador para a lista de checklists na tela principal
 */
class ChecklistAdapter(
    private val context: Context,
    private var checklists: List<Checklist>,
    private val onClickListener: (Checklist) -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.ViewHolder>() {

    private val TAG = "ChecklistAdapter"
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    // Novo formato combinado de data e hora
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.cardView)
        val tvPlacas: TextView = view.findViewById(R.id.tvPlacas)
        val tvData: TextView = view.findViewById(R.id.tvData)
        val tvCrt: TextView = view.findViewById(R.id.tvCrt)
        val viewStatus: View = view.findViewById(R.id.viewStatus)
        val btnPdf: Button = view.findViewById(R.id.btnPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_checklist_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val checklist = checklists[position]

        // Configurar as placas (cavalo/carreta)
        val placas = "${checklist.placaCavalo ?: "N/A"} / ${checklist.placaCarreta ?: "N/A"}"
        holder.tvPlacas.text = placas

        // Configurar a data com hora (formato: 10/05/2023 - 14:30)
        holder.tvData.text = dateTimeFormat.format(checklist.data)

        // Configurar o CRT/MIC
        holder.tvCrt.text = "CRT/MIC: ${checklist.crtMicDue ?: "N/A"}"

        // Definir a cor do indicador de status baseado no status de envio do email
        if (checklist.emailEnviado) {
            // Email enviado - verde
            holder.viewStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.status_positive))
        } else {
            // Email não enviado - laranja
            holder.viewStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.orange_pending))
        }

        // Configurar clique no item
        holder.cardView.setOnClickListener {
            onClickListener(checklist)
        }

        // Configurar o botão PDF
        holder.btnPdf.setOnClickListener {
            val pdfPath = checklist.pdfPath

            Log.d(TAG, "Botão PDF clicado para checklist #${checklist.id}")
            Log.d(TAG, "Caminho do PDF: $pdfPath")

            if (pdfPath != null && pdfPath.isNotEmpty()) {
                try {
                    val file = File(pdfPath)
                    val fileExists = file.exists()
                    val fileReadable = file.canRead()
                    val fileSize = if (fileExists) file.length() else 0

                    Log.d(TAG, "Arquivo existe: $fileExists, Legível: $fileReadable, Tamanho: $fileSize bytes")

                    if (fileExists && fileReadable) {
                        // Verificar se o arquivo é um PDF válido (pelo menos verificar tamanho mínimo)
                        if (fileSize > 100) { // PDFs válidos geralmente são maiores que 100 bytes
                            Log.d(TAG, "Tentando abrir PDF: ${file.absolutePath}")
                            abrirPdfMelhorado(context, file)
                        } else {
                            Log.e(TAG, "Arquivo muito pequeno para ser um PDF válido: $fileSize bytes")
                            Toast.makeText(context, "PDF inválido ou corrompido", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Tentar caminho alternativo se o arquivo não for encontrado
                        val alternativePath = tentarCaminhoAlternativo(checklist)
                        if (alternativePath != null) {
                            Log.d(TAG, "Tentando caminho alternativo: $alternativePath")
                            val alternativeFile = File(alternativePath)
                            if (alternativeFile.exists() && alternativeFile.canRead()) {
                                Log.d(TAG, "Arquivo alternativo encontrado, abrindo...")
                                abrirPdfMelhorado(context, alternativeFile)
                                return@setOnClickListener
                            }
                        }

                        Toast.makeText(context,
                            "Não foi possível encontrar o arquivo PDF",
                            Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Problema com o arquivo: Existe=$fileExists, Legível=$fileReadable")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao verificar o arquivo PDF", e)
                    Toast.makeText(context, "Erro ao verificar o PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "PDF não disponível para este checklist", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Caminho do PDF vazio ou nulo para checklist #${checklist.id}")
            }
        }
    }

    /**
     * Tenta encontrar um caminho alternativo para o PDF caso o caminho original não seja válido
     */
    private fun tentarCaminhoAlternativo(checklist: Checklist): String? {
        try {
            // Primeiro, tentar construir o caminho baseado no ID do checklist
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            val checklistDir = File(dir, "checklists")

            // Verificar diferentes possíveis nomes de arquivo
            val possibleNames = listOf(
                "checklist_${checklist.id}.pdf",
                "checklist_${checklist.placaCavalo}.pdf",
                "checklist_${checklist.placaCavalo}_${dateFormat.format(checklist.data)}.pdf"
            )

            for (name in possibleNames) {
                val file = File(checklistDir, name)
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Arquivo alternativo encontrado: ${file.absolutePath}")
                    return file.absolutePath
                }
            }

            // Tentar encontrar qualquer PDF relacionado a este checklist
            val files = checklistDir.listFiles { file ->
                file.name.startsWith("checklist_") &&
                        file.name.endsWith(".pdf") &&
                        (file.name.contains(checklist.id.toString()) ||
                                file.name.contains(checklist.placaCavalo ?: ""))
            }

            if (!files.isNullOrEmpty()) {
                // Ordenar por data de modificação (mais recente primeiro)
                val mostRecentFile = files.maxByOrNull { it.lastModified() }
                if (mostRecentFile != null) {
                    Log.d(TAG, "PDF mais recente encontrado: ${mostRecentFile.absolutePath}")
                    return mostRecentFile.absolutePath
                }
            }

            // Último recurso: listar todos os PDFs disponíveis e usar o mais recente
            Log.d(TAG, "Tentando encontrar qualquer PDF disponível...")
            val allPdfs = checklistDir.listFiles { file -> file.name.endsWith(".pdf") }
            if (!allPdfs.isNullOrEmpty() && allPdfs.isNotEmpty()) {
                val mostRecentPdf = allPdfs.maxByOrNull { it.lastModified() }
                if (mostRecentPdf != null) {
                    Log.d(TAG, "PDF mais recente (entre todos) encontrado: ${mostRecentPdf.absolutePath}")
                    return mostRecentPdf.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar caminhos alternativos", e)
        }

        return null
    }

    /**
     * Método melhorado para abrir PDF com múltiplas estratégias
     * Funciona melhor em Android 11+ e oferece alternativas
     */
    private fun abrirPdfMelhorado(context: Context, file: File) {
        try {
            Log.d(TAG, "Iniciando abertura melhorada do PDF: ${file.absolutePath}")

            // Verificar se o arquivo existe e é legível
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "Arquivo não existe ou não pode ser lido")
                Toast.makeText(context, "Erro ao acessar o arquivo PDF", Toast.LENGTH_SHORT).show()
                return
            }

            // Criar URI usando FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            Log.d(TAG, "URI criado: $uri")

            // Estratégia 1: Tentar com Intent.createChooser (mais confiável)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Criar intent chooser para dar mais opções ao usuário
            val chooserIntent = Intent.createChooser(intent, "Abrir PDF com...")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Verificar se tem algum app para abrir PDF (método melhorado para Android 11+)
            val hasAppsToOpen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - usar resolveActivity com flag especial
                val resolveIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val packageManager = context.packageManager
                // Primeiro tentar com o método padrão
                val resolveInfo = packageManager.resolveActivity(resolveIntent, PackageManager.MATCH_DEFAULT_ONLY)

                if (resolveInfo != null) {
                    true
                } else {
                    // Se não encontrou, verificar se existe algum app PDF conhecido
                    val knownPdfApps = listOf(
                        "com.adobe.reader",
                        "com.google.android.apps.docs",
                        "com.google.android.apps.pdfviewer",
                        "com.microsoft.office.officehub",
                        "com.foxit.mobile.pdf.lite",
                        "com.xodo.pdf.reader"
                    )

                    knownPdfApps.any { packageName ->
                        try {
                            packageManager.getPackageInfo(packageName, 0)
                            true
                        } catch (e: PackageManager.NameNotFoundException) {
                            false
                        }
                    }
                }
            } else {
                // Android 10 e abaixo - método tradicional
                val activities = context.packageManager.queryIntentActivities(intent, 0)
                activities.isNotEmpty()
            }

            if (hasAppsToOpen) {
                // Tentar abrir com chooser
                try {
                    context.startActivity(chooserIntent)
                    Log.d(TAG, "PDF aberto com sucesso usando chooser")
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao abrir com chooser, tentando método direto", e)

                    // Estratégia 2: Tentar abrir diretamente
                    try {
                        context.startActivity(intent)
                        Log.d(TAG, "PDF aberto com sucesso usando intent direto")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Erro ao abrir diretamente", e2)
                        oferecerAlternativas(context, file, uri)
                    }
                }
            } else {
                // Nenhum app encontrado - oferecer alternativas
                Log.w(TAG, "Nenhum app para abrir PDF encontrado")
                oferecerAlternativas(context, file, uri)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro geral ao abrir PDF", e)
            Toast.makeText(
                context,
                "Erro ao abrir PDF: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Oferece alternativas quando não consegue abrir o PDF diretamente
     */
    private fun oferecerAlternativas(context: Context, file: File, uri: android.net.Uri) {
        try {
            // Criar lista de opções
            val opcoes = arrayOf(
                "Compartilhar PDF",
                "Instalar leitor de PDF",
                "Cancelar"
            )

            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Não foi possível abrir o PDF")
                .setMessage("Nenhum aplicativo de PDF foi encontrado. O que deseja fazer?")
                .setItems(opcoes) { _, which ->
                    when (which) {
                        0 -> compartilharPdf(context, file, uri)
                        1 -> abrirPlayStoreParaPdf(context)
                    }
                }
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao mostrar alternativas", e)
            // Última tentativa - compartilhar diretamente
            compartilharPdf(context, file, uri)
        }
    }

    /**
     * Compartilha o PDF via Intent.ACTION_SEND
     */
    private fun compartilharPdf(context: Context, file: File, uri: android.net.Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Checklist TruckCheck")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar PDF via...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooser)
            Log.d(TAG, "PDF compartilhado com sucesso")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao compartilhar PDF", e)
            Toast.makeText(
                context,
                "Erro ao compartilhar PDF: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Abre a Play Store para instalar um leitor de PDF
     */
    private fun abrirPlayStoreParaPdf(context: Context) {
        try {
            // Tentar abrir a página do Adobe Reader
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=com.adobe.reader")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Se não tem Play Store, abrir no navegador
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.adobe.reader")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir Play Store", e)
            Toast.makeText(
                context,
                "Erro ao abrir loja de aplicativos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount(): Int = checklists.size

    /**
     * Atualiza a lista de checklists com novos dados
     * Esta função aceita tanto List<Checklist> quanto List<Any>
     */
    @Suppress("UNCHECKED_CAST")
    fun updateList(newChecklists: List<*>) {
        try {
            // Tentar converter a lista para List<Checklist>
            this.checklists = newChecklists as List<Checklist>
            notifyDataSetChanged()
        } catch (e: ClassCastException) {
            // Se falhar, verificar se cada item é Checklist
            val filteredList = newChecklists.filterIsInstance<Checklist>()
            if (filteredList.isNotEmpty()) {
                this.checklists = filteredList
                notifyDataSetChanged()
            } else {
                Log.e(TAG, "Não foi possível converter a lista para Checklist", e)
            }
        }
    }
}