package com.github.rhama.ducktui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.InlineApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

import java.time.Duration;

/**
 * DuckTUI - ponto de partida de uma TUI "inline" com TamboUI.
 *
 * <p>Modo inline = NAO ocupa a tela toda. O terminal continua com o seu
 * historico; apenas as ultimas linhas (a area de status) sao reservadas.
 * Texto enviado com {@code println()} rola para cima, acima do status.
 *
 * <p>Este exemplo simula uma sequencia de tarefas para demonstrar os tres
 * pilares do modo inline:
 * <ol>
 *   <li>viewport fixo no rodape ({@link #height()})</li>
 *   <li>log rolando acima ({@code println(...)})</li>
 *   <li>ticks periodicos + tratamento de teclado ({@code onKeyEvent})</li>
 * </ol>
 */
public class DuckTuiApp extends InlineApp {

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

    /** Quantas linhas reservar na parte de baixo do terminal. */
    @Override
    protected int height() {
        return 3;
    }

    /** Chamado a cada frame: descreve o estado atual da UI. */
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

    /** Eventos de teclado dos elementos. Devolver HANDLED interrompe a propagacao. */
    private EventResult aoTeclar(KeyEvent evento) {
        if (evento.isChar(' ') && !concluido) {
            pausado = !pausado;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    @Override
    protected void onStart() {
        // O agendador roda fora da render thread: sempre voltar com runOnRenderThread
        runner().scheduleRepeating(
            () -> runner().runOnRenderThread(this::tick),
            Duration.ofMillis(80)
        );
    }

    /** Avanca o progresso simulado. Roda na render thread. */
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
        new DuckTuiApp().run();
    }
}
