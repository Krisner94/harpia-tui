# HarpiaTUI

Biblioteca Java para TUIs de terminal **inline** e **declarativas**, sobre
[TamboUI](https://tamboui.dev/docs/main/).

Você anota a classe com `@HarpiaMain` e **não escreve `main`**: o
`harpiatui-processor` gera o ponto de entrada e o registro de menus na
compilação. Em runtime não há varredura de classpath nem reflexão sobre as
anotações.

```java
@HarpiaMain(title = "Minha App")
public class MinhaApp {

    @Menu(id = "arquivo", label = "Arquivo", order = 1)
    public static class Arquivo {
        @Item(label = "Novo")
        public static String novo() { return "criado"; }
    }
}
```

`MinhaApp` não tem `main`. A compilação gera:

```java
public final class MinhaApp_HarpiaLauncher {
    public static void main(String[] args) throws Exception {
        HarpiaLauncher.launch(new MinhaApp_HarpiaRegistry());
    }
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

## Como funciona (build × runtime)

**Build** — o `harpiatui-processor` roda dentro do `javac`:

1. lê `@HarpiaMain`, `@Menu`, `@Submenu`, `@Item` do modelo de fontes;
2. valida a estrutura (ids, parents, ciclos, assinaturas);
3. gera `MinhaApp_HarpiaRegistry` com os metadados e um `switch` que chama os
   métodos `@Item` **diretamente**;
4. gera `MinhaApp_HarpiaLauncher` com o `main`;
5. registra o registro em `META-INF/services` (fallback de `launch(Class)`).

**Runtime**: monta a árvore a partir das listas já prontas e executa itens por
`switch`. O caminho normal (`MinhaApp_HarpiaLauncher`) nem usa `ServiceLoader`.

O que **não** existe em runtime: `Class.forName`, `Files.walk`, `getResources`
sobre diretórios, `isAnnotationPresent`, `getDeclaredMethods`, `Method.invoke`,
`setAccessible`. As anotações têm retenção `SOURCE` — nem existem no `.class`.

| Operação | Antes (scan) | Agora |
| --- | --- | --- |
| Descobrir menus | varria todas as classes do pacote | lista constante gerada |
| Executar item | `Method.invoke` | chamada estática direta |
| Escrever `main` | você | gerado no build |
| Erro de estrutura | exception no start | **erro de compilação** |

## Por que o `main` nasce em outra classe

Java não permite que uma annotation adicione um método à classe anotada — só
ferramentas que mexem na AST do `javac` fazem isso (Lombok), com APIs internas e
risco de quebrar. A alternativa estável é gerar uma classe companheira no mesmo
pacote (`MinhaApp_HarpiaLauncher`). Para você é igual: continua sem escrever
`main`, e o nome é previsível.

## Requisitos

- Java 21+
- Um **terminal de verdade** para executar apps (inline TUI não funciona pela IDE)

## Instalar localmente (desenvolvimento)

```bash
mvn install
```

Publica `com.github.krisner94:harpiatui` e `com.github.krisner94:harpiatui-processor`
em `~/.m2/repository`.

## Publicar no GitHub Packages

O projeto já tem `distributionManagement` apontando para
`https://maven.pkg.github.com/Krisner94/harpia-tui`.

**Pela CI (recomendado):** publique uma *Release* no GitHub. O workflow
`.github/workflows/publish.yml` roda `mvn deploy` com o `GITHUB_TOKEN` e publica
os artifacts. Também dá para disparar à mão em *Actions → publish → Run workflow*.

**Da sua máquina:** copie `settings.xml.example` para `~/.m2/settings.xml`,
exporte um PAT e rode:

```bash
export GITHUB_TOKEN=ghp_xxx        # escopo write:packages
mvn deploy -DskipTests
```

> GitHub Packages **não aceita sobrescrever** uma versão já publicada. Para
> publicar de novo, suba a versão (`mvn versions:set -DnewVersion=1.0.1`).

## Usar em outro projeto

Basta a dependência:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.krisner94</groupId>
        <artifactId>harpiatui</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

O **annotation processor vem junto** (`harpiatui-processor` é dependência
transitiva da lib) e o `javac` o descobre no classpath. Você não configura mais
nada no `pom.xml`.

E, por causa do TamboUI transitivo (só existe como snapshot):

```xml
<repositories>
    <repository>
        <id>ossrh-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

Gradle:

```kotlin
dependencies {
    implementation("com.github.krisner94:harpiatui:1.0.0-SNAPSHOT")
}
```

### Configuração explícita do processor (opcional)

Em JDK 23+ o `javac` deixa de executar processors encontrados no classpath por
padrão. Nesse caso, declare o caminho — o artifact já está disponível:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.github.krisner94</groupId>
                <artifactId>harpiatui-processor</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

> Se o código gerado não aparecer (`<App>_HarpiaLauncher` em
> `target/generated-sources`), é sinal de que o processor não rodou: use o bloco
> acima.

```xml
<repositories>
    <!-- artifacts publicados pelo harpia-tui -->
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Krisner94/harpia-tui</url>
        <releases><enabled>true</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>

    <!-- dependencias do TamboUI -->
    <repository>
        <id>ossrh-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

**Autenticação (obrigatória):** o GitHub Packages exige credencial até para ler,
mesmo em repositório público. No seu `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>Krisner94</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
```

com `export GITHUB_TOKEN=ghp_xxx` (escopo `read:packages`).

Gradle:

```kotlin
dependencies {
    implementation("com.github.krisner94:harpiatui:1.0.0-SNAPSHOT")
    annotationProcessor("com.github.krisner94:harpiatui-processor:1.0.0-SNAPSHOT")
}
```

> O classpath do projeto já traz o processor transitivamente, então o build
> funciona sem configuração extra no JDK 21.

## Rodar

O ponto de entrada é a classe gerada:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.exemplo.MinhaApp_HarpiaLauncher
```

No `pom.xml`, dá para fixar:

```xml
<properties>
    <main.class>com.exemplo.MinhaApp_HarpiaLauncher</main.class>
</properties>
```

## Annotations

| Annotation | Onde vai | Campos | Regra |
| --- | --- | --- | --- |
| `@HarpiaMain` | classe da aplicação | `title`, `menus`, `height` | uma por app; gera o `main` |
| `@Menu` | qualquer classe | `id`, `label`, `order` | **sempre pai**: sem `parent` |
| `@Submenu` | qualquer classe | `id`, `parent`, `label`, `order` | **sempre filho** de um menu |
| `@Item` | método `public static` sem args | `label`, `order` | folha executável |

- `id` vazio = nome simples da classe; `label` vazio idem (em `@Item`, o nome do método).
- `order` posiciona entre irmãos; empate desempata pelo label.
- `@Submenu` aninhado em classe anotada dispensa `parent`.
- A classe dona de um `@Item` precisa ser `public` se estiver em outro pacote.
- Se o método devolver algo, o valor é impresso acima do status.

## Árvore por arquivo JSON

```java
@HarpiaMain(title = "Minha App", menus = "menus.json")
public class MinhaApp {
    @Item(label = "Recarregar")
    public static String recarregar() { return "ok"; }
}
```

Lido **em runtime** (classpath, depois disco), então editar o JSON não exige
recompilar. A resolução usa o registro gerado — nada de reflexão.

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
| `class` | não | classe cujos `@Item` são herdados (nome simples ou completo) |
| `items[]` | não | itens avulsos (`label`, `action`, `order`) |
| `action` | sim (item) | `"Classe#metodo"` de um `@Item` existente |

Só métodos anotados com `@Item` podem ser chamados no JSON — é o preço de não
usar reflexão.

## Erros em tempo de compilação

```
src/main/java/app/Main.java:[6,19] @Submenu 'app.Main.Orfao' nao declara parent
    e nao esta aninhado em um menu anotado.
src/main/java/app/Main.java:[6,62] @Item 'quebrado' precisa ser public static.
src/main/java/app/Main.java:[6,55] Id de menu duplicado: 'mesmo'.
src/main/java/app/Main.java:[6,65] Ciclo de menu detectado envolvendo 'a'.
```

## Limites

- O processor vê apenas as fontes do módulo compilado. Menus vindos de uma
  dependência precisam ter sido compilados com o processor nessa dependência.
- `@Item` sem reflexão implica `public static` e sem parâmetros.
- A classe gerada do `main` tem nome previsível, mas é gerada — não edite.
- Validação de JSON é em runtime (o arquivo não é conhecido no build).

## Licença

MIT.
