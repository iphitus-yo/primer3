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

    @Query("SELECT * FROM checklists WHERE emailEnviado = 0")
    suspend fun getChecklistsNaoEnviados(): List<Checklist>

    @Query("UPDATE checklists SET emailEnviado = 1, pdfPath = :pdfPath WHERE id = :id")
    suspend fun marcarComoEnviado(id: Long, pdfPath: String)

    @Query("""
        SELECT id, motoristaCpf, motoristaName, data, placaCavalo, placaCarreta, 
        crtMicDue, nLacre, pesoBruto, statusEntrada, statusSaida, statusPernoite,
        statusParada, emailEnviado, pdfPath, localColeta
        FROM checklists 
        WHERE pdfPath IS NOT NULL AND emailEnviado = 0
    """)
    suspend fun getChecklistsComPdfSemEmail(): List<Checklist>

    @Transaction
    suspend fun criarChecklistComItens(checklist: Checklist, itens: List<ItemChecklist>) {
        val id = insertChecklist(checklist)
        val itensComId = itens.map { it.copy(checklistId = id) }
        insertItensChecklist(itensComId)
    }
}