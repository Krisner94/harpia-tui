package com.github.rhama.harpiatui.boot;

import com.github.rhama.harpiatui.spi.HarpiaRegistry;
import com.github.rhama.harpiatui.spi.ItemDescriptor;
import com.github.rhama.harpiatui.spi.MenuDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta a arvore de menus a partir do registro gerado em tempo de compilacao.
 *
 * <p>Nao ha varredura de classpath nem leitura de annotations: recebe listas
 * prontas de {@link MenuDescriptor} e {@link ItemDescriptor}. O mesmo construtor
 * serve o modo JSON, que apenas troca a origem dos nos.
 */
final class MenuTreeBuilder {

    private static final Comparator<MenuEntry> ORDEM =
            Comparator.comparingInt(MenuEntry::order).thenComparing(MenuEntry::label);

    private record NoDef(String id, String parent, String label, int order, List<NoItem> itens) {
    }

    private record NoItem(String label, int order, String actionId) {
    }

    private MenuTreeBuilder() {
    }

    /** Arvore declarada por {@code @Menu}/{@code @Submenu}. */
    static List<MenuEntry> dasAnnotations(HarpiaRegistry registry) {
        Map<String, NoDef> defs = new LinkedHashMap<>();

        for (MenuDescriptor menu : registry.menus()) {
            if (defs.containsKey(menu.id())) {
                throw new HarpiaException("Id de menu duplicado: '" + menu.id() + "'.");
            }
            defs.put(menu.id(), new NoDef(
                    menu.id(), menu.parent(), menu.label(), menu.order(),
                    itensDoDono(registry, menu.ownerClass())));
        }

        if (defs.isEmpty()) {
            throw new HarpiaException("""
                    Nenhum @Menu encontrado no registro gerado.
                    Anote as classes de menu com @Menu/@Submenu ou use @HarpiaMain(menus = "menus.json").""");
        }

        return montarArvore(defs);
    }

    /** Arvore declarada no arquivo JSON, usando o registro para resolver classes e acoes. */
    static List<MenuEntry> doJson(String arquivo, HarpiaRegistry registry) {
        Map<String, NoDef> defs = new LinkedHashMap<>();

        for (JsonMenuLoader.Node no : JsonMenuLoader.ler(arquivo)) {
            if (defs.containsKey(no.id())) {
                throw new HarpiaException("Id de menu duplicado no arquivo '%s': '%s'."
                        .formatted(arquivo, no.id()));
            }

            List<NoItem> itens = new ArrayList<>();

            if (!no.className().isBlank()) {
                itens.addAll(itensDoDono(registry, resolverDono(no.className(), registry, arquivo)));
            }

            for (JsonMenuLoader.Item item : no.items()) {
                itens.add(new NoItem(item.label(), item.order(), validarAcao(item.action(), registry, arquivo)));
            }

            defs.put(no.id(), new NoDef(no.id(), no.parent(), no.label(), no.order(), itens));
        }

        if (defs.isEmpty()) {
            throw new HarpiaException("O arquivo de menus '%s' nao declara nenhum menu.".formatted(arquivo));
        }

        return montarArvore(defs);
    }

    private static List<NoItem> itensDoDono(HarpiaRegistry registry, String dono) {
        List<NoItem> itens = new ArrayList<>();
        for (ItemDescriptor item : registry.items()) {
            if (item.ownerClass().equals(dono)) {
                itens.add(new NoItem(item.label(), item.order(), item.actionId()));
            }
        }
        return itens;
    }

    private static String resolverDono(String nome, HarpiaRegistry registry, String arquivo) {
        Set<String> donos = new LinkedHashSet<>();
        for (ItemDescriptor item : registry.items()) {
            donos.add(item.ownerClass());
        }

        List<String> achados = donos.stream()
                .filter(dono -> dono.equals(nome) || nomeSimples(dono).equals(nome))
                .toList();

        if (achados.size() == 1) {
            return achados.get(0);
        }

        if (achados.isEmpty()) {
            throw new HarpiaException("""
                    Classe '%s' citada em '%s' nao tem metodos @Item conhecidos.
                    Classes com @Item: %s"""
                    .formatted(nome, arquivo, donos.isEmpty() ? "(nenhuma)" : donos));
        }

        throw new HarpiaException("""
                Classe '%s' citada em '%s' e ambigua: %s.
                Use o nome completo."""
                .formatted(nome, arquivo, achados));
    }

    private static String validarAcao(String acao, HarpiaRegistry registry, String arquivo) {
        Set<String> acoes = new LinkedHashSet<>();
        for (ItemDescriptor item : registry.items()) {
            acoes.add(item.actionId());
        }

        if (acoes.contains(acao)) {
            return acao;
        }

        int separador = acao.indexOf('#');
        if (separador > 0 && separador < acao.length() - 1) {
            String classe = acao.substring(0, separador);
            String metodo = acao.substring(separador + 1);

            for (ItemDescriptor item : registry.items()) {
                String dono = item.ownerClass();
                if (dono.equals(classe) || nomeSimples(dono).equals(classe)) {
                    String candidato = item.actionId();
                    if (candidato.endsWith("#" + metodo) && acoes.contains(candidato)) {
                        return candidato;
                    }
                }
            }
        }

        throw new HarpiaException("""
                Acao '%s' citada em '%s' nao existe.
                Use "Classe#metodo" (metodo public static com @Item).
                Acoes geradas na compilacao: %s"""
                .formatted(acao, arquivo, acoes));
    }

    private static String nomeSimples(String fqn) {
        int ponto = fqn.lastIndexOf('.');
        return ponto < 0 ? fqn : fqn.substring(ponto + 1);
    }

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
                throw new HarpiaException(
                        "O menu '%s' aponta para si mesmo em parent.".formatted(def.id()));
            }
            if (!defs.containsKey(pai)) {
                throw new HarpiaException("""
                        O menu '%s' aponta para parent='%s', que nao existe.
                        Ids disponiveis: %s."""
                        .formatted(def.id(), pai, defs.keySet()));
            }

            filhosDe.computeIfAbsent(pai, chave -> new ArrayList<>()).add(def.id());
        }

        if (raizes.isEmpty()) {
            throw new HarpiaException("""
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
                    item.label(), profundidade + 1, MenuEntry.Kind.ITEM, item.order(), List.of(), item.actionId()));
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
                    throw new HarpiaException("Ciclo de menu detectado envolvendo '" + inicio + "'.");
                }
                NoDef def = defs.get(atual);
                atual = def == null ? null : def.parent();
            }
        }
    }
}
