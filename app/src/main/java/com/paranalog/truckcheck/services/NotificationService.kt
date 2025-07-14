package com.paranalog.truckcheck.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.activities.ChecklistActivity
import com.paranalog.truckcheck.receivers.NotificationReceiver
import java.util.Calendar

/**
 * Serviço para gerenciar notificações push de lembretes de checklist
 * Agenda notificações diárias nos horários configurados pelo usuário
 */
class NotificationService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "checklist_reminders"
        private const val TAG = "NotificationService"

        // IDs das notificações
        const val NOTIFICATION_ID_MORNING = 1001
        const val NOTIFICATION_ID_AFTERNOON = 1002
        const val NOTIFICATION_ID_EVENING = 1003

        // Ações para os receivers
        const val ACTION_MORNING_NOTIFICATION = "com.paranalog.truckcheck.MORNING_NOTIFICATION"
        const val ACTION_AFTERNOON_NOTIFICATION = "com.paranalog.truckcheck.AFTERNOON_NOTIFICATION"
        const val ACTION_EVENING_NOTIFICATION = "com.paranalog.truckcheck.EVENING_NOTIFICATION"

        // Extra para passar dados
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_NOTIFICATION_TITLE = "title"
        const val EXTRA_NOTIFICATION_MESSAGE = "message"
    }

    init {
        // Criar canal de notificação quando o serviço for instanciado
        createNotificationChannel()
    }

    /**
     * Cria o canal de notificação (necessário para Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Checklist"
            val descriptionText = "Notificações para lembrar de realizar o checklist do veículo"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Verifica se o aplicativo pode agendar alarmes exatos no Android 12+
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun canScheduleExactAlarms(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Agenda todas as notificações com base nas preferências
     */
    fun scheduleNotifications(morningEnabled: Boolean, afternoonEnabled: Boolean, eveningEnabled: Boolean) {
        // Cancelar todas as notificações agendadas primeiro
        cancelAllNotifications()

        // Log para debug
        Log.d(TAG, "Agendando notificações: manhã=$morningEnabled, tarde=$afternoonEnabled, noite=$eveningEnabled")

        // Agendar as notificações habilitadas
        if (morningEnabled) {
            scheduleMorningNotification()
        }

        if (afternoonEnabled) {
            scheduleAfternoonNotification()
        }

        if (eveningEnabled) {
            scheduleEveningNotification()
        }
    }

    /**
     * Cancela todas as notificações agendadas
     */
    private fun cancelAllNotifications() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancelar alarme da manhã
        val intentMorning = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MORNING_NOTIFICATION
        }
        val pendingIntentMorning = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_MORNING,
            intentMorning,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancelar alarme da tarde
        val intentAfternoon = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_AFTERNOON_NOTIFICATION
        }
        val pendingIntentAfternoon = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_AFTERNOON,
            intentAfternoon,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancelar alarme da noite
        val intentEvening = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_EVENING_NOTIFICATION
        }
        val pendingIntentEvening = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID_EVENING,
            intentEvening,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.cancel(pendingIntentMorning)
            alarmManager.cancel(pendingIntentAfternoon)
            alarmManager.cancel(pendingIntentEvening)
            Log.d(TAG, "Notificações anteriores canceladas")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao cancelar notificações anteriores", e)
        }
    }

    /**
     * Agenda a notificação da manhã (6:00)
     */
    private fun scheduleMorningNotification() {
        scheduleNotification(
            ACTION_MORNING_NOTIFICATION,
            NOTIFICATION_ID_MORNING,
            6, // 6:00 AM
            0,
            "Checklist Matinal",
            "Hora de fazer o checklist matinal do seu caminhão!"
        )
    }

    /**
     * Agenda a notificação da tarde (14:00)
     */
    private fun scheduleAfternoonNotification() {
        scheduleNotification(
            ACTION_AFTERNOON_NOTIFICATION,
            NOTIFICATION_ID_AFTERNOON,
            14, // 2:00 PM
            0,
            "Checklist da Tarde",
            "Já fez o checklist do seu veículo hoje? Segurança em primeiro lugar!"
        )
    }

    /**
     * Agenda a notificação da noite (20:00)
     */
    private fun scheduleEveningNotification() {
        scheduleNotification(
            ACTION_EVENING_NOTIFICATION,
            NOTIFICATION_ID_EVENING,
            20, // 8:00 PM
            0,
            "Checklist Noturno",
            "Antes de finalizar o dia, não esqueça do checklist do seu caminhão!"
        )
    }

    /**
     * Agenda uma notificação para um horário específico
     */
    private fun scheduleNotification(
        action: String,
        notificationId: Int,
        hour: Int,
        minute: Int,
        title: String,
        message: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_NOTIFICATION_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_MESSAGE, message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // Se o horário já passou hoje, agendar para amanhã
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Agendar o alarme de acordo com a versão do Android
        try {
            when {
                // Para Android 12+ (API 31+)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (canScheduleExactAlarms()) {
                        // Usar alarmes exatos se tiver permissão
                        Log.d(TAG, "Agendando notificação exata para $action em ${calendar.time}")
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // Fallback para alarmes inexatos
                        Log.d(TAG, "Agendando notificação inexata (sem permissão) para $action em ${calendar.time}")
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                }
                // Para Android 6-11
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    Log.d(TAG, "Agendando notificação com setExactAndAllowWhileIdle para $action em ${calendar.time}")
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                // Para versões mais antigas
                else -> {
                    Log.d(TAG, "Agendando notificação com setExact para $action em ${calendar.time}")
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao agendar notificação $action", e)
            // Fallback para alarmes normais se houver erro
            try {
                Log.d(TAG, "Tentando agendar notificação com método fallback para $action")
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao agendar notificação com método fallback para $action", e)
            }
        }
    }

    /**
     * Mostra uma notificação
     */
    fun showNotification(notificationId: Int, title: String, message: String) {
        // Intent para abrir a atividade quando o usuário tocar na notificação
        val intent = Intent(context, ChecklistActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "Criando notificação: ID=$notificationId, Título=$title")

        // Construir a notificação
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Padrão de vibração

        // Mostrar a notificação
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(notificationId, builder.build())
            Log.d(TAG, "Notificação exibida com sucesso: ID=$notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exibir notificação: ID=$notificationId", e)
        }
    }
}