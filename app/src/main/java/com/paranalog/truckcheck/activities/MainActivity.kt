package com.paranalog.truckcheck.activities

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.adapters.ChecklistAdapter
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.databinding.ActivityMainBinding
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.activities.PerfilActivity
import com.paranalog.truckcheck.activities.ManualActivity
import com.paranalog.truckcheck.services.NotificationService
import com.paranalog.truckcheck.utils.ChecklistUpdater
import com.paranalog.truckcheck.utils.EmailPendingManager
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var checklistAdapter: ChecklistAdapter
    private var allChecklists = listOf<Checklist>()

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)

        // Verificar se usuário está logado
        if (!sharedPrefsManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Configurar notificações
        setupNotifications()

        // Configurar Toolbar
        setSupportActionBar(binding.toolbar)

        // Configurar RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        checklistAdapter = ChecklistAdapter(this, emptyList()) { checklist ->
            // Abrir detalhes do checklist
            val intent = Intent(this, ChecklistActivity::class.java).apply {
                putExtra("CHECKLIST_ID", checklist.id)
            }
            startActivity(intent)
        }
        binding.recyclerView.adapter = checklistAdapter

        // Configurar FAB
        binding.fabNovoChecklist.setOnClickListener {
            startActivity(Intent(this, ChecklistActivity::class.java))
        }

        // Configurar filtros
        binding.chipTodos.setOnClickListener { filtrarChecklists("todos") }
        binding.chipPendentes.setOnClickListener { filtrarChecklists("pendentes") }
        binding.chipHoje.setOnClickListener { filtrarChecklists("hoje") }

        // Configurar perfil
        binding.ivPerfil.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        // Busca
        binding.tilBusca.setEndIconOnClickListener {
            val busca = binding.etBusca.text.toString()
            if (busca.isNotEmpty()) {
                filtrarPorBusca(busca)
            } else {
                checklistAdapter.updateList(allChecklists)
            }
        }

        // Carregar checklists
        carregarChecklists()
    }

    private fun setupNotifications() {
        // Verificar e solicitar permissões para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Solicitando permissão de notificação para Android 13+")

                // Mostrar diálogo explicativo antes de solicitar permissão
                AlertDialog.Builder(this)
                    .setTitle("Permissão de Notificações")
                    .setMessage("Para receber lembretes no horário correto e informações importantes sobre seus checklists, precisamos enviar notificações.")
                    .setPositiveButton("Configurar") { _, _ ->
                        // Solicitar permissão
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_NOTIFICATION_PERMISSION
                        )
                    }
                    .setNegativeButton("Depois", null)
                    .show()

                return // Sair do método e deixar onRequestPermissionsResult finalizar a configuração
            }
        }

        // Verificar permissão para alarmes exatos no Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.d(TAG, "Sem permissão para agendar alarmes exatos no Android 12+")
                // Informar o usuário e abrir configurações
                AlertDialog.Builder(this)
                    .setTitle("Permissão necessária")
                    .setMessage("Para receber lembretes nos horários corretos, o aplicativo precisa de permissão para agendar alarmes exatos.")
                    .setPositiveButton("Configurar") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao abrir configurações de alarme", e)
                            Toast.makeText(this, "Vá para Configurações > Apps > TruckCheck > Permissões e ative 'Alarmes exatos'", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Depois", null)
                    .show()

                // Ainda deve agendar as notificações, mesmo que não sejam exatas
            }
        }

        // Agendar notificações
        Log.d(TAG, "Agendando notificações: manhã=${sharedPrefsManager.getNotificacaoManhaAtivada()}, " +
                "tarde=${sharedPrefsManager.getNotificacaoTardeAtivada()}, " +
                "noite=${sharedPrefsManager.getNotificacaoNoiteAtivada()}")

        val notificationService = NotificationService(this)
        notificationService.scheduleNotifications(
            sharedPrefsManager.getNotificacaoManhaAtivada(),
            sharedPrefsManager.getNotificacaoTardeAtivada(),
            sharedPrefsManager.getNotificacaoNoiteAtivada()
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissão de notificação concedida, configurando notificações")
                // Permissão concedida, continuar com a configuração
                setupNotifications()
            } else {
                Log.d(TAG, "Permissão de notificação negada")
                // Permissão negada, informar o usuário
                Toast.makeText(
                    this,
                    "Sem permissão para notificações, você não receberá lembretes de checklist",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Recarregar dados ao voltar para esta tela
        carregarChecklists()

        // Verificar emails pendentes quando a activity é resumida
        val emailPendingManager = EmailPendingManager(this)
        emailPendingManager.verificarEnviosPendentes(this)

        // Verificar se há checklists com localizações pendentes para atualizar
        val checklistUpdater = ChecklistUpdater(this)
        checklistUpdater.verificarEAtualizarChecklistsPendentes(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_manual -> {
                // Abrir manual do app
                val intent = Intent(this, ManualActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_refresh -> {
                carregarChecklists()
                true
            }
            R.id.action_logout -> {
                sharedPrefsManager.logout()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun carregarChecklists() {
        lifecycleScope.launch {
            try {
                // Mostrar indicador de carregamento
                // Removido progressBar pois não existe no binding
                binding.tvEmpty.visibility = View.GONE

                val cpf = sharedPrefsManager.getUsuarioCpf()
                val db = AppDatabase.getDatabase(this@MainActivity)

                // Carregar todos os checklists do motorista
                allChecklists = withContext(Dispatchers.IO) {
                    val checklists = db.checklistDao().getChecklistsByMotorista(cpf)

                    // Log para debug
                    Log.d(TAG, "Carregados ${checklists.size} checklists")
                    checklists.forEach { checklist ->
                        Log.d(TAG, "Checklist #${checklist.id} - CRT: ${checklist.crtMicDue ?: "N/A"} - " +
                                "Placas: ${checklist.placaCavalo} / ${checklist.placaCarreta}")
                    }

                    // Verificar se há algum checklist com placas nulas ou vazias
                    val checklistsComProblema = checklists.filter {
                        it.placaCavalo.isNullOrEmpty() || it.placaCarreta.isNullOrEmpty()
                    }

                    if (checklistsComProblema.isNotEmpty()) {
                        Log.w(TAG, "${checklistsComProblema.size} checklists com problemas de placas:")
                        checklistsComProblema.forEach {
                            Log.w(TAG, "  Checklist #${it.id} - CRT: ${it.crtMicDue ?: "N/A"}")
                        }
                    }

                    // Verificar se há algum problema com checklists de CRT com valor alto
                    val checklistsCrtAlto = checklists.filter {
                        try {
                            val crtNum = it.crtMicDue?.toIntOrNull() ?: 0
                            crtNum > 1000  // considere um valor alto para teste
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (checklistsCrtAlto.isNotEmpty()) {
                        Log.w(TAG, "${checklistsCrtAlto.size} checklists com CRT alto:")
                        checklistsCrtAlto.forEach {
                            Log.w(TAG, "  Checklist #${it.id} - CRT: ${it.crtMicDue ?: "N/A"} - " +
                                    "Placas: ${it.placaCavalo} / ${it.placaCarreta}")
                        }
                    }

                    // Garantir que nenhum checklist tenha placas nulas
                    val checklistsCorrigidos = checklists.map { checklist ->
                        if (checklist.placaCavalo.isNullOrEmpty() || checklist.placaCarreta.isNullOrEmpty()) {
                            // Buscar o checklist completo diretamente do banco para tentar recuperar as placas
                            try {
                                val checklistCompleto = db.checklistDao().getChecklistById(checklist.id)
                                if (checklistCompleto != null &&
                                    (!checklistCompleto.placaCavalo.isNullOrEmpty() ||
                                            !checklistCompleto.placaCarreta.isNullOrEmpty())) {
                                    Log.d(TAG, "Recuperadas placas para checklist #${checklist.id}: " +
                                            "${checklistCompleto.placaCavalo} / ${checklistCompleto.placaCarreta}")
                                    checklistCompleto
                                } else {
                                    // Se ainda não conseguiu recuperar, usar valores padrão temporários
                                    Log.w(TAG, "Usando placas padrão para checklist #${checklist.id}")
                                    checklist.copy(
                                        placaCavalo = checklist.placaCavalo ?: "Não disponível",
                                        placaCarreta = checklist.placaCarreta ?: "Não disponível"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao recuperar dados do checklist #${checklist.id}", e)
                                checklist.copy(
                                    placaCavalo = checklist.placaCavalo ?: "Não disponível",
                                    placaCarreta = checklist.placaCarreta ?: "Não disponível"
                                )
                            }
                        } else {
                            checklist
                        }
                    }

                    checklistsCorrigidos
                }

                // Atualizar UI
                // Removido progressBar pois não existe no binding

                if (allChecklists.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    checklistAdapter.updateList(allChecklists)
                }
            } catch (e: Exception) {
                // Tratar erro ao carregar checklists
                // Removido progressBar pois não existe no binding

                Log.e(TAG, "Erro ao carregar checklists", e)
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.tvEmpty.text = "Erro ao carregar checklists: ${e.message}"
            }
        }
    }

    private fun filtrarChecklists(filtro: String) {
        val filteredList = when (filtro) {
            "pendentes" -> allChecklists.filter { !it.emailEnviado }
            "hoje" -> {
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                allChecklists.filter { checklist ->
                    val checklistDate = Calendar.getInstance().apply {
                        time = checklist.data
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

                    checklistDate == today
                }
            }
            else -> allChecklists
        }

        checklistAdapter.updateList(filteredList)
    }

    private fun filtrarPorBusca(busca: String) {
        val termoBusca = busca.lowercase(Locale.getDefault())
        val filteredList = allChecklists.filter { checklist ->
            (checklist.placaCavalo?.lowercase(Locale.getDefault())?.contains(termoBusca) == true) ||
                    (checklist.placaCarreta?.lowercase(Locale.getDefault())?.contains(termoBusca) == true) ||
                    (checklist.nLacre?.lowercase(Locale.getDefault())?.contains(termoBusca) == true) ||
                    (checklist.motoristaName.lowercase(Locale.getDefault()).contains(termoBusca)) ||
                    (checklist.crtMicDue?.lowercase(Locale.getDefault())?.contains(termoBusca) == true)
        }

        checklistAdapter.updateList(filteredList)
    }
}