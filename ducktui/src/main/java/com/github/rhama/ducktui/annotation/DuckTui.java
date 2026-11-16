package com.github.rhama.ducktui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca a classe que inicializa uma aplicacao DuckTUI.
 *
 * <p>A annotation tem retencao {@link RetentionPolicy#SOURCE}: ela existe apenas
 * em tempo de compilacao. O {@code ducktui-processor} le esta classe, gera um
 * registro com a arvore de menus e o runtime nunca precisa reler a annotation.
 *
 * <pre>{@code
 * @DuckTui(title = "Minha App")
 * public class MinhaApp {
 *     public static void main(String[] args) throws Exception {
 *         DuckTuiLauncher.launch(MinhaApp.class);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DuckTui {

    /** Titulo exibido no cabecalho da area de status. */
    String title() default "DuckTUI";

    /**
     * Arquivo JSON que descreve a arvore de menus.
     *
     * <p>Vazio (padrao) = usa as annotations {@link Menu @Menu}/{@link Submenu @Submenu}.
     * Preenchido = o JSON manda na estrutura; as annotations ficam apenas como
     * fonte dos metodos {@link Item @Item}.
     *
     * <p>O caminho e resolvido em runtime: primeiro no classpath (ex.:
     * {@code menus.json} em {@code src/main/resources}) e depois no disco.
     */
    String menus() default "";

    /**
     * Altura fixa da area de status, em linhas.
     * Use {@code 0} (padrao) para calcular automaticamente pelo numero de entradas.
     */
    int height() default 0;
}
