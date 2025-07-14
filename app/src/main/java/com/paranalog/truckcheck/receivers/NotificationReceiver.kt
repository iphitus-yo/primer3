package com.paranalog.truckcheck.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.paranalog.truckcheck.services.NotificationService

/**
 * Receiver para processar alarmes e mostrar notificações
 */
class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Log para debugging
        Log.d(TAG, "Alarme recebido: ${intent.action}")

        // Verificar qual é a ação para determinar o tipo de notificação
        val action = intent.action

        // Usar o ID da notificação correspondente baseado na ação
        val notificationId = when (action) {
            NotificationService.ACTION_MORNING_NOTIFICATION -> NotificationService.NOTIFICATION_ID_MORNING
            NotificationService.ACTION_AFTERNOON_NOTIFICATION -> NotificationService.NOTIFICATION_ID_AFTERNOON
            NotificationService.ACTION_EVENING_NOTIFICATION -> NotificationService.NOTIFICATION_ID_EVENING
            else -> intent.getIntExtra(
                NotificationService.EXTRA_NOTIFICATION_ID,
                NotificationService.NOTIFICATION_ID_MORNING
            )
        }

        // Obter o título da notificação do intent ou usar valor padrão
        val title = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_TITLE)
            ?: "Lembrete de Checklist"

        // Obter a mensagem da notificação do intent ou usar valor padrão
        val message = intent.getStringExtra(NotificationService.EXTRA_NOTIFICATION_MESSAGE)
            ?: "É hora de fazer o checklist do seu veículo!"

        Log.d(TAG, "Mostrando notificação: ID=$notificationId, Título=$title")

        // Mostrar a notificação
        val notificationService = NotificationService(context)
        notificationService.showNotification(notificationId, title, message)
    }
}