package com.paranalog.truckcheck.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "itens_checklist",
    foreignKeys = [
        ForeignKey(
            entity = Checklist::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("checklistId")]
)
data class ItemChecklist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val checklistId: Long,
    val numeroItem: Int,
    val descricao: String,
    val status: String, // SIM, NÃO, N/A
    val comentario: String? = null,
    val fotoPath: String? = null
)