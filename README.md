# DuckTUI

TUI de terminal em Java, feita com [TamboUI](https://tamboui.dev/docs/main/), com
**descoberta automática no estilo Spring Boot**. A árvore de menus pode vir de
duas fontes:

| Modo | Como declarar | Onde |
| --- | --- | --- |
| annotations | `@Menu` / `@Submenu` / `@Item` | qualquer classe dos pacotes varridos |
| arquivo JSON | `@DuckTui(menus = "menus.json")` | classpath ou disco |

Nos dois casos a classe `main` não estende nem declara nada:

```java
@DuckTui(title = "DuckTUI")
public class DuckTuiApp {
    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiApp.class);
    }
}
```

```
> ▾ Arquivo
      • Novo lago
      • Abrir...
      ▾ Recente
        • lago-norte.duck
        • lago-sul.duck
        ▾ Arquivados
          • lago-seco.duck
      • exportar
  ▾ Ferramentas
      • Recarregar menus
[setas] navegar   [enter] executar   [q] sair
```

O modo é **inline**: não toma a tela toda. Reserva só as linhas do menu no rodapé
e o que você imprime com `println()` rola para cima, acima dele.

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
./run.sh          # menus por annotation
./run.sh json     # menus pelo menus.json
```

Ou:

```bash
mvn -q compile exec:java
mvn -q compile exec:java -Dexec.mainClass=com.github.rhama.ducktui.DuckTuiJsonApp
```

## Estrutura

```
src/main/java/com/github/rhama/ducktui/
├── DuckTuiApp.java              # main: @DuckTui + launch()          (annotations)
├── DuckTuiJsonApp.java          # main: @DuckTui(menus = "...")      (json)
├── menus/                       # menus anotados, descobertos pelo scan
│   ├── ArquivoMenu.java         @Menu(id = "arquivo")
│   ├── RecentesMenu.java        @Submenu(parent = "arquivo")
│   ├── ArquivadosMenu.java      @Submenu(parent = "recentes")
│   └── AjudaMenu.java           @Menu(id = "ajuda")
├── annotation/
│   ├── DuckTui.java             # boot + pacotes varridos + arquivo de menus
│   ├── Menu.java
│   ├── Submenu.java
│   └── Item.java
├── boot/
│   ├── DuckTuiLauncher.java     # bootstrap
│   ├── ClassScanner.java        # varredura de classpath (dirs + jars)
│   ├── MenuScanner.java         # normaliza annotations/JSON, valida e monta
│   ├── JsonMenuLoader.java      # lê e valida o formato do menus.json
│   ├── MenuEntry.java
│   ├── MenuApp.java             # InlineApp que desenha o menu
│   └── DuckTuiException.java
└── demo/
    └── ProgressDemo.java        # exemplo sem annotations

src/main/resources/menus.json    # árvore usada pelo modo JSON
```

## Annotations

| Annotation | Onde vai | Campos | Regra |
| --- | --- | --- | --- |
| `@DuckTui` | classe com `main` | `title`, `scan`, `menus`, `height` | obrigatória |
| `@Menu` | qualquer classe | `id`, `label`, `order` | **sempre pai**: sem `parent`, não aninhado em menu |
| `@Submenu` | qualquer classe | `id`, `parent`, `label`, `order` | **sempre filho** de um `@Menu`/`@Submenu` |
| `@Item` | método `static` sem args | `label`, `order` | folha executável |

`id` vazio = nome simples da classe. `label` vazio = nome simples da classe
(em `@Item`, vazio = nome do método). `order` decide a posição entre irmãos;
empate desempata pelo label.

```java
@Submenu(id = "recentes", parent = "arquivo", label = "Recente", order = 3)
public class RecentesMenu {
    @Item(label = "lago-norte.duck")
    public static String norte() { return "abrindo lago-norte.duck"; }
}
```

`@Submenu` aninhado dentro de uma classe anotada dispensa `parent` (o pai é
inferido).

## Modo arquivo JSON

Aponte o arquivo em `@DuckTui(menus = ...)`. O caminho é buscado primeiro no
**classpath** (ex.: `src/main/resources/menus.json`) e depois no **disco**
(caminho absoluto ou relativo).

```java
@DuckTui(title = "DuckTUI (json)", menus = "menus.json")
public class DuckTuiJsonApp {
    public static String recarregar() { return "menus recarregados"; }

    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiJsonApp.class);
    }
}
```

```json
{
  "menus": [
    { "id": "arquivo", "label": "Arquivo", "order": 1, "class": "ArquivoMenu" },

    { "id": "recentes", "parent": "arquivo", "label": "Recente", "order": 3,
      "class": "RecentesMenu" },

    { "id": "arquivados", "parent": "recentes", "label": "Arquivados", "order": 3,
      "class": "ArquivadosMenu" },

    { "id": "ferramentas", "label": "Ferramentas", "order": 2,
      "items": [
        { "label": "Recarregar menus", "action": "DuckTuiJsonApp#recarregar", "order": 1 },
        { "label": "Sobre", "action": "AjudaMenu#sobre", "order": 2 }
      ] }
  ]
}
```

Um array puro no topo também é aceito.

### Campos

| Campo | Onde | Obrigatório | Significado |
| --- | --- | --- | --- |
| `id` | todo nó | sim (único) | identificador, usado por `parent` |
| `parent` | todo nó | não | id do pai; **ausente = menu raiz** |
| `label` | todo nó | não | texto exibido; padrão = `id` |
| `order` | todo nó | não | ordem entre irmãos; padrão `0` |
| `class` | todo nó | não | classe cujos `@Item` são herdados |
| `items[]` | todo nó | não | itens avulsos declarados no arquivo |
| `action` | item | sim | `"Classe#metodo"` (`static`, sem args) |

Em JSON não existe `@Menu`/`@Submenu`: **sem `parent` é menu raiz, com `parent`
é submenu**. A distinção é estrutural.

### Como o nome da classe é resolvido

Você não precisa informar o pacote. O `MenuScanner` monta um índice com todas as
classes dos pacotes varridos e resolve:

1. nome simples (`"ArquivoMenu"`) → procura no índice;
2. nome completo (`"com.github.rhama.ducktui.menus.ArquivoMenu"`) → `Class.forName`;
3. zero resultados ou mais de um → erro listando as candidatas.

O mesmo vale para `action`.

### Precedência

`@DuckTui(menus = "...")` preenchido **ignora as annotations**. Sem `menus`, vale
o scan de `@Menu`/`@Submenu`. Os dois modos compartilham a mesma validação e o
mesmo renderizador.

### Erros de configuração (testados)

```
JSON invalido em '.../invalido.json': Unexpected character ('"' (code 34)):
was expecting comma to separate Object entries (linha 1, coluna 26)

