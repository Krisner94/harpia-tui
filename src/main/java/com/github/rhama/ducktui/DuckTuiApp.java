package com.github.rhama.ducktui;

import com.github.rhama.ducktui.annotation.DuckTui;
import com.github.rhama.ducktui.boot.DuckTuiLauncher;

/**
 * Ponto de entrada.
 *
 * <p>A classe so declara a annotation de boot. Os menus ficam em qualquer classe
 * do pacote {@code com.github.rhama.ducktui} (e subpacotes) anotada com
 * {@code @Menu} / {@code @Submenu} -- o launcher varre o classpath e monta a
 * arvore sozinho. Veja {@code com.github.rhama.ducktui.menus}.
 */
@DuckTui(title = "DuckTUI")
public class DuckTuiApp {

    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiApp.class);
    }
}
