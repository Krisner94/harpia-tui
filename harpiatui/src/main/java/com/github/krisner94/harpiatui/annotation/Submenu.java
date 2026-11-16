package com.github.krisner94.harpiatui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um submenu, que <b>sempre</b> pertence a um {@link Menu @Menu} ou a
 * outro {@link Submenu @Submenu}.
 *
 * <p>O vinculo e declarado em {@link #parent()}. Se a classe estiver aninhada
 * dentro de um menu anotado, o pai e inferido.
 *
 * <p>Retencao {@link RetentionPolicy#SOURCE}: lida pelo processor.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Submenu {

    /** Identificador unico deste submenu. Vazio = nome simples da classe. */
    String id() default "";

    /**
     * Id do menu pai ({@code @Menu} ou {@code @Submenu}).
     * Obrigatorio quando a classe nao esta aninhada em outro menu anotado.
     */
    String parent() default "";

    /** Texto exibido. Vazio = nome simples da classe. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
