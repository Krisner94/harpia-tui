# DuckTUI

Biblioteca Java para construir TUIs de terminal **inline** e **declarativas**,
sobre [TamboUI](https://tamboui.dev/docs/main/).

Você anota classes com `@Menu` / `@Submenu` / `@Item` — ou aponta um arquivo JSON —
e a árvore de menus é montada automaticamente. A classe `main` não estende nada.

```java
@DuckTui(title = "Minha App")
public class MinhaApp {
    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(MinhaApp.class);
    }
}
```

```java
@Menu(id = "arquivo", label = "Arquivo", order = 1)
public class ArquivoMenu {
    @Item(label = "Novo")
    public static String novo() { return "criado"; }
}
```

```
> ▾ Arquivo
      • Novo
  ▾ Ajuda
[setas] navegar   [enter] executar   [q] sair
```

O modo é **inline**: não toma a tela toda. Reserva só as linhas do menu no rodapé
e o que você imprime com `println()` rola para cima, acima dele.

## Requisitos

- Java 21+
- Um **terminal de verdade** para executar apps (TUI inline não funciona direito
  pela IDE)

## Instalar a biblioteca

Este repositório **é só a biblioteca**. Para usá-la em outros projetos, instale no
seu repositório Maven local:

```bash
mvn install
```

Isso publica `com.github.rhama:ducktui:1.0.0-SNAPSHOT` em `~/.m2/repository`.

Para publicar em um repositório remoto (GitHub Packages, Nexus, etc.),
configure `distributionManagement` e use `mvn deploy`.

## Usar em outro projeto

```xml
<dependencies>
    <dependency>
        <groupId>com.github.rhama</groupId>
        <artifactId>ducktui</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

> **Importante:** o TamboUI (dependência transitiva) ainda não está no Maven
> Central. O projeto consumidor **precisa** declarar o repositório de snapshots do
> Sonatype, senão o build falha ao resolver `dev.tamboui:*`:
>
> ```xml
> <repositories>
>     <repository>
>         <id>ossrh-snapshots</id>
>         <url>https://central.sonatype.com/repository/maven-snapshots/</url>
>         <releases><enabled>false</enabled></releases>
>         <snapshots><enabled>true</enabled></snapshots>
>     </repository>
> </repositories>
> ```

Gradle:

```kotlin
repositories {
    mavenLocal()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    implementation("com.github.rhama:ducktui:1.0.0-SNAPSHOT")
}
```

## API pública

Só três coisas importam para quem consome:

| Tipo | Pacote | Papel |
| --- | --- | --- |
| `@DuckTui` | `...ducktui.annotation` | boot: título, pacotes varridos, arquivo de menus |
| `@Menu`, `@Submenu`, `@Item` | `...ducktui.annotation` | declaração da árvore |
| `DuckTuiLauncher` | `...ducktui.boot` | `launch(MinhaApp.class)` |
| `DuckTuiException` | `...ducktui.boot` | erro de configuração (unchecked) |

Todo o resto (`MenuScanner`, `ClassScanner`, `MenuApp`, ...) é interno.

## Annotations

| Annotation | Onde vai | Campos | Regra |
| --- | --- | --- | --- |
| `@DuckTui` | classe com `main` | `title`, `scan`, `menus`, `height` | obrigatória |
| `@Menu` | qualquer classe | `id`, `label`, `order` | **sempre pai**: sem `parent` |
| `@Submenu` | qualquer classe | `id`, `parent`, `label`, `order` | **sempre filho** de um menu |
| `@Item` | método `static` sem args | `label`, `order` | folha executável |

- `id` vazio = nome simples da classe.
- `label` vazio = nome simples da classe (em `@Item`, o nome do método).
- `order` posiciona entre irmãos; empate desempata pelo label.
- `scan` vazio = pacote da sua classe **e subpacotes**.
- Se o método do `@Item` devolver algo, o valor é impresso acima do status.

`@Submenu` aninhado dentro de uma classe anotada dispensa `parent`.

## Árvore por arquivo JSON

```java
@DuckTui(title = "Minha App", menus = "menus.json")
public class MinhaApp { /* ... */ }
```

O caminho é procurado primeiro no **classpath** (ex.: `src/main/resources/menus.json`)
e depois no **disco**.

```json
{
  "menus": [
    { "id": "arquivo", "label": "Arquivo", "order": 1, "class": "ArquivoMenu" },

    { "id": "recentes", "parent": "arquivo", "label": "Recente", "order": 3,
      "class": "RecentesMenu" },

    { "id": "ferramentas", "label": "Ferramentas", "order": 2,
      "items": [
        { "label": "Recarregar", "action": "MinhaApp#recarregar", "order": 1 }
      ] }
  ]
}
```

| Campo | Obrigatório | Significado |
| --- | --- | --- |
| `id` | sim (único) | identificador |
| `parent` | não | id do pai; ausente = menu raiz |
| `label` | não | texto; padrão = `id` |
| `order` | não | ordem entre irmãos |
| `class` | não | classe cujos `@Item` são herdados |
| `items[]` | não | itens avulsos (`label`, `action`, `order`) |
| `action` | sim (em item) | `"Classe#metodo"` static sem args |

Aceita também um array puro no topo. `@DuckTui(menus = ...)` **ignora** as
annotations.

### Resolução de nomes

Você não informa pacote. A lib indexa as classes dos pacotes varridos e resolve:

1. nome simples (`"ArquivoMenu"`) → busca no índice;
2. nome completo → `Class.forName`;
3. nada encontrado ou ambíguo → erro listando as candidatas.

## Regras validadas no start

Antes de desenhar a primeira linha:

- ids únicos, pai existente, sem ciclos, pelo menos um menu raiz;
- `@Menu` não pode estar aninhado em outro menu;
- `@Menu` e `@Submenu` não podem coexistir na mesma classe;
- `@Item` precisa ser `static` e sem parâmetros.

Exemplo de erro:

```
@Submenu 'app.Solto' nao declara parent e nao esta aninhado em um menu anotado.
Declare o pai: @Submenu(parent = "id-do-menu-pai", ...).
```

## Limites

- Varredura via `getResources`: cobre classpath normal (dirs e jars). Em GraalVM
  native image ou JPMS fechado pode não ver tudo — use nomes completos no JSON.
- Validação em **runtime**, não em tempo de compilação.
- `@OnAction` é a única annotation nativa do TamboUI e só funciona em `Component`
  (não em `InlineApp`).
- Jackson é a única dependência além do TamboUI/JLine.

## Repositório da app de exemplo

O consumidor de referência fica em um repositório separado (`DuckTUI-App`), que
depende apenas do artifact publicado por esta lib.

## Licença

MIT.
