# Análise de Código: Utils.kt

## Visão Geral
O arquivo `Utils.kt` centraliza todas as funções utilitárias e ferramentas auxiliares que são usadas repetidamente por todo o projeto ChopCut. Ao manter as utilidades isoladas, as classes de lógica e UI ficam mais enxutas.

## Responsabilidades
- **Formatação de Tempo**: Contém utilitários vitais como `TimeFormatter` e `TimeTracker` que convertem milissegundos para strings de exibição amigável (ex: `00:01:23`). Essencial para exibir durações na Timeline e nos Players.
- **Processamento de Vídeo Auxiliar**: Possui o `VideoUtils` e validações como `VideoConstraints` que realizam matemáticas básicas de tamanho, resolução e duração permitida antes que lógicas de processamento pesado ocorram.
- **Coroutines & Threads**: Dispõe do `DispatcherProvider` para injetar dependências de dispatchers do Kotlin Coroutines (Main, IO, Default), facilitando o uso e testes assíncronos.
- **Logs e Erros**: Centraliza todo o tratamento elegante de erros (`ErrorHandler`, `ErrorResult`, `ChopCutException`) e as rotinas base de Logs analíticos (`ActivityLogger`, extensões de ViewModel). 

## Quando alterar este arquivo?
- Ao escrever uma nova extensão (Extension Function) de Kotlin, como conversão de Data, tratamento de Arrays ou utilitário matemático de cálculo.
- Quando for necessário alterar o comportamento padronizado de Logs ou formatadores globais de interface.
