# Especificação: Otimização de Thumbnails da Timeline e Aspect Ratio Dinâmico

## Visão Geral
O objetivo desta track é otimizar o processo de extração de thumbnails, montagem das "strips" e renderização na timeline do ChopCut. Atualmente, as thumbnails apresentam serrilhado (aliasing), a timeline possui dimensões fixas (60x40dp) e há uma necessidade de equilibrar uma visualização de alta qualidade com tempos de carregamento rápidos. O foco principal está na velocidade de extração inicial, na fluidez da renderização das strips durante o scroll, e na adaptação dinâmica do aspect ratio das thumbnails com base no vídeo carregado. Para equilibrar qualidade e velocidade, será implementada uma estratégia de carregamento progressivo: versões de baixa resolução (ou placeholders) serão mostradas inicialmente para garantir carregamento instantâneo, sendo posteriormente substituídas por versões de alta qualidade geradas em background.

## Requisitos Funcionais
- **Aspect Ratio Dinâmico:** Calcular e aplicar dinamicamente a largura e a altura das thumbnails na timeline com base nas proporções do vídeo carregado (ex: 16:9, 9:16, 1:1).
- **Carregamento Progressivo:** Implementar um sistema que exibe inicialmente imagens de baixa resolução (extração rápida) ou placeholders para cada "strip" da timeline.
- **Extração em Background (Alta Qualidade):** Executar a extração das thumbnails definitivas (alta resolução, sem serrilhado) em uma thread de background para não bloquear a UI.
- **Otimização de Armazenamento (WEBP):** Utilizar o formato **WEBP** para a compressão do cache em disco, garantindo que a eficiência de compressão compense o aumento da resolução base das thumbnails.
- **Atualização Assíncrona da UI:** Substituir as imagens de baixa resolução pelas de alta qualidade na timeline de forma imperceptível assim que estiverem prontas.
- **Filtro Anti-Aliasing:** Garantir que o processo de extração de alta qualidade utilize métodos de downscaling/resampling que eliminem o efeito de borda serrilhada (aliasing).
- **Montagem Otimizada de Strips:** Refatorar a montagem dos agrupamentos visuais (strips) para garantir fluidez durante o scroll.

## Requisitos Não Funcionais
- **Espaço em Disco:** O tamanho total do cache em disco não deve aumentar significativamente em relação ao sistema atual; a eficiência do formato WEBP deve mitigar o impacto da maior fidelidade das imagens.
- **Responsividade e Fluidez (FPS):** Manter o scroll da timeline suave (idealmente 60fps), mesmo com o sistema de atualização progressiva ocorrendo concorrentemente.
- **Rapidez no Primeiro Quadro (FCP):** O tempo até o usuário ver *alguma* representação na timeline após carregar o vídeo deve ser o menor possível (ex: < 1 segundo).
- **Eficiência de Memória:** O sistema de cache (LruCache) deve gerenciar os dois níveis de qualidade, descartando versões obsoletas para evitar `OutOfMemoryError`.

## Critérios de Aceite
1. As thumbnails na timeline refletem corretamente o aspect ratio do vídeo original carregado.
2. Ao carregar um vídeo, a timeline exibe imediatamente imagens de baixa resolução/placeholders sem travar a interface.
3. As thumbnails finais são nítidas, sem serrilhamento e carregadas progressivamente.
4. O uso de disco para o cache de thumbnails permanece controlado e otimizado via compressão WEBP.
5. Realizar scroll rápido é fluido, sem engasgos perceptíveis durante a troca de placeholders pelas imagens reais.

## Fora do Escopo
- Modificações na lógica de exportação de vídeo via Media3 Transformer.
- Alterações visuais no componente de Trim além do ajuste necessário para refletir as novas alturas baseadas no aspect ratio.
