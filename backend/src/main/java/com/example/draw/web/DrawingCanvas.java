package com.example.draw.web;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.html.Div;
import java.util.function.IntConsumer;

@Tag("div")
public class DrawingCanvas extends Div {

    private static final String CANVAS_SCRIPT = """
            const host = this;
            const canvas = document.createElement('canvas');
            canvas.width = 1200;
            canvas.height = 700;
            canvas.style.cssText = 'display:block;width:100%;height:auto;aspect-ratio:12/7;background:#fff;touch-action:none;cursor:crosshair;border-radius:12px';
            host.appendChild(canvas);
            const ctx = canvas.getContext('2d');
            const state = {socket:null, boardId:null, userId:null, color:'#20242d', size:4, eraser:false, stroke:null, reconnectTimer:null};

            function draw(color, size, points) {
              if (!points || points.length === 0) return;
              ctx.strokeStyle = color;
              ctx.fillStyle = color;
              ctx.lineWidth = size;
              ctx.lineCap = 'round';
              ctx.lineJoin = 'round';
              if (points.length === 1) {
                ctx.beginPath();
                ctx.arc(points[0].x, points[0].y, size / 2, 0, Math.PI * 2);
                ctx.fill();
                return;
              }
              ctx.beginPath();
              ctx.moveTo(points[0].x, points[0].y);
              for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
              ctx.stroke();
            }

            function clear() {
              ctx.fillStyle = '#ffffff';
              ctx.fillRect(0, 0, canvas.width, canvas.height);
            }

            function send(message) {
              if (state.socket && state.socket.readyState === WebSocket.OPEN) {
                state.socket.send(JSON.stringify(message));
              }
            }

            function connect() {
              if (!state.boardId) return;
              const url = new URL('/ws/boards/' + state.boardId, window.location.href);
              url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
              const socket = new WebSocket(url);
              state.socket = socket;
              socket.onopen = () => host.$server.connectionChanged(true);
              socket.onmessage = event => {
                const message = JSON.parse(event.data);
                if (message.type === 'draw') draw(message.color, message.size, message.points);
                if (message.type === 'clear') clear();
                if (message.type === 'presence') host.$server.presenceChanged(message.count);
              };
              socket.onclose = () => {
                if (state.socket === socket) {
                  host.$server.connectionChanged(false);
                  state.reconnectTimer = window.setTimeout(connect, 1500);
                }
              };
              socket.onerror = () => socket.close();
            }

            host.initializeBoard = (boardId, strokesJson, userId) => {
              if (state.reconnectTimer) window.clearTimeout(state.reconnectTimer);
              if (state.socket) state.socket.close();
              state.boardId = boardId;
              state.userId = userId;
              clear();
              for (const stroke of JSON.parse(strokesJson)) draw(stroke.color, stroke.size, stroke.points);
              connect();
            };

            host.updateTool = (color, size, eraser) => {
              state.color = color;
              state.size = size;
              state.eraser = eraser;
            };

            host.clearBoard = () => {
              clear();
              send({type:'clear'});
            };

            function point(event) {
              const rect = canvas.getBoundingClientRect();
              return {
                x: Math.max(0, Math.min(canvas.width, (event.clientX - rect.left) * canvas.width / rect.width)),
                y: Math.max(0, Math.min(canvas.height, (event.clientY - rect.top) * canvas.height / rect.height))
              };
            }

            canvas.addEventListener('pointerdown', event => {
              if (!state.boardId) return;
              canvas.setPointerCapture(event.pointerId);
              const color = state.eraser ? '#ffffff' : state.color;
              const size = state.eraser ? state.size * 3 : state.size;
              const p = point(event);
              const id = window.crypto && window.crypto.randomUUID
                ? window.crypto.randomUUID()
                : Date.now().toString(36) + Math.random().toString(36).slice(2);
              state.stroke = {id, color, size, points:[p]};
              draw(color, size, [p]);
              send({type:'draw', strokeId:state.stroke.id, color, size, points:[p]});
              event.preventDefault();
            });

            canvas.addEventListener('pointermove', event => {
              if (!state.stroke) return;
              const p = point(event);
              const points = [state.stroke.points[state.stroke.points.length - 1], p];
              state.stroke.points.push(p);
              draw(state.stroke.color, state.stroke.size, points);
              send({type:'draw', strokeId:state.stroke.id, color:state.stroke.color, size:state.stroke.size, points});
              event.preventDefault();
            });

            function finishStroke() {
              if (!state.stroke) return;
              send({type:'stroke', userId:state.userId, stroke:state.stroke});
              state.stroke = null;
            }
            canvas.addEventListener('pointerup', finishStroke);
            canvas.addEventListener('pointercancel', finishStroke);
            clear();
            """;

    private IntConsumer presenceListener = count -> {};
    private java.util.function.Consumer<Boolean> connectionListener = connected -> {};

    public DrawingCanvas() {
        setWidthFull();
        getStyle().set("display", "block");
        getStyle().set("overflow", "hidden");
        getStyle().set("border-radius", "12px");
        getStyle().set("box-shadow", "0 12px 35px rgba(30, 40, 60, .12)");
        getElement().executeJs(CANVAS_SCRIPT);
    }

    public void initializeBoard(String boardId, String strokesJson, String userId) {
        getElement().executeJs("this.initializeBoard($0, $1, $2)", boardId, strokesJson, userId);
    }

    public void updateTool(String color, int size, boolean eraser) {
        getElement().executeJs("this.updateTool($0, $1, $2)", color, size, eraser);
    }

    public void clearBoard() {
        getElement().executeJs("this.clearBoard()");
    }

    public void setPresenceListener(IntConsumer listener) {
        presenceListener = listener;
    }

    public void setConnectionListener(java.util.function.Consumer<Boolean> listener) {
        connectionListener = listener;
    }

    @ClientCallable
    public void presenceChanged(int count) {
        presenceListener.accept(count);
    }

    @ClientCallable
    public void connectionChanged(boolean connected) {
        connectionListener.accept(connected);
    }
}
