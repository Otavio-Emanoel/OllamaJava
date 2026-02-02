package com.otavio;

import com.formdev.flatlaf.FlatDarkLaf;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

public class Main {
    // Configurações
    private static final String MODEL_NAME = "qwen2.5-coder:3b"; // Seu modelo
    private static final String OLLAMA_URL = "http://127.0.0.1:11434/api/generate";
    private static final int WIDTH = 400; // Largura da janela lateral

    private JTextArea chatArea;
    private JTextField inputField;
    private HttpClient client;

    public static void main(String[] args) {
        // Ativa o tema Dark moderno
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
    }

    private void createAndShowGUI() {
        JFrame frame = new JFrame("Otavio AI");
        frame.setUndecorated(true); // Tira a barra de título (fica clean)
        frame.setAlwaysOnTop(true); // Fica sempre visível sobre o VS Code

        // Configura posição: Lateral Direita Total
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(WIDTH, screenSize.height);
        frame.setLocation(screenSize.width - WIDTH, 0);

        // Layout
        frame.setLayout(new BorderLayout());

        // Área de Chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 14)); // Fonte de Dev

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Campo de Input
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Ação ao apertar Enter
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        // Botão de fechar (já que não tem barra de título)
        JButton closeBtn = new JButton("X");
        closeBtn.addActionListener(e -> System.exit(0));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(closeBtn, BorderLayout.EAST);

        frame.add(scroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Inicializa HTTP Client
        client = HttpClient.newHttpClient();

        frame.setVisible(true);
        inputField.requestFocus();
    }
    private void sendMessage() {
        String text = inputField.getText();
        if (text.isEmpty()) return;

        chatArea.append("VOCÊ: " + text + "\n\n");
        inputField.setText("");
        inputField.setEnabled(false); // Trava enquanto pensa

        // Roda a requisição numa Thread separada para não travar a UI
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // Monta o JSON (usando stream: false para vir a resposta toda de uma vez por enquanto)
                JSONObject json = new JSONObject();
                json.put("model", MODEL_NAME);
                json.put("prompt", text);
                json.put("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // Parse da resposta
                JSONObject responseJson = new JSONObject(response.body());
                String aiResponse = responseJson.getString("response");

                // Atualiza a UI na Thread do Swing
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("IA: " + aiResponse + "\n-------------------\n");
                    inputField.setEnabled(true);
                    inputField.requestFocus();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("ERRO: " + e.getMessage() + "\n");
                    inputField.setEnabled(true);
                });
            }
        });
    }
}