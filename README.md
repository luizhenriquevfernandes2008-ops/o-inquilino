# O Inquilino

> *Este mundo já tinha um morador.*

Um mod de terror no estilo **ARG** para **Minecraft Java 26.2** (Fabric), inspirado nos mods de terror famosos recentes (The Broken Script, From The Fog, The Man From The Fog, Cave Dweller...) — mas com uma história e um vilão próprios.

Em 2011, um garoto chamado **T.** criou um mundo chamado *CASA*. Alguém mais morava lá. Alguém sem rosto, que aprendeu a escrever nas placas, aprendeu o nome dele — o nome **de verdade** — e pediu o quarto dele.

T. enterrou o quarto e escondeu a localização "na memória". Agora o Inquilino entrou no **seu** mundo. E ele ainda está procurando.

### Novidades da 1.1

- 📼 **Câmera VHS**: tudo parece gravado numa filmadora velha — lente curva, linhas de varredura, cor lavada, aberração cromática, faixa de tracking e rasgos na fita. Na tela: **● REC**, contador da fita, bateria, data e hora de verdade. Quanto mais perto ele está, pior fica o sinal.
- 🎥 **Câmera de mão realista**: a cabeça balança com os passos, inclina quando você anda de lado ou vira rápido, afunda quando você cai, e respira quando você está parado. Com medo, a mão treme.
- 🧍 **Seu corpo aparece** quando você olha para baixo.
- 🔥 **Tocha na mão ilumina** (qualquer mão, e qualquer item que brilhe: lanterna, pedra luminosa, balde de lava...). Perto dele, a chama treme e apaga.
- 🌫️ **Neblina** que fecha ao seu redor conforme a noite e a assombração pioram. Música do jogo silenciada e um zumbido grave de fundo.
- 😱 **Ele é uma ameaça de verdade**: quando você desvia o olhar, **ele está mais perto** (e se chegar, pega você). Aparece **colado atrás de você**. Encosta o rosto na **janela**. **Tranca as portas** com você dentro. Te acorda na cama. A caçada não acaba quando você foge: ele continua atrás de você.
- 💬 **Ele fala muito mais**: comenta o que você está fazendo (no escuro, na caverna, correndo, parado, ferido...), **responde o que você escreve no chat** e depois **repete as suas próprias frases**.
- ⏱️ **Ritmo muito mais rápido**: primeiro sinal em segundos, fases em 1 / 5 / 12 minutos.

---

## ⚠️ Aviso

- Sustos repentinos com **sons altos** e **imagens piscantes/estroboscópicas**. Na primeira vez que abrir o jogo aparece um aviso com a opção **"Entrar sem flashes"**.
- Ele **cria arquivos de texto** em `.minecraft/o_inquilino/` (faz parte do ARG). Nada fora da pasta do jogo é tocado, nada é enviado para a internet.
- Em mundos **locais** (um jogador), ele pode chamar você pelo **nome de usuário do seu computador**. Em servidores ele usa só o nome do jogador. Dá para desligar (veja *Configuração*).

---

## Instalação

