# Análise de Código: CoreGraphics.kt

## Visão Geral
O arquivo `CoreGraphics.kt` encapsula toda a pesada infraestrutura gráfica, renderização de baixo nível (OpenGL) e os processamentos de pipeline de vídeo (Transformação e Codec). Devido à complexidade de manipulação de EGL no Android, este código é mantido em uma fronteira estrita.

## Responsabilidades
- **OpenGL & EGL**: Implementa renderizadores como o `GLRenderer` e a ponte de display de superfície (`SurfaceBridge`) necessários para colocar quadros de vídeo na tela através da GPU ou aplicar filtros.
- **Codecs e Media**: Mapeia capacidades de hardware via `CodecCapabilities`, para descobrir limites de compressão, taxa de bits (bitrate) e perfis (profiles) suportados pelo dispositivo Android no ato da exportação.
- **Video Pipeline (Exportação)**: Define a esteira (`Pipeline`) de processamento (`TransformerPipeline`, `CopyPipeline`, `TrimProgress`) responsável por gerar, encodar, recortar e salvar o arquivo final de vídeo exportado e repassar o progresso.

## Cuidados Especiais
- Alterações neste arquivo têm impacto direto em **desempenho** de reprodução e estabilidade de exportação. Falhas aqui causam `OutOfMemory` e problemas severos que paralisam a renderização de superfícies por todo o projeto.
- Classes de UI nunca devem falar diretamente com `CoreGraphics`. O `EditorFeature` interage através das ViewModels.
