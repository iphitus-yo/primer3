package com.paranalog.truckcheck.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.databinding.ActivityCadastroBinding
import com.paranalog.truckcheck.models.Usuario
import com.paranalog.truckcheck.utils.MaskUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding
    private var isCpfErrorState = false
    private var isPlacaCavaloErrorState = false
    private var isPlacaCarretaErrorState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Substituir as máscaras padrão pelas implementações personalizadas
        // MaskUtils.setupCpfMask(binding.etCpf)
        // MaskUtils.setupPlacaMask(binding.etPlacaCavalo)
        // MaskUtils.setupPlacaMask(binding.etPlacaCarreta)

        // Configurar os campos com os novos tratamentos
        setupCpfField()
        setupPlacaField(binding.etPlacaCavalo, isPlacaCavaloErrorState)
        setupPlacaField(binding.etPlacaCarreta, isPlacaCarretaErrorState)

        binding.btnCadastrar.setOnClickListener {
            if (validarCampos()) {
                cadastrarUsuario()
            }
        }

        binding.btnVoltar.setOnClickListener {
            finish()
        }
    }

    private fun setupCpfField() {
        val cpfEditText = binding.etCpf

        // Listener para limpar campo em caso de erro
        cpfEditText.setOnClickListener {
            if (cpfEditText.error != null || isCpfErrorState) {
                cpfEditText.setText("")
                cpfEditText.error = null
                isCpfErrorState = false
            }
        }

        cpfEditText.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            var deletingPosition = -1

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isFormatting) {
                    if (count > 0 && after < count) {
                        deletingPosition = start
                    }
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Não precisa implementar
            }

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                // Remover caracteres especiais
                val digits = s.toString().replace("[^\\d]".toRegex(), "")

                // Tratamento especial ao deletar
                if (deletingPosition >= 0 && s.toString().length == deletingPosition) {
                    if (deletingPosition > 0 && (s.toString().endsWith(".") || s.toString().endsWith("-"))) {
                        s.delete(deletingPosition - 1, deletingPosition)
                    }
                    deletingPosition = -1
                }

                // Formatação do CPF
                val formatted = formatCpf(digits)

                // Atualizar texto
                if (formatted != s.toString()) {
                    s.replace(0, s.length, formatted)
                }

                isFormatting = false

                // Limpar erro se o CPF estiver no formato correto
                if (digits.length == 11) {
                    cpfEditText.error = null
                    isCpfErrorState = false
                }
            }
        })
    }

    private fun setupPlacaField(placaEditText: android.widget.EditText, isErrorState: Boolean) {
        // Listener para limpar campo em caso de erro
        placaEditText.setOnClickListener {
            if (placaEditText.error != null) {
                placaEditText.setText("")
                placaEditText.error = null

                if (placaEditText == binding.etPlacaCavalo) {
                    isPlacaCavaloErrorState = false
                } else if (placaEditText == binding.etPlacaCarreta) {
                    isPlacaCarretaErrorState = false
                }
            }
        }

        placaEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Não precisa implementar
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Não precisa implementar
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return

                // Converter para maiúsculas
                val text = s.toString().uppercase()
                if (text != s.toString()) {
                    s.replace(0, s.length, text)
                }

                // Verificar formato
                if (text.length == 7) {
                    if (isPlacaValida(text)) {
                        placaEditText.error = null
                        if (placaEditText == binding.etPlacaCavalo) {
                            isPlacaCavaloErrorState = false
                        } else if (placaEditText == binding.etPlacaCarreta) {
                            isPlacaCarretaErrorState = false
                        }
                    }
                }
            }
        })
    }

    private fun formatCpf(digits: String): String {
        val sb = StringBuilder()

        for (i in digits.indices) {
            if (i == 3 || i == 6) sb.append(".")
            if (i == 9) sb.append("-")
            if (i < 11) sb.append(digits[i]) // Limita a 11 dígitos (tamanho do CPF)
        }

        return sb.toString()
    }

    private fun clearCpfField() {
        binding.etCpf.setText("")
        binding.etCpf.error = null
        isCpfErrorState = false
    }

    private fun validarCampos(): Boolean {
        val nome = binding.etNome.text.toString()
        val cpfComMascara = binding.etCpf.text.toString()
        val cpf = cpfComMascara.replace("[^\\d]".toRegex(), "") // Remove a máscara
        val senha = binding.etSenha.text.toString()
        val confirmaSenha = binding.etConfirmaSenha.text.toString()
        val placaCavalo = binding.etPlacaCavalo.text.toString()
        val placaCarreta = binding.etPlacaCarreta.text.toString()

        var isValido = true

        // Validar campos vazios
        if (nome.isEmpty()) {
            binding.etNome.error = "Campo obrigatório"
            isValido = false
        }

        if (cpf.isEmpty()) {
            binding.etCpf.error = "Campo obrigatório"
            isCpfErrorState = true
            isValido = false
        }

        if (senha.isEmpty()) {
            binding.etSenha.error = "Campo obrigatório"
            isValido = false
        }

        if (confirmaSenha.isEmpty()) {
            binding.etConfirmaSenha.error = "Campo obrigatório"
            isValido = false
        }

        if (placaCavalo.isEmpty()) {
            binding.etPlacaCavalo.error = "Campo obrigatório"
            isPlacaCavaloErrorState = true
            isValido = false
        }

        if (placaCarreta.isEmpty()) {
            binding.etPlacaCarreta.error = "Campo obrigatório"
            isPlacaCarretaErrorState = true
            isValido = false
        }

        if (!isValido) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validar CPF (verificação básica)
        if (cpf.length != 11) {
            binding.etCpf.error = "CPF inválido"
            isCpfErrorState = true
            Toast.makeText(this, "CPF inválido", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validar senha (5 dígitos)
        if (senha.length != 5) {
            binding.etSenha.error = "A senha deve ter 5 dígitos"
            Toast.makeText(this, "A senha deve ter 5 dígitos", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validar confirmação de senha
        if (senha != confirmaSenha) {
            binding.etConfirmaSenha.error = "As senhas não coincidem"
            Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
            return false
        }

        // Validar formato das placas (LLLNLNN)
        if (!isPlacaValida(placaCavalo)) {
            binding.etPlacaCavalo.error = "Formato de placa inválido"
            isPlacaCavaloErrorState = true
            Toast.makeText(this, "Formato de placa do cavalo inválido (deve ser no formato LLLNLNN)", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!isPlacaValida(placaCarreta)) {
            binding.etPlacaCarreta.error = "Formato de placa inválido"
            isPlacaCarretaErrorState = true
            Toast.makeText(this, "Formato de placa da carreta inválido (deve ser no formato LLLNLNN)", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun isPlacaValida(placa: String): Boolean {
        if (placa.length != 7) {
            return false
        }

        // Verifica o formato LLLNLNN (3 letras, 1 número, 1 letra, 2 números)
        val regex = Regex("[A-Z]{3}[0-9][A-Z][0-9]{2}")
        return regex.matches(placa)
    }

    private fun cadastrarUsuario() {
        val nome = binding.etNome.text.toString()
        val cpf = binding.etCpf.text.toString().replace("[^\\d]".toRegex(), "") // Remove a máscara
        val senha = binding.etSenha.text.toString()
        val placaCavalo = binding.etPlacaCavalo.text.toString()
        val placaCarreta = binding.etPlacaCarreta.text.toString()

        val novoUsuario = Usuario(
            cpf = cpf,
            nome = nome,
            senha = senha,
            placaCavalo = placaCavalo,
            placaCarreta = placaCarreta
        )

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@CadastroActivity)

            // Verificar se já existe usuário com este CPF
            val usuarioExistente = withContext(Dispatchers.IO) {
                db.usuarioDao().getUsuarioByCpf(cpf)
            }

            if (usuarioExistente != null) {
                Toast.makeText(this@CadastroActivity,
                    "Já existe um usuário com este CPF",
                    Toast.LENGTH_SHORT).show()
                binding.etCpf.error = "CPF já cadastrado"
                isCpfErrorState = true
                return@launch
            }

            // Inserir o novo usuário
            withContext(Dispatchers.IO) {
                db.usuarioDao().insert(novoUsuario)
            }

            Toast.makeText(this@CadastroActivity,
                "Usuário cadastrado com sucesso!",
                Toast.LENGTH_SHORT).show()

            finish()
        }
    }
}