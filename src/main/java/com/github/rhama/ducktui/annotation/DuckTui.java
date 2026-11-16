package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca a classe que inicializa uma aplicacao DuckTUI.
 *
 * <p>Funciona como o {@code @SpringBootApplication}: a classe anotada nao precisa
 * estender nada. O metodo {@code main} apenas delega para
 * {@code DuckTuiLauncher.launch(MinhaApp.class)}, que le esta annotation,
 * varre as {@link Menu @Menu} e sobe a aplicacao.
 *
 * <pre>{@code
 * @DuckTui(title = "DuckTUI")
 * public class MinhaApp {
 *     public static void main(String[] args) throws Exception {
 *         DuckTuiLauncher.launch(MinhaApp.class);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DuckTui {

    /** Titulo exibido no cabecalho da area de status. */
    String title() default "DuckTUI";

    /**
     * Altura fixa da area de status, em linhas.
     * Use {@code 0} (padrao) para calcular automaticamente pelo numero de entradas.
     */
    int height() default 0;
}
