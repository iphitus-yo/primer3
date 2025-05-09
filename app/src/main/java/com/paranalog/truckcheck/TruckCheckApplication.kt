package com.paranalog.truckcheck

import android.app.Application
import androidx.multidex.MultiDexApplication
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.paranalog.truckcheck.workers.ChecklistSyncWorker
import java.util.concurrent.TimeUnit

class TruckCheckApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // Configurar trabalho periódico de sincronização
        configurarSincronizacaoPeriodica()
    }

    private fun configurarSincronizacaoPeriodica() {
        // Configurar restrições - executar apenas quando houver conexão
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Criar trabalho periódico (15 minutos é o mínimo permitido)
        val syncRequest = PeriodicWorkRequestBuilder<ChecklistSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Agendar o trabalho periódico
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "checklist_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}