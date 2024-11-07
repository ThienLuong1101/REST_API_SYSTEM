import java.io.*;
import java.net.*;
import java.util.HashMap; // Import HashMap
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 4567;
    private static final int MIN_DELAY = 1000; // Minimum delay in milliseconds (1 second)
    private static final int MAX_DELAY = 2000; // Maximum delay in milliseconds (2 seconds)
    private static final int CLIENT_COUNT = 1; // Number of clients to simulate

    // Cache to store weather data (OPTIONAL)
    private static HashMap<String, String> cache = new HashMap<>(); 

    private static long lamportClock = 0;

    public static void main(String[] args) {
    
        ExecutorService executor = Executors.newFixedThreadPool(CLIENT_COUNT);

        
        for (int i = 1; i <= CLIENT_COUNT; i++) {
            final int clientId = i;
            executor.execute(() -> {
                while (true) { 
                    simulateClient(clientId);
                }
            });
        }

        // Shutdown the executor after the tasks are complete
        executor.shutdown();
    }

    // Simulate a client sending a request
    public static void simulateClient(int clientId) {
        try {
            // Generate a random delay between MIN_DELAY and MAX_DELAY
            Random random = new Random();
            int delay = MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY + 1);

            Thread.sleep(delay);

            // set id 20
            int stationId = 20;

            System.out.println("Client " + clientId + " sending request for station " + stationId);

            // Check if data is already in cache
            if (cache.containsKey("station" + stationId)) {
                // If data is in cache, retrieve and print it without sending a request
                System.out.println("Client " + clientId + " retrieved cached data for station " + stationId + ": " + cache.get("station" + stationId));
                return; // Exit if data is cached
            }

            // Send the request to the server
            sendRequest(stationId);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Method to send the GET request to the server
    public static void sendRequest(int stationId) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Display the current Lamport clock
            System.out.println("Current Lamport Clock before sending request: " + lamportClock);

            // Build the GET request
            StringBuilder request = new StringBuilder("GET /weather HTTP/1.1\r\n");
            request.append("Host: ").append(SERVER_ADDRESS).append(":").append(SERVER_PORT).append("\r\n");
            request.append("Station-Id: ").append(stationId).append("\r\n");
            request.append("Connection: close\r\n");
            request.append("\r\n"); // End of headers

            // Increment the Lamport clock when sending the request
            lamportClock++;
            out.print(request);
            out.flush();

            // Read the response
            StringBuilder responseBuilder = new StringBuilder();
            String responseLine;

            while ((responseLine = in.readLine()) != null) {
                // Check for Lamport-Clock in the response header
                if (responseLine.startsWith("Lamport-Clock: ")) {
                    long serverLamportClock = Long.parseLong(responseLine.split(": ")[1]); // Extract the Lamport clock value
                    // Update the client Lamport clock based on the server's response
                    lamportClock = Math.max(lamportClock, serverLamportClock) + 1; // Increment based on max value
                } else {
                    // Store the response body
                    responseBuilder.append(responseLine).append("\n");
                }
            }

            // Print the response body and the updated Lamport clock
            System.out.println("Response from server:\n" + responseBuilder.toString().trim());
            System.out.println("Updated Lamport Clock: " + lamportClock);

            // Cache the response
            cache.put("station" + stationId, responseBuilder.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
