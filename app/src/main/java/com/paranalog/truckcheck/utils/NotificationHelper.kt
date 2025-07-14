package com.paranalog.truckcheck.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.activities.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "truck_check_channel"
        const val CHANNEL_NAME = "TruckCheck Lembretes"
        const val CHANNEL_DESCRIPTION = "Notificações para lembretes de checklist"

        // Inicializa canais de notificação na inicialização do app
        fun createNotificationChannels(context: Context) {
            // Cria canais de notificação para Android Oreo (API 26) e superior
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_HIGH // Alta importância para lembretes
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    importance
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    // Configurações adicionais do canal
                    enableVibration(true)
                    enableLights(true)
                }

                // Registra o canal no sistema
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    // Método para mostrar notificação de lembrete de checklist
    fun showChecklistReminder(title: String, message: String, notificationId: Int = 1) {
        // Intent para abrir o app quando a notificação for clicada
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Construir a notificação
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use seu ícone real
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Alta prioridade
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Remove quando clicada
            .setCategory(NotificationCompat.CATEGORY_REMINDER) // Categoria para lembretes
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Enviar a notificação
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Log de erro se a permissão foi negada
            }
        }
    }

    // Método para mostrar notificação de checklist atrasado
    fun showOverdueChecklistNotification(placa: String, notificationId: Int = 2) {
        val title = "Checklist Atrasado"
        val message = "O checklist para o veículo $placa está atrasado. Por favor, realize a inspeção."

        // Intent para abrir a activity de checklist
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Você pode adicionar extras ao intent para abrir diretamente a tela de checklist
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Construir a notificação com alta prioridade
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use seu ícone real
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // Enviar a notificação
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Log de erro se a permissão foi negada
            }
        }
    }
}