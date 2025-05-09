package com.paranalog.truckcheck.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paranalog.truckcheck.services.NotificationService
import com.paranalog.truckcheck.services.SyncService
import com.paranalog.truckcheck.utils.SharedPrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Iniciar o serviço de sincronização após o boot
            val syncIntent = Intent(context, SyncService::class.java)
            context.startService(syncIntent)

            // Verificar se o usuário está logado
            val sharedPrefs = SharedPrefsManager(context)

            if (sharedPrefs.isLoggedIn()) {
                // Reagendar notificações
                val notificationService = NotificationService(context)

                notificationService.scheduleNotifications(
                    sharedPrefs.getNotificacaoManhaAtivada(),
                    sharedPrefs.getNotificacaoTardeAtivada(),
                    sharedPrefs.getNotificacaoNoiteAtivada()
                )
            }
        }
    }
}