1. Instale o **Fabric Loader 0.19.5+** para Minecraft **26.2** (https://fabricmc.net/use/installer/).
2. Coloque na pasta `.minecraft/mods/`:
   - `o-inquilino-1.1.0.jar` (este mod — está em `build/libs/`)
   - **Fabric API** `0.160.0+26.2` ou mais nova (https://modrinth.com/mod/fabric-api)
3. Abra o jogo com o perfil do Fabric. Recomendado: **à noite, sozinho, com fones de ouvido.**

Funciona em um jogador e em servidores (o mod precisa estar no servidor **e** nos clientes).

## Compilar

Dê dois cliques em `compilar.bat` (usa o Java 25 que já vem com o launcher do Minecraft) ou rode:

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`. As texturas são geradas por código em `tools/TextureGen.java`.

---

## Como a assombração funciona

Ela avança em **fases** conforme o tempo que você passa no mundo (o ritmo pode ser ajustado):

| Fase | Quando | O que acontece |
|---|---|---|
| **0 — Silêncio** | início | Passos atrás de você que param quando você se vira. Alguém minerando do outro lado da parede. Uma tocha que some. Uma porta aberta. |
| **1 — Presença** | ~1 min | Ele aparece **longe, encarando**. Às vezes some quando você olha — às vezes **chega mais perto cada vez que você desvia o olhar**. Batidas na porta. Sussurros. Respiração na nuca. *"Inquilino entrou no jogo"*. Ele começa a falar. Páginas do diário do T. em baús. |
| **2 — Intrusão** | ~5 min | Ele **segue você**, aparece **colado atrás de você**, **encosta o rosto na janela**, **tranca as portas** com você dentro e apaga as luzes. Te acorda na cama. **Você "fala" no chat coisas que não digitou.** Sua cabeça é virada à força para ele. Uma **cópia sua** ao longe. Placas com o seu nome, palavras no chão, árvores mortas, túneis, bilhetes. **Caçadas no escuro.** Arquivos na pasta do jogo. |
| **3 — Caça** | ~12 min | Neblina fechada. **Ele caça você** sempre que estiver escuro, apagando as tochas pelo caminho — e pode **arrancar a tocha da sua mão**. Apagões. Uma **tela falsa de "Conexão perdida"** — e quando você clica em voltar... |

Ele **nunca some por muito tempo**: se ficar mais de um minuto sem aparecer, ele volta.

**Dicas do T.** (estão no arquivo que ele deixa para você): não durma se ouvir batidas, e se ele correr na sua direção, **bata nele** — ele odeia ser tocado. Segurar uma tocha deixa ele mais lento na caçada. E se ele estiver se aproximando cada vez que você desvia o olhar... **não desvie**: encare até ele desistir.

### O ARG

O mistério se resolve **dentro e fora do jogo**: leia os diários, leia os arquivos que aparecem em `.minecraft/o_inquilino/`, decifre onde o quarto foi enterrado... e desça.

<details>
<summary>🔒 Solução completa (spoiler!)</summary>

1. Os diários 1 a 5 aparecem em baús ao longo das fases. O 5º diz que o T. escondeu a localização do quarto "na memória", do jeito que o pai dele escondia senhas, e deixou uma cópia "fora do jogo".
2. Aparece `memoria_corrompida.txt` na pasta `o_inquilino`. As linhas em hexadecimal, decodificadas, dizem: *"a linha diferente é base64. ela diz onde. desça a escada."*
3. A única linha que não é hexadecimal (a do setor `0x7F3AE0`, citado no cabeçalho) é **base64** — decodificada, dá `X ... Z ...`.
4. Nessas coordenadas há uma placa **"DESÇA"** e uma escada. Lá embaixo está o quarto do T.: a cama vermelha, a última página do diário e uma placa com o seu nome.
5. Entrar no quarto dispara o **final**. Depois disso, ele guarda uma memória global: o menu principal muda, e **no próximo mundo que você criar, ele vai lembrar de você**.

</details>

---

## Configuração

Arquivo `config/inquilino/inquilino.properties`:

| Opção | Padrão | Descrição |
|---|---|---|
| `flashes` | `true` | Flashes, estática forte e quadros subliminares. |
| `usar_nome_do_computador` | `true` | Ele pode usar o nome de usuário do PC em mundos locais. |
| `criar_arquivos` | `true` | Permite os arquivos do ARG em `.minecraft/o_inquilino/`. |
| `ritmo` | `1.0` | Velocidade da assombração (`2.0` = duas vezes mais rápido, `0.5` = mais lento, se estiver intenso demais). |
| `aviso_visto` | `false` | Se o aviso inicial já foi aceito. |
| `camera_vhs` | `true` | Filtro e interface de filmadora VHS. |
| `camera_realista` | `true` | Câmera de mão (balanço, inclinação, respiração, tremor). |
| `corpo_visivel` | `true` | Seu corpo aparece em primeira pessoa. |
| `luz_na_mao` | `true` | Tocha na mão ilumina ao redor. |
| `neblina` | `true` | Neblina que fecha com a assombração. |
| `silenciar_musica` | `true` | Desliga a música do jogo enquanto ele estiver no mundo. |

Para ele **esquecer você**, apague `config/inquilino/memoria.dat`. *(ele vai perceber.)*

## Comandos (operador / cheats ligados)

| Comando | O que faz |
|---|---|
| `/inquilino estado` | Mostra fase, tempo, diário e onde está o quarto. |
| `/inquilino fase <0-4>` | Pula para uma fase. |
| `/inquilino evento <id>` | Dispara um evento (tem autocompletar: `observador`, `cacada`, `desconexao`, `olhe`, `doppelganger`...). |
| `/inquilino invocar` | Coloca ele parado na sua frente por 30 segundos. |
| `/inquilino resetar` | Reinicia a assombração neste mundo. |

---

## Detalhes técnicos

- Minecraft **26.2**, Fabric Loader **0.19.5**, Fabric API **0.160.0+26.2**, Java **25**, Loom **1.17**.
- Todos os sons são remixes de sons vanilla (`assets/inquilino/sounds.json`) — sussurros do Vale das Almas em tom mais grave, batimentos do Warden, rugidos acelerados, chuva em alta rotação virando estática...
- Textos em **português** e **inglês** (`assets/inquilino/lang/`).
- O Inquilino nunca é salvo no mundo — ele só existe enquanto alguém está olhando para o lugar certo.
