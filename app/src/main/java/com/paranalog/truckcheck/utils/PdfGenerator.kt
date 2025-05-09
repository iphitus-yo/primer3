package com.paranalog.truckcheck.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Image
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfGenerator(private val context: Context) {
    private val TAG = "PdfGenerator"
    private val fonteTitulo = Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD)
    private val fonteSubtitulo = Font(Font.FontFamily.HELVETICA, 14f, Font.BOLD)
    private val fonteNormal = Font(Font.FontFamily.HELVETICA, 12f, Font.NORMAL)
    private val fonteBold = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD)
    private val fontePequena = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    fun gerarPdf(checklist: Checklist, itens: List<ItemChecklist>): File? {
        try {
            // Criar diretório se não existir
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "checklists")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // Criar nome do arquivo
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "checklist_${checklist.placaCavalo}_$timestamp.pdf"
            val file = File(dir, filename)

            // Log para debug
            Log.d(TAG, "Gerando PDF para checklist ID: ${checklist.id}")
            Log.d(TAG, "Dados do checklist: Responsável: ${checklist.motoristaName}, " +
                    "Placas: ${checklist.placaCavalo}/${checklist.placaCarreta}, " +
                    "CRT: ${checklist.crtMicDue}")
            Log.d(TAG, "Localização coletada: ${checklist.localColeta}")

            // Criar documento PDF
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // Adicionar cabeçalho
            adicionarCabecalho(document, checklist)

            // Adicionar tabela de itens
            adicionarTabelaItens(document, itens)

            // Adicionar rodapé
            adicionarRodape(document, checklist)

            document.close()
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gerar PDF", e)
            return null
        }
    }

    private fun adicionarCabecalho(document: Document, checklist: Checklist) {
        // Garantir que temos valores não nulos
        val placaCavalo = checklist.placaCavalo ?: "N/A"
        val placaCarreta = checklist.placaCarreta ?: "N/A"
        val motoristaNome = checklist.motoristaName ?: "N/A"

        // Formatar a localização para garantir um valor adequado
        val localColeta = when {
            checklist.localColeta.isNullOrBlank() -> "Localização não disponível"
            checklist.localColeta == "Localização não disponível" -> "Localização não disponível no momento da coleta"
            checklist.localColeta!!.startsWith("Lat:") -> "Coordenadas: ${checklist.localColeta}"
            else -> checklist.localColeta
        }

        // Título
        val titulo = Paragraph("CHECKLIST PARA INSPEÇÃO DE VEÍCULOS - 17 PONTOS", fonteTitulo)
        titulo.alignment = Element.ALIGN_CENTER
        titulo.spacingAfter = 20f
        document.add(titulo)

        // Tabela de informações
        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(1f, 3f))
        table.spacingAfter = 20f

        // Local e data
        adicionarCelulaCabecalho(table, "Local da Coleta:", fonteBold)
        adicionarCelulaNormal(table, localColeta, fonteNormal)

        adicionarCelulaCabecalho(table, "Data:", fonteBold)
        adicionarCelulaNormal(table, dateFormat.format(checklist.data) + " " + timeFormat.format(checklist.data), fonteNormal)

        // CRT/MIC/DUE
        adicionarCelulaCabecalho(table, "CRT/MIC/DUE:", fonteBold)
        adicionarCelulaNormal(table, checklist.crtMicDue ?: "N/A", fonteNormal)

        // Responsável
        adicionarCelulaCabecalho(table, "Responsável pela inspeção:", fonteBold)
        adicionarCelulaNormal(table, motoristaNome, fonteNormal)

        // Placas - garantir que exista sempre um valor
        adicionarCelulaCabecalho(table, "Placa cavalo:", fonteBold)
        adicionarCelulaNormal(table, placaCavalo, fonteNormal)

        adicionarCelulaCabecalho(table, "Placa carreta:", fonteBold)
        adicionarCelulaNormal(table, placaCarreta, fonteNormal)

        // Motorista - garantir que exista sempre um valor
        adicionarCelulaCabecalho(table, "Motorista:", fonteBold)
        adicionarCelulaNormal(table, motoristaNome, fonteNormal)

        // N. Lacre
        adicionarCelulaCabecalho(table, "N. Lacre:", fonteBold)
        adicionarCelulaNormal(table, checklist.nLacre ?: "N/A", fonteNormal)

        // Peso Bruto
        adicionarCelulaCabecalho(table, "Peso Bruto (Kg):", fonteBold)
        adicionarCelulaNormal(table, checklist.pesoBruto ?: "N/A", fonteNormal)

        // Status
        adicionarCelulaCabecalho(table, "Status:", fonteBold)
        val status = StringBuilder()
        if (checklist.statusEntrada) status.append("Entrada ")
        if (checklist.statusSaida) status.append("Saída ")
        if (checklist.statusPernoite) status.append("Pernoite ")
        if (checklist.statusParada) status.append("Parada ")
        if (status.isEmpty()) status.append("N/A")
        adicionarCelulaNormal(table, status.toString(), fonteNormal)

        document.add(table)
    }

    private fun adicionarTabelaItens(document: Document, itens: List<ItemChecklist>) {
        // Título da seção
        val tituloItens = Paragraph("Itens Inspecionados", fonteSubtitulo)
        tituloItens.alignment = Element.ALIGN_CENTER
        tituloItens.spacingAfter = 10f
        document.add(tituloItens)

        // Tabela de itens
        val table = PdfPTable(5)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(0.5f, 2.5f, 0.6f, 0.6f, 0.6f))
        table.spacingAfter = 20f

        // Cabeçalho da tabela
        adicionarCelulaCabecalhoTabela(table, "Nº", fonteBold)
        adicionarCelulaCabecalhoTabela(table, "Item Inspecionado", fonteBold)
        adicionarCelulaCabecalhoTabela(table, "SIM", fonteBold)
        adicionarCelulaCabecalhoTabela(table, "NÃO", fonteBold)
        adicionarCelulaCabecalhoTabela(table, "N/A", fonteBold)

        // Itens
        for (item in itens) {
            // Número
            adicionarCelulaNormal(table, item.numeroItem.toString(), fonteNormal)

            // Descrição
            adicionarCelulaNormal(table, item.descricao, fonteNormal)

            // Status SIM
            adicionarCelulaCheckbox(table, item.status == "SIM")

            // Status NÃO
            adicionarCelulaCheckbox(table, item.status == "NÃO")

            // Status N/A
            adicionarCelulaCheckbox(table, item.status == "N/A")
        }

        document.add(table)

        // Adicionar comentários
        document.add(Paragraph("Comentários:", fonteSubtitulo))
        document.add(Paragraph(" "))

        var comentariosExistem = false
        for (item in itens) {
            if (!item.comentario.isNullOrBlank()) {
                comentariosExistem = true
                document.add(Paragraph("Item ${item.numeroItem} - ${item.descricao}:", fonteBold))
                document.add(Paragraph(item.comentario, fonteNormal))
                document.add(Paragraph(" "))
            }
        }

        if (!comentariosExistem) {
            document.add(Paragraph("Nenhum comentário registrado", fonteNormal))
            document.add(Paragraph(" "))
        }

        // Adicionar fotos
        document.add(Paragraph("Fotos relacionadas aos itens:", fonteSubtitulo))
        document.add(Paragraph(" "))

        var fotosExistem = false
        for (item in itens) {
            if (!item.fotoPath.isNullOrBlank()) {
                fotosExistem = true
                try {
                    document.add(Paragraph("Foto relacionada ao item ${item.numeroItem} - ${item.descricao}:", fonteBold))

                    // Carregar a imagem
                    val file = File(item.fotoPath)
                    if (file.exists()) {
                        val image = Image.getInstance(item.fotoPath)

                        // Redimensionar imagem se for muito grande
                        val pageWidth = document.pageSize.width - document.leftMargin() - document.rightMargin()
                        val pageHeight = document.pageSize.height - document.topMargin() - document.bottomMargin()

                        // Ajustar tamanho mantendo proporção
                        val ratio = image.width / image.height
                        var imageWidth = pageWidth - 40 // Margem adicional
                        var imageHeight = imageWidth / ratio

                        // Se a altura for maior que o disponível, ajustar
                        if (imageHeight > pageHeight * 0.6) { // Usar no máximo 60% da altura da página
                            imageHeight = pageHeight * 0.6f
                            imageWidth = imageHeight * ratio
                        }

                        image.scaleAbsolute(imageWidth, imageHeight)
                        document.add(image)
                    } else {
                        document.add(Paragraph("(Não foi possível carregar a imagem)", fontePequena))
                    }

                    document.add(Paragraph(" "))
                } catch (e: Exception) {
                    document.add(Paragraph("Erro ao carregar imagem: ${e.message}", fontePequena))
                    document.add(Paragraph(" "))
                }
            }
        }

        if (!fotosExistem) {
            document.add(Paragraph("Nenhuma foto registrada", fonteNormal))
            document.add(Paragraph(" "))
        }
    }

    private fun adicionarRodape(document: Document, checklist: Checklist) {
        // Garantir que temos um nome válido
        val motoristaNome = checklist.motoristaName ?: "N/A"

        // Adicionar linha de assinatura
        document.add(Paragraph(" "))
        document.add(Paragraph(" "))

        val table = PdfPTable(3)
        table.widthPercentage = 100f

        // Assinatura Vistoriador
        val cellVistoriador = PdfPCell()
        cellVistoriador.border = Rectangle.TOP
        cellVistoriador.setPadding(5f)
        cellVistoriador.addElement(Paragraph("ASSINADO DIGITALMENTE POR", fontePequena))
        cellVistoriador.addElement(Paragraph(motoristaNome, fonteNormal))
        table.addCell(cellVistoriador)

        // Motorista
        val cellMotorista = PdfPCell()
        cellMotorista.border = Rectangle.TOP
        cellMotorista.setPadding(5f)
        cellMotorista.addElement(Paragraph("Motorista:", fontePequena))
        cellMotorista.addElement(Paragraph(motoristaNome, fonteNormal))
        table.addCell(cellMotorista)

        // Data
        val cellData = PdfPCell()
        cellData.border = Rectangle.TOP
        cellData.setPadding(5f)
        cellData.addElement(Paragraph("Data Checklist", fontePequena))
        cellData.addElement(Paragraph(dateFormat.format(checklist.data), fonteNormal))
        table.addCell(cellData)

        document.add(table)

        // Data e hora de geração
        val dataGeracao = Paragraph("Documento gerado em TruckCheck by Paranálog " +
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR")).format(Date()),
            fontePequena)
        dataGeracao.alignment = Element.ALIGN_CENTER
        document.add(dataGeracao)
    }

    private fun adicionarCelulaCabecalho(table: PdfPTable, texto: String, fonte: Font) {
        val cell = PdfPCell(Phrase(texto, fonte))
        cell.backgroundColor = BaseColor.LIGHT_GRAY
        cell.horizontalAlignment = Element.ALIGN_LEFT
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(5f)
        table.addCell(cell)
    }

    private fun adicionarCelulaNormal(table: PdfPTable, texto: String, fonte: Font) {
        val cell = PdfPCell(Phrase(texto, fonte))
        cell.horizontalAlignment = Element.ALIGN_LEFT
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(5f)
        table.addCell(cell)
    }

    private fun adicionarCelulaCabecalhoTabela(table: PdfPTable, texto: String, fonte: Font) {
        val cell = PdfPCell(Phrase(texto, fonte))
        cell.backgroundColor = BaseColor.LIGHT_GRAY
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(5f)
        table.addCell(cell)
    }

    private fun adicionarCelulaCheckbox(table: PdfPTable, checked: Boolean) {
        val cell = PdfPCell()
        cell.horizontalAlignment = Element.ALIGN_CENTER
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        cell.setPadding(5f)

        if (checked) {
            cell.phrase = Phrase("X", fonteBold)
        } else {
            cell.phrase = Phrase(" ", fonteBold)
        }

        table.addCell(cell)
    }
}