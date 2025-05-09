package com.paranalog.truckcheck.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paranalog.truckcheck.database.dao.UsuarioDao
import com.paranalog.truckcheck.database.dao.ChecklistDao
import com.paranalog.truckcheck.models.Usuario
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist

@Database(
    entities = [Usuario::class, Checklist::class, ItemChecklist::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "truckcheck_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}