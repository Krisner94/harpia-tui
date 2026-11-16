package com.github.rhama.ducktui.boot;

import com.github.rhama.ducktui.spi.DuckTuiRegistry;
import com.github.rhama.ducktui.spi.DuckTuiSettings;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Ponto de entrada do DuckTUI.
 *
 * <p>Nao ha varredura de classpath: a arvore vem do registro gerado pelo
 * {@code ducktui-processor} em tempo de compilacao, localizado via
 * {@link ServiceLoader}.
 *
 * <pre>{@code
 * @DuckTui(title = "Minha App")
 * public class MinhaApp {
 *     public static void main(String[] args) throws Exception {
 *         DuckTuiLauncher.launch(MinhaApp.class);
 *     }
 * }
 * }</pre>
 */
public final class DuckTuiLauncher {

    private DuckTuiLauncher() {
    }

    /**
     * Inicia a aplicacao anotada com {@code @DuckTui}, usando o registro gerado
     * na compilacao.
     *
     * @param appClass a classe anotada (normalmente {@code MinhaApp.class})
     * @throws DuckTuiException se o processor nao tiver sido configurado
     * @throws Exception        se o terminal falhar ao iniciar
     */
    public static void launch(Class<?> appClass) throws Exception {
        launch(procurar(appClass.getName()));
    }

    /**
     * Inicia a aplicacao com um registro explicito.
     *
     * <p>Use esta forma se quiser evitar ate o {@link ServiceLoader} — passe a
     * instancia da classe gerada ({@code MinhaApp_DuckTuiRegistry}).
     */
    public static void launch(DuckTuiRegistry registry) throws Exception {
        DuckTuiSettings settings = registry.settings();

        List<MenuEntry> arvore = settings.menusFile().isBlank()
                ? MenuTreeBuilder.dasAnnotations(registry)
                : MenuTreeBuilder.doJson(settings.menusFile(), registry);

        new MenuApp(settings, registry, arvore).run();
    }

    private static DuckTuiRegistry procurar(String appClassName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = DuckTuiLauncher.class.getClassLoader();
        }

        for (DuckTuiRegistry registro : ServiceLoader.load(DuckTuiRegistry.class, loader)) {
            if (registro.appClassName().equals(appClassName)) {
                return registro;
            }
        }

        throw new DuckTuiException("""
                Nenhum registro gerado para '%s'.
                O annotation processor nao esta configurado no build. Adicione:

                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <annotationProcessorPaths>
                        <path>
                          <groupId>com.github.rhama</groupId>
                          <artifactId>ducktui-processor</artifactId>
                          <version>1.0.0-SNAPSHOT</version>
                        </path>
                      </annotationProcessorPaths>
                    </configuration>
                  </plugin>"""
                .formatted(appClassName));
    }
}
