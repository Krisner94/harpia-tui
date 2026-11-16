package com.github.rhama.harpiatui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um menu de nivel raiz.
 *
 * <p>Um {@code @Menu} e <b>sempre</b> um no pai: nao tem {@code parent} e nao
 * pode estar aninhado em outro menu. Validado em tempo de compilacao.
 *
 * <p>Retencao {@link RetentionPolicy#SOURCE}: lida pelo processor, nao existe
 * em runtime.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Menu {

    /** Identificador unico, usado por {@link Submenu#parent()}. Vazio = nome simples da classe. */
    String id() default "";

    /** Texto exibido. Vazio = nome simples da classe. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
