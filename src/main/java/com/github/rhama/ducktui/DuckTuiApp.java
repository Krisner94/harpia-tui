package com.github.rhama.ducktui;

import com.github.rhama.ducktui.annotation.DuckTui;
import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Menu;
import com.github.rhama.ducktui.annotation.Submenu;
import com.github.rhama.ducktui.boot.DuckTuiLauncher;

/**
 * Aplicacao de exemplo: a classe {@code main} so carrega a annotation de boot.
 *
 * <p>Repare que ela nao estende nada. Quem sobe a aplicacao e o
 * {@link DuckTuiLauncher}, que le {@code @DuckTui} e varre a arvore declarada
 * abaixo.
 */
@DuckTui(title = "DuckTUI")
public class DuckTuiApp {

    @Menu(label = "Arquivo", order = 1)
    static class Arquivo {

        @Item(label = "Novo lago", order = 1)
        static String novoLago() {
            return "lago novo criado";
        }

        @Item(label = "Abrir...", order = 2)
        static String abrir() {
            return "abrir: ainda nao implementado";
        }

        // Submenu dentro de @Menu: valido.
        @Submenu(label = "Recentes", order = 3)
        static class Recentes {

            @Item(label = "lago-norte.duck", order = 1)
            static String norte() {
                return "abrindo lago-norte.duck";
            }

            @Item(label = "lago-sul.duck", order = 2)
            static String sul() {
                return "abrindo lago-sul.duck";
            }

            // Submenu dentro de submenu: tambem valido.
            @Submenu(label = "Arquivados", order = 3)
            static class Arquivados {

                @Item(label = "lago-seco.duck")
                static String seco() {
                    return "abrindo lago-seco.duck (arquivado)";
                }
            }
        }

        // Sem label: o nome do metodo vira o texto do menu.
        @Item(order = 4)
        static String exportar() {
            return "exportado";
        }
    }

    @Menu(label = "Ajuda", order = 2)
    static class Ajuda {

        @Item(label = "Sobre o DuckTUI", order = 1)
        static String sobre() {
            return "DuckTUI 1.0 - TamboUI 0.8.0-SNAPSHOT";
        }

        @Item(label = "Documentacao", order = 2)
        static String documentacao() {
            return "https://tamboui.dev/docs/main/";
        }
    }

    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiApp.class);
    }
}
