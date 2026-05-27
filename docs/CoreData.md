# Análise de Código: CoreData.kt

## Visão Geral
O `CoreData.kt` é responsável pela camada `Data` (Modelo, Repositórios locais e Cache) global do app ChopCut. Toda interfaceação com o armazenamento do aparelho e abstração da fonte de dados é exposta aqui, mantendo o restante do app isolado de bibliotecas como o Room ou Shared Preferences.

## Responsabilidades
- **Modelos de Dados Globais (Data Classes)**: Contém as estruturas de dados fundamentais (`VideoInfo`, `VideoRange`, `Size`, `Transform`, `FilterType`, `ThumbnailSettings`) que circulam através de todas as camadas.
- **Gerenciamento Local (Storage)**: Acesso a armazenamento chave-valor local (`PreferencesManager`), garantindo um singleton ou interface de manipulação de configurações de usuário persistentes.
- **Repositórios Compartilhados**: Classes como `VideoRepository` que sabem de onde buscar os vídeos (MediaStore, File System local), isolando as features de precisarem saber lidar com o ContentResolver do Android de forma grosseira.

## Quando alterar este arquivo?
- Ao adicionar novas chaves para o PreferencesManager salvar persistências gerais.
- Quando for necessário alterar o esquema ou adicionar novos Modelos de Domínio (`.model`) que são transferidos entre telas e camadas lógicas.
