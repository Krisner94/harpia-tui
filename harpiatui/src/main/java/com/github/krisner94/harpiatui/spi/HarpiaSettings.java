package com.github.krisner94.harpiatui.spi;

/**
 * Configuracao da aplicacao, gerada a partir de {@code @HarpiaMain}.
 *
 * @param title     titulo exibido no cabecalho
 * @param height    altura fixa da area de status (0 = automatico)
 * @param menusFile arquivo JSON de menus (vazio = usar annotations)
 */
public record HarpiaSettings(String title, int height, String menusFile) {
}
