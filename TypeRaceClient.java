import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

public class TypeRaceClient extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private String currentSentence = "";
    private Map<String, Integer> racers = new HashMap<>();
    private Map<String, Integer> wpmResults = new HashMap<>();
    private JTextArea textArea;
    private JTextField inputField;
    private JPanel raceTrackPanel;
    private boolean raceFinished = false;
    private JLabel countdownLabel;
    private long startTime;
    private Font customFont;
    private Image carImage;
    private Image finishedCarImage;
    private Image trophyImage;

    public TypeRaceClient() {
        loadResources();
        initializeUI();
        connectToServer();
    }

    private void loadResources() {
        // Load custom font
        try {
            customFont = Font.createFont(Font.TRUETYPE_FONT, 
                new File("arial.ttf")).deriveFont(16f);
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .registerFont(customFont);
        } catch (Exception e) {
            customFont = new Font("Arial", Font.PLAIN, 16);
        }
        
        // Load car images (fallback to drawn cars)
        try {
            carImage = new ImageIcon("finished_car.png").getImage().getScaledInstance(120, 60, Image.SCALE_SMOOTH);
            finishedCarImage = new ImageIcon("finished_car.png").getImage().getScaledInstance(120, 60, Image.SCALE_SMOOTH);
            trophyImage = new ImageIcon("trophy.png").getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
        } catch (Exception e) {
            carImage = null;
            finishedCarImage = null;
            trophyImage = null;
        }
    }

    private void initializeUI() {
        setTitle("Type Racing Game - " + clientName);
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(240, 245, 255));

        // Get player name
        clientName = JOptionPane.showInputDialog(this, 
            "<html><b>Enter your racing name:</b></html>", 
            "Player Registration", 
            JOptionPane.PLAIN_MESSAGE);
        
        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Racer" + new Random().nextInt(1000);
        }

        // Race track panel
        raceTrackPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawRaceTrack(g);
            }
        };
        raceTrackPanel.setBackground(new Color(230, 240, 255));
        raceTrackPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        add(raceTrackPanel, BorderLayout.CENTER);

        // Countdown label
        countdownLabel = new JLabel("Waiting for players...", SwingConstants.CENTER);
        countdownLabel.setFont(customFont.deriveFont(Font.BOLD, 24));
        countdownLabel.setForeground(new Color(50, 50, 150));
        countdownLabel.setBorder(new EmptyBorder(10, 0, 20, 0));
        add(countdownLabel, BorderLayout.NORTH);

        // Text area for sentence
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(customFont.deriveFont(Font.BOLD, 18));
        textArea.setBackground(new Color(250, 250, 250));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(new CompoundBorder(
            new LineBorder(new Color(200, 200, 220), 2),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 100));
        add(scrollPane, BorderLayout.NORTH);

        // Input field
        inputField = new JTextField();
        inputField.setFont(customFont.deriveFont(Font.PLAIN, 18));
        inputField.setEnabled(false);
        inputField.setBorder(new CompoundBorder(
            new LineBorder(new Color(150, 150, 200), 2),
            new EmptyBorder(8, 10, 8, 10)
        ));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                checkTypingProgress();
            }
        });
        add(inputField, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void drawRaceTrack(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int trackHeight = 100;
        int startY = 80;
        int trackWidth = 800;

        // Draw track with gradient and border
        GradientPaint gp = new GradientPaint(
            0, 0, new Color(210, 220, 240), 
            0, trackHeight * racers.size(), new Color(180, 190, 210)
        );
        g2d.setPaint(gp);
        
        for (int i = 0; i < racers.size(); i++) {
            // Track background
            g2d.fillRoundRect(50, startY + i * trackHeight - 30, trackWidth, 60, 30, 30);
            
            // Track border
            g2d.setColor(new Color(150, 160, 180));
            g2d.drawRoundRect(50, startY + i * trackHeight - 30, trackWidth, 60, 30, 30);
            
            // Lane markers
            g2d.setColor(Color.WHITE);
            for (int x = 100; x < 800; x += 50) {
                g2d.fillRect(x, startY + i * trackHeight, 20, 3);
            }
        }

        // Draw finish line with checkered pattern
        g2d.setColor(Color.RED);
        g2d.fillRect(800, startY - 30, 10, trackHeight * racers.size() + 30);
        
        // Checkered flag pattern
        boolean white = true;
        int checkSize = 20;
        for (int y = startY - 30; y < startY + trackHeight * racers.size(); y += checkSize) {
            for (int x = 800; x < 810; x += checkSize) {
                g2d.setColor(white ? Color.WHITE : Color.BLACK);
                g2d.fillRect(x, y, checkSize, checkSize);
                white = !white;
            }
            white = !white;
        }

        // Draw "FINISH" text
        g2d.setColor(Color.WHITE);
        g2d.setFont(customFont.deriveFont(Font.BOLD, 16));
        g2d.drawString("FINISH", 820, startY + (trackHeight * racers.size()) / 2);

        // Draw all racers
        int i = 0;
        for (Map.Entry<String, Integer> entry : racers.entrySet()) {
            String name = entry.getKey();
            int progress = entry.getValue();
            int carX = 50 + (int) (750 * ((double) progress / currentSentence.length()));
            int carY = startY + i * trackHeight - 15;

            // Draw car (image or graphic)
            if (wpmResults.containsKey(name)) {
                drawFinishedCar(g2d, carX, carY, name.equals(clientName));
            } else {
                drawCar(g2d, carX, carY, name.equals(clientName));
            }

            // Draw player info
            g2d.setColor(Color.BLACK);
            String info = name;
            if (wpmResults.containsKey(name)) {
                info += " - " + wpmResults.get(name) + " WPM";
                g2d.setColor(new Color(0, 120, 0)); // Dark green for finished players
            }
            
            g2d.setFont(customFont.deriveFont(Font.BOLD, 18));
            g2d.drawString(info, 60, carY - 5);

            // Draw progress percentage
            int percent = (int) ((double) progress / currentSentence.length() * 100);
            g2d.setColor(Color.BLACK);
            g2d.drawString(percent + "%", carX + 80, carY + 15);

            i++;
        }
    }

    private void drawCar(Graphics2D g2d, int x, int y, boolean isCurrentPlayer) {
        if (carImage != null) {
            // Draw car image (larger for current player)
            int width = isCurrentPlayer ? 140 : 120;
            int height = isCurrentPlayer ? 70 : 60;
            g2d.drawImage(carImage, x, y - (isCurrentPlayer ? 15 : 10), width, height, this);
            
            // Add current player indicator
            if (isCurrentPlayer) {
                g2d.setColor(Color.RED);
                g2d.fillOval(x + 60, y - 30, 10, 10);
            }
        } else {
            // Fallback: Draw car graphic
            Color carColor = isCurrentPlayer ? new Color(0, 100, 200) : new Color(200, 50, 50);
            int carWidth = isCurrentPlayer ? 120 : 100;
            int carHeight = isCurrentPlayer ? 50 : 40;
            
            // Car body
            g2d.setColor(carColor);
            g2d.fillRoundRect(x, y, carWidth, carHeight, 20, 20);
            
            // Car roof
            g2d.fillRoundRect(x + 15, y - 20, carWidth - 30, 20, 10, 10);
            
            // Windows
            g2d.setColor(new Color(200, 230, 255, 180));
            g2d.fillRoundRect(x + 20, y - 15, 40, 15, 5, 5);
            g2d.fillRoundRect(x + 60, y - 15, 40, 15, 5, 5);
            
            // Wheels
            g2d.setColor(Color.BLACK);
            g2d.fillOval(x + 5, y + carHeight - 15, 20, 20);
            g2d.fillOval(x + carWidth - 25, y + carHeight - 15, 20, 20);
            
            // Wheel rims
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillOval(x + 10, y + carHeight - 10, 10, 10);
            g2d.fillOval(x + carWidth - 20, y + carHeight - 10, 10, 10);
            
            // Headlights
            g2d.setColor(Color.YELLOW);
            g2d.fillOval(x + carWidth - 10, y + 10, 10, 10);
            
            // Current player indicator
            if (isCurrentPlayer) {
                g2d.setColor(Color.RED);
                g2d.fillOval(x + 50, y - 30, 10, 10);
            }
        }
    }

    private void drawFinishedCar(Graphics2D g2d, int x, int y, boolean isCurrentPlayer) {
        if (finishedCarImage != null) {
            // Draw finished car image
            g2d.drawImage(finishedCarImage, x, y - 10, 120, 60, this);
        } else {
            // Fallback: Draw finished car graphic
            Color carColor = new Color(0, 150, 0); // Green for finished cars
            
            // Car body
            g2d.setColor(carColor);
            g2d.fillRoundRect(x, y, 120, 50, 20, 20);
            
            // Celebration stars
            g2d.setColor(Color.YELLOW);
            int[] starX = {x+30, x+50, x+70, x+90};
            int[] starY = {y-25, y-35, y-25, y-30};
            for (int i = 0; i < starX.length; i++) {
                drawStar(g2d, starX[i], starY[i], 5, 10, 5);
            }
            
            // Trophy
            g2d.setColor(new Color(255, 215, 0)); // Gold
            g2d.fillOval(x + 50, y - 45, 20, 15); // Trophy cup
            g2d.fillRect(x + 55, y - 30, 10, 10); // Trophy base
        }
    }

    private void drawStar(Graphics2D g2d, int x, int y, int innerRadius, int outerRadius, int points) {
        double angle = Math.PI / points;
        Polygon p = new Polygon();
        
        for (int i = 0; i < 2 * points; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            p.addPoint((int) (x + r * Math.sin(i * angle)), 
                      (int) (y - r * Math.cos(i * angle)));
        }
        
        g2d.fill(p);
    }

    private void checkTypingProgress() {
        if (raceFinished) return;
        
        String typedText = inputField.getText();
        int correctChars = 0;

        for (int i = 0; i < typedText.length() && i < currentSentence.length(); i++) {
            if (typedText.charAt(i) == currentSentence.charAt(i)) {
                correctChars++;
            } else {
                break;
            }
        }

        out.println("PROGRESS:" + correctChars);

        if (correctChars == currentSentence.length()) {
            long endTime = System.currentTimeMillis();
            double minutes = (endTime - startTime) / 60000.0;
            int wordCount = currentSentence.split(" ").length;
            int wpm = (int) (wordCount / minutes);
            
            showWpmResult(wpm);
            inputField.setEditable(false);
        }
    }

    private void showWpmResult(int wpm) {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(new Color(240, 245, 255));
        
        // Trophy image
        JLabel iconLabel;
        if (trophyImage != null) {
            iconLabel = new JLabel(new ImageIcon(trophyImage));
        } else {
            iconLabel = new JLabel("🏆");
            iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 60));
        }
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // WPM text
        JLabel textLabel = new JLabel(
            "<html><div style='text-align: center;'>" +
            "<font size='5' color='#006400'><b>Race Completed!</b></font><br><br>" +
            "<font size='4'>Your typing speed:</font><br>" +
            "<font size='6' color='#00008B'><b>" + wpm + " WPM</b></font><br><br>" +
            "<font size='3'>" + getWpmFeedback(wpm) + "</font>" +
            "</div></html>", 
            SwingConstants.CENTER);
        
        panel.add(iconLabel, BorderLayout.NORTH);
        panel.add(textLabel, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(20, 40, 20, 40));
        
        JOptionPane.showOptionDialog(
            this, 
            panel, 
            "Race Results", 
            JOptionPane.DEFAULT_OPTION, 
            JOptionPane.PLAIN_MESSAGE, 
            null, 
            new Object[]{}, 
            null
        );
    }

    private String getWpmFeedback(int wpm) {
        if (wpm >= 80) return "Professional typist speed!";
        if (wpm >= 60) return "Excellent typing speed!";
        if (wpm >= 40) return "Good typing speed!";
        if (wpm >= 30) return "Average typing speed";
        return "Keep practicing to improve!";
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5555);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(clientName);
            new Thread(this::listenForServerMessages).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this, 
                "Could not connect to server:\n" + e.getMessage(), 
                "Connection Error", 
                JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
    }

    private void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("SENTENCE:")) {
                    currentSentence = message.substring(9);
                    SwingUtilities.invokeLater(() -> {
                        textArea.setText(currentSentence);
                        inputField.setText("");
                        inputField.setEnabled(true);
                        inputField.requestFocus();
                        startTime = System.currentTimeMillis();
                        raceFinished = false;
                    });
                } else if (message.startsWith("PROGRESS:")) {
                    String progressData = message.substring(9);
                    racers.clear();
                    for (String entry : progressData.split(";")) {
                        if (!entry.isEmpty()) {
                            String[] parts = entry.split(",");
                            racers.put(parts[0], Integer.parseInt(parts[1]));
                        }
                    }
                    SwingUtilities.invokeLater(raceTrackPanel::repaint);
                } else if (message.startsWith("GAME_START")) {
                    SwingUtilities.invokeLater(() -> {
                        countdownLabel.setText("Race started! Type the sentence below:");
                        inputField.setEnabled(true);
                        inputField.requestFocus();
                        raceFinished = false;
                        wpmResults.clear();
                    });
                } else if (message.startsWith("FINISH:")) {
                    String[] parts = message.substring(7).split(",");
                    String name = parts[0];
                    int wpm = Integer.parseInt(parts[1]);
                    wpmResults.put(name, wpm);
                    SwingUtilities.invokeLater(raceTrackPanel::repaint);
                } else if (message.startsWith("GAME_END:")) {
                    String winner = message.substring(9);
                    raceFinished = true;
                    SwingUtilities.invokeLater(() -> {
                        countdownLabel.setText("Race finished! Winner: " + winner + 
                            " | Your WPM: " + wpmResults.getOrDefault(clientName, 0));
                        inputField.setEnabled(false);
                        raceTrackPanel.repaint();
                    });
                } else if (message.startsWith("COUNTDOWN:")) {
                    int seconds = Integer.parseInt(message.substring(10));
                    SwingUtilities.invokeLater(() -> {
                        countdownLabel.setText("Game starting in " + seconds + " seconds...");
                    });
                }
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    this, 
                    "Disconnected from server", 
                    "Connection Lost", 
                    JOptionPane.WARNING_MESSAGE
                );
                System.exit(0);
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Use default look and feel
            }
            
            TypeRaceClient client = new TypeRaceClient();
        });
    }
}