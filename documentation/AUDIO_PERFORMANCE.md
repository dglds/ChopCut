# Investigação de Performance: Extração de Áudio 🚀

Este documento resume as otimizações e o plano de instrumentação implementados para a extração de áudio no ChopCut, focando em **velocidade máxima** e rastreamento via **Perfetto**.

## 🎯 Objetivos
1.  **Velocidade Extrema**: Extrair áudio e gerar waveforms no menor tempo possível.
2.  **Qualidade Mínima**: Sacrificar a fidelidade do áudio em prol do ganho de performance.
3.  **Observabilidade**: Medir cada etapa do pipeline para identificar gargalos no hardware/OS.

---

## 🛠️ Mudanças Implementadas

### 1. `AudioExtractor` (Remuxing Direto)
*   **Traces**: Adicionados blocos `AudioExtractor.Setup` e `AudioExtractor.CopyTrack`.
*   **JNI Optimization**: Alterado `ByteBuffer.allocate` para `ByteBuffer.allocateDirect`. Isso evita cópias desnecessárias entre a Heap do Java e a memória nativa durante a extração via `MediaExtractor`.
*   **Trace por Amostra**: Marcações detalhadas em `ReadSample`, `WriteSample` e `Advance` para medir latência de I/O de disco.

### 2. `WaveformExtractor` (Waveform Ultra-Rápido)
*   **Qualidade Degradada**: Implementado `step 4` no processamento de amostras PCM. Agora o extrator pula 75% das amostras, processando apenas 1 a cada 4 frames.
*   **Ganho de CPU**: Redução drástica no tempo de processamento matemático de normalização e boost de voz.
*   **Traces**: Marcações em `Setup`, `DecodeLoop` (decodificação MediaCodec) e `ThresholdPhase` (pós-processamento).

### 3. Correção de Estabilidade (`VideoTimeline`)
*   **Crash Fix**: Corrigido erro de `IllegalArgumentException: x + width must be <= bitmap.width()`.
*   **Causa**: Descompasso entre o layout de sprites (3 colunas) e o componente de UI (que esperava 7).
*   **Solução**: Sincronização com `THUMBS_PER_SPRITE = 3` e adição de `coerceIn` para evitar recortes fora dos limites da imagem.

---

## 📊 Como Realizar o Profiling

### Passo 1: Executar o Script de Trace
Na raiz do projeto, execute o script automatizado que limpa o cache, inicia o Perfetto e abre o app:

```bash
./run_perfetto_trace.sh
```

