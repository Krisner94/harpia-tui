package com.github.rhama.harpiatui.boot;

import com.github.rhama.harpiatui.spi.HarpiaRegistry;
import com.github.rhama.harpiatui.spi.HarpiaSettings;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Ponto de entrada do HarpiaTUI.
 *
 * <p>Nao ha varredura de classpath: a arvore vem do registro gerado pelo
 * {@code harpiatui-processor} em tempo de compilacao.
 *
 * <p>Normalmente voce <b>nao usa esta classe</b>: o processor gera
 * {@code MinhaApp_HarpiaLauncher} com o {@code main} pronto e o registro
 * instanciado direto (sem {@link ServiceLoader}).
 *
 * <pre>{@code
 * @HarpiaMain(title = "Minha App")
 * public class MinhaApp {
 *     // sem main: gere com o processor e rode MinhaApp_HarpiaLauncher
 * }
 * }</pre>
 */
public final class HarpiaLauncher {

    private HarpiaLauncher() {
    }

    /**
     * Inicia a aplicacao anotada com {@code @HarpiaMain}, usando o registro gerado
     * na compilacao.
     *
     * @param appClass a classe anotada (normalmente {@code MinhaApp.class})
     * @throws HarpiaException se o processor nao tiver sido configurado
     * @throws Exception        se o terminal falhar ao iniciar
     */
    public static void launch(Class<?> appClass) throws Exception {
        launch(procurar(appClass.getName()));
    }

    /**
     * Inicia a aplicacao com um registro explicito.
     *
     * <p>Use esta forma se quiser evitar ate o {@link ServiceLoader} — passe a
     * instancia da classe gerada ({@code MinhaApp_HarpiaRegistry}).
     */
    public static void launch(HarpiaRegistry registry) throws Exception {
        HarpiaSettings settings = registry.settings();

        List<MenuEntry> arvore = settings.menusFile().isBlank()
                ? MenuTreeBuilder.dasAnnotations(registry)
                : MenuTreeBuilder.doJson(settings.menusFile(), registry);

        new MenuApp(settings, registry, arvore).run();
    }

    private static HarpiaRegistry procurar(String appClassName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = HarpiaLauncher.class.getClassLoader();
        }

        for (HarpiaRegistry registro : ServiceLoader.load(HarpiaRegistry.class, loader)) {
            if (registro.appClassName().equals(appClassName)) {
                return registro;
            }
        }

        throw new HarpiaException("""
                Nenhum registro gerado para '%s'.
                O annotation processor nao esta configurado no build. Adicione:

                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                      <annotationProcessorPaths>
                        <path>
                          <groupId>com.github.rhama</groupId>
                          <artifactId>harpiatui-processor</artifactId>
                          <version>1.0.0-SNAPSHOT</version>
                        </path>
                      </annotationProcessorPaths>
                    </configuration>
                  </plugin>"""
                .formatted(appClassName));
    }
}
