### Adicionar ranges para corte

## o que é?
Funcionalidade do componente de [[Timeline.kt]], que serve para o user remover múltiplos pedaços do vídeo.

## como funciona?
 Cada range adicionaado corresponde a um intervalo do vídeo. Deve ser definido o começo e fim (starrRange e endRange) em ms. esse período será removido do vídeo.

ex: um vídeo de um minuto, o user adiciona ranges a serem removidos. [0.00 - 1.00, 2.500 - 5.500].

## Como funciona visualmente?

O usuário ira navegar pela timeline normalmente, quando ele acionar o FAB button ele irá iniciar o im adicionando o start range. o star range será definido pelo ponto onde o [[Playhead.kt]]. após selecionar o rangeA o rangeB será definido pelo próximo clique do FAB.

 - O range deve ser identidicado por um overlay semi transparente e não deve afetar a navegação de scroll.
 - esse range será armazenado em uma lista para futuramente ser utilizado no trim service.
 - o range B pode ser menor que o rangeA, a ordem de adição do range não importa
 - O botão de add range só deve funcionar se estiver fora de um range.
 - ranges não devem colidir, quando user add um range, ele não deve sobrepor outros ranges.
 - Não há limites de adicção de ranges.
 - O overlay deve ir se adaptando dinamicamente conforme o movimento do scroll, do range A para o range B.


Exemplo visual

1. click add rangeA 
[..........↓..........]
[.........()..........] rangeA definido > (

2. Scroll para →
[..........↓..........]
[.......(→→)..........] 

3. user seleciona rangeB.

[..........↓..........]
[..(→→→→→→→)..........] rangeB definido > )

Assim o range fica definido.



## Timestamp rangeA e rangeB

deve ser adicionado um timestamp do valor do range A e do range B nas extremidades **inferiores** dentro do overlay do range indicando que pontos estão ambos como descrito abaixo. a fonte deve ser a menor possível.

[..........↓..........]
[..(→→→→→→)...........] 
[..(t1→→t2)...........] 




