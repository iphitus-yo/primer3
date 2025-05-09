package com.paranalog.truckcheck.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.paranalog.truckcheck.R
import com.paranalog.truckcheck.models.Checklist
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador para a lista de checklists na tela principal
 */
class ChecklistAdapter(
    private val context: Context,
    private var checklists: List<Checklist>,
    private val onClickListener: (Checklist) -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.cardView)
        val tvPlacas: TextView = view.findViewById(R.id.tvPlacas)
        val tvData: TextView = view.findViewById(R.id.tvData)
        val tvCrt: TextView = view.findViewById(R.id.tvCrt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_checklist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val checklist = checklists[position]

        // Configurar as placas (cavalo/carreta)
        val placas = "${checklist.placaCavalo ?: "N/A"} / ${checklist.placaCarreta ?: "N/A"}"
        holder.tvPlacas.text = placas

        // Configurar a data
        holder.tvData.text = dateFormat.format(checklist.data)

        // Configurar o CRT/MIC
        holder.tvCrt.text = "CRT/MIC: ${checklist.crtMicDue ?: "N/A"}"

        // Configurar clique no item
        holder.cardView.setOnClickListener {
            onClickListener(checklist)
        }
    }

    override fun getItemCount(): Int = checklists.size

    /**
     * Atualiza a lista de checklists com novos dados
     * Esta função aceita tanto List<Checklist> quanto List<Any>
     */
    @Suppress("UNCHECKED_CAST")
    fun updateList(newChecklists: List<*>) {
        try {
            // Tentar converter a lista para List<Checklist>
            this.checklists = newChecklists as List<Checklist>
            notifyDataSetChanged()
        } catch (e: ClassCastException) {
            // Se falhar, verificar se cada item é Checklist
            val filteredList = newChecklists.filterIsInstance<Checklist>()
            if (filteredList.isNotEmpty()) {
                this.checklists = filteredList
                notifyDataSetChanged()
            } else {
                // Se não houver itens válidos, logar erro
                android.util.Log.e("ChecklistAdapter", "Não foi possível converter a lista para Checklist", e)
            }
        }
    }
}