package com.paranalog.truckcheck.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.models.Checklist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChecklistUpdater(private val context: Context) {
    private val TAG = "ChecklistUpdater"

    // Verificar e atualizar checklists pendentes
    fun verificarEAtualizarChecklistsPendentes(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val locationManager = LocationManager(context)

                // Buscar checklists com "Obtendo localização..."
                val checklistsPendentes = withContext(Dispatchers.IO) {
                    db.checklistDao().getChecklistsComLocalizacaoPendente()
                }

                Log.d(TAG, "Encontrados ${checklistsPendentes.size} checklists com localização pendente")

                // Para cada checklist pendente, tentar atualizar localização
                checklistsPendentes.forEach { checklist ->
                    atualizarLocalizacaoChecklist(checklist, locationManager, db)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar checklists pendentes: ${e.message}", e)
            }
        }
    }

    private suspend fun atualizarLocalizacaoChecklist(
        checklist: Checklist,
        locationManager: LocationManager,
        db: AppDatabase
    ) {
        try {
            // Obter nova localização
            val novaLocalizacao = locationManager.getCurrentLocation()

            // Atualizar no banco de dados
            withContext(Dispatchers.IO) {
                db.checklistDao().updateLocalColeta(checklist.id, novaLocalizacao)
            }

            Log.d(TAG, "Localização atualizada para checklist #${checklist.id}: $novaLocalizacao")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar localização para checklist #${checklist.id}: ${e.message}", e)

            // Se falhar em obter localização, colocar valor padrão
            withContext(Dispatchers.IO) {
                db.checklistDao().updateLocalColeta(checklist.id, "Localização não disponível")
            }
        }
    }
}