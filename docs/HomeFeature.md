# Análise de Código: HomeFeature.kt

## Visão Geral
`HomeFeature.kt` encapsula, isoladamente, a tela inicial (ponto de aterrissagem) do usuário e suas lógicas subjacentes de busca de mídia (MVI/MVVM), formando uma unidade contida e coesa.

## Responsabilidades
- **HomeScreen (UI)**: O Composables que montam a hierarquia visual da tela de início, lidando com permissões de armazenamento, exibição de projetos recentes, botões para importação de vídeos e menus gerais.
- **HomeViewModel & State**: Arquitetura que cuida da obtenção e armazenamento do estado da Home. Pode injetar repositórios (vindos do `CoreData.kt`) para ler vídeos locais via MediaStore e apresentá-los na lista antes de enviar para o editor.
- Ao clicar em um vídeo selecionado, esta Feature engatilha a rota via Navigation que transporta a URI do arquivo diretamente para o `EditorFeature.kt`.

## Quando alterar este arquivo?
- Se você precisar atualizar o layout da página inicial.
- Se a página inicial começar a mostrar configurações de usuário de cara, listas adicionais de templates ou lógica de onboarding.
