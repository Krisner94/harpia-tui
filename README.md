# DuckTUI

TUI de terminal em Java, feita com [TamboUI](https://tamboui.dev/docs/main/), com
**descoberta automática no estilo Spring Boot**: você anota a classe e ela aparece
no menu. A classe `main` não estende nem declara nada.

```java
@DuckTui(title = "DuckTUI")
public class DuckTuiApp {
    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiApp.class);
    }
}
```

```java
@Menu(id = "arquivo", label = "Arquivo", order = 1)
public class ArquivoMenu {
    @Item(label = "Novo lago")
    public static String novoLago() { return "lago novo criado"; }
}
```

```
> ▾ Arquivo
      • Novo lago
      • Abrir...
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
./run.sh
```

Ou:

```bash
mvn -q compile exec:java
```

## Estrutura

```
src/main/java/com/github/rhama/ducktui/
├── DuckTuiApp.java              # main: só @DuckTui + launch()
├── menus/                       # menus soltos, descobertos pelo scan
│   ├── ArquivoMenu.java         @Menu(id = "arquivo")
│   ├── RecentesMenu.java        @Submenu(parent = "arquivo")
│   ├── ArquivadosMenu.java      @Submenu(parent = "recentes")
│   └── AjudaMenu.java           @Menu(id = "ajuda")
├── annotation/
│   ├── DuckTui.java             # boot + pacotes varridos
│   ├── Menu.java                # nó pai
│   ├── Submenu.java             # nó filho (parent = id do pai)
│   └── Item.java                # folha executável
├── boot/
│   ├── DuckTuiLauncher.java     # bootstrap
│   ├── ClassScanner.java        # varredura de classpath (dirs + jars)
│   ├── MenuScanner.java         # descobre, liga pais e valida
│   ├── MenuEntry.java           # nó da árvore
│   ├── MenuApp.java             # InlineApp que desenha o menu
│   └── DuckTuiException.java
└── demo/
    └── ProgressDemo.java        # exemplo sem annotations
```

## Annotations

| Annotation | Onde vai | Campos | Regra |
| --- | --- | --- | --- |
| `@DuckTui` | classe com `main` | `title`, `scan`, `height` | obrigatória |
| `@Menu` | qualquer classe | `id`, `label`, `order` | **sempre pai**: sem `parent`, não aninhado em menu |
| `@Submenu` | qualquer classe | `id`, `parent`, `label`, `order` | **sempre filho** de um `@Menu`/`@Submenu` |
| `@Item` | método `static` sem args | `label`, `order` | folha executável |

`id` vazio = nome simples da classe. `label` vazio = nome simples da classe
(em `@Item`, vazio = nome do método). `order` decide a posição entre irmãos;
empate desempata pelo label.

## Como a descoberta funciona

1. `DuckTuiLauncher.launch(MinhaApp.class)` lê `@DuckTui`.
2. Os pacotes varridos são `@DuckTui(scan = ...)` ou, se vazio, o pacote da sua
   classe **e todos os subpacotes**.
3. `ClassScanner` enumera as classes do classpath — diretórios (`target/classes`)
   e jars — sem dependência externa.
4. `MenuScanner` registra toda classe com `@Menu`/`@Submenu`, liga cada
   `@Submenu` ao `parent` e monta a árvore.
5. `MenuApp` renderiza o resultado.

Não há ordem de declaração a respeitar: o scanner monta a árvore pelas
referências de `id`. Classes em arquivos e pacotes diferentes se encontram.

### Ainda aceita classes aninhadas

Se você preferir o estilo antigo, `@Submenu` aninhado dentro de uma classe
anotada tem o pai inferido — `parent` pode ficar vazio:

```java
@Menu(id = "arquivo", label = "Arquivo")
static class Arquivo {
    @Submenu(label = "Recentes")   // pai inferido: "arquivo"
    static class Recentes { }
}
```

### Restrições validadas no start

Java não expressa "isto só pode ficar dentro daquilo" — annotation não sabe onde
está aninhada. Então tudo é checado **antes** de desenhar a primeira linha:

```
@Submenu 'orfao.Solto' nao declara parent e nao esta aninhado em um menu anotado.
Declare o pai: @Submenu(parent = "id-do-menu-pai", ...).

Id de menu duplicado: 'mesmo'.
Usado por 'duplicado.Dois' e por 'duplicado.Um'.

@Submenu 'paiinexistente.Filho' aponta para parent='naoexiste', que nao existe.
Ids disponiveis: [filho, raiz].

Ciclo de @Submenu detectado envolvendo 'a'.
```

Também são barrados: `@Menu` aninhado em outro menu, `@Menu` + `@Submenu` na mesma
classe, `@Item` não-`static`, `@Item` com parâmetros e app sem `@DuckTui`.

## Como o app funciona

| Peça | Papel |
| --- | --- |
| `MenuApp extends InlineApp` | desenha o menu e trata as teclas |
| `height()` | linhas reservadas: entradas + cabeçalho + dica |
| `onKeyEvent` | ↑/↓ navegam, Enter executa, `q` sai (nativo do TamboUI) |
| `@Item` com retorno | o valor é impresso acima da área de status |

Detalhes que importam:

- **Ticks**: `runner().scheduleRepeating(...)` roda fora da render thread; toda
  mudança de estado precisa passar por `runner().runOnRenderThread(...)`.
- **Teclado**: o elemento precisa de `.id(...)` + `.focusable()` para entrar na
  cadeia de foco. Sem isso o evento nunca chega.
- **Sair**: `q` e Ctrl+C já encerram por padrão, sem código extra.

## Limites da varredura

- Funciona com classpath normal (dirs e jars). Em **GraalVM native image** ou
  módulos JPMS a enumeração por `getResources` pode não ver tudo — nesses casos,
  restringir `scan` para um pacote pequeno ou trocar por uma lista explícita.
- Em classpaths gigantes, varra só o pacote da aplicação.

## Evoluir para validação em tempo de compilação

A validação atual é em runtime. Para o `javac` recusar antes de rodar, use um
**annotation processor** em módulo separado (`ducktui-processor`):

- registre `javax.annotation.processing.Processor` em
  `META-INF/services/`;
- para cada elemento anotado, cheque `getEnclosingElement()`/atributos e emita
  `Messager.printMessage(Diagnostic.Kind.ERROR, ...)`;
- declare em `<annotationProcessorPaths>` no `pom.xml`.

O `tamboui-processor` do próprio TamboUI faz isso para `@OnAction` e serve de
referência. Obs.: Processadores rodam sobre fontes, então a checagem seria de
`@Item` estático, ciclos e `@Submenu` aninhado — o `parent` por string só pode
ser verificado se o processor conseguir resolver as classes.

> `@OnAction` é a **única** annotation nativa do TamboUI e só funciona em
> `Component` (não em `InlineApp`). Para eventos, o caminho normal é
> `.onKeyEvent(...)`.

## Próximos passos

- Colapsar/expandir submenus (Enter em nó pai alterna)
- `@Item(quit = true)` para o item "Sair"
- Atalhos: `@Item(shortcut = "n")`
- Ordenar menus por `order` também entre raízes (já funciona) e agrupar por `@Menu(group = ...)`

## Avisos

- TamboUI é **experimental**, versão `0.8.0-SNAPSHOT`: a API pode mudar.
- A lib **não está no Maven Central**. Só existe como snapshot no Sonatype (por
  isso o repositório no `pom.xml`). O build pode quebrar quando um snapshot novo
  for publicado; se acontecer, fixe a versão ou rode
  `./gradlew publishToMavenLocal` no [repo da lib](https://github.com/tamboui/tamboui).
