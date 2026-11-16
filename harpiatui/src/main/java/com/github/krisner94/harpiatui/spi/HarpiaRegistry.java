package com.github.krisner94.harpiatui.spi;

import java.util.List;

/**
 * Registro gerado em tempo de compilacao pelo {@code harpiatui-processor}.
 *
 * <p>E a ponte entre build e runtime: traz a arvore de menus e as acoes ja
 * resolvidas, sem varredura de classpath e sem reflexao sobre annotations. A
 * execucao de um {@link ItemDescriptor} acontece em {@link #invoke(String)},
 * que e um {@code switch} gerado chamando os metodos diretamente.
 *
 * <p>Implementacoes sao descobertas via {@link java.util.ServiceLoader}
 * ({@code META-INF/services}).
 */
public interface HarpiaRegistry {

    /** Nome completo da classe anotada com {@code @HarpiaMain}. */
    String appClassName();

    /** Configuracao lida de {@code @HarpiaMain}. */
    HarpiaSettings settings();

    /** Menus declarados com {@code @Menu} / {@code @Submenu}, nao ordenados. */
    List<MenuDescriptor> menus();

    /** Todos os metodos {@code @Item} encontrados na compilacao. */
    List<ItemDescriptor> items();

    /**
     * Executa uma acao sem reflexao.
     *
     * @param actionId id no formato {@code classe#metodo}
     * @return o valor devolvido pelo metodo (ou {@code null} se void)
     */
    Object invoke(String actionId);
}
