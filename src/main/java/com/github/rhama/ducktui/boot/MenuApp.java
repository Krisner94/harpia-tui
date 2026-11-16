package com.github.rhama.ducktui.boot;

import static dev.tamboui.toolkit.Toolkit.*;

import com.github.rhama.ducktui.annotation.DuckTui;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplicacao inline que desenha a arvore de menus montada pelo {@link MenuScanner}.
 *
 * <p>Ela e criada por {@link DuckTuiLauncher}; voce normalmente nao instancia esta
 * classe a mao.
 */
final class MenuApp extends InlineApp {

    private final DuckTui config;
    private final List<MenuEntry> plana = new ArrayList<>();
    private int selecionado = 0;

    MenuApp(DuckTui config, List<MenuEntry> arvore) {
        this.config = config;
        for (MenuEntry raiz : arvore) {
            raiz.flattenInto(plana);
        }
    }

    @Override
    protected int height() {
        if (config.height() > 0) {
            return config.height();
        }
        // cabecalho + uma linha por entrada + dica de teclas
        return plana.size() + 2;
    }

    @Override
    protected Element render() {
        List<Element> linhas = new ArrayList<>(plana.size() + 2);

        linhas.add(text(config.title()).cyan().bold());
        for (int i = 0; i < plana.size(); i++) {
            linhas.add(linha(plana.get(i), i == selecionado));
        }
        linhas.add(text("[setas] navegar   [enter] executar   [q] sair").dim());

        return column(linhas.toArray(new Element[0]))
                .id("duck-menu")
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
            Object resultado = entrada.invoke();
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
