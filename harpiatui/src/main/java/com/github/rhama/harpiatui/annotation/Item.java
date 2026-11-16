package com.github.rhama.harpiatui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara uma folha executavel do menu.
 *
 * <p>O metodo precisa ser <b>{@code public static}</b> e nao receber parametros.
 * Isso e verificado em tempo de compilacao: o processor gera uma chamada direta
 * ao metodo, sem {@code Method.invoke}.
 *
 * <p>Se devolver algo diferente de {@code void}, o valor e impresso acima da
 * area de status.
 *
 * <p>Retencao {@link RetentionPolicy#SOURCE}: lida pelo processor.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Item {

    /** Texto exibido. Vazio = nome do metodo. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
