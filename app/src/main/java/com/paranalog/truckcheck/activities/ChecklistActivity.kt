package com.paranalog.truckcheck.activities

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.adapters.ChecklistItemAdapter
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.databinding.ActivityChecklistBinding
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist
import com.paranalog.truckcheck.utils.EmailSender
import com.paranalog.truckcheck.utils.FirebaseManager
import com.paranalog.truckcheck.utils.LocationManager
import com.paranalog.truckcheck.utils.PdfGenerator
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Versão DEFINITIVA - Sem travamentos
 *
 * Estratégia:
 * 1. NUNCA bloquear a UI
 * 2. Timeouts ULTRA agressivos
 * 3. Sempre finalizar, não importa o que aconteça
 * 4. Firebase 100% isolado e opcional
 * 5. Email tentado UMA vez com timeout curto
 * 6. Emails pendentes são enviados pelo EmailPendingManager na MainActivity
 *
 * O EmailPendingManager é executado em:
 * - MainActivity.onResume()
 * - Quando detecta conexão de internet
 * - Periodicamente em background
 */

class ChecklistActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private var localColeta: String? = null
    private lateinit var binding: ActivityChecklistBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var checklistItemAdapter: ChecklistItemAdapter
    private var checklistItems = mutableListOf<ItemChecklist>()
    private var editingChecklistId: Long = -1L
    private var currentPhotoPath: String? = null
    private var currentItemPosition: Int = -1
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    private val TAG = "ChecklistActivity"
    private var savedChecklistId: Long = -1L
    private var isSaving = false
    private var loadingDialog: Dialog? = null
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    // Firebase opcional
    private var firebaseManager: FirebaseManager? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_GALLERY = 2
        private const val REQUEST_CAMERA_PERMISSION = 3
        private const val REQUEST_STORAGE_PERMISSION = 4
        private const val REQUEST_LOCATION_PERMISSION = 5
        private const val REQUEST_NOTIFICATION_PERMISSION = 6
        private const val STATE_CURRENT_PHOTO_PATH = "state_current_photo_path"
        private const val STATE_CURRENT_ITEM_POSITION = "state_current_item_position"
        private const val STATE_EDITING_ID = "state_editing_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistBinding.inflate(layoutInflater)

        binding.loadingContainer.visibility = View.VISIBLE
        binding.mainContainer.visibility = View.GONE

        setContentView(binding.root)

        solicitarPermissaoNotificacao()

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleCameraResult()
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleGalleryResult(result.data)
            }
        }

        initializeComponents()
        setupScreen(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmarSaidaSemSalvar()
    }

    override fun onDestroy() {
        super.onDestroy()
        loadingDialog?.dismiss()
        loadingDialog = null
        System.gc()
    }

    private fun solicitarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun initializeComponents() {
        sharedPrefsManager = SharedPrefsManager(this)

        locationManager = LocationManager(this)

        try {
            firebaseManager = FirebaseManager(this)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase não disponível: ${e.message}")
            firebaseManager = null
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerViewItens.layoutManager = LinearLayoutManager(this)
        checklistItemAdapter = ChecklistItemAdapter(
            this,
            checklistItems,
            onFotoClick = { position ->
                currentItemPosition = position
                mostrarOpcoesFoto()
            },
            onStatusChanged = { position, status ->
                if (position < checklistItems.size) {
                    val item = checklistItems[position]
                    checklistItems[position] = item.copy(status = status)
                    atualizarProgresso()
                }
            },
            onComentarioChanged = { position, comentario ->
                if (position < checklistItems.size) {
                    val item = checklistItems[position]
                    checklistItems[position] = item.copy(comentario = comentario)
                }
            }
        )
        binding.recyclerViewItens.adapter = checklistItemAdapter

        verificarPermissoes()

        binding.chipTodos.setOnClickListener { filtrarItensPorCategoria("todos") }
        binding.chipMotor.setOnClickListener { filtrarItensPorCategoria("motor") }
        binding.chipPneus.setOnClickListener { filtrarItensPorCategoria("pneus") }
        binding.chipCabine.setOnClickListener { filtrarItensPorCategoria("cabine") }

        binding.fabSalvar.setOnClickListener {
            salvarChecklist()
        }

        setupPesoBrutoFormatter()
    }

    private fun setupPesoBrutoFormatter() {
        binding.etPesoBruto.addTextChangedListener(object : TextWatcher {
            var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return

                isFormatting = true

                val userInput = s.toString()
                val rawInput = userInput.replace(".", "")

                if (rawInput.isNotEmpty()) {
                    val formatted = formatPesoBruto(rawInput)
                    s.replace(0, s.length, formatted)
                }

                isFormatting = false
            }
        })

        val etPesoBrutoText = binding.etPesoBruto.text
        if (etPesoBrutoText?.isNotEmpty() == true) {
            val initialText = binding.etPesoBruto.text.toString()
            val rawInput = initialText.replace(".", "")
            if (rawInput.isNotEmpty()) {
                binding.etPesoBruto.setText(formatPesoBruto(rawInput))
            }
        }
    }

    private fun formatPesoBruto(input: String): String {
        val numericOnly = input.replace(Regex("[^0-9]"), "")
        if (numericOnly.isEmpty()) return ""

        val valor = numericOnly.toLongOrNull() ?: 0L
        val formatter = DecimalFormat("#,###")
        formatter.decimalFormatSymbols = DecimalFormatSymbols(Locale("pt", "BR"))

        return formatter.format(valor)
    }

    private fun setupScreen(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(this@ChecklistActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                    obterLocalizacaoAtual()
                } else {
                    ActivityCompat.requestPermissions(
                        this@ChecklistActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        REQUEST_LOCATION_PERMISSION
                    )
                }

                if (savedInstanceState != null) {
                    editingChecklistId = savedInstanceState.getLong(STATE_EDITING_ID, -1)
                    currentPhotoPath = savedInstanceState.getString(STATE_CURRENT_PHOTO_PATH)
                    currentItemPosition = savedInstanceState.getInt(STATE_CURRENT_ITEM_POSITION, -1)
                } else {
                    editingChecklistId = intent.getLongExtra("CHECKLIST_ID", -1)
                }

                if (editingChecklistId != -1L) {
                    supportActionBar?.title = "Editar Checklist"
                    carregarChecklistExistente(editingChecklistId)
                } else {
                    supportActionBar?.title = "Novo Checklist"
                    configurarNovoChecklist()
                }

                withContext(Dispatchers.Main) {
                    binding.loadingContainer.visibility = View.GONE
                    binding.mainContainer.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao configurar tela", e)
                withContext(Dispatchers.Main) {
                    binding.loadingContainer.visibility = View.GONE
                    binding.mainContainer.visibility = View.VISIBLE
                    Toast.makeText(this@ChecklistActivity,
                        "Erro ao carregar dados: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoadingDialog(message: String): Dialog {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_loading)
            .setCancelable(false)
            .create()

        dialog.show()

        val textView = dialog.findViewById<TextView>(R.id.textViewLoading)
        textView?.text = message

        return dialog
    }

    private fun obterLocalizacaoAtual() {
        lifecycleScope.launch {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this@ChecklistActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        this@ChecklistActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Sem permissão de localização")
                    localColeta = "Localização não disponível - Sem permissão"
                    return@launch
                }

                // Mostrar que está obtendo localização
                localColeta = "Obtendo localização..."

                // Verificar se o GPS está ativado
                val androidLocationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val gpsEnabled = androidLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

                if (!gpsEnabled) {
                    Log.w(TAG, "GPS desativado - tentando obter última localização conhecida")
                }

                // Usar timeout maior se estiver offline ou GPS desativado
                val timeout = if (gpsEnabled && verificarConectividadeBasica()) 10000L else 15000L

                Log.d(TAG, "Iniciando obtenção de localização com timeout de ${timeout}ms")

                // Usar o locationManager correto (nosso LocationManager customizado)
                val location = withTimeoutOrNull(timeout) {
                    locationManager.getCurrentLocation(timeout)
                } ?: "GPS sem sinal"

                localColeta = location
                Log.d(TAG, "Localização obtida: $localColeta")

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter localização: ${e.message}", e)
                localColeta = "GPS sem sinal"
            }
        }
    }

    private fun verificarPermissoes() {
        val permissoesNecessarias = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissoesNecessarias.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissoesNecessarias.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissoesNecessarias.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            permissoesNecessarias.add(Manifest.permission.INTERNET)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissoesNecessarias.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissoesNecessarias.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissoesNecessarias.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissoesNecessarias.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissoesNecessarias.toTypedArray(),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION, REQUEST_LOCATION_PERMISSION, REQUEST_NOTIFICATION_PERMISSION -> {
                val todasPermissoesConcedidas = grantResults.isNotEmpty() &&
                        grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                if (!todasPermissoesConcedidas) {
                    Toast.makeText(this,
                        "Algumas permissões não foram concedidas. Alguns recursos podem não funcionar corretamente.",
                        Toast.LENGTH_LONG).show()
                } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
                    obterLocalizacaoAtual()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_EDITING_ID, editingChecklistId)
        outState.putString(STATE_CURRENT_PHOTO_PATH, currentPhotoPath)
        outState.putInt(STATE_CURRENT_ITEM_POSITION, currentItemPosition)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                confirmarSaidaSemSalvar()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmarSaidaSemSalvar() {
        AlertDialog.Builder(this)
            .setTitle("Sair sem salvar?")
            .setMessage("Se você sair agora, todas as alterações serão perdidas.")
            .setPositiveButton("Sair") { _, _ -> finish() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configurarNovoChecklist() {
        try {
            val nome = sharedPrefsManager.getUsuarioNome()
            val placaCavalo = sharedPrefsManager.getPlacaCavalo()
            val placaCarreta = sharedPrefsManager.getPlacaCarreta()
            val dataAtual = Date()

            binding.tvMotorista.text = nome
            binding.tvData.text = dateFormat.format(dataAtual)
            binding.tvPlacaCavalo.text = placaCavalo
            binding.tvPlacaCarreta.text = placaCarreta

            criarItensChecklist()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao configurar novo checklist: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao configurar novo checklist", e)
        }
    }

    private fun criarItensChecklist() {
        try {
            val itensInspecao = listOf(
                "PARA CHOQUE",
                "MOTOR",
                "PNEUS",
                "PISO DA UNIDADE TRATORA",
                "TANQUE DE COMBUSTÍVEL",
                "CABINE",
                "RESERVATÓRIO DE AR",
                "EIXO DE TRANSMISSÃO",
                "QUINTA RODA",
                "SISTEMA DE EXAUSTÃO",
                "CHASSI",
                "PORTAS TRASEIRA (BAÚ/SIDER)",
                "PORTA LATERAL DIREITA",
                "PORTA LATERAL ESQUERDA",
                "PAREDE FRONTAL",
                "TETO",
                "PISO DO COMPARTIMENTO DE CARGA",
                "UNID. EXAUSTORA/VENTILADORES/FILTRO DE AR",
                "MOTOR CÂMARA FRIA",
                "ODORES",
                "PRAGAS VISÍVEIS (mofo, insetos, roedores e afins)",
                "CONTAMINAÇÃO QUÍMICA",
                "FUNDO OU PAREDE FALSA DETECTADO?",
                "INDÍCIOS DE CONTAMINAÇÃO?",
                "AUTORIDADE COMPETENTE NOTIFICADA?"
            )

            checklistItems.clear()
            itensInspecao.forEachIndexed { index, descricao ->
                checklistItems.add(
                    ItemChecklist(
                        id = 0,
                        checklistId = 0,
                        numeroItem = index + 1,
                        descricao = descricao,
                        status = "",
                        comentario = null,
                        fotoPath = null
                    )
                )
            }

            checklistItemAdapter.updateList(checklistItems)
            atualizarProgresso()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao criar itens: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao criar itens do checklist", e)
        }
    }

    private fun carregarChecklistExistente(checklistId: Long) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ChecklistActivity)

                val checklist = withContext(Dispatchers.IO) {
                    db.checklistDao().getChecklistById(checklistId)
                }

                if (checklist != null) {
                    binding.tvMotorista.text = checklist.motoristaName
                    binding.tvData.text = dateFormat.format(checklist.data)
                    binding.tvPlacaCavalo.text = checklist.placaCavalo ?: "N/A"
                    binding.tvPlacaCarreta.text = checklist.placaCarreta ?: "N/A"

                    binding.etCrtMicDue.setText(checklist.crtMicDue)
                    binding.etNLacre.setText(checklist.nLacre)

                    if (!checklist.pesoBruto.isNullOrEmpty()) {
                        val rawInput = checklist.pesoBruto.replace(".", "")
                        if (rawInput.isNotEmpty()) {
                            binding.etPesoBruto.setText(formatPesoBruto(rawInput))
                        } else {
                            binding.etPesoBruto.setText(checklist.pesoBruto)
                        }
                    } else {
                        binding.etPesoBruto.setText("")
                    }

                    binding.checkEntrada.isChecked = checklist.statusEntrada
                    binding.checkSaida.isChecked = checklist.statusSaida
                    binding.checkPernoite.isChecked = checklist.statusPernoite
                    binding.checkParada.isChecked = checklist.statusParada

                    localColeta = checklist.localColeta
                    if (localColeta == null || localColeta == "Localização não disponível" || localColeta == "Obtendo localização...") {
                        Log.d(TAG, "Localização não disponível no checklist, tentando obter nova")
                        obterLocalizacaoAtual()
                    } else {
                        Log.d(TAG, "Usando localização existente do checklist: $localColeta")
                    }

                    val itens = withContext(Dispatchers.IO) {
                        db.checklistDao().getItensByChecklistId(checklistId)
                    }

                    checklistItems.clear()
                    checklistItems.addAll(itens)
                    checklistItemAdapter.updateList(checklistItems)

                    atualizarProgresso()
                } else {
                    Toast.makeText(this@ChecklistActivity,
                        "Checklist não encontrado",
                        Toast.LENGTH_SHORT).show()
                    configurarNovoChecklist()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChecklistActivity,
                    "Erro ao carregar checklist: ${e.message}",
                    Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Erro ao carregar checklist existente", e)
                configurarNovoChecklist()
            }
        }
    }

    private fun atualizarProgresso() {
        try {
            val total = checklistItems.size
            val preenchidos = checklistItems.count { it.status.isNotEmpty() }
            val percentual = if (total > 0) (preenchidos * 100) / total else 0

            binding.progressBar.progress = percentual
            binding.tvProgresso.text = "$percentual% completo"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar progresso", e)
        }
    }

    private fun filtrarItensPorCategoria(categoria: String) {
        try {
            if (categoria == "todos") {
                checklistItemAdapter.updateList(checklistItems)
                return
            }

            val categorias = mapOf(
                "motor" to listOf(2 , 5, 18, 19),
                "pneus" to listOf(3),
                "cabine" to listOf(6, 7)
            )

            val itensFiltrados = if (categorias.containsKey(categoria)) {
                val indices = categorias[categoria] ?: emptyList()
                checklistItems.filter { it.numeroItem in indices }
            } else {
                checklistItems
            }

            checklistItemAdapter.updateList(itensFiltrados)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao filtrar itens: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao filtrar itens por categoria", e)
        }
    }

    private fun mostrarOpcoesFoto() {
        try {
            val options = arrayOf("Tirar foto", "Escolher da galeria", "Remover foto")

            val cameraIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_camera)

            AlertDialog.Builder(this)
                .setTitle("Foto")
                .setIcon(cameraIcon)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> verificarEIniciarCamera()
                        1 -> verificarEIniciarGaleria()
                        2 -> removerFoto()
                    }
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao mostrar opções: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao mostrar opções de foto", e)
        }
    }

    private fun verificarEIniciarCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            Toast.makeText(this, "É necessário conceder permissão para a câmera", Toast.LENGTH_SHORT).show()
        } else {
            tirarFoto()
        }
    }

    private fun verificarEIniciarGaleria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
                Toast.makeText(this, "É necessário conceder permissão para acessar a galeria", Toast.LENGTH_SHORT).show()
                return
            }
        }
        escolherDaGaleria()
    }

    private fun tirarFoto() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            val photoFile = criarArquivoImagem()

            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    photoFile
                )

                currentPhotoPath = photoFile.absolutePath
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

                takePictureLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Erro ao criar arquivo para foto", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro geral ao tirar foto", e)
            Toast.makeText(this, "Erro ao iniciar câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun escolherDaGaleria() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            try {
                galleryLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao abrir galeria: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Erro ao abrir galeria", e)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao iniciar galeria: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro geral ao escolher da galeria", e)
        }
    }

    private fun handleCameraResult() {
        try {
            if (currentPhotoPath != null && currentItemPosition >= 0 && currentItemPosition < checklistItems.size) {
                val file = File(currentPhotoPath!!)
                if (file.exists()) {
                    val item = checklistItems[currentItemPosition]
                    checklistItems[currentItemPosition] = item.copy(fotoPath = currentPhotoPath)
                    checklistItemAdapter.notifyItemChanged(currentItemPosition)
                    Toast.makeText(this, "Foto salva", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Arquivo de foto não encontrado", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar resultado da câmera: ${e.message}", e)
            Toast.makeText(this, "Erro ao processar foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryResult(data: Intent?) {
        try {
            data?.data?.let { uri ->
                if (currentItemPosition >= 0 && currentItemPosition < checklistItems.size) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val file = criarArquivoImagem()

                        file?.let {
                            it.outputStream().use { output ->
                                inputStream?.copyTo(output)
                            }
                            val item = checklistItems[currentItemPosition]
                            checklistItems[currentItemPosition] = item.copy(fotoPath = file.absolutePath)
                            checklistItemAdapter.notifyItemChanged(currentItemPosition)
                            Toast.makeText(this, "Foto salva", Toast.LENGTH_SHORT).show()
                        }

                        inputStream?.close()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar resultado da galeria: ${e.message}", e)
            Toast.makeText(this, "Erro ao processar foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removerFoto() {
        try {
            if (currentItemPosition >= 0 && currentItemPosition < checklistItems.size) {
                val item = checklistItems[currentItemPosition]
                item.fotoPath?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao deletar arquivo de foto", e)
                    }
                }
                checklistItems[currentItemPosition] = item.copy(fotoPath = null)
                checklistItemAdapter.notifyItemChanged(currentItemPosition)
                Toast.makeText(this, "Foto removida", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao remover foto: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao remover foto", e)
        }
    }

    private fun criarArquivoImagem(): File? {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao criar arquivo de imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro ao criar arquivo de imagem", e)
            return null
        }
    }

    private fun salvarChecklist() {
        if (isSaving) {
            Toast.makeText(this, "Aguarde, salvamento em andamento...", Toast.LENGTH_SHORT).show()
            return
        }

        val statusVazios = checklistItems.count { it.status.isEmpty() }
        if (statusVazios > 0) {
            AlertDialog.Builder(this)
                .setTitle("Itens não preenchidos")
                .setMessage("É necessário preencher todos os itens antes de salvar.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // ESTRATÉGIA: Salvar localmente SEMPRE e finalizar RÁPIDO
        isSaving = true
        loadingDialog = showLoadingDialog("Salvando checklist...")

        // Usar um timeout geral para GARANTIR que não trave
        lifecycleScope.launch {
            val job = launch {
                var checklistId = -1L

                try {
                    // 1. SALVAR NO BANCO (rápido e confiável)
                    checklistId = salvarNoBancoRapido()

                    if (checklistId <= 0) {
                        finalizarImediato("Erro ao salvar checklist", false)
                        return@launch
                    }

                    // 2. GERAR PDF COM TIMEOUT AGRESSIVO
                    updateDialog("Gerando PDF...")

                    val pdfGerado = withTimeoutOrNull(8000) {
                        gerarPdfSeguro(checklistId)
                    } ?: false

                    if (!pdfGerado) {
                        // PDF falhou mas checklist está salvo
                        finalizarImediato("Checklist salvo! (PDF não gerado)", true)
                        return@launch
                    }

                    // 3. NÃO VERIFICAR INTERNET - TENTAR DIRETO
                    updateDialog("Finalizando...")

                    // Tentar email com timeout curto
                    val emailJob = launch {
                        tentarEnviarEmailRapido(checklistId)
                    }

                    // Firebase em paralelo (não espera)
                    launch {
                        tentarEnviarFirebase(checklistId)
                    }

                    // Esperar email no máximo 3 segundos
                    withTimeoutOrNull(3000) {
                        emailJob.join()
                    }

                    // SEMPRE finalizar com sucesso
                    if (checklistId > 0) {
                        // MUDANÇA AQUI: Verificar se o email foi enviado usando método otimizado
                        val emailFoiEnviado = withContext(Dispatchers.IO) {
                            AppDatabase.getDatabase(this@ChecklistActivity)
                                .checklistDao()
                                .isEmailEnviado(checklistId) ?: false
                        }

                        if (emailFoiEnviado) {
                            finalizarImediato("Checklist salvo e email enviado!", true)
                        } else {
                            finalizarImediato("Checklist salvo! Email será enviado automaticamente quando houver conexão.", true)
                        }
                    } else {
                        finalizarImediato("Erro ao salvar checklist", false)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Erro crítico: ${e.message}", e)
                    if (checklistId > 0) {
                        finalizarImediato("Checklist salvo com erros", true)
                    } else {
                        finalizarImediato("Erro ao salvar", false)
                    }
                }
            }

            // TIMEOUT MÁXIMO ABSOLUTO - Se demorar mais que 20 segundos, força finalização
            withTimeoutOrNull(20000) {
                job.join()
            }

            // Se ainda não finalizou, força
            if (isActive && !isFinishing) {
                finalizarImediato("Checklist salvo! (Processamento em background)", true)
            }
        }
    }

    private suspend fun salvarNoBancoRapido(): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Se ainda está obtendo localização ou não tem, tenta mais uma vez com timeout curto
                if (localColeta == null ||
                    localColeta == "Obtendo localização..." ||
                    localColeta == "Localização não disponível" ||
                    localColeta == "GPS sem sinal" ||
                    localColeta?.isEmpty() == true) {

                    Log.d(TAG, "Tentando obter localização uma última vez antes de salvar...")
                    val locationFinal = withTimeoutOrNull(5000) {
                        locationManager.getCurrentLocation(5000)
                    }

                    if (!locationFinal.isNullOrEmpty() &&
                        locationFinal != "Localização não disponível" &&
                        locationFinal != "GPS sem sinal") {
                        localColeta = locationFinal
                        Log.d(TAG, "Localização obtida no último momento: $localColeta")
                    } else {
                        // Se não conseguiu, mas tem GPS ativado, indica que pode estar sem sinal
                        localColeta = if (verificarGpsAtivado()) {
                            // Tentar uma última vez com GPS puro
                            val gpsLocation = withTimeoutOrNull(3000) {
                                obterLocalizacaoGPSPuro()
                            }
                            gpsLocation ?: "GPS ativado mas sem sinal no momento"
                        } else {
                            "GPS desativado"
                        }
                    }
                }

                // Garantir que NUNCA salve com localColeta vazio ou null
                if (localColeta.isNullOrEmpty()) {
                    localColeta = "Localização não disponível"
                }

                val db = AppDatabase.getDatabase(this@ChecklistActivity)
                val cpf = sharedPrefsManager.getUsuarioCpf()
                val nome = sharedPrefsManager.getUsuarioNome()
                val dataAtual = Date()

                val checklist = Checklist(
                    id = if (editingChecklistId != -1L) editingChecklistId else 0,
                    motoristaCpf = cpf,
                    motoristaName = nome,
                    data = dataAtual,
                    placaCavalo = binding.tvPlacaCavalo.text.toString(),
                    placaCarreta = binding.tvPlacaCarreta.text.toString(),
                    crtMicDue = binding.etCrtMicDue.text.toString(),
                    nLacre = binding.etNLacre.text.toString(),
                    pesoBruto = binding.etPesoBruto.text.toString(),
                    statusEntrada = binding.checkEntrada.isChecked,
                    statusSaida = binding.checkSaida.isChecked,
                    statusPernoite = binding.checkPernoite.isChecked,
                    statusParada = binding.checkParada.isChecked,
                    emailEnviado = false,
                    pdfPath = null,
                    localColeta = localColeta
                )

                if (editingChecklistId != -1L) {
                    db.checklistDao().updateChecklist(checklist)
                    checklistItems.forEach { item ->
                        db.checklistDao().updateItemChecklist(item.copy(checklistId = editingChecklistId))
                    }
                    editingChecklistId
                } else {
                    val id = db.checklistDao().insertChecklist(checklist)
                    val itensComId = checklistItems.map { it.copy(checklistId = id) }
                    db.checklistDao().insertItensChecklist(itensComId)
                    id
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar no banco: ${e.message}", e)
                -1L
            }
        }
    }

    private suspend fun gerarPdfSeguro(checklistId: Long): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@ChecklistActivity)
                val checklist = db.checklistDao().getChecklistById(checklistId) ?: return@withContext false
                val itens = db.checklistDao().getItensByChecklistId(checklistId)

                // Gerar PDF com proteção contra travamento
                val pdfFile = try {
                    PdfGenerator(this@ChecklistActivity).gerarPdf(checklist, itens)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao gerar PDF: ${e.message}")
                    null
                }

                if (pdfFile != null && pdfFile.exists()) {
                    val checklistComPdf = checklist.copy(pdfPath = pdfFile.absolutePath)
                    db.checklistDao().updateChecklist(checklistComPdf)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro crítico ao gerar PDF: ${e.message}", e)
            false
        }
    }

    private suspend fun tentarEnviarEmailRapido(checklistId: Long) {
        try {
            // Verificação ultra rápida de conectividade antes de tentar
            if (!verificarConectividadeBasica()) {
                Log.d(TAG, "Sem conectividade - email será enviado depois")
                return
            }

            // Usar supervisorScope para isolar falhas
            supervisorScope {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@ChecklistActivity)
                    val checklist = db.checklistDao().getChecklistById(checklistId) ?: return@withContext

                    if (checklist.pdfPath == null) return@withContext

                    val pdfFile = File(checklist.pdfPath)
                    if (!pdfFile.exists()) return@withContext

                    // Tentar enviar com timeout interno curto
                    try {
                        val crtNum = checklist.crtMicDue ?: "S/N"
                        val assunto = "CRT No. $crtNum | Motorista: ${checklist.motoristaName}"
                        val corpo = "Segue em anexo o checklist de inspeção do veículo " +
                                "${checklist.placaCavalo} / ${checklist.placaCarreta} " +
                                "realizado em ${dateFormat.format(checklist.data)} às " +
                                "${timeFormat.format(checklist.data)}."

                        val emailSender = EmailSender(this@ChecklistActivity)

                        // EmailSender deve ter timeout interno
                        val enviado = emailSender.enviarEmail("checklist@paranalog.com.br", assunto, corpo, pdfFile)

                        if (enviado) {
                            val checklistAtualizado = checklist.copy(emailEnviado = true)
                            db.checklistDao().updateChecklist(checklistAtualizado)
                            Log.d(TAG, "Email enviado com sucesso")
                        } else {
                            Log.d(TAG, "Email não enviado - será tentado depois pelo EmailPendingManager")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Falha ao enviar email: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no envio de email: ${e.message}")
        }
    }

    private fun verificarConectividadeBasica(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                connectivityManager.activeNetwork != null
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo != null
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun verificarGpsAtivado(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    }

    private suspend fun obterLocalizacaoGPSPuro(): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this@ChecklistActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext null
                }

                val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

                @Suppress("DEPRECATION")
                val lastKnownLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)

                if (lastKnownLocation != null) {
                    val lat = String.format("%.6f", lastKnownLocation.latitude)
                    val lng = String.format("%.6f", lastKnownLocation.longitude)
                    "Lat: $lat, Lng: $lng (última GPS)"
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter GPS puro: ${e.message}")
                null
            }
        }
    }

    private suspend fun tentarEnviarFirebase(checklistId: Long) {
        if (firebaseManager == null) return

        try {
            // Verificação básica de conectividade
            if (!verificarConectividadeBasica()) {
                Log.d(TAG, "Sem conectividade - Firebase não será enviado")
                return
            }

            supervisorScope {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@ChecklistActivity)
                    val checklist = db.checklistDao().getChecklistById(checklistId) ?: return@withContext
                    val itens = db.checklistDao().getItensByChecklistId(checklistId)

                    val itensSemFotos = itens.map { it.copy(fotoPath = null) }

                    try {
                        // Timeout curto para Firebase
                        withTimeoutOrNull(3000) {
                            firebaseManager?.enviarChecklistParaFirebase(checklist, itensSemFotos)
                        }
                        Log.d(TAG, "Firebase enviado")
                    } catch (e: Exception) {
                        Log.d(TAG, "Firebase falhou: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro Firebase: ${e.message}")
        }
    }

    private fun updateDialog(message: String) {
        runOnUiThread {
            loadingDialog?.findViewById<TextView>(R.id.textViewLoading)?.text = message
        }
    }

    private fun finalizarImediato(mensagem: String, sucesso: Boolean) {
        runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
            isSaving = false

            Toast.makeText(
                this@ChecklistActivity,
                mensagem,
                if (sucesso) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()

            // Finalizar imediatamente
            finish()
        }
    }
}