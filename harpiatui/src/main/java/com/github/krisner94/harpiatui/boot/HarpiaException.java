package com.github.krisner94.harpiatui.boot;

/** Erro de configuracao do HarpiaTUI (annotation ausente ou hierarquia de menu invalida). */
public class HarpiaException extends IllegalStateException {

    public HarpiaException(String message) {
        super(message);
    }

    public HarpiaException(String message, Throwable cause) {
        super(message, cause);
    }
}
