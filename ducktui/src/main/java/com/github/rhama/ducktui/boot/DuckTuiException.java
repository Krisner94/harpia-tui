package com.github.rhama.ducktui.boot;

/** Erro de configuracao do DuckTUI (annotation ausente ou hierarquia de menu invalida). */
public class DuckTuiException extends IllegalStateException {

    public DuckTuiException(String message) {
        super(message);
    }

    public DuckTuiException(String message, Throwable cause) {
        super(message, cause);
    }
}
