package com.chopcut.ui.components.timeline

import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.chopcut.R
import com.chopcut.data.thumbnail.OptimizedThumbnailProvider
import com.chopcut.data.thumbnail.ThumbnailPriority

/**
 * Adapter para a timeline de thumbnails otimizada.
 * 
 * Gerencia ~900 posições de forma fluida através de:
 * - Mapeamento de posição -> timestamp quantizado (500ms)
 * - Atualização parcial via Payloads (PAYLOAD_THUMB)
 * - Prefetching de thumbnails próximos
 * - Reuso de views (RecyclerView)
 */
class TimelineAdapter(
    private val uri: Uri,
    private val durationMs: Long,
    private val itemCountLimit: Int = 900,
    private val provider: OptimizedThumbnailProvider,
    private val thumbWidth: Int = 120,
    private val thumbHeight: Int = 120
) : RecyclerView.Adapter<TimelineAdapter.ThumbnailViewHolder>() {

    companion object {
        private const val PAYLOAD_THUMB = "PAYLOAD_THUMB"
        private const val PREFETCH_DISTANCE = 30 // Janela de prefetch
    }

    // Map de timestamps pendentes para posições (para atualização batch)
    private val pendingPositions = mutableMapOf<Long, MutableList<Int>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_thumbnail, parent, false)
        
        // Ajustar largura proporcionalmente
        val params = view.layoutParams
        params.width = thumbWidth
        view.layoutParams = params
        
        return ThumbnailViewHolder(view)
    }

    override fun getItemCount(): Int = itemCountLimit

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        bind(holder, position, false)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_THUMB)) {
            bind(holder, position, true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bind(holder: ThumbnailViewHolder, position: Int, isPayload: Boolean) {
        val timestamp = (position.toLong() * durationMs) / itemCountLimit
        val quantizedTime = (timestamp / 500) * 500 // Quantização local (500ms)
        
        // Se for bind completo, resetar imagem
        if (!isPayload) {
            holder.thumbnailImage.setImageBitmap(null)
            holder.loadingOverlay.visibility = View.VISIBLE
        }

        // Adicionar esta posição ao registro de pendentes para este timestamp
        val positions = pendingPositions.getOrPut(quantizedTime) { mutableListOf() }
        if (!positions.contains(position)) {
            positions.add(position)
        }

        // Solicitar thumbnail (Prioridade VISIBLE para o bind atual)
        provider.requestThumbnail(uri, quantizedTime, ThumbnailPriority.VISIBLE)
        
        // Realizar prefetch das posições adjacentes
        performPrefetch(position)
    }

    private fun performPrefetch(currentPosition: Int) {
        val start = (currentPosition + 1).coerceAtMost(itemCountLimit - 1)
        val end = (currentPosition + PREFETCH_DISTANCE).coerceAtMost(itemCountLimit - 1)
        
        for (i in start..end) {
            val ts = (i.toLong() * durationMs) / itemCountLimit
            provider.requestThumbnail(uri, ts, ThumbnailPriority.PREFETCH)
        }
    }

    /**
     * Atualiza os thumbnails visíveis quando novos bitmaps são gerados pelo provedor.
     */
    fun onThumbnailLoaded(timestamp: Long, bitmap: Bitmap) {
        val positions = pendingPositions.remove(timestamp) ?: return
        
        // Notificar mudanças para todas as posições que mapeiam para este timestamp
        positions.forEach { pos ->
            notifyItemChanged(pos, PAYLOAD_THUMB)
        }
    }

    class ThumbnailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnailImage: ImageView = view.findViewById(R.id.thumbnailImage)
        val loadingOverlay: View = view.findViewById(R.id.loadingOverlay)
    }
}
