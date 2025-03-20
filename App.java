import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.vosk.Model;
import org.vosk.Recognizer;

public class App {

    private JTextField userInput;
    private Connection connection;
    private Map<String, String[]> intents = new HashMap<>();
    private Random random = new Random();
    private JPanel chatPanel;
    private JScrollPane scrollPane;

    public App() {
        // Initialize intents
        intents.put("greetings", new String[]{"Hello, how can I help you today?", "Hi there! Welcome!"});
        intents.put("product_inquiry", new String[]{"Sure, I can help you with product information. What product are you interested in?"});
        intents.put("order_status", new String[]{"Please provide your order number to track your order."});
        intents.put("returns", new String[]{"I can assist you with returns. What item are you looking to return?"});
        intents.put("help", new String[]{"I can assist with product inquiries, order status, and returns. How can I assist you today?"});

        // Initialize database connection
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:chatbot.db");
            initDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Initialize UI
        createUI();
    }

    private void startVoiceRecognition() {
        new Thread(() -> {
            try {
                // Load the Vosk model (ensure the path is correct)
                Model model = new Model("src/main/resources/vosk-model-small-en-us-0.15");

                // Setup microphone input
                AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();

                // Recognizer to process the speech input
                Recognizer recognizer = new Recognizer(model, 16000);
                byte[] buffer = new byte[4096];

                while (true) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead <= 0) {
                        continue;
                    }
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult();
                        String recognizedText = parseRecognizedText(result);
                        System.out.println("Recognized text: " + recognizedText);

                        // Set the recognized text in the input field
                        SwingUtilities.invokeLater(() -> userInput.setText(recognizedText));
                        break;
                    }
                }

                microphone.stop();
                microphone.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String parseRecognizedText(String result) {
        try {
            int textIndex = result.indexOf("\"text\" : \"");
            if (textIndex >= 0) {
                int startIndex = textIndex + 9;
                int endIndex = result.indexOf("\"", startIndex + 1);
                if (endIndex >= 0) {
                    return result.substring(startIndex + 2, endIndex);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private void createUI() {
        // Set the system look and feel for a modern appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        userInput = new JTextField();
        JButton sendButton = new JButton("Send");

        // Voice input button
        JButton voiceButton = new JButton("🎤");
        voiceButton.setPreferredSize(new Dimension(50, 30));

        // Add voice recognition to the button
        voiceButton.addActionListener(e -> startVoiceRecognition());

        // Input panel layout
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonsPanel.add(voiceButton);
        buttonsPanel.add(sendButton);

        inputPanel.add(userInput, BorderLayout.CENTER);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);

        // Frame setup
        JFrame frame = new JFrame("Customer Service Chatbot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 700);
        frame.setLocationRelativeTo(null);

        // Header panel with title
        JLabel titleLabel = new JLabel("Customer Service Chatbot");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setHorizontalAlignment(JLabel.CENTER);
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        // Chat panel setup
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        frame.setLayout(new BorderLayout());
        frame.add(headerPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);

        // Send button action
        sendButton.addActionListener(e -> {
            String input = userInput.getText();
            addMessage("You", input);

            String botResponse = getBotResponse(input);
            addMessage("Bot", botResponse);

            userInput.setText("");

            // Save chat history to the database
            try {
                saveChatHistory(input, botResponse);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        // Press Enter to send message
        userInput.addActionListener(e -> sendButton.doClick());

        frame.setVisible(true);
    }

    private void addMessage(String sender, String message) {
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBackground(Color.WHITE);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextArea messageText = new JTextArea(message);
        messageText.setLineWrap(true);
        messageText.setWrapStyleWord(true);
        messageText.setEditable(false);
        messageText.setFont(new Font("Arial", Font.PLAIN, 16));
        messageText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Message bubble styling
        if (sender.equals("You")) {
            messageText.setBackground(new Color(220, 248, 198)); // light green
            messageText.setForeground(Color.BLACK);
            messageText.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 0, Color.GRAY));

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.setBackground(Color.WHITE);
            rightPanel.add(messageText, BorderLayout.EAST);
            messagePanel.add(rightPanel, BorderLayout.EAST);
        } else {
            messageText.setBackground(new Color(240, 240, 240)); // light gray
            messageText.setForeground(Color.BLACK);
            messageText.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 1, Color.GRAY));

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setBackground(Color.WHITE);
            leftPanel.add(messageText, BorderLayout.WEST);
            messagePanel.add(leftPanel, BorderLayout.WEST);
        }

        chatPanel.add(messagePanel);
        chatPanel.revalidate();

        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private String getBotResponse(String userInput) {
        String intent = classifyIntent(userInput);
        if (intent.equals("product_inquiry")) {
            String productName = extractProductName(userInput);
            if (productName != null) {
                return getProductDetails(productName);
            } else {
                return randomResponse("product_inquiry");
            }
        }
        return randomResponse(intent);
    }

    private String classifyIntent(String userInput) {
        if (userInput.toLowerCase().contains("order")) {
            return "order_status";
        } else if (userInput.toLowerCase().contains("product")) {
            return "product_inquiry";
        } else if (userInput.toLowerCase().contains("return")) {
            return "returns";
        } else if (userInput.toLowerCase().contains("help")) {
            return "help";
        } else {
            return "greetings";
        }
    }

    private String extractProductName(String userInput) {
        try {
            String[] words = userInput.split(" ");
            for (String word : words) {
                PreparedStatement stmt = connection.prepareStatement("SELECT name FROM products WHERE LOWER(name) = ?");
                stmt.setString(1, word.toLowerCase());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getProductDetails(String productName) {
        try {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM products WHERE name = ?");
            stmt.setString(1, productName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return "Product: " + rs.getString("name") + "\nDescription: " + rs.getString("description") + "\nPrice: $" + rs.getDouble("price");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Sorry, I couldn't find details for the product: " + productName;
    }

    private String randomResponse(String intent) {
        String[] responses = intents.get(intent);
        return responses[random.nextInt(responses.length)];
    }

    private void saveChatHistory(String userInput, String botResponse) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement("INSERT INTO chat_history (user_input, bot_response) VALUES (?, ?)");
        stmt.setString(1, userInput);
        stmt.setString(2, botResponse);
        stmt.executeUpdate();
    }

    private void initDatabase() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, description TEXT, price REAL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS chat_history (id INTEGER PRIMARY KEY AUTOINCREMENT, user_input TEXT, bot_response TEXT)");

        // Insert sample products
        PreparedStatement insertStmt = connection.prepareStatement("INSERT OR IGNORE INTO products (name, description, price) VALUES (?, ?, ?)");
        insertStmt.setString(1, "smartphone");
        insertStmt.setString(2, "A high-end smartphone with 128GB storage.");
        insertStmt.setDouble(3, 699.99);
        insertStmt.executeUpdate();

        insertStmt.setString(1, "laptop");
        insertStmt.setString(2, "A lightweight laptop with 16GB RAM and 512GB SSD.");
        insertStmt.setDouble(3, 1199.99);
        insertStmt.executeUpdate();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
