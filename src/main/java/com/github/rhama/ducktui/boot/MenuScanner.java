package com.github.rhama.ducktui.boot;

import com.github.rhama.ducktui.annotation.DuckTui;
import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Menu;
import com.github.rhama.ducktui.annotation.Submenu;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le as annotations {@link Menu @Menu}, {@link Submenu @Submenu} e {@link Item @Item}
 * de uma classe {@link DuckTui @DuckTui} e monta a arvore de menus.
 *
 * <p>Aqui ficam as regras estruturais que o Java nao sabe expressar sozinho:
 * <ul>
 *   <li>{@code @Menu} e sempre no pai -> so pode ficar no nivel raiz</li>
 *   <li>{@code @Submenu} so existe dentro de {@code @Menu} ou {@code @Submenu}</li>
 *   <li>{@code @Item} precisa ser {@code static} e sem parametros</li>
 * </ul>
 * Qualquer violacao aborta a aplicacao com {@link DuckTuiException} antes de
 * qualquer desenho na tela.
 */
public final class MenuScanner {

    private static final Comparator<MenuEntry> ORDEM =
            Comparator.comparingInt(MenuEntry::order).thenComparing(MenuEntry::label);

    private MenuScanner() {
    }

    /** Monta a arvore de menus da aplicacao anotada. */
    public static List<MenuEntry> scan(Class<?> appClass) {
        List<MenuEntry> raizes = new ArrayList<>();

        for (Class<?> aninhada : appClass.getDeclaredClasses()) {
            if (aninhada.isAnnotationPresent(Menu.class)) {
                raizes.add(lerMenu(aninhada));
            } else if (aninhada.isAnnotationPresent(Submenu.class)) {
                throw new DuckTuiException("""
                        @Submenu '%s' esta no nivel raiz.
                        @Submenu so pode existir dentro de uma classe @Menu ou @Submenu.
                        Para um menu de topo, use @Menu em vez de @Submenu."""
                        .formatted(aninhada.getName()));
            }
        }

        raizes.sort(ORDEM);
        return List.copyOf(raizes);
    }

    private static MenuEntry lerMenu(Class<?> tipo) {
        Class<?> pai = tipo.getEnclosingClass();
        if (pai != null && (pai.isAnnotationPresent(Menu.class) || pai.isAnnotationPresent(Submenu.class))) {
            throw new DuckTuiException("""
                    @Menu '%s' esta dentro de outro menu.
                    @Menu e sempre o no pai: para menus aninhados, use @Submenu."""
                    .formatted(tipo.getName()));
        }

        Menu anotacao = tipo.getAnnotation(Menu.class);
        return lerNo(tipo, anotacao.label(), anotacao.order(), 0, MenuEntry.Kind.MENU);
    }

    private static MenuEntry lerSubmenu(Class<?> tipo, int profundidade) {
        Class<?> pai = tipo.getEnclosingClass();
        boolean paiValido =
                pai != null && (pai.isAnnotationPresent(Menu.class) || pai.isAnnotationPresent(Submenu.class));

        if (!paiValido) {
            throw new DuckTuiException("""
                    @Submenu '%s' nao esta dentro de um menu.
                    Ele precisa ser uma classe aninhada em uma classe @Menu ou @Submenu."""
                    .formatted(tipo.getName()));
        }

        Submenu anotacao = tipo.getAnnotation(Submenu.class);
        return lerNo(tipo, anotacao.label(), anotacao.order(), profundidade, MenuEntry.Kind.SUBMENU);
    }

    private static MenuEntry lerNo(Class<?> tipo, String label, int ordem, int profundidade, MenuEntry.Kind kind) {
        List<MenuEntry> filhos = new ArrayList<>();

        for (Class<?> aninhada : tipo.getDeclaredClasses()) {
            if (aninhada.isAnnotationPresent(Submenu.class)) {
                filhos.add(lerSubmenu(aninhada, profundidade + 1));
            } else if (aninhada.isAnnotationPresent(Menu.class)) {
                throw new DuckTuiException("""
                        @Menu '%s' esta dentro de '%s'.
                        @Menu e sempre o no pai: para menus aninhados, use @Submenu."""
                        .formatted(aninhada.getName(), tipo.getName()));
            }
        }

        for (Method metodo : tipo.getDeclaredMethods()) {
            if (metodo.isAnnotationPresent(Item.class)) {
                filhos.add(lerItem(metodo, profundidade + 1));
            }
        }

        filhos.sort(ORDEM);
        return new MenuEntry(label, profundidade, kind, ordem, filhos, null);
    }

    private static MenuEntry lerItem(Method metodo, int profundidade) {
        Item anotacao = metodo.getAnnotation(Item.class);
        String nome = anotacao.label().isEmpty() ? metodo.getName() : anotacao.label();

        if (!Modifier.isStatic(metodo.getModifiers())) {
            throw new DuckTuiException("""
                    @Item '%s' precisa ser um metodo static.
                    Metodo encontrado: %s"""
                    .formatted(nome, metodo));
        }

        if (metodo.getParameterCount() != 0) {
            throw new DuckTuiException("""
                    @Item '%s' nao pode receber parametros.
                    Metodo encontrado: %s"""
                    .formatted(nome, metodo));
        }

        metodo.setAccessible(true);
        return new MenuEntry(nome, profundidade, MenuEntry.Kind.ITEM, anotacao.order(), List.of(), metodo);
    }
}