### Passo 2: Analisar no Perfetto UIººººººººº]]]]]ºººººº]]]]]ººº}}}
1.  Acesse [ui.perfetto.dev](https://ui.perfetto.dev/).
2.  Arraste o arquivo `.perfetto-trace` gerado para a página.
3.  Use as queries SQL abaixo na aba **Query (SQL)** para extrair métricas precisas.

---

## 🔍 Query Mestra de Investigação (SQL)

Cole esta query única na aba **Query (SQL)** do Perfetto para ter um relatório completo e unificado de todo o pipeline de Áudio e Thumbnails, incluindo o comparativo.

```sql
SELECT 
  name as Operacao, 
  COUNT(1) as Total_Chamadas, 
  ROUND(MIN(dur)/1000000.0, 3) as Min_ms,
  ROUND(MAX(dur)/1000000.0, 3) as Max_ms,
  ROUND(AVG(dur)/1000000.0, 3) as Media_ms,
  ROUND(SUM(dur)/1000000.0, 3) as Tempo_Total_Gasto_ms,
  CASE 
    WHEN name LIKE 'AudioExtractor.%' THEN 'Áudio (Arquivo)'
    WHEN name LIKE 'WaveformExtractor.%' THEN 'Áudio (Waveform)'
    WHEN name LIKE 'TSM.%' THEN 'Thumbnails'
    ELSE 'Outros'
  END as Categoria
FROM slice 
WHERE name LIKE 'Audio%' OR name LIKE 'TSM.%'
GROUP BY name
ORDER BY Tempo_Total_Gasto_ms DESC;
```

### 📊 Resultado da Última Medição (Pós-otimização com `seekTo` e `step 4`):

| Operacao | Total_Chamadas | Min_ms | Max_ms | Media_ms | Tempo_Total_Gasto_ms | Categoria |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| WaveformExtractor.extractRawPcmData | 1 | 1431.311 | 1431.311 | 1431.311 | 1431.311 | Áudio (Waveform) |
| WaveformExtractor.DecodeLoop | 1 | 1259.793 | 1259.793 | 1259.793 | 1259.793 | Áudio (Waveform) |
| WaveformExtractor.Setup | 1 | 104.222 | 104.222 | 104.222 | 104.222 | Áudio (Waveform) |
| WaveformExtractor.ThresholdPhase | 1 | 66.782 | 66.782 | 66.782 | 66.782 | Áudio (Waveform) |

**Análise:** Uma redução massiva de **8.2s** para **1.4s** (~83% de ganho de performance). O `seekTo` permitiu que o MediaCodec processasse apenas os frames necessários, eliminando o gargalo de decodificação linear. 🚀

---

## 📈 KPIs Esperados
*   **Tempo de Setup**: < 80ms (Instanciação do Codec).
*   **Extração de Waveform**: Deve ser pelo menos 3x mais rápida que a extração de thumbnails.
*   **JNI Overhead**: O tempo de `ReadSample` deve ser dominado pelo I/O nativo, não por gerenciamento de memória da JVM.


| Operacao | Total_Chamadas | Min_ms | Max_ms | Media_ms | Tempo_Total_Gasto_ms | Categoria |
| --- | --- | --- | --- | --- | --- | --- |
| WaveformExtractor.extractRawPcmData | 1 | 1431.311 | 1431.311 | 1431.311 | 1431.311 | Áudio (Waveform) |
| WaveformExtractor.DecodeLoop | 1 | 1259.793 | 1259.793 | 1259.793 | 1259.793 | Áudio (Waveform) |
| WaveformExtractor.Setup | 1 | 104.222 | 104.222 | 104.222 | 104.222 | Áudio (Waveform) |
| WaveformExtractor.ThresholdPhase | 1 | 66.782 | 66.782 | 66.782 | 66.782 | Áudio (Waveform) |

---

## ⚠️ Requisitos e Restrições de Projeto / Teste

> [!IMPORTANT]
> **Manter Caches de Áudio e Fotos Desativados (`cacheEnabled = false`)**:
> Por diretriz estrita do projeto (definida pelo Chefe/Liderança), **todos os caches de áudio e de imagens (miniaturas/fotos) na timeline devem permanecer obrigatoriamente desativados**.
> 
> * **Áreas Afetadas**:
>   - **Áudio (Waveform)** em [AudioViewModel.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/ui/viewmodel/AudioViewModel.kt)
>   - **Fotos (Timeline Thumbnails)** em [OptimizedThumbnailProvider.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/data/thumbnail/OptimizedThumbnailProvider.kt)
>   - **Strips de Thumbnail** em [ThumbnailCacheManager.kt](file:///home/diego/Android/ChopCut/app/src/main/java/com/chopcut/data/thumbnail/ThumbnailCacheManager.kt)
> 
> * **Motivo**: Garantir que todas as sessões de perfilamento, testes de stress de UI, latência nativa e instrumentação via Perfetto meçam a latência real e "crua" da extração física e decodificação do hardware (MediaCodec / MediaExtractor / MediaMetadataRetriever) de forma 100% limpa, evitando "falsos positivos" ou medições mascaradas por cache hits.
> * **Ação**: Esta configuração é imutável e não deve ser alterada ou ativada sob nenhuma hipótese durante esta fase de homologação e validação técnica da timeline do ChopCut.


