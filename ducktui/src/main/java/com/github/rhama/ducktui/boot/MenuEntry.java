package com.github.rhama.ducktui.boot;

import java.util.List;

/**
 * Um no da arvore de menus: menu, submenu ou item executavel.
 *
 * <p>Guarda apenas dados: a execucao de um item e delegada ao
 * {@code DuckTuiRegistry} gerado em tempo de compilacao, via
 * {@link #actionId()}.
 *
 * @param label    texto exibido
 * @param depth    nivel de indentacao (0 = raiz)
 * @param kind     tipo do no
 * @param order    ordem entre irmaos
 * @param children filhos (submenus e itens)
 * @param actionId id da acao ({@code classe#metodo}), quando {@code kind == ITEM}
 */
record MenuEntry(
        String label,
        int depth,
        Kind kind,
        int order,
        List<MenuEntry> children,
        String actionId) {

    enum Kind { MENU, SUBMENU, ITEM }

    MenuEntry {
        children = List.copyOf(children);
    }

    boolean isItem() {
        return kind == Kind.ITEM;
    }

    /** Percorre a arvore em profundidade, adicionando os nos na lista. */
    void flattenInto(List<MenuEntry> destino) {
        destino.add(this);
        for (MenuEntry filho : children) {
            filho.flattenInto(destino);
        }
    }
}
