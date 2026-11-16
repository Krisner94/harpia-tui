# DuckTUI

Biblioteca Java para TUIs de terminal **inline** e **declarativas**, sobre
[TamboUI](https://tamboui.dev/docs/main/).

A árvore de menus é resolvida em **tempo de compilação**, no estilo Quarkus: um
annotation processor lê as anotações e gera um *registro*. Em runtime não há
varredura de classpath nem reflexão sobre as anotações.

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

## Como funciona (build × runtime)

**Build** — o `ducktui-processor` roda dentro do `javac`:

1. lê `@DuckTui`, `@Menu`, `@Submenu`, `@Item` do modelo de fontes;
2. valida a estrutura (ids, parents, ciclos, assinaturas);
3. gera `MinhaApp_DuckTuiRegistry`, que devolve os metadados e implementa
   `invoke(...)` com um `switch` chamando os métodos **diretamente**;
4. registra a classe em `META-INF/services/...DuckTuiRegistry`.

**Runtime**:

1. `ServiceLoader` localiza o registro (um `ClassLoader` lookup, sem varredura);
2. monta a árvore a partir das listas já prontas;
3. ao executar um item, chama `registry.invoke(id)` — um `switch` compilado.

O que **não** existe em runtime: `Class.forName`, `Files.walk`, `getResources`
sobre diretórios, `isAnnotationPresent`, `getDeclaredMethods`, `Method.invoke`,
`setAccessible`. As anotações têm retenção `SOURCE` — nem existem no `.class`.

| Operação | Antes (scan) | Agora |
| --- | --- | --- |
| Descobrir menus | varria todas as classes do pacote | lista constante gerada |
| Executar item | `Method.invoke` | chamada estática direta |
| Erro de estrutura | exception no start | **erro de compilação** |

## Requisitos

- Java 21+
- Um **terminal de verdade** para executar apps (inline TUI não funciona pela IDE)

## Instalar

```bash
mvn install
```

Publica `com.github.rhama:ducktui` e `com.github.rhama:ducktui-processor` em
`~/.m2/repository`.

## Usar em outro projeto

Duas dependências — a lib e o processor:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.rhama</groupId>
        <artifactId>ducktui</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.github.rhama</groupId>
                        <artifactId>ducktui-processor</artifactId>
                        <version>1.0.0-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

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
repositories {
    mavenLocal()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    implementation("com.github.rhama:ducktui:1.0.0-SNAPSHOT")
    annotationProcessor("com.github.rhama:ducktui-processor:1.0.0-SNAPSHOT")
}
```

> **Sem o processor não funciona.** O runtime não descobre nada sozinho — se o
> registro não existir, `launch` falha com uma mensagem mostrando como configurar.

## Annotations

| Annotation | Onde vai | Campos | Regra |
| --- | --- | --- | --- |
| `@DuckTui` | classe com `main` | `title`, `menus`, `height` | uma por app |
| `@Menu` | qualquer classe | `id`, `label`, `order` | **sempre pai**: sem `parent` |
| `@Submenu` | qualquer classe | `id`, `parent`, `label`, `order` | **sempre filho** de um menu |
| `@Item` | método `public static` sem args | `label`, `order` | folha executável |

- `id` vazio = nome simples da classe; `label` vazio idem (em `@Item`, o nome do método).
- `order` posiciona entre irmãos; empate desempata pelo label.
- `@Submenu` aninhado em classe anotada dispensa `parent`.
- `@Item` deve ser `public static` (chamada direta). A classe dona precisa ser
  `public` se estiver em pacote diferente do registro.
- Se o método devolver algo, o valor é impresso acima do status.

## Árvore por arquivo JSON

```java
@DuckTui(title = "Minha App", menus = "menus.json")
public class MinhaApp { /* ... */ }
```

Lido **em runtime** (classpath, depois disco), então editar o JSON não exige
recompilar. A resolução ainda usa o registro gerado — nada de reflexão.

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

## Sobre o "zero reflexão"

O único mecanismo reflexivo que sobra é o `ServiceLoader` carregar a classe
gerada. Não há varredura nem inspeção de membros. Se quiser eliminar até isso,
passe o registro direto:

```java
DuckTuiLauncher.launch(new MinhaApp_DuckTuiRegistry());
```

A classe gerada é `public final`, então dá para instanciá-la normalmente.

## Limites

- Um `@DuckTui` por app; vários apps no mesmo módulo geram um registro cada.
- O processor vê apenas as fontes do módulo compilado. Menus vindos de uma
  dependência precisam ter sido compilados com o processor nessa dependência.
- `@Item` sem reflexão implica `public static` e sem parâmetros.
- Validação de JSON é em runtime (o arquivo não é conhecido no build).

## Licença

MIT.
