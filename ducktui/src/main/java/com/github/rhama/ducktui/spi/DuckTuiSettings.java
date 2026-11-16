package com.github.rhama.ducktui.spi;

/**
 * Configuracao da aplicacao, gerada a partir de {@code @DuckTui}.
 *
 * @param title     titulo exibido no cabecalho
 * @param height    altura fixa da area de status (0 = automatico)
 * @param menusFile arquivo JSON de menus (vazio = usar annotations)
 */
public record DuckTuiSettings(String title, int height, String menusFile) {
}
