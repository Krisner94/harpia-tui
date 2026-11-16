package com.github.rhama.ducktui;

import com.github.rhama.ducktui.annotation.DuckTui;
import com.github.rhama.ducktui.boot.DuckTuiLauncher;

/**
 * Mesma aplicacao, mas a arvore de menus vem do {@code menus.json} em vez das
 * annotations.
 *
 * <p>O JSON cita as classes por nome simples ({@code "class": "ArquivoMenu"});
 * o launcher localiza cada uma no classpath e herda os {@code @Item} dela.
 * Tambem da para declarar itens avulsos por {@code "action": "Classe#metodo"}.
 *
 * <p>Rode com {@code ./run.sh json}.
 */
@DuckTui(title = "DuckTUI (json)", menus = "menus.json")
public class DuckTuiJsonApp {

    /** Chamado pelo item "Recarregar menus" declarado no JSON. */
    public static String recarregar() {
        return "menus recarregados";
    }

    public static void main(String[] args) throws Exception {
        DuckTuiLauncher.launch(DuckTuiJsonApp.class);
    }
}
