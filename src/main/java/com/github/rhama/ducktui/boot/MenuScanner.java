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
import java.util.stream.Collectors;

/**
 * Monta a arvore de menus a partir de duas fontes possiveis:
 *
 * <ul>
 *   <li><b>annotations</b> ({@link Menu @Menu}, {@link Submenu @Submenu}, {@link Item @Item})</li>
 *   <li><b>arquivo JSON</b> apontado por {@link DuckTui#menus()}</li>
 * </ul>
 *
 * Nos dois casos as mesmas regras sao aplicadas antes de desenhar qualquer coisa:
 * ids unicos, pai existente, sem ciclos, pelo menos um menu raiz e metodos
 * {@code @Item} estaticos e sem parametros.
 */
public final class MenuScanner {

    private static final Comparator<MenuEntry> ORDEM =
            Comparator.comparingInt(MenuEntry::order).thenComparing(MenuEntry::label);

    /** No normalizado, independente de ter vindo de annotation ou de JSON. */
    private record NoDef(String id, String parent, String label, int order, List<NoItem> itens) {
    }

    /** Item normalizado. */
    private record NoItem(String label, int order, Method metodo) {
    }

    private MenuScanner() {
    }

    /** Descobre (annotations ou JSON) e monta a arvore de menus. */
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

        Map<String, Class<?>> indice = new LinkedHashMap<>();
        for (String pacote : pacotes) {
            for (Class<?> tipo : ClassScanner.scan(loader, pacote)) {
                indice.putIfAbsent(tipo.getName(), tipo);
            }
        }

        Map<String, NoDef> defs = config.menus().isBlank()
                ? dasAnnotations(indice)
                : doJson(config.menus(), indice, loader);

