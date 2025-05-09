package com.paranalog.truckcheck.activities

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.adapters.ChecklistItemAdapter
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.databinding.ActivityChecklistBinding
import com.paranalog.truckcheck.models.Checklist
import com.paranalog.truckcheck.models.ItemChecklist
import com.paranalog.truckcheck.utils.EmailSender
import com.paranalog.truckcheck.utils.PdfGenerator
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChecklistActivity() : AppCompatActivity(), Parcelable {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var localColeta: String? = null
    private val REQUEST_LOCATION_PERMISSION = 5
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
    private var isSaving = false // Flag para evitar salvamentos duplicados
    private var loadingDialog: Dialog? = null // Diálogo de carregamento
    private var locationCallback: LocationCallback? = null // Para receber atualizações de localização
    private var isRequiredFieldsFilled = false // Para verificar campos obrigatórios

    constructor(parcel: Parcel) : this() {
        localColeta = parcel.readString()
        editingChecklistId = parcel.readLong()
        currentPhotoPath = parcel.readString()
        currentItemPosition = parcel.readInt()
        savedChecklistId = parcel.readLong()
        isSaving = parcel.readByte() != 0.toByte()
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_GALLERY = 2
        private const val REQUEST_CAMERA_PERMISSION = 3
        private const val REQUEST_STORAGE_PERMISSION = 4
        private const val STATE_CURRENT_PHOTO_PATH = "state_current_photo_path"
        private const val STATE_CURRENT_ITEM_POSITION = "state_current_item_position"
        private const val STATE_EDITING_ID = "state_editing_id"

        // Creator do Parcelable dentro do companion object para evitar erro
        @JvmField
        val CREATOR: Parcelable.Creator<ChecklistActivity> = object : Parcelable.Creator<ChecklistActivity> {
            override fun createFromParcel(parcel: Parcel): ChecklistActivity = ChecklistActivity(parcel)
            override fun newArray(size: Int): Array<ChecklistActivity?> = arrayOfNulls(size)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecklistBinding.inflate(layoutInflater)

        // Mostrar tela de carregamento enquanto inicializa
        binding.loadingContainer.visibility = View.VISIBLE
        binding.mainContainer.visibility = View.GONE

        // Garantir que o botão esteja sempre habilitado
        binding.fabSalvar.isEnabled = true
        binding.fabSalvar.alpha = 1.0f

        setContentView(binding.root)

        // Inicializar componentes
        initializeComponents()

        // Configurar a tela com base nos parâmetros
        setupScreen(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remover callback de localização quando a activity é destruída
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    private fun initializeComponents() {
        sharedPrefsManager = SharedPrefsManager(this)

        // Inicializar o cliente de localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Configurar RecyclerView
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

        // Solicitar permissões
        verificarPermissoes()

        // Configurar filtros de categoria
        binding.chipTodos.setOnClickListener { filtrarItensPorCategoria("todos") }
        binding.chipMotor.setOnClickListener { filtrarItensPorCategoria("motor") }
        binding.chipPneus.setOnClickListener { filtrarItensPorCategoria("pneus") }
        binding.chipCabine.setOnClickListener { filtrarItensPorCategoria("cabine") }

        // Configurar FAB para salvar - MODIFICADO PARA SEMPRE TENTAR SALVAR
        binding.fabSalvar.setOnClickListener {
            // Forçar uma nova validação dos campos
            val camposValidos = validarCamposObrigatorios()

            // Log para debug
            Log.d(TAG, "FAB salvar clicado - Campos válidos: $camposValidos")

            // Sempre chamar salvarChecklist, que fará a validação interna
            salvarChecklist()
        }

        // Configurar formatação automática para peso bruto
        setupPesoBrutoFormatter()

        // Adicionar TextWatcher para os campos obrigatórios
        setupRequiredFieldsValidation()
    }

    private fun setupRequiredFieldsValidation() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validarCamposObrigatorios()
            }
        }

        // Aplicar o textWatcher a todos os campos obrigatórios
        binding.etCrtMicDue.addTextChangedListener(textWatcher)
        binding.etNLacre.addTextChangedListener(textWatcher)
        binding.etPesoBruto.addTextChangedListener(textWatcher)
    }

    private fun validarCamposObrigatorios(): Boolean {
        val micPreenchido = binding.etCrtMicDue.text.toString().trim().isNotEmpty()
        val lacrePreenchido = binding.etNLacre.text.toString().trim().isNotEmpty()
        val pesoPreenchido = binding.etPesoBruto.text.toString().trim().isNotEmpty()

        // Verificar se pelo menos uma opção de status está marcada
        val statusMarcado = binding.checkEntrada.isChecked ||
                binding.checkSaida.isChecked ||
                binding.checkPernoite.isChecked ||
                binding.checkParada.isChecked

        isRequiredFieldsFilled = micPreenchido && lacrePreenchido && pesoPreenchido && statusMarcado

        // Atualizar o visual do botão de salvar
        updateSaveButtonState()

        return isRequiredFieldsFilled
    }

    private fun updateSaveButtonState() {
        // Sempre manter o botão habilitado, mas mudar a aparência para dar feedback visual
        binding.fabSalvar.isEnabled = true

        // Ajustar apenas a opacidade para indicar status
        binding.fabSalvar.alpha = 1.0f  // Sempre 100% visível

        // Log para debug
        Log.d(TAG, "Atualizando estado do botão - Campos preenchidos: $isRequiredFieldsFilled")
    }

    private fun setupPesoBrutoFormatter() {
        binding.etPesoBruto.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Guardar o texto anterior para comparação
                if (!isFormatting) {
                    previousText = s?.toString() ?: ""
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nada a fazer aqui
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return

                // Evitar loops de formatação
                isFormatting = true

                try {
                    val userInput = s.toString()

                    // Só formatar se o texto mudou
                    if (userInput != previousText) {
                        // Remover todos os pontos existentes
                        val rawInput = userInput.replace(".", "")

                        if (rawInput.isNotEmpty()) {
                            // Criar a versão formatada com pontos
                            val formatted = formatPesoBruto(rawInput)

                            // Atualizar o texto
                            if (formatted != userInput) {
                                s.replace(0, s.length, formatted)
                                Log.d(TAG, "Peso formatado: $rawInput -> $formatted")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao formatar peso bruto", e)
                } finally {
                    isFormatting = false
                    validarCamposObrigatorios()
                }
            }
        })

        // Formatar o valor inicial, se houver
        val textoInicial = binding.etPesoBruto.text?.toString()
        if (!textoInicial.isNullOrEmpty()) {
            val rawInput = textoInicial.replace(".", "")
            if (rawInput.isNotEmpty()) {
                val formatted = formatPesoBruto(rawInput)
                binding.etPesoBruto.setText(formatted)
                Log.d(TAG, "Formatação inicial do peso: $textoInicial -> $formatted")
            }
        }
    }

    private fun formatPesoBruto(input: String): String {
        val numericOnly = input.replace(Regex("[^0-9]"), "")
        if (numericOnly.isEmpty()) return ""

        val valor = numericOnly.toLongOrNull() ?: 0L

        // Usar DecimalFormat com configuração específica para garantir separador de milhar
        val formatter = DecimalFormat("#,###")
        val symbols = DecimalFormatSymbols(Locale("pt", "BR"))
        symbols.groupingSeparator = '.' // Definir explicitamente o separador de milhar como ponto
        formatter.decimalFormatSymbols = symbols

        // Formatar o valor e garantir que tenha o separador
        val resultado = formatter.format(valor)

        // Log para debug
        Log.d(TAG, "Formatação de peso: $input -> $resultado")

        return resultado
    }

    private fun salvarChecklist() {
        if (isSaving) {
            Log.d(TAG, "Salvamento já em andamento, ignorando clique")
            return
        }

        Log.d(TAG, "Iniciando processo de salvamento")

        // Verificar os campos específicos e mostrar mensagens informativas
        val erros = mutableListOf<String>()

        if (binding.etCrtMicDue.text.toString().trim().isEmpty()) {
            erros.add("MIC")
            binding.etCrtMicDue.error = "Campo obrigatório"
        }

        if (binding.etNLacre.text.toString().trim().isEmpty()) {
            erros.add("LACRE")
            binding.etNLacre.error = "Campo obrigatório"
        }

        if (binding.etPesoBruto.text.toString().trim().isEmpty()) {
            erros.add("PESO")
            binding.etPesoBruto.error = "Campo obrigatório"
        }

        val statusMarcado = binding.checkEntrada.isChecked ||
                binding.checkSaida.isChecked ||
                binding.checkPernoite.isChecked ||
                binding.checkParada.isChecked

        if (!statusMarcado) {
            erros.add("STATUS (Entrada/Saída/Pernoite/Parada)")
        }

        if (erros.isNotEmpty()) {
            val mensagem = "Preencha os campos obrigatórios: ${erros.joinToString(", ")}"
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
            Log.d(TAG, "Validação falhou: $mensagem")
            return
        }

        // Se chegou aqui, todos os campos estão preenchidos
        isSaving = true
        loadingDialog = showLoadingDialog("Salvando checklist...")
        Log.d(TAG, "Todos os campos validados, iniciando salvamento")
        processarSalvamento()
    }

    private fun setupScreen(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(
                        this@ChecklistActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    obterLocalizacaoAtual()
                } else {
                    ActivityCompat.requestPermissions(
                        this@ChecklistActivity,
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
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
                    validarCamposObrigatorios()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao configurar tela", e)
                Toast.makeText(
                    this@ChecklistActivity,
                    "Erro ao carregar dados: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                withContext(Dispatchers.Main) {
                    binding.loadingContainer.visibility = View.GONE
                    binding.mainContainer.visibility = View.VISIBLE
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
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }

        try {
            localColeta = "Obtendo localização..."
            Log.d(TAG, "Solicitando localização atual...")

            val locationRequest = LocationRequest.create().apply {
                priority = Priority.PRIORITY_HIGH_ACCURACY
                interval = 5000
                fastestInterval = 2000
                numUpdates = 1
            }

            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d(TAG, "Localização recebida: ${location.latitude}, ${location.longitude}")
                        processarLocalizacao(location)
                    } else {
                        Log.d(TAG, "Localização recebida é nula")
                        localColeta = "Não foi possível determinar localização"
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Última localização conhecida: ${location.latitude}, ${location.longitude}")
                        processarLocalizacao(location)
                    } else {
                        Log.d(TAG, "Sem localização conhecida, solicitando atualização...")
                        try {
                            fusedLocationClient.requestLocationUpdates(
                                locationRequest,
                                locationCallback!!,
                                Looper.getMainLooper()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao solicitar atualizações de localização", e)
                            localColeta = "Erro ao obter localização"
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Erro ao obter última localização conhecida", e)
                    try {
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback!!,
                            Looper.getMainLooper()
                        )
                    } catch (e2: Exception) {
                        Log.e(TAG, "Erro ao solicitar atualizações de localização", e2)
                        localColeta = "Erro ao obter localização"
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao configurar serviços de localização", e)
            localColeta = "Erro ao acessar serviços de localização"
        }
    }

    private fun processarLocalizacao(location: android.location.Location) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            Log.d(TAG, "Processando localização: ${location.latitude}, ${location.longitude}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        localColeta = formatarEndereco(addresses[0])
                    } else {
                        localColeta = "Lat: ${location.latitude}, Long: ${location.longitude}"
                    }
                    Log.d(TAG, "Localização atualizada para: $localColeta")
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    localColeta = formatarEndereco(addresses[0])
                } else {
                    localColeta = "Lat: ${location.latitude}, Long: ${location.longitude}"
                }
                Log.d(TAG, "Localização atualizada para: $localColeta")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao obter endereço: ${e.message}")
            localColeta = "Lat: ${location.latitude}, Long: ${location.longitude}"
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao processar localização", e)
            localColeta = "Lat: ${location.latitude}, Long: ${location.longitude}"
        }
    }

    private fun formatarEndereco(address: Address): String {
        val partes = mutableListOf<String>()
        if (!address.thoroughfare.isNullOrEmpty()) partes.add(address.thoroughfare)
        if (!address.subThoroughfare.isNullOrEmpty()) partes.add(address.subThoroughfare)
        if (!address.locality.isNullOrEmpty()) partes.add(address.locality)
        if (!address.adminArea.isNullOrEmpty()) partes.add(address.adminArea)
        if (!address.postalCode.isNullOrEmpty()) partes.add(address.postalCode)
        if (!address.countryName.isNullOrEmpty()) partes.add(address.countryName)

        return if (partes.isNotEmpty()) {
            partes.joinToString(", ")
        } else {
            "Lat: ${address.latitude}, Long: ${address.longitude}"
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

        if (permissoesNecessarias.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissoesNecessarias.toTypedArray(), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION, REQUEST_LOCATION_PERMISSION -> {
                val todasPermissoesConcedidas = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (!todasPermissoesConcedidas) {
                    Toast.makeText(
                        this,
                        "Algumas permissões não foram concedidas. Alguns recursos podem não funcionar corretamente.",
                        Toast.LENGTH_LONG
                    ).show()
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

    override fun onBackPressed() {
        confirmarSaidaSemSalvar()
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

                    binding.etCrtMicDue.setText(checklist.crtMicDue ?: "")
                    binding.etNLacre.setText(checklist.nLacre ?: "")

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
                    if (localColeta == null || localColeta == "Localização não disponível") {
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
                    validarCamposObrigatorios()
                } else {
                    Toast.makeText(this@ChecklistActivity, "Checklist não encontrado", Toast.LENGTH_SHORT).show()
                    configurarNovoChecklist()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChecklistActivity, "Erro ao carregar checklist: ${e.message}", Toast.LENGTH_SHORT).show()
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
                "motor" to listOf(2, 5, 18, 19),
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
        val opcoes = arrayOf("Tirar Foto", "Escolher da Galeria")
        AlertDialog.Builder(this)
            .setTitle("Selecionar Foto")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> verificarEIniciarCamera()
                    1 -> verificarEIniciarGaleria()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun verificarEIniciarCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            Toast.makeText(this, "É necessário conceder permissão para a câmera", Toast.LENGTH_SHORT).show()
        } else {
            tirarFoto()
        }
    }

    private fun tirarFoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                criarArquivoImagem()
            } catch (ex: IOException) {
                Log.e(TAG, "Erro ao criar arquivo de imagem", ex)
                null
            }

            photoFile?.also {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    it
                )
                currentPhotoPath = it.absolutePath
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            }
        } else {
            Toast.makeText(this, "Nenhum aplicativo de câmera disponível", Toast.LENGTH_SHORT).show()
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

    private fun escolherDaGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    private fun processarResultadoCamera() {
        if (currentItemPosition >= 0 && currentItemPosition < checklistItems.size && currentPhotoPath != null) {
            val item = checklistItems[currentItemPosition]
            checklistItems[currentItemPosition] = item.copy(fotoPath = currentPhotoPath)
            checklistItemAdapter.notifyItemChanged(currentItemPosition)
            Toast.makeText(this, "Foto capturada com sucesso", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Erro ao salvar a foto capturada", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Posição do item inválida ou caminho da foto nulo: $currentItemPosition, $currentPhotoPath")
        }
    }

    private fun processarResultadoGaleria(data: Intent?) {
        try {
            data?.data?.let { uri ->
                if (currentItemPosition >= 0 && currentItemPosition < checklistItems.size) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val file = criarArquivoImagem()

                        file?.let { outputFile ->
                            outputFile.outputStream().use { output ->
                                inputStream?.copyTo(output)
                            }
                            val item = checklistItems[currentItemPosition]
                            checklistItems[currentItemPosition] = item.copy(fotoPath = outputFile.path)
                            checklistItemAdapter.notifyItemChanged(currentItemPosition)
                            Toast.makeText(this, "Foto da galeria salva", Toast.LENGTH_SHORT).show()
                        }

                        inputStream?.close()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Erro ao processar imagem da galeria", e)
                    }
                } else {
                    Toast.makeText(this, "Posição do item inválida", Toast.LENGTH_SHORT).show()
                    Log.w(TAG, "Posição do item inválida (galeria): $currentItemPosition")
                }
            } ?: run {
                Toast.makeText(this, "Nenhuma imagem selecionada", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "URI da imagem é nulo")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar imagem da galeria: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Erro geral ao processar resultado da galeria", e)
        }
    }

    private fun criarArquivoImagem(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun processarSalvamento() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ChecklistActivity)

                // Validar campos obrigatórios novamente antes de salvar
                if (!validarCamposObrigatorios()) {
                    withContext(Dispatchers.Main) {
                        loadingDialog?.dismiss()
                        isSaving = false
                        Toast.makeText(
                            this@ChecklistActivity,
                            "Preencha todos os campos obrigatórios antes de salvar",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Obtendo o CPF do motorista do SharedPreferences
                val motoristaCpf = sharedPrefsManager.getUsuarioCpf() ?: ""

                // Criar objeto checklist com todos os campos necessários
                val checklist = Checklist(
                    id = if (editingChecklistId != -1L) editingChecklistId else 0,
                    motoristaName = binding.tvMotorista.text.toString(),
                    motoristaCpf = motoristaCpf,
                    data = dateFormat.parse(binding.tvData.text.toString()) ?: Date(),
                    placaCavalo = binding.tvPlacaCavalo.text.toString(),
                    placaCarreta = binding.tvPlacaCarreta.text.toString(),
                    crtMicDue = binding.etCrtMicDue.text.toString(),
                    nLacre = binding.etNLacre.text.toString(),
                    pesoBruto = binding.etPesoBruto.text.toString(),
                    statusEntrada = binding.checkEntrada.isChecked,
                    statusSaida = binding.checkSaida.isChecked,
                    statusPernoite = binding.checkPernoite.isChecked,
                    statusParada = binding.checkParada.isChecked,
                    localColeta = localColeta ?: "Localização não disponível",
                    emailEnviado = false,
                    pdfPath = null
                )

                // Salvar o checklist no banco de dados
                val checklistId = withContext(Dispatchers.IO) {
                    if (checklist.id == 0L) {
                        db.checklistDao().insertChecklist(checklist)
                    } else {
                        db.checklistDao().updateChecklist(checklist)
                        checklist.id
                    }
                }

                // Salvar os itens do checklist
                checklistItems.forEach { item ->
                    val updatedItem = item.copy(checklistId = checklistId)
                    try {
                        withContext(Dispatchers.IO) {
                            // Tente salvar o item
                            if (item.id == 0L) {
                                db.checklistDao().saveItem(updatedItem)
                            } else {
                                db.checklistDao().saveItem(updatedItem)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao salvar item de checklist: ${e.message}")
                    }
                }

                savedChecklistId = checklistId

                // Gerar PDF e enviar email
                try {
                    val pdfFile = File(getExternalFilesDir(null), "checklist_${checklistId}.pdf")

                    // Tentar gerar PDF usando PdfGenerator
                    try {
                        val checklistAtualizado = withContext(Dispatchers.IO) {
                            val pdfPathTemp = PdfGenerator.createPdf(this@ChecklistActivity, checklist, checklistItems)
                            val checklistComPdf = checklist.copy(pdfPath = pdfPathTemp)
                            db.checklistDao().updateChecklist(checklistComPdf)
                            checklistComPdf
                        }

                        // Tentar enviar e-mail
                        try {
                            withContext(Dispatchers.IO) {
                                val success = EmailSender.send(this@ChecklistActivity, pdfFile, checklistAtualizado)
                                if (success) {
                                    // Atualizar status de e-mail enviado
                                    val checklistFinal = checklistAtualizado.copy(emailEnviado = true)
                                    db.checklistDao().updateChecklist(checklistFinal)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao enviar e-mail, mas o checklist foi salvo", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao gerar PDF, mas o checklist foi salvo", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar PDF/Email, mas o checklist foi salvo", e)
                }

                // Finalizar a atividade após salvar
                withContext(Dispatchers.Main) {
                    loadingDialog?.dismiss()
                    isSaving = false
                    Toast.makeText(this@ChecklistActivity, "Checklist salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog?.dismiss()
                    isSaving = false
                    Toast.makeText(this@ChecklistActivity, "Erro ao salvar checklist: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Erro ao salvar checklist", e)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (resultCode == Activity.RESULT_OK) {
                when (requestCode) {
                    REQUEST_IMAGE_CAPTURE -> processarResultadoCamera()
                    REQUEST_GALLERY -> processarResultadoGaleria(data)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Operação cancelada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar resultado: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Erro ao processar resultado de activity", e)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(localColeta)
        parcel.writeLong(editingChecklistId)
        parcel.writeString(currentPhotoPath)
        parcel.writeInt(currentItemPosition)
        parcel.writeLong(savedChecklistId)
        parcel.writeByte(if (isSaving) 1 else 0)
    }

    override fun describeContents(): Int = 0
}