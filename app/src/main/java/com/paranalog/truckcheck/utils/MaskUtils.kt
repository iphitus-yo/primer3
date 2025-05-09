package com.paranalog.truckcheck.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

object MaskUtils {
    fun setupCpfMask(editText: EditText) {
        // Configurar para não mudar de campo automaticamente
        editText.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE

        // Aumentar o maxLength para acomodar a máscara completa
        editText.filters = arrayOf(android.text.InputFilter.LengthFilter(14))

        editText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) {
                    return
                }

                try {
                    isUpdating = true

                    // Remover caracteres não numéricos
                    val str = s.toString().replace("[^\\d]".toRegex(), "")
                    val sb = StringBuilder()

                    // Garantir que todos os 11 dígitos possam ser inseridos
                    val maxDigits = minOf(str.length, 11)

                    for (i in 0 until maxDigits) {
                        sb.append(str[i])
                        if (i == 2 || i == 5) {
                            sb.append(".")
                        } else if (i == 8) {
                            sb.append("-")
                        }
                    }

                    val formattedText = sb.toString()

                    // Atualizar o texto apenas se for diferente
                    if (formattedText != editText.text.toString()) {
                        editText.removeTextChangedListener(this)
                        editText.setText(formattedText)

                        // Colocar o cursor no final
                        editText.setSelection(formattedText.length)
                        editText.addTextChangedListener(this)
                    }
                } catch (e: Exception) {
                    // Tratar exceções silenciosamente
                } finally {
                    isUpdating = false
                }
            }
        })
    }

    fun setupPlacaMask(editText: EditText) {
        // Desativar a ação de "Next" no teclado que pode estar causando mudanças de foco
        editText.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NONE

        editText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating || s == null) {
                    return
                }

                try {
                    isUpdating = true

                    val str = s.toString().replace("[^a-zA-Z0-9]".toRegex(), "").uppercase()
                    val sb = StringBuilder()

                    // Processa a entrada para o formato LLLNLNN
                    var validChars = 0
                    var i = 0

                    // Processa as 3 primeiras letras (LLL)
                    while (i < str.length && validChars < 3) {
                        val c = str[i]
                        i++

                        if (c.isLetter()) {
                            sb.append(c)
                            validChars++
                        }
                    }

                    // Processa o primeiro número (N)
                    while (i < str.length && validChars == 3) {
                        val c = str[i]
                        i++

                        if (c.isDigit()) {
                            sb.append(c)
                            validChars++
                        }
                    }

                    // Processa a letra no meio (L)
                    while (i < str.length && validChars == 4) {
                        val c = str[i]
                        i++

                        if (c.isLetter()) {
                            sb.append(c)
                            validChars++
                        }
                    }

                    // Processa os dois últimos números (NN)
                    while (i < str.length && validChars < 7) {
                        val c = str[i]
                        i++

                        if (c.isDigit() && (validChars == 5 || validChars == 6)) {
                            sb.append(c)
                            validChars++
                        }
                    }

                    // Atualizar texto apenas se for diferente
                    val formattedText = sb.toString()
                    if (formattedText != editText.text.toString()) {
                        editText.removeTextChangedListener(this)
                        editText.setText(formattedText)

                        // Posicionar cursor no final
                        if (formattedText.isNotEmpty()) {
                            editText.setSelection(formattedText.length)
                        }
                        editText.addTextChangedListener(this)
                    }
                } catch (e: Exception) {
                    // Captura exceções silenciosamente
                } finally {
                    isUpdating = false
                }
            }
        })
    }
}