package org.otavio;

import com.formdev.flatlaf.FlatDarkLaf;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

public class Main {
    // --- CONFIGURAÇÕES ---
    private static final String MODEL_NAME = "qwen2.5-coder:3b";
    private static final String OLLAMA_URL = "http://127.0.0.1:11434/api/generate";
    private static final int WIDTH = 450;

    // Variáveis para permitir arrastar a janela
    private static Point mouseDownCompCoords = null;

    private JTextArea chatArea;
    private JTextField inputField;
    private HttpClient client;
    private JFrame frame;

    public static void main(String[] args) {
        FlatDarkLaf.setup(); // Tema Dark
        SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Otavio AI - Java Client");
        frame.setUndecorated(true); // Sem bordas
        frame.setAlwaysOnTop(true);

        // Tenta pegar apenas o monitor principal para iniciar
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle screenBounds = defaultScreen.getDefaultConfiguration().getBounds();

        // Posiciona no canto direito do MONITOR PRINCIPAL
        frame.setSize(WIDTH, screenBounds.height - 40); // -40 para não cobrir a barra inferior se tiver
        frame.setLocation(screenBounds.width - WIDTH, 0);

        frame.setLayout(new BorderLayout());

        // --- LÓGICA PARA ARRASTAR A JANELA (DRAG AND DROP) ---
        MouseAdapter dragListener = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                mouseDownCompCoords = e.getPoint();
            }
            public void mouseReleased(MouseEvent e) {
                mouseDownCompCoords = null;
            }
            public void mouseDragged(MouseEvent e) {
                Point currCoords = e.getLocationOnScreen();
                frame.setLocation(currCoords.x - mouseDownCompCoords.x, currCoords.y - mouseDownCompCoords.y);
            }
        };
        frame.addMouseListener(dragListener);
        frame.addMouseMotionListener(dragListener);

        // --- ÁREA DE CHAT ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
        chatArea.setForeground(new Color(220, 220, 220)); // Texto claro

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- CAMPO DE INPUT ---
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        // --- BOTÃO FECHAR ---
        JButton closeBtn = new JButton(" X ");
        closeBtn.setBackground(new Color(200, 50, 50));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> System.exit(0));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(closeBtn, BorderLayout.EAST);

        frame.add(scroll, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Client HTTP com Timeout para não travar
        client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        frame.setVisible(true);
        inputField.requestFocus();

        System.out.println("Janela iniciada. Tentando conectar no Monitor: " + defaultScreen.getIDstring());
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        chatArea.append("VOCÊ: " + text + "\n");
        inputField.setText("");
        inputField.setEnabled(false); // Trava input

        // Feedback visual imediato
        chatArea.append(">> Pensando...\n");
        System.out.println("Enviando prompt para Ollama: " + text);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("model", MODEL_NAME);
                json.put("prompt", text);
                json.put("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                System.out.println("Conectando em " + OLLAMA_URL + "...");
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("Status Code: " + response.statusCode());

                if (response.statusCode() == 200) {
                    JSONObject responseJson = new JSONObject(response.body());
                    String aiResponse = responseJson.getString("response");

                    SwingUtilities.invokeLater(() -> {
                        // Remove o "Pensando..." (gambiarra simples: substitui o texto todo ou só adiciona)
                        // Vamos apenas adicionar por enquanto para simplificar
                        chatArea.append("IA: " + aiResponse + "\n\n====================\n\n");
                        // Rola para baixo
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    });
                } else {
                    System.out.println("Erro do Servidor: " + response.body());
                    SwingUtilities.invokeLater(() -> chatArea.append("ERRO HTTP: " + response.statusCode() + "\n"));
                }

            } catch (Exception e) {
                e.printStackTrace(); // Mostra o erro exato no terminal
                SwingUtilities.invokeLater(() -> chatArea.append("ERRO TÉCNICO: " + e.getMessage() + "\n"));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    inputField.setEnabled(true);
                    inputField.requestFocus();
                });
            }
        });
    }
}