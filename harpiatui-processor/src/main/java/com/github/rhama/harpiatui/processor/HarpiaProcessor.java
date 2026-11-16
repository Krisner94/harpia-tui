package com.github.rhama.harpiatui.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le {@code @HarpiaMain}, {@code @Menu}, {@code @Submenu} e {@code @Item} em
 * tempo de compilacao e, para cada classe {@code @HarpiaMain}, gera:
 *
 * <ul>
 *   <li>{@code <App>_HarpiaRegistry} — metadados da arvore + {@code switch} que
 *       chama os metodos {@code @Item} diretamente (sem {@code Method.invoke});</li>
 *   <li>{@code <App>_HarpiaLauncher} — o {@code main} da aplicacao, para voce
 *       nao precisar escrever metodo de inicializacao;</li>
 *   <li>{@code META-INF/services/...HarpiaRegistry} — registro para o
 *       {@code ServiceLoader} (usado por {@code HarpiaLauncher.launch(Class)}).</li>
 * </ul>
 *
 * <p>O processor nao depende da biblioteca em tempo de compilacao: ele trabalha
 * com os nomes qualificados das annotations e com {@link AnnotationMirror}. Isso
 * permite que o artefato seja uma dependencia transitiva da lib, sem ciclo.
 *
 * <p>Com isso o runtime nao varre classpath nem le annotations. Erros de
 * estrutura (id duplicado, parent inexistente, ciclo, metodo nao estatico)
 * viram erro de compilacao.
 */
