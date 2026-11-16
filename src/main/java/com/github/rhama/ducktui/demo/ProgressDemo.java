package com.github.rhama.ducktui.demo;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import java.time.Duration;

/**
 * Exemplo de TUI inline "na unha": barra de progresso + log rolando acima.
 *
 * <p>Serve de referencia para quem quiser usar {@link InlineApp} diretamente,
 * sem o esquema de annotations do {@code boot}.
 */
public class ProgressDemo extends InlineApp {

    private static final String[] TAREFAS = {
        "acordar as patas",
        "aquecer o lago",
        "ensinar o patinho a nadar",
        "servir o pao"
    };

    private int tarefa = 0;
    private double progresso = 0.0;
    private boolean pausado = false;
    private boolean concluido = false;

    @Override
    protected int height() {
        return 3;
    }

    @Override
    protected Element render() {
        if (concluido) {
            return column(
                text("DuckTUI | tudo pronto").green().bold(),
                text("[q] sair").dim()
            );
        }

        String status = pausado ? "pausado" : TAREFAS[tarefa];

        return column(
            text("DuckTUI | " + status).cyan().bold(),
            gauge(progresso)
                .label(String.format("%3.0f%%", progresso * 100))
                .gaugeColor(Color.GREEN),
            text("[espaco] pausar  [q] sair").dim()
        )
        .id("ducktui-root")
        .focusable()
        .onKeyEvent(this::aoTeclar);
    }

    private EventResult aoTeclar(KeyEvent evento) {
        if (evento.isChar(' ') && !concluido) {
            pausado = !pausado;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    @Override
    protected void onStart() {
        runner().scheduleRepeating(
            () -> runner().runOnRenderThread(this::tick),
            Duration.ofMillis(80)
        );
    }

    private void tick() {
        if (pausado || concluido) {
            return;
        }

        progresso += 0.02;
        if (progresso < 1.0) {
            return;
        }

        println(text("ok  " + TAREFAS[tarefa]).green());
        tarefa++;
        progresso = 0.0;

        if (tarefa >= TAREFAS.length) {
            concluido = true;
            println(text("DuckTUI concluido. quack.").bold());
        }
    }

    public static void main(String[] args) throws Exception {
        new ProgressDemo().run();
    }
}
