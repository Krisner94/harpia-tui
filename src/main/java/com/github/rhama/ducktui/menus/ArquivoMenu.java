package com.github.rhama.ducktui.menus;

import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Menu;

/**
 * Menu raiz. Basta estar anotado: o scanner encontra esta classe pelo classpath.
 */
@Menu(id = "arquivo", label = "Arquivo", order = 1)
public class ArquivoMenu {

    @Item(label = "Novo lago", order = 1)
    public static String novoLago() {
        return "lago novo criado";
    }

    @Item(label = "Abrir...", order = 2)
    public static String abrir() {
        return "abrir: ainda nao implementado";
    }

    @Item(order = 4)
    public static String exportar() {
        return "exportado";
    }
}
