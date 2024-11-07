import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiGETClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 4567;
    private static final int MIN_DELAY = 50; // Minimum delay in milliseconds (5 seconds)
    private static final int MAX_DELAY = 100; // Maximum delay in milliseconds (10 seconds)
    private static final int CLIENT_COUNT = 10; 
    private static final int NUM_STATION_ID = 30;

    public static void main(String[] args) {
        // ExecutorService to manage the threads for the clients
        ExecutorService executor = Executors.newFixedThreadPool(CLIENT_COUNT);

        // Simulate CLIENT_COUNT clients
        for (int i = 1; i <= CLIENT_COUNT; i++) {
            final int clientId = i;
            executor.execute(() -> {
                long lamportClock = 0; 
                while (true) { 
                    simulateClient(clientId, lamportClock);
                }
            });
        }
    }

    // Simulate a client sending a request
    public static void simulateClient(int clientId, long lamportClock) {
        try {
     
            Random random = new Random();
            int delay = MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY + 1);

         
            Thread.sleep(delay);

          
            int stationId = random.nextInt(NUM_STATION_ID) + 1;

           
            System.out.println("Client " + clientId + " sending request for station " + stationId);
            lamportClock = sendRequest(stationId, lamportClock);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Method to send the GET request to the server
    public static long sendRequest(int stationId, long lamportClock) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

           
            lamportClock++;
           
            System.out.println("Client Lamport Clock before sending request: " + lamportClock);

        
            StringBuilder request = new StringBuilder("GET /weather HTTP/1.1\r\n");
            request.append("Host: ").append(SERVER_ADDRESS).append(":").append(SERVER_PORT).append("\r\n");
            request.append("Station-Id: ").append(stationId).append("\r\n");
            request.append("Lamport-Clock: ").append(lamportClock).append("\r\n"); // Send client's Lamport clock
            request.append("Connection: close\r\n");
            request.append("\r\n"); // End of headers

            out.print(request);
            out.flush();

          
            StringBuilder responseBuilder = new StringBuilder();
            String responseLine;

            while ((responseLine = in.readLine()) != null) {
              
                if (responseLine.startsWith("Lamport-Clock: ")) {
                    long serverLamportClock = Long.parseLong(responseLine.split(": ")[1]); 
                
                    lamportClock = Math.max(lamportClock, serverLamportClock) + 1;
                } else {
                    responseBuilder.append(responseLine).append("\n");
                }
            }

           
            System.out.println("Response from server:\n" + responseBuilder.toString().trim());
            System.out.println("Updated Client Lamport Clock: " + lamportClock);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return lamportClock; 
    }
}
