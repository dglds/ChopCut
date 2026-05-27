# Análise de Código: AppCore.kt

## Visão Geral
O `AppCore.kt` é o arquivo fundamental do aplicativo ChopCut. Ele consolida as configurações iniciais, o ciclo de vida do aplicativo, as definições globais de interface do usuário e os estados globais do sistema. Nenhuma lógica de feature específica deve residir neste arquivo.

## Responsabilidades
- **Inicialização do App**: Contém a classe `ChopCutApplication` que estende `Application` e inicializa frameworks essenciais na inicialização, além da `MainActivity` que atua como ponto de entrada para a interface gráfica Android.
- **Roteamento e Navegação**: Implementa o `ChopCutNavGraph`, que dita o mapa de telas e como o usuário navega da Home para o Editor, por exemplo.
- **Design System Básico (Tema)**: Abriga a definição visual básica do aplicativo Jetpack Compose (`Theme.kt`, `Color.kt`, `Type.kt`, `Spacing.kt`). Isso garante que todas as telas compartilhem as mesmas cores e tipografias.
- **Configurações e Estados Globais**: Classes de enumeração e constantes fundamentais, como `CompressionLevel`, configurações de áudio e configurações base de timeline, são definidas aqui para que qualquer funcionalidade do aplicativo possa referenciá-las de forma genérica.

## Quando alterar este arquivo?
- Ao adicionar uma nova fonte ou cor na paleta global.
- Ao adicionar uma nova rota/tela principal na navegação.
- Ao necessitar inicializar um SDK de terceiros no `onCreate` do App.