        return montarArvore(defs);
    }

    // ------------------------------------------------------------------ annotations

    private static Map<String, NoDef> dasAnnotations(Map<String, Class<?>> indice) {
        Map<String, NoDef> defs = new LinkedHashMap<>();

        for (Class<?> tipo : indice.values()) {
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
            if (defs.containsKey(id)) {
                throw new DuckTuiException("""
                        Id de menu duplicado: '%s'.
                        Defina um id unico em @Menu/@Submenu."""
                        .formatted(id));
            }

            String label = ehMenu
                    ? tipo.getAnnotation(Menu.class).label()
                    : tipo.getAnnotation(Submenu.class).label();
            int ordem = ehMenu
                    ? tipo.getAnnotation(Menu.class).order()
                    : tipo.getAnnotation(Submenu.class).order();

            if (label.isEmpty()) {
                label = tipo.getSimpleName();
            }

            String parent = null;
            if (ehMenu) {
                if (temAncestralAnotado(tipo)) {
                    throw new DuckTuiException("""
                            @Menu '%s' esta aninhado em outro menu.
                            @Menu e sempre o no pai: para menus aninhados, use @Submenu."""
                            .formatted(tipo.getName()));
                }
            } else {
                parent = resolverPai(tipo);
            }

            defs.put(id, new NoDef(id, parent, label, ordem, itensDaClasse(tipo)));
        }

        if (defs.isEmpty()) {
            throw new DuckTuiException("""
                    Nenhum @Menu encontrado.
                    Anote as classes de menu com @Menu ou @Submenu, ou use @DuckTui(menus = "menus.json").""");
        }

        return defs;
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

    // ------------------------------------------------------------------ json

    private static Map<String, NoDef> doJson(String arquivo, Map<String, Class<?>> indice, ClassLoader loader) {
        Map<String, NoDef> defs = new LinkedHashMap<>();

        for (JsonMenuLoader.Node no : JsonMenuLoader.ler(arquivo)) {
            if (defs.containsKey(no.id())) {
                throw new DuckTuiException("Id de menu duplicado no arquivo '%s': '%s'."
                        .formatted(arquivo, no.id()));
            }

            List<NoItem> itens = new ArrayList<>();

            if (!no.className().isBlank()) {
                itens.addAll(itensDaClasse(resolverClasse(no.className(), indice, loader, arquivo)));
            }

            for (JsonMenuLoader.Item item : no.items()) {
                Method metodo = resolverAcao(item.action(), indice, loader, arquivo);
                itens.add(new NoItem(item.label(), item.order(), metodo));
            }

            defs.put(no.id(), new NoDef(no.id(), no.parent(), no.label(), no.order(), itens));
        }

        if (defs.isEmpty()) {
            throw new DuckTuiException("O arquivo de menus '%s' nao declara nenhum menu.".formatted(arquivo));
        }

        return defs;
    }

    private static Class<?> resolverClasse(
            String nome, Map<String, Class<?>> indice, ClassLoader loader, String arquivo) {

        if (nome.contains(".")) {
            try {
                return Class.forName(nome, false, loader);
            } catch (ClassNotFoundException | LinkageError ignorada) {
                // cai na busca por nome simples abaixo
            }
        }

        List<Class<?>> achadas = indice.values().stream()
                .filter(tipo -> tipo.getSimpleName().equals(nome) || tipo.getName().equals(nome))
                .toList();

        if (achadas.size() == 1) {
            return achadas.get(0);
        }

        if (achadas.isEmpty()) {
            throw new DuckTuiException("""
                    Classe '%s' citada em '%s' nao foi encontrada.
                    Use o nome completo ou aponte @DuckTui(scan = "...") para o pacote dela."""
                    .formatted(nome, arquivo));
        }

        throw new DuckTuiException("""
                Classe '%s' citada em '%s' e ambigua: %s.
                Use o nome completo."""
                .formatted(nome, arquivo,
                        achadas.stream().map(Class::getName).collect(Collectors.joining(", "))));
    }

    private static Method resolverAcao(
            String acao, Map<String, Class<?>> indice, ClassLoader loader, String arquivo) {

        int separador = acao.indexOf('#');
        if (separador <= 0 || separador == acao.length() - 1) {
            throw new DuckTuiException("""
                    Acao invalida '%s' em '%s'.
                    Use o formato "Classe#metodo"."""
                    .formatted(acao, arquivo));
        }

        Class<?> alvo = resolverClasse(acao.substring(0, separador), indice, loader, arquivo);
        String nomeMetodo = acao.substring(separador + 1);

        for (Method metodo : alvo.getDeclaredMethods()) {
            if (metodo.getName().equals(nomeMetodo)) {
                validarAssinatura(metodo, acao);
                return metodo;
            }
        }

        throw new DuckTuiException("""
                Metodo '%s' nao encontrado em %s (citado em '%s')."""
                .formatted(nomeMetodo, alvo.getName(), arquivo));
    }

    // ------------------------------------------------------------------ itens

    private static List<NoItem> itensDaClasse(Class<?> tipo) {
        List<NoItem> itens = new ArrayList<>();
        for (Method metodo : tipo.getDeclaredMethods()) {
            if (metodo.isAnnotationPresent(Item.class)) {
                Item anotacao = metodo.getAnnotation(Item.class);
                String label = anotacao.label().isEmpty() ? metodo.getName() : anotacao.label();
                validarAssinatura(metodo, label);
                itens.add(new NoItem(label, anotacao.order(), metodo));
            }
        }
        return itens;
    }

    private static void validarAssinatura(Method metodo, String descricao) {
        if (!Modifier.isStatic(metodo.getModifiers())) {
            throw new DuckTuiException("""
                    Item '%s' precisa ser um metodo static.
                    Metodo encontrado: %s"""
                    .formatted(descricao, metodo));
        }
        if (metodo.getParameterCount() != 0) {
            throw new DuckTuiException("""
                    Item '%s' nao pode receber parametros.
                    Metodo encontrado: %s"""
                    .formatted(descricao, metodo));
        }
        metodo.setAccessible(true);
    }

    // ------------------------------------------------------------------ arvore

    private static List<MenuEntry> montarArvore(Map<String, NoDef> defs) {
        Map<String, List<String>> filhosDe = new HashMap<>();
        List<String> raizes = new ArrayList<>();

        for (NoDef def : defs.values()) {
            String pai = def.parent();

            if (pai == null || pai.isBlank()) {
                raizes.add(def.id());
                continue;
            }

            if (pai.equals(def.id())) {
                throw new DuckTuiException(
                        "O menu '%s' aponta para si mesmo em parent.".formatted(def.id()));
            }
            if (!defs.containsKey(pai)) {
                throw new DuckTuiException("""
                        O menu '%s' aponta para parent='%s', que nao existe.
                        Ids disponiveis: %s."""
                        .formatted(def.id(), pai, defs.keySet()));
            }

            filhosDe.computeIfAbsent(pai, chave -> new ArrayList<>()).add(def.id());
        }

        if (raizes.isEmpty()) {
            throw new DuckTuiException("""
                    Nenhum menu raiz: todos os menus declaram parent.
                    Pelo menos um precisa ficar sem parent (ser @Menu).""");
        }

        validarCiclos(defs);

        List<MenuEntry> arvore = new ArrayList<>(raizes.size());
        for (String raiz : raizes) {
            arvore.add(montar(raiz, 0, defs, filhosDe));
        }

        arvore.sort(ORDEM);
        return List.copyOf(arvore);
    }

    private static MenuEntry montar(
            String id, int profundidade, Map<String, NoDef> defs, Map<String, List<String>> filhosDe) {

        NoDef def = defs.get(id);
        List<MenuEntry> filhos = new ArrayList<>();

        for (String filho : filhosDe.getOrDefault(id, List.of())) {
            filhos.add(montar(filho, profundidade + 1, defs, filhosDe));
        }

        for (NoItem item : def.itens()) {
            filhos.add(new MenuEntry(
                    item.label(), profundidade + 1, MenuEntry.Kind.ITEM, item.order(), List.of(), item.metodo()));
        }

        filhos.sort(ORDEM);

        return new MenuEntry(
                def.label(),
                profundidade,
                profundidade == 0 ? MenuEntry.Kind.MENU : MenuEntry.Kind.SUBMENU,
                def.order(),
                filhos,
                null);
    }

    private static void validarCiclos(Map<String, NoDef> defs) {
        for (String inicio : defs.keySet()) {
            Set<String> vistos = new HashSet<>();
            String atual = inicio;

            while (atual != null && !atual.isBlank()) {
                if (!vistos.add(atual)) {
                    throw new DuckTuiException(
                            "Ciclo de menu detectado envolvendo '" + inicio + "'.");
                }
                NoDef def = defs.get(atual);
                atual = def == null ? null : def.parent();
            }
        }
    }
}
