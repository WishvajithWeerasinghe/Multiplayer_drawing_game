package com.example.drawclient;

import com.example.drawclient.Models.BoardInfo;
import com.example.drawclient.Models.Point;
import com.example.drawclient.Models.StrokeData;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.UUID;
import javax.swing.*;

/** Drawing window for one board. */
public class BoardFrame extends JFrame implements CanvasPanel.Listener, DrawSocket.Handler {
    private final CanvasPanel canvas = new CanvasPanel(this);
    private final DrawSocket socket;
    private final JLabel status = new JLabel("connecting…");
    private final JButton colorBtn = new JButton("Color");
    private boolean connected;
    private int users = 1;

    public BoardFrame(ApiClient api, BoardInfo board) {
        super("Collab Draw – " + board.name());
        socket = new DrawSocket(api.wsUrl(board.id()), UUID.randomUUID().toString(), this);

        colorBtn.setBackground(Color.BLACK);
        colorBtn.setForeground(Color.WHITE);
        colorBtn.setOpaque(true);
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Pick a color", canvas.getColor());
            if (c != null) {
                canvas.setColor(c);
                colorBtn.setBackground(c);
                colorBtn.setForeground(c.getRed() + c.getGreen() + c.getBlue() > 380 ? Color.BLACK : Color.WHITE);
            }
        });

        JSlider slider = new JSlider(1, 30, 4);
        slider.addChangeListener(e -> canvas.setSize(slider.getValue()));

        JToggleButton eraser = new JToggleButton("Eraser");
        eraser.addActionListener(e -> canvas.setEraser(eraser.isSelected()));

        JButton clear = new JButton("Clear board");
        clear.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear the board for everyone?", "Clear", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                canvas.clear();
                socket.sendClear();
            }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.add(colorBtn);
        bar.add(new JLabel("Size"));
        bar.add(slider);
        bar.add(eraser);
        bar.add(clear);
        bar.add(status);

        setLayout(new BorderLayout());
        add(bar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { socket.close(); }
        });
        pack();
        setLocationByPlatform(true);

        // load saved strokes, then go live
        new Thread(() -> {
            try {
                List<StrokeData> strokes = api.getStrokes(board.id());
                SwingUtilities.invokeLater(() -> strokes.forEach(s -> canvas.drawSegment(s.color(), s.size(), s.points())));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Could not load strokes: " + ex.getMessage()));
            }
            socket.connect();
        }, "load-board").start();
    }

    private void refreshStatus() {
        status.setText((connected ? "● connected" : "○ reconnecting…") + "  |  " + users + " user(s) online");
        status.setForeground(connected ? new Color(0x15803d) : Color.RED);
    }

    // ---- CanvasPanel.Listener (local drawing -> network) ----
    @Override public void segment(String strokeId, String color, int size, List<Point> pts) {
        socket.sendDraw(strokeId, color, size, pts);
    }
    @Override public void strokeFinished(StrokeData stroke) { socket.sendStroke(stroke); }

    // ---- DrawSocket.Handler (network -> UI) ----
    @Override public void onDraw(String color, int size, List<Point> pts) {
        SwingUtilities.invokeLater(() -> canvas.drawSegment(color, size, pts));
    }
    @Override public void onClear() { SwingUtilities.invokeLater(canvas::clear); }
    @Override public void onPresence(int count) {
        SwingUtilities.invokeLater(() -> { users = count; refreshStatus(); });
    }
    @Override public void onStatus(boolean isConnected) {
        SwingUtilities.invokeLater(() -> { connected = isConnected; refreshStatus(); });
    }
}
