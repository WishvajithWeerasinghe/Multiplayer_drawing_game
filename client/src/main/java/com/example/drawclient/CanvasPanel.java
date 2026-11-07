package com.example.drawclient;

import com.example.drawclient.Models.Point;
import com.example.drawclient.Models.StrokeData;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;

/** Draws on a fixed 1200x700 logical image that is scaled to fit the window, so all users share coordinates. */
public class CanvasPanel extends JPanel {
    public static final int W = 1200, H = 700;

    public interface Listener {
        void segment(String strokeId, String color, int size, List<Point> pts);
        void strokeFinished(StrokeData stroke);
    }

    private final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private final Listener listener;
    private Color color = Color.BLACK;
    private int size = 4;
    private boolean eraser;
    private StrokeData current;

    public CanvasPanel(Listener listener) {
        this.listener = listener;
        setPreferredSize(new Dimension(900, 525));
        setBackground(new Color(0xEEEEEE));
        clearImage();

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point p = toLogical(e);
                String c = eraser ? "#ffffff" : hex(color);
                int s = eraser ? size * 3 : size;
                current = new StrokeData(UUID.randomUUID().toString(), c, s, new ArrayList<>(List.of(p)));
                drawSegment(c, s, List.of(p));
                listener.segment(current.id(), c, s, List.of(p));
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (current == null) return;
                Point p = toLogical(e);
                List<Point> pts = current.points();
                Point prev = pts.get(pts.size() - 1);
                pts.add(p);
                List<Point> seg = List.of(prev, p);
                drawSegment(current.color(), current.size(), seg);
                listener.segment(current.id(), current.color(), current.size(), seg);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (current == null) return;
                StrokeData done = current;
                current = null;
                listener.strokeFinished(done);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void setColor(Color c) { this.color = c; this.eraser = false; }
    public void setSize(int s) { this.size = s; }
    public void setEraser(boolean on) { this.eraser = on; }
    public Color getColor() { return color; }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void clearImage() {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.dispose();
    }

    public void clear() {
        clearImage();
        repaint();
    }

    /** Draw a polyline onto the shared image (call on the Swing thread). */
    public void drawSegment(String hexColor, int strokeSize, List<Point> pts) {
        if (pts.isEmpty()) return;
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.decode(hexColor));
        if (pts.size() == 1) {
            Point p = pts.get(0);
            g.fillOval((int) (p.x() - strokeSize / 2.0), (int) (p.y() - strokeSize / 2.0), strokeSize, strokeSize);
        } else {
            g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < pts.size(); i++) {
                g.drawLine((int) pts.get(i - 1).x(), (int) pts.get(i - 1).y(), (int) pts.get(i).x(), (int) pts.get(i).y());
            }
        }
        g.dispose();
        repaint();
    }

    // ---- scaling helpers ----
    private double scale() { return Math.min(getWidth() / (double) W, getHeight() / (double) H); }
    private int offX() { return (int) ((getWidth() - W * scale()) / 2); }
    private int offY() { return (int) ((getHeight() - H * scale()) / 2); }

    private Point toLogical(MouseEvent e) {
        double s = scale();
        double x = Math.max(0, Math.min(W, (e.getX() - offX()) / s));
        double y = Math.max(0, Math.min(H, (e.getY() - offY()) / s));
        return new Point(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double s = scale();
        g2.drawImage(image, offX(), offY(), (int) (W * s), (int) (H * s), null);
    }
}
