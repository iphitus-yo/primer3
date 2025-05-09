package com.paranalog.truckcheck.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class SharedPrefsManager(context: Context) {
    private val TAG = "SharedPrefsManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("truck_check_prefs", Context.MODE_PRIVATE)

    companion object {
        // Chaves para preferências
        private const val KEY_USUARIO_LOGADO = "usuario_logado"
        private const val KEY_USUARIO_CPF = "usuario_cpf"
        private const val KEY_USUARIO_NOME = "usuario_nome"
        private const val KEY_PLACA_CAVALO = "placa_cavalo"
        private const val KEY_PLACA_CARRETA = "placa_carreta"
        private const val KEY_NOTIFICACAO_MANHA = "notificacao_manha"
        private const val KEY_NOTIFICACAO_TARDE = "notificacao_tarde"
        private const val KEY_NOTIFICACAO_NOITE = "notificacao_noite"
        private const val KEY_HAS_BIOMETRIC = "has_biometric"
        private const val KEY_BIOMETRIC_CPF = "biometric_cpf"
        private const val KEY_BIOMETRIC_SENHA = "biometric_senha"
        private const val KEY_NOTIFICACAO_MANHA_ENABLED = "notificacao_manha_enabled"
        private const val KEY_NOTIFICACAO_TARDE_ENABLED = "notificacao_tarde_enabled"
        private const val KEY_NOTIFICACAO_NOITE_ENABLED = "notificacao_noite_enabled"
        private const val KEY_ENVIO_AUTOMATICO_EMAILS = "envio_automatico_emails"
    }

    fun salvarDadosUsuario(cpf: String, nome: String, placaCavalo: String, placaCarreta: String) {
        Log.d(TAG, "Salvando dados do usuário: Nome=$nome, CPF=$cpf, Cavalo=$placaCavalo, Carreta=$placaCarreta")
        prefs.edit().apply {
            putBoolean(KEY_USUARIO_LOGADO, true)
            putString(KEY_USUARIO_CPF, cpf)
            putString(KEY_USUARIO_NOME, nome)
            putString(KEY_PLACA_CAVALO, placaCavalo)
            putString(KEY_PLACA_CARRETA, placaCarreta)
            apply()
        }
    }

    fun atualizarDadosVeiculos(placaCavalo: String, placaCarreta: String) {
        Log.d(TAG, "Atualizando dados dos veículos: Cavalo=$placaCavalo, Carreta=$placaCarreta")
        prefs.edit().apply {
            putString(KEY_PLACA_CAVALO, placaCavalo)
            putString(KEY_PLACA_CARRETA, placaCarreta)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        val logado = prefs.getBoolean(KEY_USUARIO_LOGADO, false)
        Log.d(TAG, "Verificando se está logado: $logado")
        return logado
    }

    fun getUsuarioCpf(): String {
        val cpf = prefs.getString(KEY_USUARIO_CPF, "") ?: ""
        Log.d(TAG, "Recuperando CPF do usuário: $cpf")
        return cpf
    }

    fun getUsuarioNome(): String {
        val nome = prefs.getString(KEY_USUARIO_NOME, "") ?: ""
        Log.d(TAG, "Recuperando nome do usuário: $nome")
        return nome
    }

    fun getPlacaCavalo(): String {
        val placaCavalo = prefs.getString(KEY_PLACA_CAVALO, "") ?: ""
        Log.d(TAG, "Recuperando placa do cavalo: $placaCavalo")
        return placaCavalo
    }

    fun getPlacaCarreta(): String {
        val placaCarreta = prefs.getString(KEY_PLACA_CARRETA, "") ?: ""
        Log.d(TAG, "Recuperando placa da carreta: $placaCarreta")
        return placaCarreta
    }

    // Métodos para notificações (ativação/desativação)
    fun setNotificacaoManha(enabled: Boolean) {
        Log.d(TAG, "Configurando notificação da manhã: $enabled")
        prefs.edit().putBoolean(KEY_NOTIFICACAO_MANHA_ENABLED, enabled).apply()
    }

    fun setNotificacaoTarde(enabled: Boolean) {
        Log.d(TAG, "Configurando notificação da tarde: $enabled")
        prefs.edit().putBoolean(KEY_NOTIFICACAO_TARDE_ENABLED, enabled).apply()
    }

    fun setNotificacaoNoite(enabled: Boolean) {
        Log.d(TAG, "Configurando notificação da noite: $enabled")
        prefs.edit().putBoolean(KEY_NOTIFICACAO_NOITE_ENABLED, enabled).apply()
    }

    fun getNotificacaoManhaAtivada(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICACAO_MANHA_ENABLED, false)
    }

    fun getNotificacaoTardeAtivada(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICACAO_TARDE_ENABLED, false)
    }

    fun getNotificacaoNoiteAtivada(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICACAO_NOITE_ENABLED, false)
    }

    // Métodos para horários de notificação
    fun salvarHorariosNotificacao(manha: String?, tarde: String?, noite: String?) {
        Log.d(TAG, "Salvando horários de notificação: Manhã=$manha, Tarde=$tarde, Noite=$noite")
        prefs.edit().apply {
            putString(KEY_NOTIFICACAO_MANHA, manha)
            putString(KEY_NOTIFICACAO_TARDE, tarde)
            putString(KEY_NOTIFICACAO_NOITE, noite)
            apply()
        }
    }

    fun getHorarioNotificacaoManha(): String? {
        return prefs.getString(KEY_NOTIFICACAO_MANHA, null)
    }

    fun getHorarioNotificacaoTarde(): String? {
        return prefs.getString(KEY_NOTIFICACAO_TARDE, null)
    }

    fun getHorarioNotificacaoNoite(): String? {
        return prefs.getString(KEY_NOTIFICACAO_NOITE, null)
    }

    // Biometric methods
    fun saveBiometricCredentials(cpf: String, senha: String) {
        Log.d(TAG, "Salvando credenciais biométricas para CPF: $cpf")
        prefs.edit().apply {
            putString(KEY_BIOMETRIC_CPF, cpf)
            putString(KEY_BIOMETRIC_SENHA, senha)
            putBoolean(KEY_HAS_BIOMETRIC, true)
            apply()
        }
    }

    fun hasBiometricCredentials(): Boolean {
        return prefs.getBoolean(KEY_HAS_BIOMETRIC, false)
    }

    fun getBiometricCpf(): String {
        return prefs.getString(KEY_BIOMETRIC_CPF, "") ?: ""
    }

    fun getBiometricSenha(): String {
        return prefs.getString(KEY_BIOMETRIC_SENHA, "") ?: ""
    }

    // Métodos adicionais para configuração de email
    fun setEnvioAutomaticoEmails(enabled: Boolean) {
        Log.d(TAG, "Configurando envio automático de emails: $enabled")
        prefs.edit().putBoolean(KEY_ENVIO_AUTOMATICO_EMAILS, enabled).apply()
    }

    fun getEnvioAutomaticoEmails(): Boolean {
        return prefs.getBoolean(KEY_ENVIO_AUTOMATICO_EMAILS, true) // Padrão: ativado
    }

    // Outros métodos
    fun setPlacaCavalo(placaCavalo: String) {
        Log.d(TAG, "Atualizando placa do cavalo: $placaCavalo")
        prefs.edit().putString(KEY_PLACA_CAVALO, placaCavalo).apply()
    }

    fun setPlacaCarreta(placaCarreta: String) {
        Log.d(TAG, "Atualizando placa da carreta: $placaCarreta")
        prefs.edit().putString(KEY_PLACA_CARRETA, placaCarreta).apply()
    }

    fun setSenha(senha: String) {
        Log.d(TAG, "Atualizando senha do usuário")
        // Como estamos apenas simulando, não precisamos realmente salvar a senha
        // Em um sistema real, você integraria isso com o banco de dados
    }

    fun logout() {
        Log.d(TAG, "Realizando logout do usuário")
        prefs.edit().apply {
            putBoolean(KEY_USUARIO_LOGADO, false)
            apply()
        }
    }
}