package com.paranalog.truckcheck.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.paranalog.truckcheck.R

class ManualActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    // DEFINA AQUI O NÚMERO DO SUPORTE (formato: código país + DDD + número)
    private val NUMERO_SUPORTE = "5545998107708" // ← ALTERE AQUI PARA SEU NÚMERO!

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)

        webView = findViewById(R.id.webViewManual)
        setupWebView()
        loadManual()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Configurar settings do WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            setSupportZoom(false)
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // Configurar WebViewClient para interceptar URLs customizadas
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return when (url) {
                    "truckcheck://goback" -> {
                        finish()
                        true
                    }
                    "truckcheck://whatsapp" -> {
                        abrirWhatsApp()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun loadManual() {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Manual TruckCheck</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        line-height: 1.6;
                        color: #1f2937;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        margin: 0;
                        padding: 0;
                    }

                    .container {
                        max-width: 100%;
                        background: white;
                        min-height: 100vh;
                        box-shadow: 0 0 30px rgba(0,0,0,0.1);
                    }

                    .header {
                        background: linear-gradient(135deg, #1e3a8a, #3b82f6, #06b6d4);
                        color: white;
                        padding: 24px 20px;
                        text-align: center;
                        position: relative;
                        overflow: hidden;
                    }

                    .header::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 100" fill="rgba(255,255,255,0.1)"><polygon points="0,100 1000,100 1000,0"/></svg>');
                        background-size: cover;
                    }

                    .header * {
                        position: relative;
                        z-index: 1;
                    }

                    .header h1 {
                        font-size: 26px;
                        margin-bottom: 8px;
                        font-weight: 700;
                        text-shadow: 0 2px 4px rgba(0,0,0,0.3);
                    }

                    .header p {
                        font-size: 16px;
                        opacity: 0.95;
                        font-weight: 400;
                    }

                    .btn-back {
                        background: rgba(255,255,255,0.25);
                        color: white;
                        border: 2px solid rgba(255,255,255,0.3);
                        padding: 10px 20px;
                        border-radius: 25px;
                        font-size: 14px;
                        font-weight: 600;
                        margin-bottom: 20px;
                        text-decoration: none;
                        display: inline-block;
                        backdrop-filter: blur(10px);
                        transition: all 0.3s ease;
                    }

                    .btn-back:hover {
                        background: rgba(255,255,255,0.35);
                        transform: translateY(-2px);
                    }

                    .content {
                        padding: 24px 20px;
                        background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
                    }

                    .section {
                        background: linear-gradient(145deg, #ffffff, #f8fafc);
                        margin-bottom: 20px;
                        padding: 24px;
                        border-radius: 16px;
                        border: 1px solid #e2e8f0;
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                        position: relative;
                        overflow: hidden;
                    }

                    .section::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 4px;
                        height: 100%;
                        background: linear-gradient(180deg, #3b82f6, #06b6d4, #10b981);
                    }

                    .section h2 {
                        color: #1e40af;
                        margin-bottom: 20px;
                        font-size: 20px;
                        font-weight: 700;
                        display: flex;
                        align-items: center;
                        text-shadow: 0 1px 2px rgba(0,0,0,0.1);
                    }

                    .section h2 .emoji {
                        margin-right: 12px;
                        font-size: 24px;
                        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.1));
                    }

                    .step {
                        background: linear-gradient(135deg, #eff6ff, #dbeafe, #bfdbfe);
                        padding: 20px;
                        margin: 16px 0;
                        border-left: 5px solid #3b82f6;
                        border-radius: 12px;
                        position: relative;
                        box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
                        transition: transform 0.2s ease;
                    }

                    .step:hover {
                        transform: translateX(4px);
                    }

                    .step strong {
                        color: #1e40af;
                        display: block;
                        margin-bottom: 12px;
                        font-size: 16px;
                        font-weight: 700;
                    }

                    .step ul {
                        margin-left: 20px;
                        margin-top: 12px;
                    }

                    .step li {
                        margin-bottom: 6px;
                        font-weight: 500;
                    }

                    .status-grid {
                        display: grid;
                        grid-template-columns: 1fr;
                        gap: 16px;
                        margin: 20px 0;
                    }

                    .status-item {
                        display: flex;
                        align-items: center;
                        padding: 20px;
                        border-radius: 12px;
                        border: 2px solid;
                        position: relative;
                        overflow: hidden;
                        transition: transform 0.2s ease;
                    }

                    .status-item:hover {
                        transform: scale(1.02);
                    }

                    .status-verde {
                        background: linear-gradient(135deg, #ecfdf5, #d1fae5);
                        border-color: #10b981;
                        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2);
                    }

                    .status-amarelo {
                        background: linear-gradient(135deg, #fffbeb, #fef3c7);
                        border-color: #f59e0b;
                        box-shadow: 0 4px 12px rgba(245, 158, 11, 0.2);
                    }

                    .status-vermelho {
                        background: linear-gradient(135deg, #fef2f2, #fecaca);
                        border-color: #ef4444;
                        box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
                    }

                    .status-icon {
                        font-size: 32px;
                        margin-right: 16px;
                        filter: drop-shadow(0 2px 4px rgba(0,0,0,0.1));
                    }

                    .status-info strong {
                        display: block;
                        margin-bottom: 6px;
                        font-size: 18px;
                        font-weight: 700;
                    }

                    .status-info small {
                        font-size: 14px;
                        opacity: 0.9;
                        font-weight: 500;
                    }

                    .status-verde strong { color: #065f46; }
                    .status-verde small { color: #047857; }
                    .status-amarelo strong { color: #92400e; }
                    .status-amarelo small { color: #b45309; }
                    .status-vermelho strong { color: #991b1b; }
                    .status-vermelho small { color: #b91c1c; }

                    .photo-steps {
                        counter-reset: step-counter;
                        margin: 20px 0;
                    }

                    .photo-step {
                        counter-increment: step-counter;
                        display: flex;
                        align-items: flex-start;
                        margin-bottom: 16px;
                        padding: 12px 0;
                        transition: transform 0.2s ease;
                    }

                    .photo-step:hover {
                        transform: translateX(8px);
                    }

                    .photo-step::before {
                        content: counter(step-counter);
                        background: linear-gradient(135deg, #3b82f6, #1d4ed8);
                        color: white;
                        border-radius: 50%;
                        width: 32px;
                        height: 32px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 14px;
                        font-weight: bold;
                        margin-right: 16px;
                        flex-shrink: 0;
                        margin-top: 2px;
                        box-shadow: 0 4px 8px rgba(59, 130, 246, 0.3);
                    }

                    .tip-box {
                        background: linear-gradient(135deg, #fff7ed, #fed7aa);
                        border: 2px solid #f97316;
                        padding: 20px;
                        border-radius: 12px;
                        margin-top: 20px;
                        box-shadow: 0 4px 12px rgba(249, 115, 22, 0.2);
                    }

                    .tip-box p {
                        color: #9a3412;
                        font-size: 15px;
                        font-weight: 600;
                        margin: 0;
                    }

                    .tips-list {
                        list-style: none;
                        padding: 0;
                        margin: 16px 0;
                    }

                    .tips-list li {
                        padding: 12px 16px;
                        margin-bottom: 8px;
                        background: linear-gradient(135deg, #f1f5f9, #e2e8f0);
                        border-radius: 8px;
                        border-left: 4px solid #64748b;
                        font-weight: 500;
                        transition: all 0.2s ease;
                    }

                    .tips-list li:hover {
                        background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
                        transform: translateX(4px);
                    }

                    .btn-whatsapp {
                        background: linear-gradient(135deg, #25d366, #128c7e, #075e54);
                        color: white;
                        padding: 18px 28px;
                        border: none;
                        border-radius: 16px;
                        font-size: 18px;
                        font-weight: 700;
                        cursor: pointer;
                        width: 100%;
                        margin-top: 20px;
                        text-decoration: none;
                        display: block;
                        text-align: center;
                        box-shadow: 0 8px 25px rgba(37, 211, 102, 0.4);
                        transition: all 0.3s ease;
                        position: relative;
                        overflow: hidden;
                    }

                    .btn-whatsapp::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: -100%;
                        width: 100%;
                        height: 100%;
                        background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
                        transition: left 0.5s;
                    }

                    .btn-whatsapp:hover::before {
                        left: 100%;
                    }

                    .btn-whatsapp:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 12px 35px rgba(37, 211, 102, 0.5);
                    }

                    .footer {
                        background: linear-gradient(135deg, #1f2937, #374151, #4b5563);
                        padding: 32px 20px;
                        text-align: center;
                        color: #f9fafb;
                        position: relative;
                        overflow: hidden;
                    }

                    .footer::before {
                        content: '';
                        position: absolute;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 100" fill="rgba(255,255,255,0.05)"><polygon points="0,0 1000,100 1000,0"/></svg>');
                        background-size: cover;
                    }

                    .footer * {
                        position: relative;
                        z-index: 1;
                    }

                    .footer .highlight {
                        color: #60a5fa;
                        font-weight: 800;
                        font-size: 20px;
                        margin-bottom: 12px;
                        text-shadow: 0 2px 4px rgba(0,0,0,0.3);
                    }

                    .footer p {
                        font-size: 16px;
                        font-weight: 600;
                        margin-bottom: 16px;
                    }

                    .footer .status-legend {
                        font-size: 15px;
                        font-weight: 600;
                        background: rgba(255,255,255,0.1);
                        padding: 12px 16px;
                        border-radius: 8px;
                        backdrop-filter: blur(10px);
                        display: inline-block;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <a href="truckcheck://goback" class="btn-back">← Voltar</a>
                        <h1>📱 Manual TruckCheck</h1>
                        <p>Guia rápido para motoristas</p>
                    </div>

                    <div class="content">
                        <div class="section">
                            <h2><span class="emoji">🚀</span>Como fazer um Checklist</h2>
                            
                            <div class="step">
                                <strong>1º PASSO - Entrar no App</strong>
                                Toque no botão verde <strong>"Novo Checklist"</strong>
                            </div>
                            
                            <div class="step">
                                <strong>2º PASSO - Preencher Dados</strong>
                                <ul>
                                    <li><strong>CRT/MIC/DUE:</strong> Número do documento</li>
                                    <li><strong>Nº Lacre:</strong> Número do lacre</li>
                                    <li><strong>Peso Bruto:</strong> Ex: 15.000</li>
                                    <li><strong>Marcar:</strong> Entrada ☑️ Saída ☑️ Pernoite ☑️</li>
                                </ul>
                            </div>
                            
                            <div class="step">
                                <strong>3º PASSO - Fazer Inspeção</strong>
                                Para cada item da lista:
                                <ul>
                                    <li><strong>✅ SIM</strong> = Item está OK</li>
                                    <li><strong>❌ NÃO</strong> = Item tem problema</li>
                                    <li><strong>📷 FOTO</strong> = Obrigatória se marcar "NÃO"</li>
                                </ul>
                            </div>
                            
                            <div class="step">
                                <strong>4º PASSO - Salvar</strong>
                                Toque no botão <strong>"SALVAR"</strong> e aguarde até 15 segundos
                            </div>
                        </div>

                        <div class="section">
                            <h2><span class="emoji">🚦</span>Entenda as Cores</h2>
                            <div class="status-grid">
                                <div class="status-item status-verde">
                                    <span class="status-icon">✅</span>
                                    <div class="status-info">
                                        <strong>🟢 VERDE</strong>
                                        <small>Email enviado - Tudo certo!</small>
                                    </div>
                                </div>
                                
                                <div class="status-item status-amarelo">
                                    <span class="status-icon">⚠️</span>
                                    <div class="status-info">
                                        <strong>🟡 AMARELO</strong>
                                        <small>Sem internet - Enviará depois</small>
                                    </div>
                                </div>
                                
                                <div class="status-item status-vermelho">
                                    <span class="status-icon">❌</span>
                                    <div class="status-info">
                                        <strong>🔴 VERMELHO</strong>
                                        <small>Incompleto - Precisa terminar</small>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="section">
                            <h2><span class="emoji">📷</span>Como tirar Fotos</h2>
                            <div class="photo-steps">
                                <div class="photo-step">Toque no ícone 📷 do item</div>
                                <div class="photo-step">Escolha: "Tirar foto" ou "Galeria"</div>
                                <div class="photo-step">Aponte bem para o problema</div>
                                <div class="photo-step">Confirme tocando em ✅</div>
                            </div>
                            
                            <div class="tip-box">
                                <p>💡 <strong>Dica:</strong> Foto é obrigatória quando marcar "NÃO" em qualquer item!</p>
                            </div>
                        </div>

                        <div class="section">
                            <h2><span class="emoji">💡</span>Dicas Importantes</h2>
                            <ul class="tips-list">
                                <li>📶 <strong>Com sinal:</strong> Fica 🟢 verde na hora</li>
                                <li>📶 <strong>Sem sinal:</strong> Fica 🟡 amarelo, envia depois</li>
                                <li>🔋 <strong>Bateria:</strong> Mantenha celular carregado</li>
                                <li>🆘 <strong>App travou:</strong> Feche e abra novamente</li>
                                <li>❓ <strong>Não salvou:</strong> Tente novamente com internet</li>
                            </ul>
                        </div>

                        <div class="section">
                            <h2><span class="emoji">📞</span>Precisa de Ajuda?</h2>
                            <p style="text-align: center; margin-bottom: 16px;">
                                Em caso de dúvidas ou problemas
                            </p>
                            <a href="truckcheck://whatsapp" class="btn-whatsapp">
                                💬 Falar no WhatsApp
                            </a>
                        </div>
                    </div>

                    <div class="footer">
                        <div class="highlight">💡 LEMBRE-SE:</div>
                        <p><strong>Checklist é obrigatório antes de sair com o caminhão!</strong></p>
                        <div class="status-legend">
                            🟢 Verde = Liberado | 🟡 Amarelo = Aguardar | 🔴 Vermelho = Completar
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun abrirWhatsApp() {
        try {
            val isWhatsAppInstalled = isAppInstalled("com.whatsapp")

            if (isWhatsAppInstalled) {
                abrirWhatsAppComNumero()
            } else {
                abrirWhatsAppNoBrowser()
            }
        } catch (e: Exception) {
            Toast.makeText(this,
                "Erro ao abrir WhatsApp. Verifique se está instalado.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun abrirWhatsAppComNumero() {
        try {
            val mensagem = "Olá! Preciso de ajuda com o app TruckCheck."
            val mensagemCodificada = Uri.encode(mensagem)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$NUMERO_SUPORTE?text=$mensagemCodificada")
            }

            startActivity(intent)
        } catch (e: Exception) {
            abrirWhatsAppNoBrowser()
        }
    }

    private fun abrirWhatsAppNoBrowser() {
        try {
            val mensagem = "Olá! Preciso de ajuda com o app TruckCheck."
            val mensagemCodificada = Uri.encode(mensagem)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$NUMERO_SUPORTE?text=$mensagemCodificada")
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this,
                "Não foi possível abrir o WhatsApp. Verifique sua conexão.",
                Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}