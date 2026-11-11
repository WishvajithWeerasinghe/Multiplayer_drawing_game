package com.example.draw.web;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Div;
import java.util.function.Consumer;

@Tag("div")
public class DrawingTools extends Div {

    private Consumer<ToolSettings> toolListener = settings -> {};

    public DrawingTools() {
        getStyle().set("display", "flex");
        getStyle().set("align-items", "end");
        getStyle().set("gap", "16px");
        getStyle().set("flex", "1");
        getStyle().set("min-width", "0");
        getElement().executeJs("""
                const host = this;
                host.innerHTML = `
                  <label style="display:grid;gap:6px;color:#475569;font:500 14px system-ui">
                    Color
                    <input type="color" value="#20242d" aria-label="Brush color"
                      style="width:52px;height:36px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer">
                  </label>
                  <label style="display:grid;gap:6px;color:#475569;font:500 14px system-ui">
                    Brush size
                    <input type="range" min="1" max="30" value="4" aria-label="Brush size"
                      style="width:150px;accent-color:#3456d1">
                  </label>
                  <button type="button" aria-pressed="false"
                    style="height:36px;padding:0 14px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;color:#334155;cursor:pointer">
                    Eraser
                  </button>`;
                const color = host.querySelector('input[type=color]');
                const size = host.querySelector('input[type=range]');
                const eraser = host.querySelector('button');
                const notify = () => host.$server.toolChanged(color.value, Number(size.value), eraser.getAttribute('aria-pressed') === 'true');
                color.addEventListener('input', notify);
                size.addEventListener('input', notify);
                eraser.addEventListener('click', () => {
                  const enabled = eraser.getAttribute('aria-pressed') !== 'true';
                  eraser.setAttribute('aria-pressed', String(enabled));
                  eraser.style.background = enabled ? '#3456d1' : '#fff';
                  eraser.style.color = enabled ? '#fff' : '#334155';
                  eraser.style.borderColor = enabled ? '#3456d1' : '#cbd5e1';
                  notify();
                });
                notify();
                """);
    }

    public void setToolListener(Consumer<ToolSettings> listener) {
        toolListener = listener;
    }

    @ClientCallable
    public void toolChanged(String color, int size, boolean eraser) {
        toolListener.accept(new ToolSettings(color, size, eraser));
    }

    public record ToolSettings(String color, int size, boolean eraser) {}
}
