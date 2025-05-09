package com.paranalog.truckcheck.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey
    val cpf: String,
    val nome: String,
    val senha: String,
    val placaCavalo: String,
    val placaCarreta: String
)