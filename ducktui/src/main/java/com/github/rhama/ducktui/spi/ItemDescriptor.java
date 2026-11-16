package com.github.rhama.ducktui.spi;

/**
 * Metadados de uma folha executavel.
 *
 * @param ownerClass classe que declara o metodo
 * @param methodName nome do metodo {@code public static}
 * @param label      texto exibido
 * @param order      ordem entre irmaos
 */
public record ItemDescriptor(String ownerClass, String methodName, String label, int order) {

    /** Id usado em {@link DuckTuiRegistry#invoke(String)} e no JSON. */
    public String actionId() {
        return ownerClass + "#" + methodName;
    }
}
