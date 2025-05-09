package com.paranalog.truckcheck.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.paranalog.truckcheck.services.NotificationService

/**
 * Receiver para processar alarmes e mostrar notificações
 */
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            NotificationService.EXTRA_NOTIFICATION_ID,
            NotificationService.NOTIFICATION_ID_MORNING
        )

        val title = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_TITLE)
            ?: "Lembrete de Checklist"

        val message = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_MESSAGE)
            ?: "É hora de fazer o checklist do seu veículo!"

        // Mostrar a notificação
        val notificationService = NotificationService(context)
        notificationService.showNotification(notificationId, title, message)
    }
}