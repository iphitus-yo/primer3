package com.paranalog.truckcheck.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paranalog.truckcheck.workers.ChecklistSyncWorker
import java.util.concurrent.TimeUnit

class SyncService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Agendar o worker para sincronização quando houver conexão com a internet
        agendarSincronizacao()

        // Service não precisa ficar rodando continuamente
        stopSelf()

        return START_NOT_STICKY
    }

    private fun agendarSincronizacao() {
        // Definir restrições - só executar quando houver conexão com a internet
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Criar a requisição de trabalho
        val syncRequest = OneTimeWorkRequestBuilder<ChecklistSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES) // Atrasar um pouco para não sobrecarregar
            .build()

        // Agendar o trabalho
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Este serviço não suporta vinculação
    }
}