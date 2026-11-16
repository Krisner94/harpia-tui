# DuckTUI

TUI de terminal em Java, feita com [TamboUI](https://tamboui.dev/docs/main/).

O modo usado aqui é **inline**: o app **não** toma a tela toda. Ele reserva apenas
algumas linhas no rodapé para uma área de status, e tudo que você imprime com
`println()` rola para cima, acima dela — o padrão do Gradle/npm, não o de um
editor de texto.

```
DuckTUI | aquecer o lago
██████████████▍                        18%
[espaco] pausar  [q] sair
```

## Requisitos

- JDK 21+
- Maven 3.9+
- Um **terminal de verdade** (o modo inline/TTY não funciona direito rodando pela IDE)

Nesta máquina, o JDK e o Maven foram instalados localmente (sem `sudo`):

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13+9"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/apache-maven-3.9.9/bin:$PATH"
```

## Rodar

```bash
./run.sh
```

Ou direto pelo Maven:

```bash
mvn -q compile exec:java
```

Não use `./gradlew run` / `gradle run` se migrar para Gradle: o daemon do Gradle
não tem TTY e você cai no erro `creating a dumb terminal`. Nesse caso, gere um
jar executável e rode com `java -jar`.

## Estrutura

```
src/main/java/com/github/rhama/ducktui/DuckTuiApp.java
pom.xml
run.sh
```

## Como o código funciona

`DuckTuiApp` estende `InlineApp` e implementa três coisas:

| Método | Papel |
| --- | --- |
| `height()` | quantas linhas reservar no rodapé (aqui: 3) |
| `render()` | descreve a UI do frame atual (declarativo, chamado a cada frame) |
| `onStart()` | agenda trabalho periódico antes do primeiro frame |

Detalhes que importam:

- **Ticks**: `onStart()` usa `runner().scheduleRepeating(...)`. O agendador roda
  fora da render thread, então toda mudança de estado passa por
  `runner().runOnRenderThread(...)`.
- **Teclado**: os eventos ficam nos elementos via `.onKeyEvent(...)`, e o
  elemento precisa de `.id(...)` + `.focusable()` para entrar na cadeia de foco.
- **Sair**: `q` (e Ctrl+C) já encerram por padrão, sem código extra.
- **Log acima do status**: `println(...)` aceita `String` ou um `Element`
  estilizado.

## Próximos passos

- [Toolkit DSL](https://tamboui.dev/docs/main/api-levels.html#toolkit-dsl) — `row`, `table`, `list`, `tabs`, `textInput`
- [Forms](https://tamboui.dev/docs/main/forms.html) — validação e campos
- [CSS Styling](https://tamboui.dev/docs/main/styling.html) — temas em `.tcss`
- [Widgets](https://tamboui.dev/docs/main/widgets.html) — referência completa
- [MVC](https://tamboui.dev/docs/main/mvc-architecture.html) — organização para apps maiores

## Avisos

- TamboUI é **experimental** e está na `0.8.0-SNAPSHOT`. A API pode mudar.
- A lib **não está no Maven Central**: só existe como snapshot no Sonatype (por
  isso o repositório declarado no `pom.xml`). O build pode quebrar quando um novo
  snapshot for publicado. Se acontecer, fixe uma versão de snapshot específica
  ou rode `./gradlew publishToMavenLocal` no [repositório da lib](https://github.com/tamboui/tamboui).
