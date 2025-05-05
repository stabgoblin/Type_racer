import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TypeRaceServer {
    private static final int PORT = 5555;
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 4;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final List<String> SENTENCES = Arrays.asList(
        "The quick brown fox jumps over the lazy dog while the sun shines brightly in the clear blue sky above them all",
        "Programming computers is incredibly rewarding when you finally solve that tricky bug after hours of debugging",
        "Java is a powerful object oriented language that enables developers to create robust portable applications",
        "Typing quickly and accurately is an essential skill for programmers who want to be productive in their work",
        "Practice makes perfect when it comes to improving your typing speed and reducing errors in your code",
        "To be or not to be that is the question whether it is nobler in the mind to suffer the slings and arrows of outrageous fortune",
        "The early bird catches the worm but the second mouse gets the cheese in this strange paradoxical world we live in today",
        "Artificial intelligence and machine learning are transforming how we interact with technology in our daily lives forever"
    );

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private List<ClientHandler> clients;
    private String currentSentence;
    private boolean gameRunning;
    private Map<String, Integer> clientProgress;
    private Map<String, Long> finishTimes;
    private Map<String, Integer> wpmResults;
    private long gameStartTime;
    private ScheduledExecutorService gameScheduler;
    private int countdown;

    public TypeRaceServer() {
        executorService = Executors.newCachedThreadPool();
        gameScheduler = Executors.newScheduledThreadPool(1);
        clients = new CopyOnWriteArrayList<>();
        clientProgress = new ConcurrentHashMap<>();
        finishTimes = new ConcurrentHashMap<>();
        wpmResults = new ConcurrentHashMap<>();
        gameRunning = false;
        countdown = COUNTDOWN_SECONDS;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);
            System.out.println("Waiting for players to connect...");

            while (true) {
                Socket socket = serverSocket.accept();
                if (clients.size() >= MAX_PLAYERS) {
                    PrintWriter tempOut = new PrintWriter(socket.getOutputStream(), true);
                    tempOut.println("SERVER_FULL");
                    socket.close();
                    continue;
                }

                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                executorService.execute(clientHandler);
                
                System.out.println("Player connected. Total players: " + clients.size());
                
                // Start countdown when enough players join
                if (clients.size() >= MIN_PLAYERS && !gameRunning) {
                    startCountdown();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void startCountdown() {
        System.out.println("Starting countdown for game...");
        gameRunning = true;
        
        gameScheduler.scheduleAtFixedRate(() -> {
            if (countdown > 0) {
                broadcastMessage("COUNTDOWN:" + countdown);
                System.out.println("Countdown: " + countdown);
                countdown--;
            } else {
                startGame();
                gameScheduler.shutdown();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private synchronized void startGame() {
        System.out.println("Game starting with " + clients.size() + " players");
        
        // Select a random sentence
        Random random = new Random();
        currentSentence = SENTENCES.get(random.nextInt(SENTENCES.size()));
        
        clientProgress.clear();
        finishTimes.clear();
        wpmResults.clear();

        for (ClientHandler client : clients) {
            clientProgress.put(client.getClientName(), 0);
        }

        gameStartTime = System.currentTimeMillis();
        broadcastMessage("GAME_START");
        broadcastMessage("SENTENCE:" + currentSentence);
    }

    private synchronized void handlePlayerProgress(String clientName, int progress) {
        clientProgress.put(clientName, progress);
        broadcastProgress();

        if (progress >= currentSentence.length() && !finishTimes.containsKey(clientName)) {
            handlePlayerFinish(clientName);
        }
    }

    private synchronized void handlePlayerFinish(String clientName) {
        long finishTime = System.currentTimeMillis();
        finishTimes.put(clientName, finishTime);
        
        // Calculate WPM (words per minute)
        double minutes = (finishTime - gameStartTime) / 60000.0;
        int wordCount = currentSentence.split(" ").length;
        int wpm = (int) (wordCount / minutes);
        wpmResults.put(clientName, wpm);
        
        System.out.println(clientName + " finished with " + wpm + " WPM");
        broadcastMessage("FINISH:" + clientName + "," + wpm);
        
        // Check if all players have finished
        if (finishTimes.size() == clients.size()) {
            endGame();
        }
    }

    private synchronized void endGame() {
        String winner = determineWinner();
        System.out.println("Game ended. Winner: " + winner);
        broadcastMessage("GAME_END:" + winner);
        resetGame();
    }

    private String determineWinner() {
        return finishTimes.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("No winner");
    }

    private void resetGame() {
        gameRunning = false;
        countdown = COUNTDOWN_SECONDS;
        gameScheduler = Executors.newScheduledThreadPool(1);
        
        // Reset all clients for new game
        clients.forEach(client -> client.resetForNewGame());
    }

    private void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    private void broadcastProgress() {
        StringBuilder progressMsg = new StringBuilder("PROGRESS:");
        for (Map.Entry<String, Integer> entry : clientProgress.entrySet()) {
            progressMsg.append(entry.getKey()).append(",").append(entry.getValue()).append(";");
        }
        broadcastMessage(progressMsg.toString());
    }

    private void shutdown() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            executorService.shutdown();
            gameScheduler.shutdown();
            System.out.println("Server shutdown complete");
        } catch (IOException e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;
        private boolean readyForNewGame;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.readyForNewGame = true;
        }

        public String getClientName() {
            return clientName;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void resetForNewGame() {
            this.readyForNewGame = true;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Get client name
                clientName = in.readLine();
                System.out.println(clientName + " connected from " + socket.getInetAddress());
                clientProgress.put(clientName, 0);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith("PROGRESS:")) {
                        if (!gameRunning) continue;
                        
                        int progress = Integer.parseInt(inputLine.substring(9));
                        handlePlayerProgress(clientName, progress);
                    }
                }
            } catch (IOException e) {
                System.out.println(clientName + " disconnected: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void cleanup() {
            try {
                clients.remove(this);
                clientProgress.remove(clientName);
                finishTimes.remove(clientName);
                
                System.out.println(clientName + " removed from game");
                
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                
                // If game is running and players drop below minimum, end game
                if (gameRunning && clients.size() < MIN_PLAYERS) {
                    endGame();
                }
            } catch (IOException e) {
                System.err.println("Error cleaning up client: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new TypeRaceServer().start();
    }
}