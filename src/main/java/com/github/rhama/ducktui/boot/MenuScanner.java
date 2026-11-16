package com.github.rhama.ducktui.boot;

import com.github.rhama.ducktui.annotation.DuckTui;
import com.github.rhama.ducktui.annotation.Item;
import com.github.rhama.ducktui.annotation.Menu;
import com.github.rhama.ducktui.annotation.Submenu;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Descobre os menus anotados e monta a arvore.
 *
 * <p>E o "component scan" do DuckTUI: varre os pacotes configurados em
 * {@link DuckTui#scan()}, encontra toda classe com {@link Menu @Menu} ou
 * {@link Submenu @Submenu} e liga os {@link Submenu @Submenu} aos seus pais
 * pelo {@link Submenu#parent()}.
 *
 * <p>Regras validadas antes de desenhar qualquer coisa:
 * <ul>
 *   <li>{@code @Menu} e sempre raiz (sem parent, nao aninhado em outro menu)</li>
 *   <li>{@code @Submenu} precisa de um pai {@code @Menu} ou {@code @Submenu} existente</li>
 *   <li>ids unicos, sem ciclos</li>
 *   <li>{@code @Item} precisa ser {@code static} e sem parametros</li>
 * </ul>
 */
public final class MenuScanner {

    private static final Comparator<MenuEntry> ORDEM =
            Comparator.comparingInt(MenuEntry::order).thenComparing(MenuEntry::label);

    private MenuScanner() {
    }

    /** Descobre e monta a arvore de menus da aplicacao anotada. */
    public static List<MenuEntry> scan(Class<?> appClass) {
        DuckTui config = appClass.getAnnotation(DuckTui.class);
        if (config == null) {
            throw new DuckTuiException(
                    "A classe " + appClass.getName() + " precisa da annotation @DuckTui para ser iniciada.");
        }

        String[] pacotes = config.scan().length > 0
                ? config.scan()
                : new String[] {appClass.getPackageName()};

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = appClass.getClassLoader();
        }

        Map<String, Class<?>> porId = new LinkedHashMap<>();
        Map<String, String> paiDe = new LinkedHashMap<>();
        List<String> raizes = new ArrayList<>();

        for (String pacote : pacotes) {
            for (Class<?> tipo : ClassScanner.scan(loader, pacote)) {
                boolean ehMenu = tipo.isAnnotationPresent(Menu.class);
                boolean ehSubmenu = tipo.isAnnotationPresent(Submenu.class);

                if (!ehMenu && !ehSubmenu) {
                    continue;
                }
                if (ehMenu && ehSubmenu) {
                    throw new DuckTuiException(
                            "@Menu e @Submenu nao podem estar na mesma classe: " + tipo.getName());
                }

                String id = idDe(tipo);
                Class<?> anterior = porId.putIfAbsent(id, tipo);
                if (anterior != null) {
                    throw new DuckTuiException("""
                            Id de menu duplicado: '%s'.
                            Usado por '%s' e por '%s'.
                            Defina um id unico em @Menu/@Submenu."""
                            .formatted(id, anterior.getName(), tipo.getName()));
                }

                if (ehMenu) {
                    if (temAncestralAnotado(tipo)) {
                        throw new DuckTuiException("""
                                @Menu '%s' esta aninhado em outro menu.
                                @Menu e sempre o no pai: para menus aninhados, use @Submenu."""
                                .formatted(tipo.getName()));
                    }
                    raizes.add(id);
                } else {
                    paiDe.put(id, resolverPai(tipo));
                }
            }
        }

        if (porId.isEmpty()) {
            throw new DuckTuiException("""
                    Nenhum @Menu encontrado.
                    Pacotes varridos: %s.
                    Anote as classes de menu com @Menu ou @Submenu."""
                    .formatted(String.join(", ", pacotes)));
        }

        validarPais(porId, paiDe);
        validarCiclos(paiDe);

        Map<String, List<String>> filhosDe = new HashMap<>();
        paiDe.forEach((filho, pai) -> filhosDe.computeIfAbsent(pai, chave -> new ArrayList<>()).add(filho));

        List<MenuEntry> arvore = new ArrayList<>(raizes.size());
        for (String raiz : raizes) {
            arvore.add(montar(raiz, 0, porId, filhosDe));
        }

        arvore.sort(ORDEM);
        return List.copyOf(arvore);
    }

    private static MenuEntry montar(
            String id, int profundidade, Map<String, Class<?>> porId, Map<String, List<String>> filhosDe) {

        Class<?> tipo = porId.get(id);
        boolean ehMenu = tipo.isAnnotationPresent(Menu.class);

        String label = ehMenu
                ? tipo.getAnnotation(Menu.class).label()
                : tipo.getAnnotation(Submenu.class).label();
        int ordem = ehMenu
                ? tipo.getAnnotation(Menu.class).order()
                : tipo.getAnnotation(Submenu.class).order();

        if (label.isEmpty()) {
            label = tipo.getSimpleName();
        }

        List<MenuEntry> filhos = new ArrayList<>();

        for (String filho : filhosDe.getOrDefault(id, List.of())) {
            filhos.add(montar(filho, profundidade + 1, porId, filhosDe));
        }

        for (Method metodo : tipo.getDeclaredMethods()) {
            if (metodo.isAnnotationPresent(Item.class)) {
                filhos.add(lerItem(metodo, profundidade + 1));
            }
        }

        filhos.sort(ORDEM);

        return new MenuEntry(
                label,
                profundidade,
                ehMenu ? MenuEntry.Kind.MENU : MenuEntry.Kind.SUBMENU,
                ordem,
                filhos,
                null);
    }

    private static String idDe(Class<?> tipo) {
        String id = tipo.isAnnotationPresent(Menu.class)
                ? tipo.getAnnotation(Menu.class).id()
                : tipo.getAnnotation(Submenu.class).id();
        return id.isEmpty() ? tipo.getSimpleName() : id;
    }

    private static String resolverPai(Class<?> tipo) {
        Submenu anotacao = tipo.getAnnotation(Submenu.class);

        if (!anotacao.parent().isEmpty()) {
            return anotacao.parent();
        }

        Class<?> pai = tipo.getEnclosingClass();
        while (pai != null && !(pai.isAnnotationPresent(Menu.class) || pai.isAnnotationPresent(Submenu.class))) {
            pai = pai.getEnclosingClass();
        }

        if (pai == null) {
            throw new DuckTuiException("""
                    @Submenu '%s' nao declara parent e nao esta aninhado em um menu anotado.
                    Declare o pai: @Submenu(parent = "id-do-menu-pai", ...)."""
                    .formatted(tipo.getName()));
        }

        return idDe(pai);
    }

    private static boolean temAncestralAnotado(Class<?> tipo) {
        Class<?> pai = tipo.getEnclosingClass();
        while (pai != null) {
            if (pai.isAnnotationPresent(Menu.class) || pai.isAnnotationPresent(Submenu.class)) {
                return true;
            }
            pai = pai.getEnclosingClass();
        }
        return false;
    }

    private static void validarPais(Map<String, Class<?>> porId, Map<String, String> paiDe) {
        paiDe.forEach((filho, pai) -> {
            if (filho.equals(pai)) {
                throw new DuckTuiException(
                        "@Submenu '%s' aponta para si mesmo em parent.".formatted(filho));
            }
            if (!porId.containsKey(pai)) {
                throw new DuckTuiException("""
                        @Submenu '%s' aponta para parent='%s', que nao existe.
                        Ids disponiveis: %s."""
                        .formatted(porId.get(filho).getName(), pai, porId.keySet()));
            }
        });
    }

    private static void validarCiclos(Map<String, String> paiDe) {
        for (String inicio : paiDe.keySet()) {
            Set<String> vistos = new HashSet<>();
            String atual = inicio;
            while (atual != null) {
                if (!vistos.add(atual)) {
                    throw new DuckTuiException(
                            "Ciclo de @Submenu detectado envolvendo '" + inicio + "'.");
                }
                atual = paiDe.get(atual);
            }
        }
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
