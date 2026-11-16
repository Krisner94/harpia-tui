package com.github.rhama.ducktui.spi;

/**
 * Metadados de um menu ou submenu, gerados em tempo de compilacao.
 *
 * @param id         identificador unico
 * @param parent     id do pai, ou {@code null} para menu raiz
 * @param label      texto exibido
 * @param order      ordem entre irmaos
 * @param ownerClass classe que declara os {@code @Item} deste menu
 */
public record MenuDescriptor(String id, String parent, String label, int order, String ownerClass) {
}
