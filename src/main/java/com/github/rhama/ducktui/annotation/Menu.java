package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declara um menu de nivel raiz.
 *
 * <p>Basta anotar qualquer classe do pacote varrido e ela entra no menu
 * automaticamente. Um {@code @Menu} e <b>sempre</b> um no pai: nao tem
 * {@code parent} e nao pode estar aninhado em outro menu.
 *
 * <pre>{@code
 * @Menu(id = "arquivo", label = "Arquivo", order = 1)
 * public class ArquivoMenu {
 *     @Item(label = "Novo")
 *     public static String novo() { return "criado"; }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Menu {

    /** Identificador unico, usado por {@link Submenu#parent()}. Vazio = nome simples da classe. */
    String id() default "";

    /** Texto exibido no menu. Vazio = nome simples da classe. */
    String label() default "";

    /** Ordem entre irmaos (menor primeiro). Empate desempata pelo label. */
    int order() default 0;
}
