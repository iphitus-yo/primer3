package com.paranalog.truckcheck.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.database.AppDatabase
import com.paranalog.truckcheck.databinding.ActivityLoginBinding
import com.paranalog.truckcheck.utils.SharedPrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var isErrorState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)

        // Verificar se o usuário já está logado
        if (sharedPrefsManager.isLoggedIn()) {
            irParaTelaInicial()
            return
        }

        // Configurar formatação do CPF e validações
        setupCpfField()

        // Configurar animação para o ícone de biometria
        setupBiometricAnimations()

        // Configurar biometria
        setupBiometricLogin()

        // Configurar eventos de clique
        setupClickEvents()
    }

    private fun setupCpfField() {
        binding.etCpf.addTextChangedListener(object : TextWatcher {
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
                // Não precisamos implementar
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

                // Formatação do CPF (###.###.###-##)
                val formatted = formatCpf(digits)

                // Atualizar texto
                if (formatted != s.toString()) {
                    s.replace(0, s.length, formatted)
                }

                isFormatting = false

                // Limpar erro se o campo foi preenchido corretamente
                if (digits.length == 11) {
                    binding.tilCpf.error = null
                    if (isErrorState) {
                        hideErrorMessage()
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

    private fun setupBiometricAnimations() {
        // Pulsar animação para chamar atenção ao login biométrico
        val pulseAnimation = AlphaAnimation(1.0f, 0.7f).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.ivBiometria.startAnimation(pulseAnimation)
    }

    private fun setupBiometricLogin() {
        // Verificar se o dispositivo suporta biometria e se o usuário tem dados salvos
        if (!sharedPrefsManager.hasBiometricCredentials()) {
            binding.layoutBiometric.visibility = View.GONE
            return
        }

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    // Recuperar dados salvos
                    val cpf = sharedPrefsManager.getBiometricCpf()
                    val senha = sharedPrefsManager.getBiometricSenha()

                    if (cpf.isNotEmpty() && senha.isNotEmpty()) {
                        binding.etCpf.setText(cpf)
                        binding.etSenha.setText(senha)
                        // Mostrar animação de carregamento
                        mostrarCarregamento(true)
                        fazerLogin(cpf, senha)
                    } else {
                        Toast.makeText(this@LoginActivity,
                            "Dados biométricos inválidos. Por favor, faça login com CPF e senha.",
                            Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Ignore cancelamentos, mas mostre outros erros
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        Toast.makeText(this@LoginActivity,
                            "Erro de autenticação: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Login biométrico")
            .setSubtitle("Faça login com sua impressão digital")
            .setNegativeButtonText("Cancelar")
            .build()

        binding.layoutBiometric.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupClickEvents() {
        // Botão Entrar
        binding.btnEntrar.setOnClickListener {
            // Remover máscara do CPF para processamento
            val cpf = binding.etCpf.text.toString().replace("[^\\d]".toRegex(), "")
            val senha = binding.etSenha.text.toString()

            // Validar campos
            if (!validarCampos(cpf, senha)) {
                return@setOnClickListener
            }

            // Mostrar animação de carregamento
            mostrarCarregamento(true)

            // Fazer login
            fazerLogin(cpf, senha)
        }

        // Link para cadastro
        binding.tvCadastrar.setOnClickListener {
            // Adicionar uma animação de clique
            binding.tvCadastrar.alpha = 0.5f
            binding.tvCadastrar.animate().alpha(1.0f).setDuration(300).start()

            val intent = Intent(this, CadastroActivity::class.java)
            startActivity(intent)
        }

        // Limpar erro ao clicar no campo CPF se estiver em estado de erro
        binding.etCpf.setOnClickListener {
            if (isErrorState) {
                binding.etCpf.setText("")
                hideErrorMessage()
            }
        }

        // Limpar erro ao clicar no campo Senha se estiver em estado de erro
        binding.etSenha.setOnClickListener {
            if (isErrorState) {
                binding.etSenha.setText("")
                hideErrorMessage()
            }
        }
    }

    private fun validarCampos(cpf: String, senha: String): Boolean {
        var isValido = true

        // Verificar se o CPF foi preenchido
        if (cpf.isEmpty()) {
            binding.tilCpf.error = "Digite seu CPF"
            isValido = false
        } else if (cpf.length != 11) {
            binding.tilCpf.error = "CPF deve ter 11 dígitos"
            isValido = false
        } else {
            binding.tilCpf.error = null
        }

        // Verificar se a senha foi preenchida
        if (senha.isEmpty()) {
            binding.tilSenha.error = "Digite sua senha"
            isValido = false
        } else {
            binding.tilSenha.error = null
        }

        return isValido
    }

    private fun mostrarCarregamento(mostrar: Boolean) {
        if (mostrar) {
            binding.btnEntrar.text = "ENTRANDO..."
            binding.progressBar.visibility = View.VISIBLE
            binding.btnEntrar.isEnabled = false
        } else {
            binding.btnEntrar.text = "ENTRAR"
            binding.progressBar.visibility = View.GONE
            binding.btnEntrar.isEnabled = true
        }
    }

    private fun showErrorMessage(message: String) {
        isErrorState = true
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE

        // Animação para destacar a mensagem de erro
        binding.tvError.alpha = 0f
        binding.tvError.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideErrorMessage() {
        isErrorState = false
        binding.tvError.visibility = View.GONE
        binding.tilCpf.error = null
        binding.tilSenha.error = null
    }

    private fun fazerLogin(cpf: String, senha: String) {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@LoginActivity)
                val usuario = withContext(Dispatchers.IO) {
                    db.usuarioDao().getUsuarioByCpf(cpf)
                }

                if (usuario != null && usuario.senha == senha) {
                    // Login bem-sucedido

                    // Obter os dados do usuário da base de dados
                    val usuarioNome = usuario.nome
                    val placaCavalo = usuario.placaCavalo ?: ""  // Usar string vazia se for nula
                    val placaCarreta = usuario.placaCarreta ?: ""  // Usar string vazia se for nula

                    // Salvar credenciais para biometria se for o primeiro login bem-sucedido
                    if (!sharedPrefsManager.hasBiometricCredentials()) {
                        showBiometricEnrollDialog(cpf, senha, usuarioNome, placaCavalo, placaCarreta)
                    } else {
                        // Login normal
                        completarLogin(cpf, usuarioNome, placaCavalo, placaCarreta)
                    }
                } else {
                    // Login falhou
                    mostrarCarregamento(false)
                    showErrorMessage("CPF ou senha incorretos")
                }
            } catch (e: Exception) {
                // Erro ao acessar o banco de dados
                mostrarCarregamento(false)
                showErrorMessage("Erro ao fazer login. Tente novamente.")
            }
        }
    }

    private fun showBiometricEnrollDialog(cpf: String, senha: String, nome: String, placaCavalo: String, placaCarreta: String) {
        // Perguntar se o usuário quer habilitar login biométrico
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Login Biométrico")
            .setMessage("Deseja ativar o login por impressão digital para facilitar seus próximos acessos?")
            .setPositiveButton("Sim") { _, _ ->
                // Salvar credenciais para biometria
                sharedPrefsManager.saveBiometricCredentials(cpf, senha)

                // Continuar com o login usando os dados passados como parâmetro
                completarLogin(cpf, nome, placaCavalo, placaCarreta)

                // Mostrar confirmação
                Toast.makeText(this,
                    "Login biométrico ativado com sucesso!",
                    Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Não") { _, _ ->
                // Continuar com o login sem salvar biometria
                completarLogin(cpf, nome, placaCavalo, placaCarreta)
            }
            .show()
    }

    private fun completarLogin(cpf: String, nome: String, placaCavalo: String, placaCarreta: String) {
        // Usar o método correto que existe no SharedPrefsManager
        sharedPrefsManager.salvarDadosUsuario(cpf, nome, placaCavalo, placaCarreta)
        mostrarCarregamento(false)
        irParaTelaInicial()
    }

    private fun irParaTelaInicial() {
        startActivity(Intent(this, MainActivity::class.java))

        // Adicionar uma animação de transição suave
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

        finish()
    }
}