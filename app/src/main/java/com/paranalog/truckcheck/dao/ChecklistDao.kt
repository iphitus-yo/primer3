package com.paranalog.truckcheck.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist

@Dao
interface ChecklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: Checklist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItensChecklist(itens: List<ItemChecklist>)

    @Update
    suspend fun updateChecklist(checklist: Checklist)

    @Update
    suspend fun updateItemChecklist(item: ItemChecklist)

    @Query("""
        SELECT id, motoristaCpf, motoristaName, data, placaCavalo, placaCarreta, 
        crtMicDue, nLacre, pesoBruto, statusEntrada, statusSaida, statusPernoite,
        statusParada, emailEnviado, pdfPath, localColeta
        FROM checklists 
        WHERE motoristaCpf = :cpf 
        ORDER BY data DESC
    """)
    suspend fun getChecklistsByMotorista(cpf: String): List<Checklist>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklistById(id: Long): Checklist?

    @Query("SELECT * FROM itens_checklist WHERE checklistId = :checklistId ORDER BY numeroItem ASC")
    suspend fun getItensByChecklistId(checklistId: Long): List<ItemChecklist>

    /**
     * Obtém checklists com email não enviado (status amarelo)
     * Limitado a 10 registros mais recentes para não sobrecarregar o reenvio
     */
    @Query("""
        SELECT * FROM checklists 
        WHERE emailEnviado = 0 AND pdfPath IS NOT NULL 
        ORDER BY data DESC 
        LIMIT 10
    """)
    suspend fun getChecklistsNaoEnviados(): List<Checklist>

    /**
     * Obtém todos os checklists com email não enviado (sem limite)
     * Usado para estatísticas e relatórios
     */
    @Query("SELECT * FROM checklists WHERE emailEnviado = 0")
    suspend fun getTodosChecklistsNaoEnviados(): List<Checklist>

    @Query("UPDATE checklists SET emailEnviado = 1, pdfPath = :pdfPath WHERE id = :id")
    suspend fun marcarComoEnviado(id: Long, pdfPath: String)

    /**
     * Obtém checklists que têm PDF gerado mas email não foi enviado
     * Usado para identificar checklists prontos para reenvio
     */
    @Query("""
        SELECT id, motoristaCpf, motoristaName, data, placaCavalo, placaCarreta, 
        crtMicDue, nLacre, pesoBruto, statusEntrada, statusSaida, statusPernoite,
        statusParada, emailEnviado, pdfPath, localColeta
        FROM checklists 
        WHERE pdfPath IS NOT NULL AND emailEnviado = 0
        ORDER BY data DESC
    """)
    suspend fun getChecklistsComPdfSemEmail(): List<Checklist>

    /**
     * Obtém checklists pendentes de um motorista específico (para reenvio direcionado)
     */
    @Query("""
        SELECT * FROM checklists 
        WHERE motoristaCpf = :cpf AND emailEnviado = 0 AND pdfPath IS NOT NULL
        ORDER BY data DESC 
        LIMIT 5
    """)
    suspend fun getChecklistsPendentesByMotorista(cpf: String): List<Checklist>

    /**
     * Método otimizado para marcar email como enviado
     */
    @Query("UPDATE checklists SET emailEnviado = 1 WHERE id = :id")
    suspend fun marcarEmailComoEnviado(id: Long)

    /**
     * Método para verificar se existe checklist pendente recente
     * Evita tentativas excessivas de reenvio
     */
    @Query("""
        SELECT COUNT(*) FROM checklists 
        WHERE emailEnviado = 0 AND pdfPath IS NOT NULL 
        AND date(data) >= date('now', '-1 day')
    """)
    suspend fun getChecklistsPendentesRecentes(): Int

    @Transaction
    suspend fun criarChecklistComItens(checklist: Checklist, itens: List<ItemChecklist>) {
        val id = insertChecklist(checklist)
        val itensComId = itens.map { it.copy(checklistId = id) }
        insertItensChecklist(itensComId)
    }

    /**
     * Obtém a contagem total de checklists para um motorista específico
     */
    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf")
    suspend fun getChecklistCountByMotorista(cpf: String): Int

    /**
     * Conta quantos checklists têm email não enviado (pendentes)
     */
    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf AND emailEnviado = 0")
    suspend fun getChecklistsPendentesCount(cpf: String): Int

    /**
     * Conta quantos itens com problema (status "NÃO") existem nos checklists do motorista
     */
    @Query("""
        SELECT COUNT(*) 
        FROM itens_checklist i 
        JOIN checklists c ON i.checklistId = c.id 
        WHERE c.motoristaCpf = :cpf AND i.status = 'NÃO'
    """)
    suspend fun getProblemasReportadosCount(cpf: String): Int

    /**
     * Atualiza a localização de um checklist específico
     */
    @Query("UPDATE checklists SET localColeta = :localColeta WHERE id = :checklistId")
    suspend fun updateLocalColeta(checklistId: Long, localColeta: String)

    /**
     * Obtém todos os checklists com localização pendente
     */
    @Query("SELECT * FROM checklists WHERE localColeta = 'Obtendo localização...'")
    suspend fun getChecklistsComLocalizacaoPendente(): List<Checklist>

    /**
     * Estatísticas gerais do sistema
     */
    @Query("SELECT COUNT(*) FROM checklists WHERE emailEnviado = 1")
    suspend fun getTotalChecklistsEnviados(): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE emailEnviado = 0 AND pdfPath IS NOT NULL")
    suspend fun getTotalChecklistsPendentes(): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE pdfPath IS NULL")
    suspend fun getTotalChecklistsSemPdf(): Int

    /**
     * Limpeza e manutenção - versão simplificada
     */
    @Query("""
        DELETE FROM checklists 
        WHERE emailEnviado = 0 AND pdfPath IS NULL 
        AND date(data) <= date('now', '-7 days')
    """)
    suspend fun limparChecklistsIncompletos(): Int

    /**
     * Obtém checklists mais antigos que precisam de atenção
     */
    @Query("""
        SELECT * FROM checklists 
        WHERE emailEnviado = 0 AND pdfPath IS NOT NULL
        AND date(data) <= date('now', '-2 days')
        ORDER BY data ASC
        LIMIT 5
    """)
    suspend fun getChecklistsAntigosPendentes(): List<Checklist>

    /**
     * Verifica se um checklist específico teve email enviado
     */
    @Query("SELECT emailEnviado FROM checklists WHERE id = :id")
    suspend fun isEmailEnviado(id: Long): Boolean?

    /**
     * Atualiza apenas o status de email enviado e o caminho do PDF
     */
    @Query("UPDATE checklists SET emailEnviado = :enviado, pdfPath = :pdfPath WHERE id = :id")
    suspend fun updateStatusEmailEPdf(id: Long, enviado: Boolean, pdfPath: String?)

    /**
     * Obtém estatísticas separadas dos checklists de um motorista
     * Métodos individuais para evitar problemas de mapeamento do Room
     */
    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf")
    suspend fun getTotalChecklistsMotorista(cpf: String): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf AND emailEnviado = 1")
    suspend fun getChecklistsEnviadosMotorista(cpf: String): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf AND emailEnviado = 0 AND pdfPath IS NOT NULL")
    suspend fun getChecklistsPendentesMotorista(cpf: String): Int

    @Query("SELECT COUNT(*) FROM checklists WHERE motoristaCpf = :cpf AND pdfPath IS NULL")
    suspend fun getChecklistsIncompletosMotorista(cpf: String): Int

    /**
     * Método para reprocessar checklists antigos pendentes
     * Simplificado para evitar problemas de sintaxe
     */
    @Query("""
        SELECT COUNT(*) FROM checklists 
        WHERE emailEnviado = 0 AND pdfPath IS NOT NULL
        AND date(data) <= date('now', '-1 day')
    """)
    suspend fun contarChecklistsParaReprocessamento(): Int

    // 🔥 =================== MÉTODOS FIREBASE ADICIONADOS ===================

    /**
     * 🔥 FIREBASE: Busca checklists recentes para tentar reenviar ao Firebase
     * Prioriza os mais recentes que ainda não foram sincronizados
     */
    @Query("SELECT * FROM checklists ORDER BY data DESC LIMIT :limit")
    suspend fun getChecklistsRecentes(limit: Int = 5): List<Checklist>

    /**
     * 🔥 FIREBASE: Busca checklists por período de data
     * Útil para sincronização de dados específicos
     */
    @Query("SELECT * FROM checklists WHERE data BETWEEN :dataInicio AND :dataFim ORDER BY data DESC")
    suspend fun getChecklistsPorPeriodo(dataInicio: Long, dataFim: Long): List<Checklist>

    /**
     * 🔥 FIREBASE: Busca checklists de hoje
     * Para estatísticas em tempo real no dashboard
     */
    @Query("SELECT * FROM checklists WHERE date(data/1000, 'unixepoch') = date('now') ORDER BY data DESC")
    suspend fun getChecklistsHoje(): List<Checklist>

    /**
     * 🔥 FIREBASE: Busca checklists por placa
     * Para rastreamento de veículos específicos
     */
    @Query("SELECT * FROM checklists WHERE placaCavalo = :placa ORDER BY data DESC LIMIT 10")
    suspend fun getChecklistsPorPlaca(placa: String): List<Checklist>

    /**
     * 🔥 FIREBASE: Busca último checklist de cada veículo
     * Para status da frota no dashboard
     */
    @Query("""
        SELECT c.* FROM checklists c
        INNER JOIN (
            SELECT placaCavalo, MAX(data) as max_data
            FROM checklists 
            WHERE placaCavalo IS NOT NULL 
            GROUP BY placaCavalo
        ) latest ON c.placaCavalo = latest.placaCavalo AND c.data = latest.max_data
        ORDER BY c.data DESC
    """)
    suspend fun getUltimoChecklistPorVeiculo(): List<Checklist>

    /**
     * 🔥 FIREBASE: Conta total de checklists
     * Para estatísticas do dashboard
     */
    @Query("SELECT COUNT(*) FROM checklists")
    suspend fun getTotalChecklists(): Int

    /**
     * 🔥 FIREBASE: Conta checklists de hoje
     * Para KPIs do dashboard
     */
    @Query("SELECT COUNT(*) FROM checklists WHERE date(data/1000, 'unixepoch') = date('now')")
    suspend fun getCountChecklistsHoje(): Int

    /**
     * 🔥 FIREBASE: Conta veículos únicos
     * Para estatísticas de frota
     */
    @Query("SELECT COUNT(DISTINCT placaCavalo) FROM checklists WHERE placaCavalo IS NOT NULL")
    suspend fun getCountVeiculosUnicos(): Int

    /**
     * 🔥 FIREBASE: Conta motoristas únicos
     * Para estatísticas de equipe
     */
    @Query("SELECT COUNT(DISTINCT motoristaCpf) FROM checklists WHERE motoristaCpf IS NOT NULL")
    suspend fun getCountMotoristasUnicos(): Int

    /**
     * 🔥 FIREBASE: Busca checklists com problemas críticos OEA
     * Para alertas de segurança
     */
    @Query("""
        SELECT DISTINCT c.* FROM checklists c
        INNER JOIN itens_checklist i ON c.id = i.checklistId
        WHERE i.status = 'NÃO' 
        AND i.numeroItem IN (2, 3, 5, 7, 8, 9, 11, 20, 21, 22, 23, 24, 25)
        ORDER BY c.data DESC
        LIMIT 20
    """)
    suspend fun getChecklistsComProblemasCriticos(): List<Checklist>

    /**
     * 🔥 FIREBASE: Conta problemas críticos por período
     * Para análise de tendências
     */
    @Query("""
        SELECT COUNT(DISTINCT c.id) FROM checklists c
        INNER JOIN itens_checklist i ON c.id = i.checklistId
        WHERE i.status = 'NÃO' 
        AND i.numeroItem IN (2, 3, 5, 7, 8, 9, 11, 20, 21, 22, 23, 24, 25)
        AND c.data BETWEEN :dataInicio AND :dataFim
    """)
    suspend fun getCountProblemasCriticosPorPeriodo(dataInicio: Long, dataFim: Long): Int

    /**
     * 🔥 FIREBASE: Calcula taxa de conformidade geral
     * Para KPIs do dashboard
     */
    @Query("""
        SELECT 
            COUNT(*) as total,
            CAST(SUM(CASE 
                WHEN (SELECT COUNT(*) FROM itens_checklist WHERE checklistId = c.id AND status = 'SIM') * 100.0 / 
                     (SELECT COUNT(*) FROM itens_checklist WHERE checklistId = c.id) >= 90 
                THEN 1 ELSE 0 
            END) as INTEGER) as conformes
        FROM checklists c
        WHERE c.data BETWEEN :dataInicio AND :dataFim
    """)
    suspend fun getEstatisticasConformidade(dataInicio: Long, dataFim: Long): ConformidadeStats

    /**
     * 🔥 FIREBASE: Lista todos os veículos únicos com último checklist
     * Para gestão de frota
     */
    @Query("""
        SELECT DISTINCT 
            placaCavalo, 
            placaCarreta,
            MAX(data) as ultimoChecklist,
            (SELECT motoristaName FROM checklists c2 
             WHERE c2.placaCavalo = c1.placaCavalo 
             ORDER BY c2.data DESC LIMIT 1) as ultimoMotorista
        FROM checklists c1 
        WHERE placaCavalo IS NOT NULL 
        GROUP BY placaCavalo, placaCarreta
        ORDER BY ultimoChecklist DESC
    """)
    suspend fun getResumoFrota(): List<VeiculoResumo>

    /**
     * 🔥 FIREBASE: Peso total transportado por período
     * Para estatísticas logísticas
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN pesoBruto IS NOT NULL AND pesoBruto != '' 
                THEN CAST(REPLACE(REPLACE(pesoBruto, '.', ''), ',', '') AS INTEGER)
                ELSE 20000 
            END
        ), 0) as pesoTotal
        FROM checklists 
        WHERE data BETWEEN :dataInicio AND :dataFim
    """)
    suspend fun getPesoTotalPorPeriodo(dataInicio: Long, dataFim: Long): Long

    /**
     * 🔥 FIREBASE: Conta itens com problema por categoria
     * Para análise de tipos de problemas
     */
    @Query("""
        SELECT COUNT(*) FROM itens_checklist i
        INNER JOIN checklists c ON i.checklistId = c.id
        WHERE i.status = 'NÃO' 
        AND i.numeroItem BETWEEN :numeroInicio AND :numeroFim
        AND c.data BETWEEN :dataInicio AND :dataFim
    """)
    suspend fun getProblemasPorCategoria(
        numeroInicio: Int,
        numeroFim: Int,
        dataInicio: Long,
        dataFim: Long
    ): Int
}

/**
 * 🔥 FIREBASE: Data class para estatísticas de conformidade
 */
data class ConformidadeStats(
    val total: Int,
    val conformes: Int
) {
    val taxaConformidade: Double
        get() = if (total > 0) (conformes.toDouble() / total) * 100 else 0.0
}

/**
 * 🔥 FIREBASE: Data class para resumo de veículos
 */
data class VeiculoResumo(
    val placaCavalo: String?,
    val placaCarreta: String?,
    val ultimoChecklist: Long,
    val ultimoMotorista: String?
)