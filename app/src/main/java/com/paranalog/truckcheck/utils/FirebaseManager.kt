package com.paranalog.truckcheck.utils

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gerenciador Firebase CORRIGIDO para TruckCheck - Paranálog Transportes
 * ✅ Firebase SEMPRE transparente ao usuário
 * ✅ Estrutura 100% compatível com Dashboard
 * ✅ Mapeamento OEA correto (conformidade real)
 * ✅ Campos timestamp consistentes
 */
class FirebaseManager(private val context: Context) {
    private val db: FirebaseFirestore by lazy {
        initializeFirestore()
    }
    private val TAG = "FirebaseManager"
    private val COMPANY_ID = "paranalog_001"

    init {
        initializeFirebase()
    }

    /**
     * Inicializa Firebase silenciosamente
     */
    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Firebase inicializado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar Firebase: ${e.message}")
        }
    }

    /**
     * Configura Firestore com settings otimizados
     */
    private fun initializeFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()

        // Configurações para melhor performance
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Cache offline
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        firestore.firestoreSettings = settings
        return firestore
    }

    /**
     * Envia checklist ao Firebase de forma silenciosa
     * @param checklist Dados do checklist
     * @param itens Lista de itens inspecionados
     * @return Boolean indicando sucesso (apenas para log interno)
     */
    suspend fun enviarChecklistParaFirebase(
        checklist: Checklist,
        itens: List<ItemChecklist>
    ): Boolean {
        return try {
            Log.d(TAG, "Iniciando envio ao Firebase - Checklist ID: ${checklist.id}")

            val checklistData = criarDocumentoFirebase(checklist, itens)
            val docId = gerarDocumentId(checklist)

            // Enviar para coleção principal
            db.collection("checklists")
                .document(docId)
                .set(checklistData)
                .await()

            // Atualizar dados da empresa (estatísticas)
            atualizarEstatisticasEmpresa()

            // Atualizar dados do veículo
            atualizarDadosVeiculo(checklist)

            // Atualizar dados do motorista
            atualizarDadosMotorista(checklist)

            Log.d(TAG, "✅ Checklist enviado com sucesso ao Firebase: $docId")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao enviar checklist ao Firebase: ${e.message}", e)
            false
        }
    }

    /**
     * ✅ CORRIGIDO: Cria documento estruturado para Firebase 100% compatível com Dashboard
     */
    private fun criarDocumentoFirebase(
        checklist: Checklist,
        itens: List<ItemChecklist>
    ): Map<String, Any> {
        // Calcular estatísticas CORRETAS do checklist
        val stats = calcularEstatisticasOEA(itens)

        // Mapear itens do checklist com conformidade CORRETA
        val itemsMap = mapearItensChecklistOEA(itens)

        // ✅ Timestamp Firebase consistente
        val timestampFirebase = Timestamp(checklist.data)

        return mapOf(
            // === IDENTIFICAÇÃO ===
            "id" to gerarDocumentId(checklist),
            "companyId" to COMPANY_ID,
            "vehicleId" to (checklist.placaCavalo ?: "unknown"),
            "driverId" to (checklist.motoristaCpf ?: "unknown"),

            // === DADOS DO VEÍCULO ===
            "vehiclePlate" to (checklist.placaCavalo ?: ""),
            "trailerPlate" to (checklist.placaCarreta ?: ""),
            "placaCarreta" to (checklist.placaCarreta ?: ""), // Campo alternativo
            "driverName" to (checklist.motoristaName ?: ""),
            "motoristaName" to (checklist.motoristaName ?: ""), // Campo alternativo

            // === TEMPORAL ===
            "timestamp" to timestampFirebase,
            "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(checklist.data),
            "status" to "completed",

            // === DADOS LOGÍSTICOS OEA ===
            "crtMicDue" to (checklist.crtMicDue?.takeIf { it.isNotBlank() } ?: ""),
            "sealNumber" to (checklist.nLacre?.takeIf { it.isNotBlank() } ?: ""),
            "nLacre" to (checklist.nLacre?.takeIf { it.isNotBlank() } ?: ""), // Campo alternativo
            "grossWeight" to parseWeight(checklist.pesoBruto),

            // === LOCALIZAÇÃO ===
            "location" to mapOf(
                "address" to (checklist.localColeta ?: "Localização não disponível"),
                "coordinates" to mapOf(
                    "latitude" to 0.0,  // TODO: Extrair coordenadas reais se disponível
                    "longitude" to 0.0
                )
            ),

            // === ITENS DO CHECKLIST (25 pontos OEA) - ESTRUTURA CORRIGIDA ===
            "items" to itemsMap,

            // === ESTATÍSTICAS CORRETAS OEA ===
            "summary" to mapOf(
                "totalItems" to stats.totalItems,
                "itemsOk" to stats.itemsOk,
                "itemsProblema" to stats.itemsProblema,
                "itemsNA" to stats.itemsNA,
                "criticalIssues" to stats.criticalIssues,
                "conformityRate" to stats.conformityRate,
                "completionTime" to calcularTempoPreenchimento()
            ),

            // === ANEXOS ===
            "attachments" to mapOf(
                "photos" to itens.mapNotNull { it.fotoPath }.map {
                    mapOf(
                        "path" to it,
                        "itemNumber" to (itens.find { item -> item.fotoPath == it }?.numeroItem ?: 0)
                    )
                },
                "pdfGenerated" to (checklist.pdfPath != null),
                "pdfPath" to (checklist.pdfPath ?: ""),
                "emailSent" to checklist.emailEnviado
            ),

            // === METADADOS ===
            "createdAt" to timestampFirebase,
            "updatedAt" to Timestamp.now(),
            "version" to "1.0",
            "source" to "TruckCheck-Android",
            "deviceInfo" to mapOf(
                "os" to "Android",
                "app" to "TruckCheck"
            )
        )
    }

    /**
     * ✅ CORRIGIDO: Mapeia itens com lógica OEA CORRETA
     */
    private fun mapearItensChecklistOEA(itens: List<ItemChecklist>): Map<String, Any> {
        val itemsMap = mutableMapOf<String, Any>()

        itens.forEach { item ->
            val itemKey = getItemKey(item.numeroItem)
            val statusMapeado = mapearStatusOEA(item.status)
            val isConform = calcularConformidadeOEA(item.numeroItem, statusMapeado)

            itemsMap[itemKey] = mapOf(
                "numeroItem" to item.numeroItem,
                "name" to (item.descricao ?: ""),
                "descricao" to (item.descricao ?: ""), // Campo alternativo
                "status" to statusMapeado,
                "actualAnswer" to (item.status ?: ""),
                "expectedAnswer" to getExpectedAnswerOEA(item.numeroItem),
                "isConform" to isConform, // ✅ CAMPO ESSENCIAL para Dashboard
                "comentario" to (item.comentario ?: ""),
                "observation" to (item.comentario ?: ""), // Campo alternativo
                "temFoto" to (item.fotoPath != null),
                "fotoPath" to (item.fotoPath ?: ""),
                "photoUrl" to (item.fotoPath ?: ""), // Campo alternativo
                "required" to true,
                "critical" to isItemCritical(item.numeroItem),
                "isCritical" to isItemCritical(item.numeroItem), // Campo alternativo
                "category" to getCategoryForItem(item.numeroItem),
                "timestamp" to Timestamp.now()
            )
        }

        return itemsMap
    }

    /**
     * ✅ CORRIGIDO: Calcula estatísticas com lógica OEA REAL
     */
    private fun calcularEstatisticasOEA(itens: List<ItemChecklist>): ChecklistStats {
        val totalItems = itens.size
        var itemsConformes = 0
        var itemsNaoConformes = 0
        var itemsNA = 0
        var criticalIssues = 0

        itens.forEach { item ->
            val statusMapeado = mapearStatusOEA(item.status)
            val isConform = calcularConformidadeOEA(item.numeroItem, statusMapeado)

            when {
                isConform -> itemsConformes++
                statusMapeado == "na" -> itemsNA++
                else -> {
                    itemsNaoConformes++
                    if (isItemCritical(item.numeroItem)) {
                        criticalIssues++
                    }
                }
            }
        }

        val conformityRate = if (totalItems > 0) {
            ((itemsConformes.toDouble() / totalItems) * 100).toInt()
        } else 0

        return ChecklistStats(
            totalItems = totalItems,
            itemsOk = itemsConformes,
            itemsProblema = itemsNaoConformes,
            itemsNA = itemsNA,
            criticalIssues = criticalIssues,
            conformityRate = conformityRate
        )
    }

    /**
     * ✅ NOVA: Calcula conformidade OEA CORRETA por item
     */
    private fun calcularConformidadeOEA(numeroItem: Int, status: String): Boolean {
        return when(numeroItem) {
            in 1..17 -> status == "ok"        // Itens 1-17: Conformidade = SIM (ok)
            in 18..19 -> status == "na"       // Itens 18-19: Conformidade = N/A (na)
            in 20..25 -> status == "problema" // Itens 20-25: Conformidade = NÃO (problema)
            else -> false
        }
    }

    /**
     * ✅ NOVA: Resposta esperada para conformidade OEA
     */
    private fun getExpectedAnswerOEA(numeroItem: Int): String {
        return when(numeroItem) {
            in 1..17 -> "SIM"  // Conformidade esperada
            in 18..19 -> "N/A" // Conformidade esperada
            in 20..25 -> "NÃO" // Conformidade esperada
            else -> "SIM"
        }
    }

    /**
     * Gera ID único para documento
     */
    private fun gerarDocumentId(checklist: Checklist): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HHhMMm", Locale.getDefault())
        val date = dateFormat.format(checklist.data)
        val time = timeFormat.format(checklist.data)
        val plate = checklist.placaCavalo?.replace("-", "") ?: "UNKNOWN"

        return "${date}_${plate}_${time}"
    }

    /**
     * Mapeia status do checklist para padrão Firebase
     */
    private fun mapearStatusOEA(status: String?): String {
        return when(status?.uppercase()?.trim()) {
            "SIM" -> "ok"
            "NÃO" -> "problema"
            "N/A", "NA" -> "na"
            else -> "pendente"
        }
    }

    /**
     * Converte peso bruto para número
     */
    private fun parseWeight(pesoBruto: String?): Int {
        return try {
            pesoBruto?.replace(".", "")?.replace(",", "")?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Determina se item é crítico para OEA
     */
    private fun isItemCritical(numeroItem: Int): Boolean {
        val itensCriticos = setOf(
            2,  // MOTOR
            3,  // PNEUS
            5,  // TANQUE DE COMBUSTÍVEL
            7,  // RESERVATÓRIO DE AR
            8,  // EIXO DE TRANSMISSÃO
            9,  // QUINTA RODA
            11, // CHASSI
            20, // ODORES
            21, // PRAGAS VISÍVEIS
            22, // CONTAMINAÇÃO QUÍMICA
            23, // FUNDO OU PAREDE FALSA DETECTADO?
            24, // INDÍCIOS DE CONTAMINAÇÃO?
            25  // AUTORIDADE COMPETENTE NOTIFICADA?
        )
        return numeroItem in itensCriticos
    }

    /**
     * Obtém categoria do item
     */
    private fun getCategoryForItem(numeroItem: Int): String {
        return when(numeroItem) {
            in 1..11 -> "veiculo"
            in 12..17 -> "carga"
            in 18..19 -> "climatizacao"
            in 20..25 -> "oea"
            else -> "outros"
        }
    }

    /**
     * Gera chave do item para Firebase
     */
    private fun getItemKey(numeroItem: Int): String {
        val itemNames = mapOf(
            1 to "paraChoque",
            2 to "motor",
            3 to "pneus",
            4 to "pisoUnidadeTratora",
            5 to "tanqueCombustivel",
            6 to "cabine",
            7 to "reservatorioAr",
            8 to "eixoTransmissao",
            9 to "quintaRoda",
            10 to "sistemaExaustao",
            11 to "chassi",
            12 to "portasTraseira",
            13 to "portaLateralDireita",
            14 to "portaLateralEsquerda",
            15 to "paredeFrontal",
            16 to "teto",
            17 to "pisoCompartimentoCarga",
            18 to "unidExaustora",
            19 to "motorCamaraFria",
            20 to "odores",
            21 to "pragasVisiveis",
            22 to "contaminacaoQuimica",
            23 to "fundoParedeFalsa",
            24 to "indiciosContaminacao",
            25 to "autoridadeNotificada"
        )

        return itemNames[numeroItem] ?: "item_$numeroItem"
    }

    /**
     * Atualiza estatísticas da empresa (execução silenciosa)
     */
    private suspend fun atualizarEstatisticasEmpresa() {
        try {
            val empresaRef = db.collection("companies").document(COMPANY_ID)
            val updateData = mapOf(
                "lastChecklistUpdate" to Timestamp.now(),
                "totalChecklists" to com.google.firebase.firestore.FieldValue.increment(1)
            )
            empresaRef.update(updateData).await()
            Log.d(TAG, "Estatísticas da empresa atualizadas")
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao atualizar estatísticas da empresa: ${e.message}")
        }
    }

    /**
     * Atualiza dados do veículo (execução silenciosa)
     */
    private suspend fun atualizarDadosVeiculo(checklist: Checklist) {
        try {
            if (checklist.placaCavalo.isNullOrEmpty()) return

            val vehicleRef = db.collection("vehicles").document(checklist.placaCavalo!!)
            val vehicleData = mapOf(
                "id" to checklist.placaCavalo,
                "companyId" to COMPANY_ID,
                "plate" to checklist.placaCavalo,
                "trailerPlate" to (checklist.placaCarreta ?: ""),
                "currentDriver" to (checklist.motoristaName ?: ""),
                "driverId" to (checklist.motoristaCpf ?: ""),
                "lastChecklistDate" to Timestamp(checklist.data),
                "active" to true,
                "updatedAt" to Timestamp.now(),
                "createdAt" to Timestamp.now()
            )
            vehicleRef.set(vehicleData, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG, "Dados do veículo atualizados: ${checklist.placaCavalo}")
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao atualizar dados do veículo: ${e.message}")
        }
    }

    /**
     * Atualiza dados do motorista (execução silenciosa)
     */
    private suspend fun atualizarDadosMotorista(checklist: Checklist) {
        try {
            if (checklist.motoristaCpf.isNullOrEmpty()) return

            val driverRef = db.collection("drivers").document(checklist.motoristaCpf!!)
            val driverData = mapOf(
                "id" to checklist.motoristaCpf,
                "companyId" to COMPANY_ID,
                "name" to (checklist.motoristaName ?: ""),
                "cpf" to checklist.motoristaCpf,
                "lastChecklistDate" to Timestamp(checklist.data),
                "currentVehicle" to (checklist.placaCavalo ?: ""),
                "assignedVehicles" to listOf(checklist.placaCavalo ?: ""),
                "active" to true,
                "updatedAt" to Timestamp.now(),
                "createdAt" to Timestamp.now()
            )
            driverRef.set(driverData, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG, "Dados do motorista atualizados: ${checklist.motoristaName}")
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao atualizar dados do motorista: ${e.message}")
        }
    }

    /**
     * Calcula tempo de preenchimento (placeholder)
     */
    private fun calcularTempoPreenchimento(): Double {
        // Por enquanto valor fixo, pode ser implementado para calcular tempo real
        return 8.5
    }

    /**
     * Tenta reenviar checklists que falharam ao enviar
     */
    suspend fun tentarReenviarPendentes(checklists: List<Checklist>, itensMap: Map<Long, List<ItemChecklist>>) {
        checklists.forEach { checklist ->
            try {
                val itens = itensMap[checklist.id] ?: emptyList()
                if (itens.isNotEmpty()) {
                    val sucesso = enviarChecklistParaFirebase(checklist, itens)
                    if (sucesso) {
                        Log.d(TAG, "✅ Reenvio bem-sucedido para checklist ${checklist.id}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro no reenvio do checklist ${checklist.id}: ${e.message}")
            }
        }
    }

    /**
     * Data class para estatísticas
     */
    private data class ChecklistStats(
        val totalItems: Int,
        val itemsOk: Int,
        val itemsProblema: Int,
        val itemsNA: Int,
        val criticalIssues: Int,
        val conformityRate: Int
    )
}