Arquivo de menus nao encontrado: 'nao-existe-mesmo.json'
Procurei no classpath e no disco.

Menu #0 em '.../semid.json' sem 'id'. Todo no precisa de um id unico.

Classe 'NaoExiste' citada em '...' nao foi encontrada.
Use o nome completo ou aponte @DuckTui(scan = "...") para o pacote dela.

Acao invalida 'semMetodo' em '...'.
Use o formato "Classe#metodo".

O menu 'b' aponta para parent='zzz', que nao existe.
Ids disponiveis: [a, b].
```

## Como o app funciona

| Peça | Papel |
| --- | --- |
| `MenuApp extends InlineApp` | desenha o menu e trata as teclas |
| `height()` | linhas reservadas: entradas + cabeçalho + dica |
| `onKeyEvent` | ↑/↓ navegam, Enter executa, `q` sai (nativo do TamboUI) |
| `@Item`/`action` com retorno | o valor é impresso acima da área de status |

Detalhes que importam:

- **Ticks**: `runner().scheduleRepeating(...)` roda fora da render thread; toda
  mudança de estado precisa passar por `runner().runOnRenderThread(...)`.
- **Teclado**: o elemento precisa de `.id(...)` + `.focusable()` para entrar na
  cadeia de foco. Sem isso o evento nunca chega.
- **Sair**: `q` e Ctrl+C já encerram por padrão, sem código extra.

## Limites conhecidos

- A varredura usa `getResources`, então cobre classpath normal (dirs e jars). Em
  **GraalVM native image** ou JPMS fechado pode não ver tudo — nesse caso use
  nomes completos no JSON e restrinja `scan`.
- A validação é em **runtime**, no start. Um annotation processor no `javac`
  exigiria módulo separado e não resolveria `parent` por string sozinho.
- `@OnAction` é a única annotation nativa do TamboUI e só funciona em `Component`
  (não em `InlineApp`). Para eventos, use `.onKeyEvent(...)`.
- O JSON é lido com Jackson; é a única dependência além do TamboUI/JLine.

## Próximos passos

- Colapsar/expandir submenus (Enter em nó pai alterna)
- `@Item(quit = true)` / `{"quit": true}` para o item "Sair"
- Atalhos: `@Item(shortcut = "n")`
- Recarregar o JSON em tempo de execução (hoje é lido uma vez no start)

## Avisos

- TamboUI é **experimental**, versão `0.8.0-SNAPSHOT`: a API pode mudar.
- A lib **não está no Maven Central**. Só existe como snapshot no Sonatype (por
  isso o repositório no `pom.xml`). O build pode quebrar quando um snapshot novo
  for publicado; se acontecer, fixe a versão ou rode
  `./gradlew publishToMavenLocal` no [repo da lib](https://github.com/tamboui/tamboui).
