package com.paranalog.truckcheck.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.utils.FirebaseManager

/**
 * 🔥 NetworkChangeReceiver - Paranálog TruckCheck
 * Detecta quando conexão de internet volta e tenta reenviar dados ao Firebase
 */
class NetworkChangeReceiver : BroadcastReceiver() {
    private val TAG = "NetworkChangeReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        try {
            // Verificar se houve mudança na conectividade
            if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                Log.d(TAG, "Mudança de conectividade detectada")

                // Verificar se tem internet agora
                if (isInternetAvailable(context)) {
                    Log.d(TAG, "🔥 Internet disponível - tentando reenvios Firebase")

                    // Tentar reenviar dados pendentes ao Firebase em background
                    tentarReenviarFirebaseBackground(context)
                } else {
                    Log.d(TAG, "❌ Sem internet - aguardando conexão")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no NetworkChangeReceiver: ${e.message}")
        }
    }

    /**
     * Verifica se há conexão com internet
     */
    private fun isInternetAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo != null && networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar internet: ${e.message}")
            false
        }
    }

    /**
     * 🔥 FIREBASE: Tenta reenviar dados pendentes ao Firebase em background
     */
    private fun tentarReenviarFirebaseBackground(context: Context) {
        // Usar CoroutineScope para não bloquear o receiver
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firebaseManager = FirebaseManager(context)
                val db = AppDatabase.getDatabase(context)

                // Buscar até 3 checklists recentes para tentar reenviar
                val checklists = db.checklistDao().getChecklistsRecentes(3)

                if (checklists.isEmpty()) {
                    Log.d(TAG, "🔥 Firebase: Nenhum checklist para reenviar")
                    return@launch
                }

                Log.d(TAG, "🔥 Firebase: Tentando reenviar ${checklists.size} checklists")

                var sucessos = 0

                // Tentar reenviar cada checklist
                checklists.forEach { checklist ->
                    try {
                        // Buscar itens do checklist
                        val itens = db.checklistDao().getItensByChecklistId(checklist.id)

                        if (itens.isNotEmpty()) {
                            // Tentar enviar para Firebase (com timeout)
                            val sucesso = firebaseManager.enviarChecklistParaFirebase(checklist, itens)

                            if (sucesso) {
                                sucessos++
                                Log.d(TAG, "✅ Firebase: Reenvio bem-sucedido para checklist ${checklist.id}")
                            } else {
                                Log.w(TAG, "❌ Firebase: Falha no reenvio do checklist ${checklist.id}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "🔥 Firebase: Erro ao reenviar checklist ${checklist.id}: ${e.message}")
                    }
                }

                Log.d(TAG, "🔥 Firebase: Reenvio concluído - $sucessos de ${checklists.size} enviados")

            } catch (e: Exception) {
                Log.e(TAG, "🔥 Firebase: Erro geral no reenvio automático: ${e.message}")
            }
        }
    }
}