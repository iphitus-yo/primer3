package com.paranalog.truckcheck.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paranalog.truckcheck.models.Usuario

@Dao
interface UsuarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usuario: Usuario)

    @Query("SELECT * FROM usuarios WHERE cpf = :cpf")
    suspend fun getUsuarioByCpf(cpf: String): Usuario?

    @Query("SELECT * FROM usuarios")
    suspend fun getAllUsuarios(): List<Usuario>

    @Query("DELETE FROM usuarios WHERE cpf = :cpf")
    suspend fun deleteByCpf(cpf: String)
}