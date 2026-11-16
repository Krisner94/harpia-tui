package com.github.rhama.ducktui.menus;

import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Submenu;

/** Submenu dentro de outro submenu: {@code parent} aponta para "recentes". */
@Submenu(id = "arquivados", parent = "recentes", label = "Arquivados", order = 3)
public class ArquivadosMenu {

    @Item(label = "lago-seco.duck")
    public static String seco() {
        return "abrindo lago-seco.duck (arquivado)";
    }
}
