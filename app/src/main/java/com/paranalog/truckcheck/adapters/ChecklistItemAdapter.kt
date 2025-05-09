package com.paranalog.truckcheck.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.models.ItemChecklist

class ChecklistItemAdapter(
    private val context: Context,
    private var itens: List<ItemChecklist>,
    private val onFotoClick: (Int) -> Unit,
    private val onStatusChanged: (Int, String) -> Unit,
    private val onComentarioChanged: (Int, String) -> Unit
) : RecyclerView.Adapter<ChecklistItemAdapter.ChecklistItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistItemViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_checklist_inspecao, parent, false)
        return ChecklistItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChecklistItemViewHolder, position: Int) {
        val item = itens[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = itens.size

    /**
     * Atualiza a lista de itens de checklist
     * Esta função aceita qualquer tipo de lista e tenta convertê-la para List<ItemChecklist>
     */
    @Suppress("UNCHECKED_CAST")
    fun updateList(newList: List<*>) {
        try {
            // Tentar converter a lista diretamente
            this.itens = newList as List<ItemChecklist>
            notifyDataSetChanged()
        } catch (e: ClassCastException) {
            // Se falhar, verificar se cada item é do tipo ItemChecklist
            val filteredList = newList.filterIsInstance<ItemChecklist>()
            if (filteredList.isNotEmpty()) {
                this.itens = filteredList
                notifyDataSetChanged()
            } else {
                // Se não houver itens válidos, logar erro
                android.util.Log.e("ChecklistItemAdapter", "Não foi possível converter a lista para ItemChecklist", e)
            }
        }
    }

    fun getItemsList(): List<ItemChecklist> {
        return itens
    }

    fun setFotoPath(position: Int, uri: Uri?) {
        if (position < itens.size) {
            val item = itens[position]
            val updatedItem = item.copy(fotoPath = uri?.toString())
            val newList = itens.toMutableList()
            newList[position] = updatedItem
            itens = newList
            notifyItemChanged(position)
        }
    }

    inner class ChecklistItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvItemNumero: TextView = itemView.findViewById(R.id.tvItemNumero)
        private val tvItemDescricao: TextView = itemView.findViewById(R.id.tvItemDescricao)
        private val radioGroup: RadioGroup = itemView.findViewById(R.id.radioGroup)
        private val radioSim: RadioButton = itemView.findViewById(R.id.radioSim)
        private val radioNao: RadioButton = itemView.findViewById(R.id.radioNao)
        private val radioNa: RadioButton = itemView.findViewById(R.id.radioNa)
        private val tilComentario: TextInputLayout = itemView.findViewById(R.id.tilComentario)
        private val etComentario: TextInputEditText = itemView.findViewById(R.id.etComentario)
        private val btnAddFoto: Button = itemView.findViewById(R.id.btnAddFoto)
        private val ivFoto: ImageView = itemView.findViewById(R.id.ivFoto)

        fun bind(item: ItemChecklist, position: Int) {
            tvItemNumero.text = "${item.numeroItem}."
            tvItemDescricao.text = item.descricao

            // Limpar listeners antigos para evitar chamadas duplicadas
            radioGroup.setOnCheckedChangeListener(null)
            etComentario.removeTextChangedListener(null)

            // Configurar estado do radioGroup
            when (item.status) {
                "SIM" -> radioSim.isChecked = true
                "NÃO" -> radioNao.isChecked = true
                "N/A" -> radioNa.isChecked = true
                else -> radioGroup.clearCheck()
            }

            // Configurar comentário
            etComentario.setText(item.comentario)

            // Configurar foto
            if (item.fotoPath != null) {
                ivFoto.visibility = View.VISIBLE
                try {
                    ivFoto.setImageURI(Uri.parse(item.fotoPath))
                } catch (e: Exception) {
                    // Caso haja algum problema ao carregar a imagem
                    ivFoto.visibility = View.GONE
                }
            } else {
                ivFoto.visibility = View.GONE
            }

            // Configurar listeners
            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                val status = when (checkedId) {
                    R.id.radioSim -> "SIM"
                    R.id.radioNao -> "NÃO"
                    R.id.radioNa -> "N/A"
                    else -> ""
                }
                onStatusChanged(position, status)
            }

            etComentario.doAfterTextChanged { text ->
                onComentarioChanged(position, text?.toString() ?: "")
            }

            btnAddFoto.setOnClickListener {
                onFotoClick(position)
            }

            ivFoto.setOnClickListener {
                onFotoClick(position)
            }
        }
    }
}