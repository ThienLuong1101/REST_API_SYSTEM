import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MultiContentServers {
    private static final String BASE_FILE_PATH = "./WeatherData/weather"; // Base file path for weather files
    private static final String SERVER_URL = "http://localhost:4567";
    private static final int MAX_SERVERS = 30; // Maximum number of unique content servers
    private static final int MIN_SLEEP_TIME = 50; // Minimum sleep time (5 seconds)
    private static final int MAX_SLEEP_TIME = 600; // Maximum sleep time (10 seconds)

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(MAX_SERVERS); // Using a fixed thread pool for the servers

        for (int i = 1; i <= MAX_SERVERS; i++) {
            String filePath = BASE_FILE_PATH + i + ".txt"; // Generate file path for each server
            String threadId = String.valueOf(i); // Unique ID for the server

            // Submit a new content server instance
            executor.submit(() -> {
                ContentServer contentServer = new ContentServer(); // Create a new instance for each thread
                while (true) {
                    try {
                        // Simulate sending a PUT request
                        System.out.println("Thread ID " + threadId + " sending PUT request with file " + filePath);
                        contentServer.run(SERVER_URL, filePath, threadId);

                        // Sleep for a random duration
                        int sleepDuration = MIN_SLEEP_TIME + new Random().nextInt(MAX_SLEEP_TIME - MIN_SLEEP_TIME + 1); // 5000 ms + [0-5000] ms
                        Thread.sleep(sleepDuration);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        // Shutdown the executor after 30 minutes for cleanup
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
