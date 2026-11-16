package com.github.krisner94.harpiatui.boot;

import static dev.tamboui.toolkit.Toolkit.*;

import com.github.krisner94.harpiatui.spi.HarpiaRegistry;
import com.github.krisner94.harpiatui.spi.HarpiaSettings;

import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicacao inline que desenha a arvore de menus.
 *
 * <p>Criada pelo {@link HarpiaLauncher}. A execucao de um item e delegada ao
 * {@link HarpiaRegistry} gerado na compilacao.
 */
final class MenuApp extends InlineApp {

    private final HarpiaSettings settings;
    private final HarpiaRegistry registry;
    private final List<MenuEntry> plana = new ArrayList<>();
    private int selecionado = 0;

    MenuApp(HarpiaSettings settings, HarpiaRegistry registry, List<MenuEntry> arvore) {
        this.settings = settings;
        this.registry = registry;
        for (MenuEntry raiz : arvore) {
            raiz.flattenInto(plana);
        }
    }

    @Override
    protected int height() {
        if (settings.height() > 0) {
            return settings.height();
        }
        // cabecalho + uma linha por entrada + dica de teclas
        return plana.size() + 2;
    }

    @Override
    protected Element render() {
        List<Element> linhas = new ArrayList<>(plana.size() + 2);

        linhas.add(text(settings.title()).cyan().bold());
        for (int i = 0; i < plana.size(); i++) {
            linhas.add(linha(plana.get(i), i == selecionado));
        }
        linhas.add(text("[setas] navegar   [enter] executar   [q] sair").dim());

        return column(linhas.toArray(new Element[0]))
                .id("harpia-menu")
                .focusable()
                .onKeyEvent(this::aoTeclar);
    }

    private Element linha(MenuEntry entrada, boolean focado) {
        String marcador = switch (entrada.kind()) {
            case MENU -> "\u25be ";    // ▾
            case SUBMENU -> "\u25be "; // ▾ (sempre expandido)
            case ITEM -> "\u2022 ";    // •
        };

        String prefixo = focado ? "> " : "  ";
        TextElement texto = text(prefixo + "  ".repeat(entrada.depth()) + marcador + entrada.label());

        if (focado) {
            return texto.cyan().bold();
        }
        return entrada.isItem() ? texto : texto.bold();
    }

    private EventResult aoTeclar(KeyEvent evento) {
        if (evento.isUp()) {
            mover(-1);
            return EventResult.HANDLED;
        }
        if (evento.isDown()) {
            mover(1);
            return EventResult.HANDLED;
        }
        if (evento.isConfirm()) {
            executar();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void mover(int passo) {
        if (plana.isEmpty()) {
            return;
        }
        selecionado = Math.floorMod(selecionado + passo, plana.size());
    }

    private void executar() {
        if (plana.isEmpty()) {
            return;
        }

        MenuEntry entrada = plana.get(selecionado);
        if (!entrada.isItem()) {
            println(text("  " + entrada.label() + " e um menu - navegue com as setas").dim());
            return;
        }

        try {
            Object resultado = registry.invoke(entrada.actionId());
            if (resultado == null) {
                println(text("ok  " + entrada.label()).green());
            } else {
                println(text("ok  " + resultado).green());
            }
        } catch (RuntimeException e) {
            println(text("erro  " + e.getMessage()).red());
        }
    }
}
