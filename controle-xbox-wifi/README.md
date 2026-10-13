# Controle Xbox Wi-Fi

Transforma o celular Android num controle de Xbox 360 para o PC com Windows,
pelo Wi-Fi. O Windows e os jogos enxergam um controle Xbox 360 de verdade,
então funciona em qualquer jogo com suporte a controle (Steam, Epic,
emuladores e outros).

Até **4 celulares ao mesmo tempo**, cada um vira um jogador. A vibração do
jogo chega ao celular.

## Como usar

1. **No PC:** instale o driver ViGEmBus, que é o que cria o controle virtual.
   Se ele faltar, o próprio `ControleXboxWifi-Servidor.exe` avisa e oferece
   instalar na hora. Também dá para baixar em
   <https://github.com/nefarius/ViGEmBus/releases>.
2. **No PC:** abra o `ControleXboxWifi-Servidor.exe`. Se o Windows perguntar
   sobre o firewall, permita em **redes privadas**. A janela mostra o IP do PC.
3. **No celular:** instale o `ControleXboxWifi.apk` (o Android pede para
   permitir instalar de fontes desconhecidas) e abra.
4. Com celular e PC na **mesma rede Wi-Fi**, o app acha o PC sozinho. Se não
   achar, toque no texto do topo da tela e digite o IP mostrado no PC.

Também funciona sem roteador: ligue o roteador do celular (hotspot) e conecte
o PC nele.

## Onde baixar

O GitHub Actions compila os dois arquivos a cada mudança nesta pasta.

- **Para testar:** na aba *Actions*, abra a execução mais recente de
  "Controle Xbox Wi-Fi" e baixe `ControleXboxWifi-apk` e
  `ControleXboxWifi-exe` em *Artifacts* (precisa estar logado no GitHub).
- **Para distribuir:** em *Actions*, escolha "Controle Xbox Wi-Fi",
  clique em *Run workflow* e preencha a versão (ex.: `1.0.0`). Isso cria uma
  página em *Releases* com link público para os dois arquivos.

## Controles na tela

| Na tela | No Xbox 360 |
|---|---|
| Analógico da esquerda e da direita | LS e RS |
| L3 e R3 (abaixo dos analógicos) | apertar os analógicos |
| Direcional (cruz) | D-pad, aceita diagonais |
| A, B, X, Y | A, B, X, Y |
| BACK, logo verde, START | Back, Guide, Start |
| LT, LB, RT, RB (cantos de cima) | gatilhos e bumpers |

Vários dedos ao mesmo tempo funcionam, e dá para deslizar o dedo de um botão
para outro. Os gatilhos são digitais: apertado vale 100%.

## Como funciona

```
Celular (app)  --UDP 7777, 100x por segundo-->  PC (servidor)  -->  ViGEmBus  -->  jogo
               <--confirmação e vibração-------
```

O protocolo está em [`PROTOCOLO.md`](PROTOCOLO.md). Por ser UDP numa rede
local, o atraso fica na casa de poucos milissegundos; um pacote perdido é
coberto pelo próximo, 10 ms depois.

## Pastas

| Pasta | O que é |
|---|---|
| `android/` | app Android em Java, sem bibliotecas externas (Android 5.0 ou mais novo) |
| `pc/servidor.py` | servidor do PC, vira o `.exe` com PyInstaller |
| `pc/test_servidor.py` | testes do servidor, rodam sem o driver |

## Desenvolver

Servidor (precisa do Python 3.9 ou mais novo):

```
cd pc
pip install vgamepad          # no Windows, instala o ViGEmBus junto
python servidor.py            # de verdade
python servidor.py --teste    # sem driver, só mostra o que chega
python -m unittest test_servidor
```

App: abra a pasta `android/` no Android Studio, ou rode
`./gradlew assembleRelease`. O APK é assinado com a chave de depuração, o
que basta para instalar direto. Para publicar na Play Store, configure uma
chave própria em `android/app/build.gradle`.
