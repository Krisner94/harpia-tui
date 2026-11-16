package com.github.krisner94.harpiatui.boot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Le o arquivo JSON que descreve a arvore de menus.
 *
 * <pre>{@code
 * {
 *   "menus": [
 *     { "id": "arquivo", "label": "Arquivo", "order": 1, "class": "ArquivoMenu" },
 *     { "id": "recentes", "parent": "arquivo", "label": "Recente", "order": 3,
 *       "class": "RecentesMenu" },
 *     { "id": "ferramentas", "label": "Ferramentas", "order": 2,
 *       "items": [
 *         { "label": "Recarregar", "action": "HarpiaJsonApp#recarregar" }
 *       ] }
 *   ]
 * }
 * }</pre>
 *
 * <p>O array na raiz tambem e aceito. O {@code class} aceita o nome simples
 * (resolvido nos pacotes varridos) ou o nome completo.
 */
final class JsonMenuLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonMenuLoader() {
    }

    /** Um no declarado no JSON. */
    record Node(String id, String parent, String label, int order, String className, List<Item> items) {
    }

    /** Item declarado no JSON, ligado a um metodo {@code Classe#metodo}. */
    record Item(String label, String action, int order) {
    }

    /** Le e valida nome de arquivo e formato, sem resolver classes ainda. */
    static List<Node> ler(String caminho) {
        JsonNode raiz = parse(caminho);
        JsonNode lista = raiz.isArray() ? raiz : raiz.path("menus");

        if (!lista.isArray()) {
            throw new HarpiaException("""
                    Formato invalido em '%s'.
                    Esperado {"menus": [ ... ]} ou um array [ ... ] no topo."""
                    .formatted(caminho));
        }

        List<Node> nos = new ArrayList<>();
        int indice = 0;
        for (JsonNode no : lista) {
            nos.add(lerNo(no, indice++, caminho));
        }
        return nos;
    }

    private static Node lerNo(JsonNode no, int indice, String caminho) {
        String id = texto(no, "id");
        if (id.isBlank()) {
            throw new HarpiaException(
                    "Menu #%d em '%s' sem 'id'. Todo no precisa de um id unico."
                            .formatted(indice, caminho));
        }

        String label = texto(no, "label");
        String className = texto(no, "class");
        int order = no.path("order").asInt(0);

        List<Item> items = new ArrayList<>();
        JsonNode itens = no.path("items");
        if (itens.isArray()) {
            for (JsonNode item : itens) {
                String acao = texto(item, "action");
                String rotulo = texto(item, "label");
                if (rotulo.isBlank()) {
                    rotulo = acao;
                }
                if (acao.isBlank()) {
                    throw new HarpiaException(
                            "Item '%s' do menu '%s' sem 'action'. Use \"action\": \"Classe#metodo\"."
                                    .formatted(rotulo, id));
                }
                items.add(new Item(rotulo, acao, item.path("order").asInt(0)));
            }
        }

        return new Node(
                id,
                texto(no, "parent"),
                label.isBlank() ? id : label,
                order,
                className,
                items);
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asText("").trim();
    }

    private static JsonNode parse(String caminho) {
        try {
            return MAPPER.readTree(bytes(caminho));
        } catch (JsonProcessingException e) {
            throw new HarpiaException("""
                    JSON invalido em '%s': %s (linha %d, coluna %d)"""
                    .formatted(caminho, e.getOriginalMessage(),
                            e.getLocation().getLineNr(), e.getLocation().getColumnNr()), e);
        } catch (IOException e) {
            throw new HarpiaException("Falha ao ler '" + caminho + "': " + e.getMessage(), e);
        }
    }

    private static byte[] bytes(String caminho) throws IOException {
        URL classpath = JsonMenuLoader.class.getClassLoader().getResource(caminho);
        if (classpath != null) {
            try (InputStream in = classpath.openStream()) {
                return in.readAllBytes();
            }
        }

        Path disco = Paths.get(caminho);
        if (Files.isReadable(disco)) {
            return Files.readAllBytes(disco);
        }

        throw new HarpiaException("""
                Arquivo de menus nao encontrado: '%s'
                Procurei no classpath e no disco."""
                .formatted(caminho));
    }
}
