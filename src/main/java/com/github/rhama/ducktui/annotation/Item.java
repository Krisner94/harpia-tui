package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara uma folha executavel do menu.
 *
 * <p>O metodo anotado precisa ser {@code static} e nao receber parametros.
 * Se devolver algo diferente de {@code void}, o valor e impresso acima da area
 * de status (via {@code println}), junto com o resultado da acao.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Item {

    /** Texto exibido. Vazio = usa o nome do metodo. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
