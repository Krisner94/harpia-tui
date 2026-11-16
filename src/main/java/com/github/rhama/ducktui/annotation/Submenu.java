package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um submenu, que <b>sempre</b> fica dentro de um {@link Menu @Menu}
 * ou de outro {@link Submenu @Submenu}.
 *
 * <p>Essa restricao nao existe na linguagem Java (annotation nao sabe onde esta
 * aninhada), entao ela e verificada pelo {@code MenuScanner} no start da
 * aplicacao -- e tambem pode ser checada em tempo de compilacao por um
 * annotation processor, se um dia for necessario.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Submenu {

    /** Texto exibido no submenu. */
    String label();

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
