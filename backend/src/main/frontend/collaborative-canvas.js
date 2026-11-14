class CollaborativeCanvas extends HTMLElement {
  connectedCallback() {
    this.active = true;
    if (this.canvas) return;

    this.canvas = document.createElement("canvas");
    this.canvas.width = 1200;
    this.canvas.height = 700;
    this.canvas.setAttribute("aria-label", "Shared drawing canvas");
    this.append(this.canvas);
    this.context = this.canvas.getContext("2d");
    this.color = "#202127";
    this.size = 4;
    this.erasing = false;
    this.stroke = null;
    this.socket = null;
    this.reconnectDelay = 1000;
    this.clearCanvas();
    this.bindDrawingEvents();
  }

  configure(boardId, savedStrokes) {
    this.boardId = boardId;
    for (const stroke of JSON.parse(savedStrokes)) {
      this.drawPoints(stroke.color, stroke.size, stroke.points);
    }
    this.connect();
  }

  setBrush(color, size, erasing) {
    this.color = color || "#202127";
    this.size = Math.max(1, Math.min(30, Number(size) || 4));
    this.erasing = Boolean(erasing);
  }

  clearBoard() {
    this.clearCanvas();
    this.send({ type: "clear" });
  }

  bindDrawingEvents() {
    this.canvas.addEventListener("pointerdown", event => {
      if (!this.boardId) return;
      event.preventDefault();
      this.canvas.setPointerCapture(event.pointerId);
      const point = this.pointFromEvent(event);
      const color = this.erasing ? "#ffffff" : this.color;
      const size = this.erasing ? this.size * 3 : this.size;
      this.stroke = {
        id: this.createId(),
        color,
        size,
        points: [point]
      };
      this.drawPoints(color, size, [point]);
      this.send({
        type: "draw",
        strokeId: this.stroke.id,
        color,
        size,
        points: [point]
      });
    });

    this.canvas.addEventListener("pointermove", event => {
      if (!this.stroke) return;
      const point = this.pointFromEvent(event);
      const previous = this.stroke.points[this.stroke.points.length - 1];
      this.stroke.points.push(point);
      const points = [previous, point];
      this.drawPoints(this.stroke.color, this.stroke.size, points);
      this.send({
        type: "draw",
        strokeId: this.stroke.id,
        color: this.stroke.color,
        size: this.stroke.size,
        points
      });
    });

    this.canvas.addEventListener("pointerup", () => this.finishStroke());
    this.canvas.addEventListener("pointercancel", () => this.finishStroke());
  }

  finishStroke() {
    if (!this.stroke) return;
    this.send({
      type: "stroke",
      userId: this.createId(),
      stroke: this.stroke
    });
    this.stroke = null;
  }

  connect() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    this.socket = new WebSocket(
      `${protocol}//${window.location.host}/ws/boards/${encodeURIComponent(this.boardId)}`
    );
    this.socket.addEventListener("open", () => {
      this.reconnectDelay = 1000;
      this.notifyConnection(true);
    });
    this.socket.addEventListener("message", event => {
      const message = JSON.parse(event.data);
      if (message.type === "draw") {
        this.drawPoints(message.color, message.size, message.points);
      } else if (message.type === "clear") {
        this.clearCanvas();
      } else if (message.type === "presence" && this.$server) {
        this.$server.presenceChanged(message.count);
      }
    });
    this.socket.addEventListener("close", () => {
      this.notifyConnection(false);
      if (!this.active || !this.boardId) return;
      window.setTimeout(() => {
        if (this.active && this.boardId) this.connect();
      }, this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 10000);
    });
    this.socket.addEventListener("error", () => this.socket.close());
  }

  notifyConnection(connected) {
    if (this.active && this.$server) this.$server.connectionChanged(connected);
  }

  send(message) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    }
  }

  createId() {
    return window.crypto?.randomUUID
      ? window.crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  pointFromEvent(event) {
    const bounds = this.canvas.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(1200, (event.clientX - bounds.left) * 1200 / bounds.width)),
      y: Math.max(0, Math.min(700, (event.clientY - bounds.top) * 700 / bounds.height))
    };
  }

  drawPoints(color, size, points) {
    if (!points?.length) return;
    const context = this.context;
    context.beginPath();
    context.strokeStyle = color;
    context.fillStyle = color;
    context.lineWidth = size;
    context.lineCap = "round";
    context.lineJoin = "round";
    if (points.length === 1) {
      context.arc(points[0].x, points[0].y, size / 2, 0, Math.PI * 2);
      context.fill();
      return;
    }
    context.moveTo(points[0].x, points[0].y);
    for (let index = 1; index < points.length; index += 1) {
      context.lineTo(points[index].x, points[index].y);
    }
    context.stroke();
  }

  clearCanvas() {
    this.context.fillStyle = "#fff";
    this.context.fillRect(0, 0, this.canvas.width, this.canvas.height);
  }

  disconnectedCallback() {
    this.active = false;
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }
}

customElements.define("collaborative-canvas", CollaborativeCanvas);
