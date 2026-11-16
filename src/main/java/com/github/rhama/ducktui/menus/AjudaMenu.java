package com.github.rhama.ducktui.menus;

import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Menu;

/** Outro menu raiz, descoberto sozinho pelo scanner. */
@Menu(id = "ajuda", label = "Ajuda", order = 2)
public class AjudaMenu {

    @Item(label = "Sobre o DuckTUI", order = 1)
    public static String sobre() {
        return "DuckTUI 1.0 - TamboUI 0.8.0-SNAPSHOT";
    }

    @Item(label = "Documentacao", order = 2)
    public static String documentacao() {
        return "https://tamboui.dev/docs/main/";
    }
}
