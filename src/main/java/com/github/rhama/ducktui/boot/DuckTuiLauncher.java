package com.github.rhama.ducktui.boot;

import com.github.rhama.ducktui.annotation.DuckTui;

import java.util.List;

/**
 * Ponto de entrada declarativo do DuckTUI.
 *
 * <p>Este e o "bootstrap" que faltava: ele le a annotation {@link DuckTui @DuckTui}
 * da sua classe, valida a hierarquia de menus e sobe a aplicacao. Sua classe
 * {@code main} nao precisa estender nada.
 *
 * <pre>{@code
 * @DuckTui(title = "DuckTUI")
 * public class MinhaApp {
 *     @Menu(label = "Arquivo")
 *     static class Arquivo {
 *         @Item(label = "Novo")
 *         static String novo() { return "criado"; }
 *     }
 *
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
     * Valida e inicia a aplicacao anotada com {@link DuckTui @DuckTui}.
     *
     * @param appClass a classe anotada (normalmente {@code MinhaApp.class})
     * @throws DuckTuiException se a annotation estiver ausente ou a hierarquia for invalida
     * @throws Exception        se o terminal falhar ao iniciar
     */
    public static void launch(Class<?> appClass) throws Exception {
        DuckTui config = appClass.getAnnotation(DuckTui.class);
        if (config == null) {
            throw new DuckTuiException(
                    "A classe " + appClass.getName() + " precisa da annotation @DuckTui para ser iniciada.");
        }

        List<MenuEntry> arvore = MenuScanner.scan(appClass);
        new MenuApp(config, arvore).run();
    }
}
