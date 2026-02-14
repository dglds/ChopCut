package com.chopcut.data.audio

data class WaveformConfig(
    val samplingRate: Int = 100,
    val minThreshold: Float = 0.05f,
    val maxThreshold: Float = 0.2f,
    val sensitivityMultiplier: Float = 1.0f,
    val useMedian: Boolean = true,
    val targetBarCount: Int = 400,
    val preset: WaveformPreset = WaveformPreset.Medium
) {
    companion object {
        val DEFAULT = WaveformConfig()
        
        fun fromPreset(preset: WaveformPreset) = when (preset) {
            WaveformPreset.Minimal -> WaveformConfig(
                samplingRate = 200,
                minThreshold = 0.03f,
                sensitivityMultiplier = 1.5f,
                targetBarCount = 100,
                preset = preset
            )
            WaveformPreset.Low -> WaveformConfig(
                samplingRate = 150,
                minThreshold = 0.04f,
                sensitivityMultiplier = 1.2f,
                targetBarCount = 200,
                preset = preset
            )
            WaveformPreset.Medium -> WaveformConfig(
                samplingRate = 100,
                minThreshold = 0.05f,
                sensitivityMultiplier = 1.0f,
                targetBarCount = 400,
                preset = preset
            )
            WaveformPreset.High -> WaveformConfig(
                samplingRate = 50,
                minThreshold = 0.05f,
                sensitivityMultiplier = 0.8f,
                targetBarCount = 600,
                preset = preset
            )
            WaveformPreset.Custom -> WaveformConfig(preset = preset)
        }
    }
    
    fun isCustom(): Boolean = preset == WaveformPreset.Custom
}

enum class WaveformPreset(val displayName: String) {
    Minimal("Mínima"),
    Low("Baixa"),
    Medium("Média"),
    High("Alta"),
    Custom("Personalizada")
}
