# Glicólise: notas de revisão

Vídeo de 4min18s, 1920×1080, 30 fps, narrado em pt-BR, para a apresentação discente de Bioquímica (UFTM, Profa. Vera). O tempo pedido era de 5 ± 2 min.

## Decisões criativas

- **Via escolhida:** a glicólise. Ela tem começo, meio e fim claros, um balanço que dá para "contar nos dedos" e uma ponte com o dia a dia (a queimação na corrida).
- **Fio condutor:** a ideia de negócio, "investir 2 ATP para ganhar 4". As moedas verdes de ATP saem do painel "investido" (cena 5) e chegam ao painel "produzido" (cena 7).
- **Sistema visual único:** carbonos como contas, fosfato em âmbar, ATP como moeda verde, NADH em azul, enzimas em rosa e piruvato em verde-água. A trilha das 10 reações no topo mostra em que passo estamos.
- **Detalhes químicos que aparecem:**
  - o grupo carbonila (=O) passa do C1 para o C2 na isomerase;
  - a aldolase corta entre o C3 e o C4;
  - a DHAP vira G3P;
  - o fosfato muda de lugar e sai água na enolase.
- **Criatividade e busca bibliográfica** (critérios da professora):
  - cena do mito do lactato (acidose por H⁺, microlesões, ciclo de Cori);
  - cena clínica (hemácias, efeito Warburg, PET com ¹⁸F-FDG);
  - referências em ABNT na tela final.

## O que foi medido e o que não foi

- `hyperframes check`: ok. Não há erros. Os avisos que ficaram estão listados abaixo.
- Revisão por quadros: os contact sheets de cada cena, dos cortes e da versão de celular (360 px) foram vistos.
- Áudio: a narração foi gerada localmente (Kokoro, voz `pf_dora`, pt-BR). Os termos técnicos foram escritos foneticamente para a síntese (ATP → "á tê pê", NADH → "ene á dê agá", Krebs → "Crébs"). **A pronúncia não foi ouvida por ninguém, só conferida pelos fonemas gerados.** Vale assistir antes de apresentar.
- Legendas: o tempo de cada legenda foi estimado pela proporção de caracteres dentro de cada frase. O início de cada frase é exato porque cada frase foi sintetizada separadamente. O Whisper não pôde ser baixado neste ambiente.
- Loudness do arquivo final: -16,6 LUFS (normalizado com ffmpeg loudnorm depois da renderização).
- Sem trilha musical: não havia fonte de música disponível (o catálogo da HeyGen exige login). Os efeitos sonoros são os do pacote da skill (Pixabay).

## Avisos mantidos de propósito

- `text_below_floor`: números da trilha de reações e etiquetas "=O" pequenas. São marcações, não texto de leitura.
- `line_unread` na equação geral (3,6 s na tela): a narração já diz o saldo, e a equação fica como apoio.

## Ponto mais fraco e o que eu mudaria com mais tempo

- A voz local é clara, mas soa sintética. Com uma chave da HeyGen ou da ElevenLabs, basta trocar a voz em `script.json` e rodar `tools/tts.mjs`.
- Uma trilha musical baixa (cerca de 0,14 de volume) deixaria o ritmo mais dinâmico.

## Como regenerar

```bash
# narração (precisa de kokoro-onnx num Python: HYPERFRAMES_PYTHON=...)
HF=<skills>/hyperframes/scripts/plugin-cli.mjs node tools/tts.mjs
node tools/build.mjs          # injeta tempos nas cenas, gera legendas e index.html
npx hyperframes check
npx hyperframes render -q delivery -o renders/glicolise.mp4
```

O texto do roteiro fica todo em `script.json`. O campo `cap` é a legenda, o campo `say` é a pronúncia para a síntese e `pausa` acrescenta silêncio depois da frase.
