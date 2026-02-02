package org.otavio;

import com.formdev.flatlaf.FlatDarkLaf;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    // --- CORES ---
    private static final Color BG_COLOR = new Color(18, 18, 18);
    private static final Color INPUT_BG = new Color(40, 40, 40);
    private static final Color USER_BUBBLE = new Color(0, 80, 0); // Verde mais sóbrio
    private static final Color AI_BUBBLE = new Color(50, 50, 55);

    private static final String MODEL_NAME = "qwen2.5-coder:3b";
    private static final String OLLAMA_URL = "http://127.0.0.1:11434/api/generate";

    // Campos da UI
    private JFrame frame;
    private JPanel chatContainer;
    private JTextField inputField;

    // Serviços
    private final HttpClient client;
    private final ExecutorService executor;
    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("ScrollBar.width", 10); // Scroll um pouco mais largo pra facilitar
        SwingUtilities.invokeLater(Main::new);
    }

    public Main() {
        client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        executor = Executors.newSingleThreadExecutor();

        frame = new JFrame("Otavio AI");
        frame.setUndecorated(true);
        frame.setBackground(BG_COLOR);
        // Borda verde sutil para saber onde a janela termina
        frame.getRootPane().setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0, 100, 0)));

        // --- 1. POSIÇÃO INTELIGENTE (Monitor do Mouse) ---
        PointerInfo pi = MouseInfo.getPointerInfo();
        GraphicsDevice currentScreen = pi.getDevice();
        Rectangle screenBounds = currentScreen.getDefaultConfiguration().getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(currentScreen.getDefaultConfiguration());

        int width = 480;
        int height = screenBounds.height - screenInsets.top - screenInsets.bottom - 100;

        int x = screenBounds.x + screenBounds.width - width - screenInsets.right;
        int y = screenBounds.y + screenInsets.top;

        frame.setBounds(x, y, width, height);

        // --- 2. REDIMENSIONAMENTO ROBUSTO ---
        ComponentResizer resizer = new ComponentResizer();
        resizer.registerComponent(frame);
        resizer.setSnapSize(new Dimension(10, 10));

        // --- LAYOUT ---
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_COLOR);

        mainPanel.add(createHeader(), BorderLayout.NORTH);

        // Usamos GridBagLayout para que os balões tenham largura flexível
        chatContainer = new JPanel(new GridBagLayout());
        chatContainer.setBackground(BG_COLOR);

        // Painel intermediário para alinhar tudo ao topo
        JPanel chatWrapper = new JPanel(new BorderLayout());
        chatWrapper.setBackground(BG_COLOR);
        chatWrapper.add(chatContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(chatWrapper);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(createInputPanel(), BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
        inputField.requestFocus();

        addMessage("IA", "<b>Sistema Online.</b><br>Redimensionamento e Quebra de Linha ativos.");
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_COLOR);
        header.setPreferredSize(new Dimension(0, 40));
        // Cursor de movimento para indicar que pode arrastar
        header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel title = new JLabel("  :: Otavio AI (" + MODEL_NAME + ")");
        title.setForeground(Color.GRAY);
        title.setFont(new Font("Consolas", Font.BOLD, 12));

        JButton close = new JButton(" X ");
        close.setForeground(Color.WHITE);
        close.setBackground(new Color(150, 40, 40));
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> {
            executor.shutdown();
            System.exit(0);
        });

        header.add(title, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);

        // Lógica de Arrastar (Mover a janela)
        MouseAdapter drag = new MouseAdapter() {
            Point mouseDownCompCoords = null;
            public void mousePressed(MouseEvent e) { mouseDownCompCoords = e.getPoint(); }
            public void mouseDragged(MouseEvent e) {
                Point curr = e.getLocationOnScreen();
                frame.setLocation(curr.x - mouseDownCompCoords.x, curr.y - mouseDownCompCoords.y);
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);

        return header;
    }

    private JPanel createInputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_COLOR);
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        inputField = new JTextField();
        inputField.setBackground(INPUT_BG);
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.GREEN);
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputField.putClientProperty("JTextField.placeholderText", "Digite sua pergunta...");

        inputField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage();
            }
        });

        p.add(inputField, BorderLayout.CENTER);
        return p;
    }

    private void addMessage(String sender, String text) {
        boolean isUser = sender.equals("VOCÊ");
        String contentHtml = isUser ? text : renderer.render(parser.parse(text));

        // CSS ajustado para legibilidade e quebra de linha
        String css = "body { font-family: Segoe UI, sans-serif; font-size: 13px; color: #E0E0E0; }" +
                "pre { background-color: #2b2b2b; padding: 5px; color: #8be9fd; margin-top: 5px; margin-bottom: 5px; }" +
                "code { font-family: JetBrains Mono; font-size: 12px; }";

        // USANDO O COMPONENTE CUSTOMIZADO QUE FORÇA O WRAP
        WrapEditorPane pane = new WrapEditorPane();
        pane.setContentType("text/html");
        pane.setText("<html><head><style>" + css + "</style></head><body>" + contentHtml + "</body></html>");
        pane.setEditable(false);
        pane.setBackground(isUser ? USER_BUBBLE : AI_BUBBLE);
        pane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Constraints do GridBag para expandir horizontalmente
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; // Isso faz o balão ocupar a largura disponível
        gbc.insets = new Insets(5, isUser ? 50 : 10, 5, isUser ? 10 : 50);

        chatContainer.add(pane, gbc);
        chatContainer.revalidate();
        chatContainer.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatContainer);
            if(scroll != null) {
                JScrollBar vertical = scroll.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if(text.isEmpty()) return;

        addMessage("VOCÊ", text);
        inputField.setText("");

        executor.submit(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("model", MODEL_NAME);
                json.put("prompt", text);
                json.put("stream", false);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                String response = new JSONObject(res.body()).optString("response", "Erro na API");

                SwingUtilities.invokeLater(() -> addMessage("IA", response));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> addMessage("IA", "Erro: " + e.getMessage()));
            }
        });
    }

    // --- CORREÇÃO 1: COMPONENTE QUE FORÇA A QUEBRA DE LINHA ---
    // O JEditorPane padrão tenta ser maior que a janela. Essa classe impede isso.
    public static class WrapEditorPane extends JEditorPane {
        public WrapEditorPane() {
            super();
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // Isso é o segredo! Força o texto a caber na largura da view.
        }
    }

    // --- CORREÇÃO 2: REDIMENSIONADOR (COMPONENT RESIZER) ---
    public static class ComponentResizer extends MouseAdapter {
        private Component component;
        private boolean resizing = false;
        private int direction = 0;
        private static final int NORTH = 1, SOUTH = 2, EAST = 4, WEST = 8;
        private static final int BORDER_DRAG_THICKNESS = 15; // Aumentei a área de pega pra 15px

        public void registerComponent(Component component) {
            this.component = component;
            component.addMouseListener(this);
            component.addMouseMotionListener(this);
        }

        public void setSnapSize(Dimension d) {}

        @Override
        public void mousePressed(MouseEvent e) {
            if (direction != 0) resizing = true;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            resizing = false;
            component.setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            Point p = e.getPoint();
            int w = component.getWidth();
            int h = component.getHeight();
            direction = 0;

            if (p.x < BORDER_DRAG_THICKNESS) direction |= WEST;
            if (p.x > w - BORDER_DRAG_THICKNESS) direction |= EAST;
            if (p.y < BORDER_DRAG_THICKNESS) direction |= NORTH;
            if (p.y > h - BORDER_DRAG_THICKNESS) direction |= SOUTH;

            int cursorType = Cursor.DEFAULT_CURSOR;
            if (direction == (EAST | SOUTH)) cursorType = Cursor.SE_RESIZE_CURSOR;
            else if (direction == EAST) cursorType = Cursor.E_RESIZE_CURSOR;
            else if (direction == SOUTH) cursorType = Cursor.S_RESIZE_CURSOR;
            else if (direction == WEST) cursorType = Cursor.W_RESIZE_CURSOR;

            component.setCursor(Cursor.getPredefinedCursor(cursorType));
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!resizing) return;
            Point p = e.getLocationOnScreen();
            Rectangle bounds = component.getBounds();

            // Lógica simples para redimensionar (Direita e Baixo)
            if ((direction & EAST) != 0) {
                bounds.width = Math.max(300, p.x - bounds.x); // Mínimo 300px
            }
            if ((direction & SOUTH) != 0) {
                bounds.height = Math.max(200, p.y - bounds.y); // Mínimo 200px
            }

            // Aplica a mudança
            component.setBounds(bounds);

            // Força o re-layout imediato dos componentes internos
            if (component instanceof Container) {
                ((Container) component).doLayout();
                component.repaint();
            }
        }
    }
}