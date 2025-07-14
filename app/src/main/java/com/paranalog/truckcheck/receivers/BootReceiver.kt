package com.paranalog.truckcheck.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.paranalog.truckcheck.services.NotificationService
import com.paranalog.truckcheck.services.SyncService
import com.paranalog.truckcheck.utils.SharedPrefsManager

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot receiver acionado: ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Dispositivo inicializado, reagendando notificações")

            // Iniciar o serviço de sincronização após o boot
            val syncIntent = Intent(context, SyncService::class.java)
            context.startService(syncIntent)

            // Verificar se o usuário está logado
            val sharedPrefs = SharedPrefsManager(context)

            if (sharedPrefs.isLoggedIn()) {
                // Reagendar notificações
                val notificationService = NotificationService(context)

                Log.d(TAG, "Reagendando notificações após boot: " +
                        "manhã=${sharedPrefs.getNotificacaoManhaAtivada()}, " +
                        "tarde=${sharedPrefs.getNotificacaoTardeAtivada()}, " +
                        "noite=${sharedPrefs.getNotificacaoNoiteAtivada()}")

                notificationService.scheduleNotifications(
                    sharedPrefs.getNotificacaoManhaAtivada(),
                    sharedPrefs.getNotificacaoTardeAtivada(),
                    sharedPrefs.getNotificacaoNoiteAtivada()
                )

                Log.d(TAG, "Notificações reagendadas com sucesso após inicialização do dispositivo")
            } else {
                Log.d(TAG, "Usuário não está logado, pulando reagendamento de notificações")
            }
        }
    }
}