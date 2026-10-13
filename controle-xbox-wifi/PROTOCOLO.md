# Protocolo de rede

UDP, porta **7777** no PC. Todos os números são little-endian.
Todo pacote começa com os 4 bytes `X3WF` seguidos de 1 byte de tipo.

## Celular para PC

| Tipo | Nome | Conteúdo depois do tipo |
|---|---|---|
| 1 | estado | `u8` reservado, `u16` sequência, `u16` botões, `u8` LT, `u8` RT, `i16` LX, `i16` LY, `i16` RX, `i16` RY (20 bytes no total) |
| 2 | procurar | nada (enviado em broadcast) |
| 4 | tchau | nada (o celular saiu) |

O celular manda o estado a cada 10 ms. A sequência cresce de 1 em 1 e
volta a 0 depois de 65535; o PC descarta pacotes que chegam fora de ordem.

Os bits de botões são os mesmos do XInput:

| Bit | Botão | Bit | Botão |
|---|---|---|---|
| `0x0001` | direcional cima | `0x0100` | LB |
| `0x0002` | direcional baixo | `0x0200` | RB |
| `0x0004` | direcional esquerda | `0x0400` | Guide (logo Xbox) |
| `0x0008` | direcional direita | `0x1000` | A |
| `0x0010` | Start | `0x2000` | B |
| `0x0020` | Back | `0x4000` | X |
| `0x0040` | L3 | `0x8000` | Y |
| `0x0080` | R3 | | |

Eixos vão de -32768 a 32767, com Y positivo para cima.

## PC para celular

| Tipo | Nome | Conteúdo depois do tipo |
|---|---|---|
| 3 | resposta da procura | nome do PC em UTF-8 |
| 5 | vibração | `u8` motor grande, `u8` motor pequeno |
| 6 | confirmação | `u8` número do jogador (1 a 4), ou 0 se o PC está cheio |

O PC manda a confirmação ao receber o primeiro estado e depois uma vez por
segundo. O celular considera a conexão perdida se ficar 2 segundos sem ela.
O PC desliga o controle virtual de um celular que fica 3 segundos calado.
