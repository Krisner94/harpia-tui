# DuckTUI

TUI de terminal em Java, feita com [TamboUI](https://tamboui.dev/docs/main/), com
**boot por annotation**: a classe `main` não estende nada, só declara a árvore de
menus e delega para o launcher.

O modo usado é **inline**: o app **não** toma a tela toda. Ele reserva apenas
algumas linhas no rodapé para a área de status, e o que você imprime com
`println()` rola para cima, acima dela — o padrão do Gradle/npm.

```
>   • Abrir...
    ▾ Recentes
      • lago-norte.duck
      • lago-sul.duck
      ▾ Arquivados
        • lago-seco.duck
    • exportar
  ▾ Ajuda
    • Sobre o DuckTUI
[setas] navegar   [enter] executar   [q] sair
```

## Requisitos

- JDK 21+
- Maven 3.9+
- Um **terminal de verdade** (TUI inline não funciona direito rodando pela IDE)

Nesta máquina, JDK e Maven foram instalados localmente (sem `sudo`):

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

Exemplo alternativo, sem annotations (só `InlineApp`): `com.github.rhama.ducktui.demo.ProgressDemo`.

## Estrutura

```
src/main/java/com/github/rhama/ducktui/
├── DuckTuiApp.java              # sua app: @DuckTui + árvore de menus
├── annotation/
│   ├── DuckTui.java             # annotation de boot
│   ├── Menu.java                # nó pai
│   ├── Submenu.java             # nó filho
│   └── Item.java                # folha executável
├── boot/
│   ├── DuckTuiLauncher.java     # bootstrap (o "Spring" da parada)
│   ├── MenuScanner.java         # lê as annotations e valida a hierarquia
│   ├── MenuEntry.java           # nó da árvore
│   ├── MenuApp.java             # InlineApp que desenha o menu
│   └── DuckTuiException.java    # erro de configuração
└── demo/
    └── ProgressDemo.java        # exemplo sem annotations
```

## Annotations

| Annotation | Onde vai | Regra |
| --- | --- | --- |
| `@DuckTui` | classe com `main` | obrigatória; define `title` e `height` |
| `@Menu` | classe aninhada | **sempre pai**: só no nível raiz |
| `@Submenu` | classe aninhada | **sempre dentro** de `@Menu` ou `@Submenu` |
| `@Item` | método `static` sem parâmetros | folha executável |

Declarar um menu é só aninhar classes:

```java
@DuckTui(title = "DuckTUI")
public class DuckTuiApp {

    @Menu(label = "Arquivo", order = 1)
    static class Arquivo {

        @Item(label = "Novo lago", order = 1)
        static String novoLago() {
            return "lago novo criado";   // devolvido -> impresso acima do status
        }

        @Submenu(label = "Recentes", order = 2)
        static class Recentes {
            @Item(label = "lago-norte.duck")
            static String norte() { return "abrir lago-norte.duck"; }
        }
    }

    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiApp.class);
    }
}
```

`order` decide a posição entre irmãos (empate desempata pelo label). `@Item` sem
`label` usa o nome do método. Se o método devolver algo diferente de `void`, o
valor é impresso acima da área de status.

### As regras são realmente validadas

Java **não** consegue expressar "esta annotation só pode ficar dentro daquela"
— annotation não sabe onde está aninhada. Então a checagem é feita em runtime,
no `MenuScanner`, **antes** de qualquer coisa ser desenhada:

```
Exception in thread "main" com.github.rhama.ducktui.boot.DuckTuiException:
@Submenu 'SubmenuRaiz$Orfao' esta no nivel raiz.
@Submenu so pode existir dentro de uma classe @Menu ou @Submenu.
Para um menu de topo, use @Menu em vez de @Submenu.
```

Também são barrados: `@Menu` dentro de outro menu, `@Item` não-`static`,
`@Item` com parâmetros e classe sem `@DuckTui`.

## Como o código funciona

| Peça | Papel |
| --- | --- |
| `DuckTuiLauncher.launch(...)` | lê `@DuckTui`, monta a árvore e sobe a app |
| `MenuScanner.scan(...)` | varre classes/métodos aninhados e valida as regras |
| `MenuApp extends InlineApp` | desenha o menu e trata as teclas |
| `height()` | calcula linhas reservadas: entradas + cabeçalho + dica |
| `onKeyEvent` | ↑/↓ navegam, Enter executa, `q` sai (nativo do TamboUI) |

Detalhes que importam:

- **Ticks**: `runner().scheduleRepeating(...)` roda fora da render thread; toda
  mudança de estado precisa passar por `runner().runOnRenderThread(...)`.
- **Teclado**: o elemento precisa de `.id(...)` + `.focusable()` para entrar na
  cadeia de foco. Sem isso o evento nunca chega.
- **Sair**: `q` e Ctrl+C já encerram por padrão, sem código extra.

## Evoluir para validação em tempo de compilação

A validação atual é em runtime. Se quiser que o `javac` recuse o projeto antes de
rodar, o caminho é um **annotation processor**:

1. crie um módulo `ducktui-processor` com `javax.annotation.processing.Processor`;
2. nele, para cada `Element` anotado com `@Submenu`, verifique
   `getEnclosingElement()` e emita `Messager.printMessage(ERROR, ...)` se o pai
   não tiver `@Menu`/`@Submenu`;
3. registre em `META-INF/services/javax.annotation.processing.Processor`;
4. no `pom.xml`, aponte em `<annotationProcessorPaths>`.

O TamboUI já usa essa técnica no `tamboui-processor` dele (para `@OnAction`), então
serve de referência. Precisa de módulo separado porque o processor tem que estar
compilado antes de processar as suas classes.

> `@OnAction` é a **única** annotation nativa do TamboUI e só funciona em
> `Component` (não em `InlineApp`). Para eventos, o caminho normal é
> `.onKeyEvent(...)`.

## Próximos passos

- Colapsar/expandir submenus (Enter em nó pai alterna o estado)
- `@Item(quit = true)` para o item "Sair"
- Navegação por atalhos (`@Item(shortcut = "n")`)
- [Forms](https://tamboui.dev/docs/main/forms.html), [CSS](https://tamboui.dev/docs/main/styling.html), [Widgets](https://tamboui.dev/docs/main/widgets.html)

## Avisos

- TamboUI é **experimental**, versão `0.8.0-SNAPSHOT`: a API pode mudar.
- A lib **não está no Maven Central**. Só existe como snapshot no Sonatype (por
  isso o repositório no `pom.xml`). O build pode quebrar quando um snapshot novo
  for publicado; se acontecer, fixe a versão ou rode
  `./gradlew publishToMavenLocal` no [repo da lib](https://github.com/tamboui/tamboui).