@SupportedAnnotationTypes({
        "com.github.rhama.harpiatui.annotation.HarpiaMain",
        "com.github.rhama.harpiatui.annotation.Menu",
        "com.github.rhama.harpiatui.annotation.Submenu",
        "com.github.rhama.harpiatui.annotation.Item"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class HarpiaProcessor extends AbstractProcessor {

    private static final String PACOTE = "com.github.rhama.harpiatui.annotation.";
    private static final String HARPIA_MAIN = PACOTE + "HarpiaMain";
    private static final String MENU = PACOTE + "Menu";
    private static final String SUBMENU = PACOTE + "Submenu";
    private static final String ITEM = PACOTE + "Item";

    private static final String SPI = "com.github.rhama.harpiatui.spi.HarpiaRegistry";
    private static final String SUFIXO = "_HarpiaRegistry";

    private boolean gerado;

    private record MenuModel(TypeElement tipo, String id, String parent, String label, int order) {
    }

    private record ItemModel(ExecutableElement metodo, TypeElement dono, String label, int order) {
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (round.processingOver() || gerado) {
            return false;
        }

        TypeElement anotacaoMain = tipoDaAnotacao(HARPIA_MAIN);
        if (anotacaoMain == null) {
            return false;
        }

        List<TypeElement> apps = new ArrayList<>();
        for (Element elemento : round.getElementsAnnotatedWith(anotacaoMain)) {
            apps.add((TypeElement) elemento);
        }
        if (apps.isEmpty()) {
            return false;
        }

        gerado = true;

        List<ItemModel> itens = coletarItens(round);
        List<MenuModel> menus = coletarMenus(round);

        if (!validarMenus(menus)) {
            return true;
        }

        List<String> registros = new ArrayList<>();

        for (TypeElement app : apps) {
            if (!validarApp(app, menus, itens)) {
                continue;
            }
            try {
                registros.add(gerar(app, menus, itens));
                gerarLauncher(app);
            } catch (IOException e) {
                erro(app, "Falha ao gerar o registro HarpiaTUI: " + e.getMessage());
            }
        }

        if (!registros.isEmpty()) {
            escreverServiceFile(registros, apps.get(0));
        }

        return true;
    }

    // ------------------------------------------------------------------ coleta

    private List<ItemModel> coletarItens(RoundEnvironment round) {
        List<ItemModel> itens = new ArrayList<>();
        TypeElement anotacao = tipoDaAnotacao(ITEM);
        if (anotacao == null) {
            return itens;
        }

        for (Element elemento : round.getElementsAnnotatedWith(anotacao)) {
            ExecutableElement metodo = (ExecutableElement) elemento;
            String anotado = texto(metodo, ITEM, "label");
            String descricao = anotado.isEmpty() ? metodo.getSimpleName().toString() : anotado;

            if (!metodo.getModifiers().contains(Modifier.STATIC)) {
                erro(metodo, """
                        @Item '%s' precisa ser public static.
                        O registro gerado chama o metodo diretamente, sem reflexao."""
                        .formatted(descricao));
                continue;
            }
            if (!metodo.getModifiers().contains(Modifier.PUBLIC)) {
                erro(metodo, """
                        @Item '%s' precisa ser public.
                        O registro gerado chama o metodo diretamente, sem reflexao."""
                        .formatted(descricao));
                continue;
            }
            if (!metodo.getParameters().isEmpty()) {
                erro(metodo, "@Item '%s' nao pode receber parametros.".formatted(descricao));
                continue;
            }

            itens.add(new ItemModel(
                    metodo,
                    (TypeElement) metodo.getEnclosingElement(),
                    descricao,
                    inteiro(metodo, ITEM, "order")));
        }

        return itens;
    }

    private List<MenuModel> coletarMenus(RoundEnvironment round) {
        List<MenuModel> menus = new ArrayList<>();

        TypeElement anotacaoMenu = tipoDaAnotacao(MENU);
        if (anotacaoMenu != null) {
            for (Element elemento : round.getElementsAnnotatedWith(anotacaoMenu)) {
                TypeElement tipo = (TypeElement) elemento;

                if (temAncestralDeMenu(tipo)) {
                    erro(tipo, """
                            @Menu '%s' esta aninhado em outro menu.
                            @Menu e sempre o no pai: para menus aninhados, use @Submenu."""
                            .formatted(tipo.getQualifiedName()));
                    continue;
                }

                menus.add(new MenuModel(
                        tipo,
                        id(tipo),
                        null,
                        label(tipo),
                        inteiro(tipo, MENU, "order")));
            }
        }

        TypeElement anotacaoSubmenu = tipoDaAnotacao(SUBMENU);
        if (anotacaoSubmenu != null) {
            for (Element elemento : round.getElementsAnnotatedWith(anotacaoSubmenu)) {
                TypeElement tipo = (TypeElement) elemento;

                if (tem(tipo, MENU)) {
                    erro(tipo, "@Menu e @Submenu nao podem estar na mesma classe.");
                    continue;
                }

                String parent = texto(tipo, SUBMENU, "parent");
                if (parent.isEmpty()) {
                    parent = paiInferido(tipo);
                }

                if (parent == null) {
                    erro(tipo, """
                            @Submenu '%s' nao declara parent e nao esta aninhado em um menu anotado.
                            Declare o pai: @Submenu(parent = "id-do-menu-pai", ...)."""
                            .formatted(tipo.getQualifiedName()));
                    continue;
                }

                menus.add(new MenuModel(
                        tipo,
                        id(tipo),
                        parent,
                        label(tipo),
                        inteiro(tipo, SUBMENU, "order")));
            }
        }

        return menus;
    }

    private boolean temAncestralDeMenu(TypeElement tipo) {
        Element pai = tipo.getEnclosingElement();
        while (pai instanceof TypeElement paiTipo) {
            if (tem(paiTipo, MENU) || tem(paiTipo, SUBMENU)) {
                return true;
            }
            pai = paiTipo.getEnclosingElement();
        }
        return false;
    }

    private String paiInferido(TypeElement tipo) {
        Element pai = tipo.getEnclosingElement();
        while (pai instanceof TypeElement paiTipo) {
            if (tem(paiTipo, MENU)) {
                return id(paiTipo);
            }
            if (tem(paiTipo, SUBMENU)) {
                return id(paiTipo);
            }
            pai = paiTipo.getEnclosingElement();
        }
        return null;
    }

    // ------------------------------------------------------------------ validacao

    private boolean validarMenus(List<MenuModel> menus) {
        boolean ok = true;
        Map<String, MenuModel> porId = new LinkedHashMap<>();

        for (MenuModel menu : menus) {
            if (porId.putIfAbsent(menu.id(), menu) != null) {
                erro(menu.tipo(), "Id de menu duplicado: '%s'. Defina um id unico em @Menu/@Submenu."
                        .formatted(menu.id()));
                ok = false;
            }
        }

        for (MenuModel menu : menus) {
            String pai = menu.parent();
            if (pai == null || pai.isBlank()) {
                continue;
            }
            if (pai.equals(menu.id())) {
                erro(menu.tipo(), "O menu '%s' aponta para si mesmo em parent.".formatted(menu.id()));
                ok = false;
            } else if (!porId.containsKey(pai)) {
                erro(menu.tipo(), """
                        O menu '%s' aponta para parent='%s', que nao existe.
                        Ids declarados: %s"""
                        .formatted(menu.id(), pai, porId.keySet()));
                ok = false;
            }
        }

        boolean semCiclo = !temCiclo(porId);
        return ok && semCiclo;
    }

    private boolean validarApp(TypeElement app, List<MenuModel> menus, List<ItemModel> itens) {
        boolean ok = validarAcessibilidade(app, itens);
        boolean temJson = !texto(app, HARPIA_MAIN, "menus").isBlank();
        if (temJson) {
            return ok;
        }

        if (menus.isEmpty()) {
            erro(app, """
                    Nenhum @Menu encontrado.
                    Anote as classes de menu com @Menu/@Submenu ou use @HarpiaMain(menus = "menus.json").""");
            return false;
        }

        boolean temRaiz = menus.stream().anyMatch(m -> m.parent() == null || m.parent().isBlank());
        if (!temRaiz) {
            erro(app, """
                    Nenhum menu raiz: todos os menus declaram parent.
                    Pelo menos um precisa ficar sem parent (ser @Menu).""");
            return false;
        }

        return ok;
    }

    /**
     * O registro gerado fica no pacote da classe {@code @HarpiaMain} e chama os
     * metodos diretamente: ou a classe dona e public, ou esta no mesmo pacote.
     */
    private boolean validarAcessibilidade(TypeElement app, List<ItemModel> itens) {
        PackageElement pacoteApp = processingEnv.getElementUtils().getPackageOf(app);
        boolean ok = true;

        for (ItemModel item : itens) {
            PackageElement pacoteDono = processingEnv.getElementUtils().getPackageOf(item.dono());
            if (!item.dono().getModifiers().contains(Modifier.PUBLIC) && !pacoteDono.equals(pacoteApp)) {
                erro(item.metodo(), """
                        @Item '%s': a classe '%s' precisa ser public.
                        Ela esta em outro pacote e o registro gerado a chama diretamente (sem reflexao)."""
                        .formatted(item.label(), item.dono().getQualifiedName()));
                ok = false;
            }
        }

        return ok;
    }

    private boolean temCiclo(Map<String, MenuModel> porId) {
        for (String inicio : porId.keySet()) {
            Set<String> vistos = new HashSet<>();
            String atual = inicio;

            while (atual != null && !atual.isBlank()) {
                if (!vistos.add(atual)) {
                    erro(porId.get(inicio).tipo(),
                            "Ciclo de menu detectado envolvendo '" + inicio + "'.");
                    return true;
                }
                MenuModel modelo = porId.get(atual);
                atual = modelo == null ? null : modelo.parent();
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ geracao

    private String gerar(TypeElement app, List<MenuModel> menus, List<ItemModel> itens) throws IOException {
        PackageElement pacote = processingEnv.getElementUtils().getPackageOf(app);
        String pacoteNome = pacote.isUnnamed() ? "" : pacote.getQualifiedName().toString();
        String classe = app.getSimpleName() + SUFIXO;
        String fqn = pacoteNome.isEmpty() ? classe : pacoteNome + "." + classe;

        String titulo = texto(app, HARPIA_MAIN, "title");
        int altura = inteiro(app, HARPIA_MAIN, "height");
        String arquivoMenus = texto(app, HARPIA_MAIN, "menus");

        StringBuilder sb = new StringBuilder();

        if (!pacoteNome.isEmpty()) {
            sb.append("package ").append(pacoteNome).append(";\n\n");
        }

        sb.append("""
                import com.github.rhama.harpiatui.spi.HarpiaRegistry;
                import com.github.rhama.harpiatui.spi.HarpiaSettings;
                import com.github.rhama.harpiatui.spi.ItemDescriptor;
                import com.github.rhama.harpiatui.spi.MenuDescriptor;
                import java.util.List;

                /** Gerado por harpiatui-processor. Nao edite. */
                public final class %s implements HarpiaRegistry {

                    @Override
                    public String appClassName() {
                        return "%s";
                    }

                    @Override
                    public HarpiaSettings settings() {
                        return new HarpiaSettings("%s", %d, "%s");
                    }

                    @Override
                    public List<MenuDescriptor> menus() {
                """.formatted(
                classe,
                app.getQualifiedName(),
                escapar(titulo),
                altura,
                escapar(arquivoMenus)));

        if (menus.isEmpty()) {
            sb.append("        return List.of();\n");
        } else {
            sb.append("        return List.of(\n");
            for (int i = 0; i < menus.size(); i++) {
                MenuModel m = menus.get(i);
                sb.append("                new MenuDescriptor(\"%s\", %s, \"%s\", %d, \"%s\")%s\n"
                        .formatted(
                                escapar(m.id()),
                                m.parent() == null ? "null" : "\"" + escapar(m.parent()) + "\"",
                                escapar(m.label()),
                                m.order(),
                                m.tipo().getQualifiedName(),
                                i == menus.size() - 1 ? "" : ","));
            }
            sb.append("        );\n");
        }
        sb.append("    }\n\n");

        sb.append("""
                    @Override
                    public List<ItemDescriptor> items() {
                """);

        if (itens.isEmpty()) {
            sb.append("        return List.of();\n");
        } else {
            sb.append("        return List.of(\n");
            for (int i = 0; i < itens.size(); i++) {
                ItemModel item = itens.get(i);
                sb.append("                new ItemDescriptor(\"%s\", \"%s\", \"%s\", %d)%s\n"
                        .formatted(
                                item.dono().getQualifiedName(),
                                item.metodo().getSimpleName(),
                                escapar(item.label()),
                                item.order(),
                                i == itens.size() - 1 ? "" : ","));
            }
            sb.append("        );\n");
        }
        sb.append("    }\n\n");

        sb.append("""
                    @Override
                    public Object invoke(String actionId) {
                        return switch (actionId) {
                """);

        Map<String, ItemModel> porAcao = new LinkedHashMap<>();
        for (ItemModel item : itens) {
            porAcao.put(item.dono().getQualifiedName() + "#" + item.metodo().getSimpleName(), item);
        }

        for (Map.Entry<String, ItemModel> entrada : porAcao.entrySet()) {
            ItemModel item = entrada.getValue();
            boolean vazio = item.metodo().getReturnType().getKind() == TypeKind.VOID;

            sb.append("            case \"").append(escapar(entrada.getKey())).append("\" -> {\n");
            if (vazio) {
                sb.append("                ").append(item.dono().getQualifiedName()).append('.')
                        .append(item.metodo().getSimpleName()).append("();\n");
                sb.append("                yield null;\n");
            } else {
                sb.append("                yield ").append(item.dono().getQualifiedName()).append('.')
                        .append(item.metodo().getSimpleName()).append("();\n");
            }
            sb.append("            }\n");
        }

        sb.append("""
                            default -> null;
                        };
                    }
                }
                """);

        JavaFileObject arquivo = processingEnv.getFiler().createSourceFile(fqn, app);
        try (Writer writer = arquivo.openWriter()) {
            writer.write(sb.toString());
        }

        return fqn;
    }

    /**
     * Gera {@code <App>_HarpiaLauncher} com o metodo {@code main}.
     *
     * <p>Java nao permite que uma annotation adicione um metodo a classe
     * anotada, entao o ponto de entrada nasce numa classe companheira no mesmo
     * pacote. Ela instancia o registro direto — sem {@code ServiceLoader}.
     */
    private void gerarLauncher(TypeElement app) throws IOException {
        PackageElement pacote = processingEnv.getElementUtils().getPackageOf(app);
        String pacoteNome = pacote.isUnnamed() ? "" : pacote.getQualifiedName().toString();
        String classe = app.getSimpleName() + "_HarpiaLauncher";
        String fqn = pacoteNome.isEmpty() ? classe : pacoteNome + "." + classe;

        StringBuilder sb = new StringBuilder();
        if (!pacoteNome.isEmpty()) {
            sb.append("package ").append(pacoteNome).append(";\n\n");
        }

        sb.append("""
                import com.github.rhama.harpiatui.boot.HarpiaLauncher;

                /**
                 * Gerado por harpiatui-processor. Nao edite.
                 *
                 * <p>Ponto de entrada da aplicacao anotada com @HarpiaMain.
                 */
                public final class %s {

                    private %s() {
                    }

                    public static void main(String[] args) throws Exception {
                        HarpiaLauncher.launch(new %s_HarpiaRegistry());
                    }
                }
                """.formatted(classe, classe, app.getSimpleName()));

        JavaFileObject arquivo = processingEnv.getFiler().createSourceFile(fqn, app);
        try (Writer writer = arquivo.openWriter()) {
            writer.write(sb.toString());
        }
    }

    private void escreverServiceFile(List<String> registros, Element origem) {
        try (Writer writer = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + SPI, origem)
                .openWriter()) {
            for (String registro : registros) {
                writer.write(registro);
                writer.write("\n");
            }
        } catch (IOException e) {
            erro(origem, "Falha ao escrever META-INF/services: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ annotations

    private TypeElement tipoDaAnotacao(String fqn) {
        return processingEnv.getElementUtils().getTypeElement(fqn);
    }

    private static AnnotationMirror anotacao(Element elemento, String fqn) {
        for (AnnotationMirror mirror : elemento.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().asElement().toString().equals(fqn)) {
                return mirror;
            }
        }
        return null;
    }

    private static boolean tem(Element elemento, String fqn) {
        return anotacao(elemento, fqn) != null;
    }

    private Object valor(Element elemento, String fqn, String campo) {
        AnnotationMirror mirror = anotacao(elemento, fqn);
        if (mirror == null) {
            return null;
        }

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entrada
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            if (entrada.getKey().getSimpleName().contentEquals(campo)) {
                return entrada.getValue().getValue();
            }
        }
        return null;
    }

    private String texto(Element elemento, String fqn, String campo) {
        Object valor = valor(elemento, fqn, campo);
        return valor == null ? "" : valor.toString();
    }

    private int inteiro(Element elemento, String fqn, String campo) {
        Object valor = valor(elemento, fqn, campo);
        return valor instanceof Integer numero ? numero : 0;
    }

    // ------------------------------------------------------------------ util

    private String id(TypeElement tipo) {
        String anotado = tem(tipo, MENU) ? texto(tipo, MENU, "id") : texto(tipo, SUBMENU, "id");
        return anotado == null || anotado.isEmpty() ? tipo.getSimpleName().toString() : anotado;
    }

    private String label(TypeElement tipo) {
        String anotado = tem(tipo, MENU) ? texto(tipo, MENU, "label") : texto(tipo, SUBMENU, "label");
        return anotado == null || anotado.isEmpty() ? tipo.getSimpleName().toString() : anotado;
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void erro(Element elemento, String mensagem) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, mensagem, elemento);
    }
}
