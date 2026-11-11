package com.example.draw.web;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Div;
import java.util.function.Consumer;

@Tag("div")
public class DrawingTools extends Div {

    private Consumer<ToolSettings> toolListener = settings -> {};
    private Runnable clearListener = () -> {};

    public DrawingTools() {
        addClassName("whiteboard-toolbar");
        getStyle().set("display", "block");
        getStyle().set("width", "84px");
        getStyle().set("flex", "0 0 84px");
        getStyle().set("align-self", "stretch");
        getStyle().set("background", "#fff");
        getStyle().set("border", "1px solid #e6eaf0");
        getStyle().set("border-radius", "18px");
        getStyle().set("padding", "12px 8px");
        getStyle().set("box-sizing", "border-box");
        getStyle().set("box-shadow", "0 8px 26px rgba(30, 41, 59, .06)");
        getElement().executeJs("""
                const host = this;
                host.innerHTML = `
                  <div style="display:grid;justify-items:center;gap:12px;font:500 11px system-ui;color:#64748b">
                    <button type="button" data-tool="pen" aria-pressed="true" title="Pen"
                      style="width:48px;height:48px;border:0;border-radius:14px;background:#eef2ff;color:#4f46e5;cursor:pointer">
                      <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m15 5 4 4M4 20l4-.8L19 8a2.1 2.1 0 0 0-3-3L5 16l-1 4Z"/></svg>
                    </button>
                    <span>Pen</span>
                    <label style="display:grid;justify-items:center;gap:7px;margin-top:3px">
                      <span>Color</span>
                      <input type="color" value="#20242d" aria-label="Brush color"
                        style="width:38px;height:38px;padding:3px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;cursor:pointer">
                    </label>
                    <label style="display:grid;justify-items:center;gap:7px;width:100%;margin-top:5px">
                      <span>Size <b data-size style="color:#334155">4</b></span>
                      <input type="range" min="1" max="30" value="4" aria-label="Brush size"
                        style="width:60px;writing-mode:vertical-lr;direction:rtl;accent-color:#4f46e5;height:116px;cursor:pointer">
                    </label>
                    <button type="button" data-tool="eraser" aria-pressed="false" title="Eraser"
                      style="width:48px;height:48px;margin-top:7px;border:1px solid #e6eaf0;border-radius:14px;background:#fff;color:#475569;cursor:pointer">
                      <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m7 21-4-4a2 2 0 0 1 0-3l9-9a2 2 0 0 1 3 0l6 6a2 2 0 0 1 0 3l-7 7H7Z"/><path d="m5 12 7 7"/></svg>
                    </button>
                    <span>Eraser</span>
                    <button type="button" data-clear title="Clear board"
                      style="width:48px;height:48px;margin-top:7px;border:1px solid #fee2e2;border-radius:14px;background:#fff7f7;color:#dc2626;cursor:pointer">
                      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18M8 6V4h8v2m3 0-1 14H6L5 6m5 4v6m4-6v6"/></svg>
                    </button>
                    <span>Clear</span>
                  </div>`;
                const color = host.querySelector('input[type=color]');
                const size = host.querySelector('input[type=range]');
                const sizeText = host.querySelector('[data-size]');
                const pen = host.querySelector('[data-tool=pen]');
                const eraser = host.querySelector('[data-tool=eraser]');
                let eraserEnabled = false;
                const setActive = (button, active) => {
                  button.setAttribute('aria-pressed', String(active));
                  button.style.background = active ? '#eef2ff' : '#fff';
                  button.style.color = active ? '#4f46e5' : '#475569';
                  button.style.border = active ? '0' : '1px solid #e6eaf0';
                };
                const notify = () => {
                  sizeText.textContent = size.value;
                  host.$server.toolChanged(color.value, Number(size.value), eraserEnabled);
                };
                color.addEventListener('input', notify);
                size.addEventListener('input', notify);
                pen.addEventListener('click', () => { eraserEnabled = false; setActive(pen, true); setActive(eraser, false); notify(); });
                eraser.addEventListener('click', () => { eraserEnabled = true; setActive(pen, false); setActive(eraser, true); notify(); });
                host.querySelector('[data-clear]').addEventListener('click', () => host.$server.clearRequested());
                notify();
                """);
    }

    public void setToolListener(Consumer<ToolSettings> listener) {
        toolListener = listener;
    }

    public void setClearListener(Runnable listener) {
        clearListener = listener;
    }

    @ClientCallable
    public void toolChanged(String color, int size, boolean eraser) {
        toolListener.accept(new ToolSettings(color, size, eraser));
    }

    @ClientCallable
    public void clearRequested() {
        clearListener.run();
    }

    public record ToolSettings(String color, int size, boolean eraser) {}
}
