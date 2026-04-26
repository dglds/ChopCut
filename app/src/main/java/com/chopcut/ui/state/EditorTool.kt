package com.chopcut.ui.state

/**
 * Representa as ferramentas disponíveis no Editor Unificado.
 */
enum class EditorTool {
    NONE,       // Menu principal (sem ferramenta selecionada)
    TRIM,       // Cortar trechos (Trim)
    ADD_MEDIA,  // Concatenar vídeos (Join)
    FORMAT,     // Alterar resolução (Resize / Aspect Ratio)
    CROP,       // Recortar área (Crop)
    COMPRESS,   // Reduzir tamanho (Compress)
    AUDIO       // Extrair/Modificar trilha sonora (Audio)
}
