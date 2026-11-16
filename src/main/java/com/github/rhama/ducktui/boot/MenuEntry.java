package com.github.rhama.ducktui.boot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Um no da arvore de menus: pode ser um menu, um submenu ou um item executavel.
 *
 * @param label    texto exibido
 * @param depth    nivel de indentacao (0 = raiz)
 * @param kind     tipo do no
 * @param order    ordem entre irmaos
 * @param children filhos (submenus e itens)
 * @param action   metodo a executar, quando {@code kind == ITEM}
 */
public record MenuEntry(
        String label,
        int depth,
        Kind kind,
        int order,
        List<MenuEntry> children,
        Method action) {

    public enum Kind { MENU, SUBMENU, ITEM }

    public MenuEntry {
        children = List.copyOf(children);
    }

    public boolean isItem() {
        return kind == Kind.ITEM;
    }

    /** Executa o {@code @Item} e devolve o valor retornado pelo metodo. */
    public Object invoke() {
        if (action == null) {
            throw new DuckTuiException("'" + label + "' nao e um item executavel");
        }
        try {
            return action.invoke(null);
        } catch (InvocationTargetException e) {
            Throwable causa = e.getCause() == null ? e : e.getCause();
            throw new DuckTuiException("Falha ao executar '" + label + "': " + causa, causa);
        } catch (ReflectiveOperationException e) {
            throw new DuckTuiException("Falha ao executar '" + label + "'", e);
        }
    }

    /** Percorre a arvore em profundidade, adicionando os nos na lista. */
    public void flattenInto(List<MenuEntry> destino) {
        destino.add(this);
        for (MenuEntry filho : children) {
            filho.flattenInto(destino);
        }
    }
}
