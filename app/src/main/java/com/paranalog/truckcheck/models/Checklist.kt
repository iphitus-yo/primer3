package com.paranalog.truckcheck.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val motoristaCpf: String,
    val motoristaName: String,
    val data: Date,
    val placaCavalo: String,
    val placaCarreta: String,
    val crtMicDue: String?,
    val nLacre: String?,
    val pesoBruto: String?,
    val statusEntrada: Boolean,
    val statusSaida: Boolean,
    val statusPernoite: Boolean,
    val statusParada: Boolean,
    val emailEnviado: Boolean,
    val pdfPath: String?,
    val localColeta: String? = null
)