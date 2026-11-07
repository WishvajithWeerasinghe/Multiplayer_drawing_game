package com.example.drawclient;

import com.example.drawclient.Models.BoardInfo;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;

/** Start window: list boards, create a new one, open one. */
public class LobbyFrame extends JFrame {
    private final ApiClient api;
    private final DefaultListModel<BoardInfo> model = new DefaultListModel<>();
    private final JList<BoardInfo> list = new JList<>(model);
    private final JLabel message = new JLabel(" ");

    public LobbyFrame(ApiClient api) {
        super("Collab Draw");
        this.api = api;

        JTextField nameField = new JTextField();
        JButton create = new JButton("Create board");
        create.addActionListener(e -> createBoard(nameField.getText()));
        nameField.addActionListener(e -> createBoard(nameField.getText()));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("New board:"), BorderLayout.WEST);
        top.add(nameField, BorderLayout.CENTER);
        top.add(create, BorderLayout.EAST);

        JButton open = new JButton("Open selected");
        open.addActionListener(e -> openSelected());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(message);
        bottom.add(refresh);
        bottom.add(open);

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) openSelected(); }
        });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(480, 400);
        setLocationRelativeTo(null);
        refresh();
    }

    private void refresh() {
        new Thread(() -> {
            try {
                List<BoardInfo> boards = api.listBoards();
                SwingUtilities.invokeLater(() -> {
                    model.clear();
                    boards.forEach(model::addElement);
                    message.setText(" ");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> message.setText("Cannot reach server"));
            }
        }).start();
    }

    private void createBoard(String name) {
        new Thread(() -> {
            try {
                BoardInfo b = api.createBoard(name.trim());
                SwingUtilities.invokeLater(() -> { refresh(); new BoardFrame(api, b).setVisible(true); });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> message.setText("Create failed: " + ex.getMessage()));
            }
        }).start();
    }

    private void openSelected() {
        BoardInfo b = list.getSelectedValue();
        if (b != null) new BoardFrame(api, b).setVisible(true);
    }
}
