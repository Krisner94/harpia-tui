package com.github.rhama.ducktui.boot;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Varre o classpath em busca de classes de um pacote (e subpacotes).
 *
 * <p>Sem dependencia externa: le diretorios ({@code target/classes}) e jars.
 * E o equivalente ao component scan do Spring, em ~80 linhas.
 */
final class ClassScanner {

    private ClassScanner() {
    }

    /** Devolve as classes carregadas de {@code basePackage}, sem inicializar estaticos. */
    static List<Class<?>> scan(ClassLoader loader, String basePackage) {
        String caminho = basePackage.replace('.', '/');
        Set<String> nomes = new TreeSet<>();

        try {
            Enumeration<URL> recursos = loader.getResources(caminho);
            while (recursos.hasMoreElements()) {
                URL url = recursos.nextElement();
                switch (url.getProtocol()) {
                    case "file" -> coletarDiretorio(Paths.get(url.toURI()), basePackage, nomes);
                    case "jar" -> coletarJar(url, caminho, nomes);
                    default -> {
                        // protocolos exoticos (jrt:, vfs:, ...) sao ignorados de proposito
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new DuckTuiException("Falha ao varrer o classpath em '" + basePackage + "'", e);
        }

        List<Class<?>> classes = new ArrayList<>(nomes.size());
        for (String nome : nomes) {
            try {
                classes.add(Class.forName(nome, false, loader));
            } catch (ClassNotFoundException | LinkageError ignorada) {
                // classe quebrada ou com dependencia opcional ausente: segue o jogo
            }
        }
        return classes;
    }

    private static void coletarDiretorio(Path raiz, String pacote, Set<String> nomes) throws IOException {
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            arquivos.filter(caminho -> caminho.toString().endsWith(".class"))
                    .forEach(caminho -> {
                        String relativo = raiz.relativize(caminho).toString().replace(File.separatorChar, '.');
                        String nome = pacote + '.' + relativo.substring(0, relativo.length() - ".class".length());
                        if (valido(nome)) {
                            nomes.add(nome);
                        }
                    });
        }
    }

    private static void coletarJar(URL url, String caminho, Set<String> nomes) throws IOException {
        JarURLConnection conexao = (JarURLConnection) url.openConnection();
        conexao.setUseCaches(false); // evita travar o jar aberto

        try (JarFile jar = conexao.getJarFile()) {
            Enumeration<JarEntry> entradas = jar.entries();
            while (entradas.hasMoreElements()) {
                String entrada = entradas.nextElement().getName();
                if (entrada.startsWith(caminho) && entrada.endsWith(".class")) {
                    String nome = entrada.substring(0, entrada.length() - ".class".length()).replace('/', '.');
                    if (valido(nome)) {
                        nomes.add(nome);
                    }
                }
            }
        }
    }

    private static boolean valido(String nome) {
        return !nome.endsWith("module-info") && !nome.endsWith("package-info");
    }
}
