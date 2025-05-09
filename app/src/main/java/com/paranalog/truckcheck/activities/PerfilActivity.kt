package com.paranalog.truckcheck.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.databinding.ActivityPerfilBinding
import com.paranalog.truckcheck.services.NotificationService
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.launch

class PerfilActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPerfilBinding
    private lateinit var sharedPrefs: SharedPrefsManager
    private lateinit var notificationService: NotificationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inicializar serviços
        sharedPrefs = SharedPrefsManager(this)
        notificationService = NotificationService(this)

        // Carregar dados
        setupUsuarioInfo()
        setupEstatisticas()

        // Configurar controles
        setupVeiculoControls()
        setupSenhaControls()
        setupNotificacoes()
    }

    /**
     * Carrega as informações do usuário no perfil
     */
    private fun setupUsuarioInfo() {
        binding.tvNome.text = sharedPrefs.getUsuarioNome()
        binding.tvCpf.text = sharedPrefs.getUsuarioCpf()
        binding.tvPlacaCavalo.text = sharedPrefs.getPlacaCavalo()
        binding.tvPlacaCarreta.text = sharedPrefs.getPlacaCarreta()

        // Pré-preencher campos de edição
        binding.editPlacaCavalo.setText(sharedPrefs.getPlacaCavalo())
        binding.editPlacaCarreta.setText(sharedPrefs.getPlacaCarreta())
    }

    /**
     * Configura as estatísticas do usuário
     * Você pode adaptar para usar dados reais do seu banco
     */
    private fun setupEstatisticas() {
        // Preencha com dados reais do seu banco de dados aqui
        binding.tvTotalChecklists.text = "0"
        binding.tvProblemasReportados.text = "0"
        binding.tvChecklistsPendentes.text = "0"
    }

    /**
     * Configura os controles de edição de veículo
     */
    private fun setupVeiculoControls() {
        // Configurar botão de expandir/recolher
        binding.btnExpandVeiculo.setOnClickListener {
            toggleVeiculoSection()
        }

        // Configurar botão salvar
        binding.btnSalvarPlacas.setOnClickListener {
            salvarPlacas()
        }
    }

    /**
     * Expande ou recolhe a seção de veículo
     */
    private fun toggleVeiculoSection() {
        val isVisible = binding.layoutVeiculoContent.visibility == View.VISIBLE
        binding.layoutVeiculoContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.btnExpandVeiculo.setImageResource(
            if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
        )
    }

    /**
     * Salva as alterações das placas
     */
    private fun salvarPlacas() {
        val placaCavalo = binding.editPlacaCavalo.text.toString().trim().uppercase()
        val placaCarreta = binding.editPlacaCarreta.text.toString().trim().uppercase()

        // Validar placas
        if (placaCavalo.isEmpty() || placaCarreta.isEmpty()) {
            Toast.makeText(this, "Preencha todas as placas", Toast.LENGTH_SHORT).show()
            return
        }

        // Salvar nas preferências
        sharedPrefs.setPlacaCavalo(placaCavalo)
        sharedPrefs.setPlacaCarreta(placaCarreta)

        // Atualizar exibição
        binding.tvPlacaCavalo.text = placaCavalo
        binding.tvPlacaCarreta.text = placaCarreta

        // Fechar seção e mostrar feedback
        toggleVeiculoSection()
        Toast.makeText(this, "Placas atualizadas com sucesso", Toast.LENGTH_SHORT).show()
    }

    /**
     * Configura os controles de edição de senha
     */
    private fun setupSenhaControls() {
        // Configurar botão de expandir/recolher
        binding.btnExpandSenha.setOnClickListener {
            toggleSenhaSection()
        }

        // Configurar botão alterar senha
        binding.btnAlterarSenha.setOnClickListener {
            alterarSenha()
        }
    }

    /**
     * Expande ou recolhe a seção de senha
     */
    private fun toggleSenhaSection() {
        val isVisible = binding.layoutSenhaContent.visibility == View.VISIBLE
        binding.layoutSenhaContent.visibility = if (isVisible) View.GONE else View.VISIBLE
        binding.btnExpandSenha.setImageResource(
            if (isVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less
        )
    }

    /**
     * Altera a senha do usuário
     */
    private fun alterarSenha() {
        val senhaAtual = binding.editSenhaAtual.text.toString()
        val novaSenha = binding.editNovaSenha.text.toString()
        val confirmarSenha = binding.editConfirmarSenha.text.toString()

        // Validar campos
        if (senhaAtual.isEmpty() || novaSenha.isEmpty() || confirmarSenha.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return
        }

        if (novaSenha != confirmarSenha) {
            Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
            return
        }

        if (novaSenha.length < 6) {
            Toast.makeText(this, "A senha deve ter pelo menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        // Em um sistema real, aqui você verificaria a senha atual contra o banco de dados
        // Para este exemplo, vamos apenas salvar a nova senha
        sharedPrefs.setSenha(novaSenha)

        // Limpar campos
        binding.editSenhaAtual.text?.clear()
        binding.editNovaSenha.text?.clear()
        binding.editConfirmarSenha.text?.clear()

        // Fechar seção e mostrar feedback
        toggleSenhaSection()
        Toast.makeText(this, "Senha alterada com sucesso", Toast.LENGTH_SHORT).show()
    }

    /**
     * Configura os controles de notificações
     */
    private fun setupNotificacoes() {
        // Carregar preferências salvas
        binding.switchManha.isChecked = sharedPrefs.getNotificacaoManhaAtivada()
        binding.switchTarde.isChecked = sharedPrefs.getNotificacaoTardeAtivada()
        binding.switchNoite.isChecked = sharedPrefs.getNotificacaoNoiteAtivada()

        // Configurar listeners
        binding.switchManha.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.setNotificacaoManha(isChecked)
            atualizarNotificacoes()
        }

        binding.switchTarde.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.setNotificacaoTarde(isChecked)
            atualizarNotificacoes()
        }

        binding.switchNoite.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.setNotificacaoNoite(isChecked)
            atualizarNotificacoes()
        }

        // Inicializar notificações
        lifecycleScope.launch {
            atualizarNotificacoes()
        }
    }

    /**
     * Atualiza as notificações com base nas preferências
     */
    private fun atualizarNotificacoes() {
        notificationService.scheduleNotifications(
            sharedPrefs.getNotificacaoManhaAtivada(),
            sharedPrefs.getNotificacaoTardeAtivada(),
            sharedPrefs.getNotificacaoNoiteAtivada()
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}