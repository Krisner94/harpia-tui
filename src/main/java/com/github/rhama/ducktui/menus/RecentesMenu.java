package com.github.rhama.ducktui.menus;

import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Submenu;

/**
 * Submenu ligado ao menu "arquivo" pelo {@code parent}.
 *
 * <p>Esta classe fica em outro arquivo, sem nenhum vinculo estrutural com
 * {@link ArquivoMenu} -- quem faz a ligacao e o scanner.
 */
@Submenu(id = "recentes", parent = "arquivo", label = "Recentes", order = 3)
public class RecentesMenu {

    @Item(label = "lago-norte.duck", order = 1)
    public static String norte() {
        return "abrindo lago-norte.duck";
    }

    @Item(label = "lago-sul.duck", order = 2)
    public static String sul() {
        return "abrindo lago-sul.duck";
    }
}
