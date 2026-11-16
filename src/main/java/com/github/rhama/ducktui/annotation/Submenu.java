package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um submenu, que <b>sempre</b> pertence a um {@link Menu @Menu} ou a
 * outro {@link Submenu @Submenu}.
 *
 * <p>Como as classes ficam soltas no classpath, o vinculo com o pai e declarado
 * em {@link #parent()}. Se a classe estiver aninhada dentro de um menu anotado,
 * o pai e inferido e {@code parent} pode ficar vazio.
 *
 * <pre>{@code
 * @Submenu(parent = "arquivo", label = "Recentes", order = 2)
 * public class RecentesMenu {
 *     @Item(label = "lago-norte.duck")
 *     public static String norte() { return "abrindo"; }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Submenu {

    /** Identificador unico deste submenu. Vazio = nome simples da classe. */
    String id() default "";

    /**
     * Id do menu pai ({@code @Menu} ou {@code @Submenu}).
     * Obrigatorio quando a classe nao esta aninhada em outro menu anotado.
     */
    String parent() default "";

    /** Texto exibido no submenu. Vazio = nome simples da classe. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
