package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um menu de nivel raiz.
 *
 * <p>Um {@code @Menu} e <b>sempre</b> um no pai: deve ficar diretamente dentro da
 * classe anotada com {@link DuckTui @DuckTui}, nunca dentro de outro menu.
 * Filhos sao {@link Submenu @Submenu} (outros menus) ou {@link Item @Item}
 * (folhas executaveis). A regra e validada ao iniciar; se for violada, a
 * aplicacao aborta com {@link com.github.rhama.ducktui.boot.DuckTuiException}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Menu {

    /** Texto exibido no menu. */
    String label();

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
