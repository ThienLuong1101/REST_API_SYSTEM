import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class AggregationServer {
    private static final int PORT = 4567;
    private static final long EXPIRATION_TIME_MS = 30000; // 30 seconds
    private static final int MAX_ENTRIES = 20; // Maximum of 20 entries
    private static final String BACKUP_FILE = "aggregation_server_state.ser"; // Backup file for recovery
    private static final int MAX_REQUESTS_PER_SECOND = 10; 
    private static Map<String, WeatherData> dataStore = new ConcurrentHashMap<>();
    private static Map<String, Long> lastContactTime = new ConcurrentHashMap<>();
    private static AtomicLong lamportClock = new AtomicLong(0);
    private static LinkedList<String> dataOrder = new LinkedList<>(); // To track the order of CS
    private static Set<String> initializedServers = ConcurrentHashMap.newKeySet(); // To track initialized content servers
    private static final ExecutorService executor = Executors.newFixedThreadPool(10); // Thread pool for handling requests
    private static final RateLimiter rateLimiter = new RateLimiter(MAX_REQUESTS_PER_SECOND); //for socket limit requests

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PORT;

        // Load server state from backup
        loadState();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aggregation Server is running on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Save server state before shutting down
            saveState();
        }
    }

      /**
     * Handles incoming client requests (GET or PUT).
     * 
     * @param clientSocket The socket connection to the client.
     * 
     * The method checks for request type (PUT/GET), validates the request format,
     * and invokes appropriate handlers. Special case for handling rate limits.
     */
    private static void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
    
            String clientId = clientSocket.getRemoteSocketAddress().toString(); // Use client's address as ID
            
            if (!rateLimiter.isAllowed(clientId)) {
                writer.println("HTTP/1.1 429 Too Many Requests");
                writer.println("Content-Type: text/plain");
                writer.println();
                writer.println("Rate limit exceeded. Please slow down.");
                return;
            }
    
            String requestLine = reader.readLine();
            if (requestLine != null) {
                // Simulate a timeout by making the thread sleep for 10 seconds before processing (OPTIONAL)
                // try {
                //     System.out.println("Simulating delay for timeout scenario...");
                //     Thread.sleep(10000); // Sleep for 10 seconds to simulate timeout
                // } catch (InterruptedException e) {
                //     Thread.currentThread().interrupt();
                //     System.err.println("Sleep interrupted: " + e.getMessage());
                // }
    
                // Process the request after the delay
                if (requestLine.startsWith("PUT")) {
                    handlePutRequest(reader, writer);
                } else if (requestLine.startsWith("GET")) {
                    handleGetRequest(reader, writer);
                } else {
                    writer.println("HTTP/1.1 400 Bad Request");
                }
            }
    
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

      /**
     * Handles PUT requests to store weather data and synchronize the Lamport clock.
     * 
     * @param reader The BufferedReader for reading client input.
     * @param writer The PrintWriter for sending responses to the client.
     * 
     * The method extracts headers, validates the Content-Server-Id, processes the JSON
     * payload, and updates the internal data store and clock.
     * Special cases:
     * - If Content-Server-Id is missing, returns 400.
     * - If no content is provided, returns 204.
     */
    private static void handlePutRequest(BufferedReader reader, PrintWriter writer) throws IOException {
        String line;
        Map<String, String> headers = new HashMap<>();
        StringBuilder requestBody = new StringBuilder();
    
        // Read headers
        while (!(line = reader.readLine()).isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                headers.put(line.substring(0, colonIndex).trim(), line.substring(colonIndex + 1).trim());
            }
        }
    
        // Get Content-Length from headers
        String contentLengthHeader = headers.get("Content-Length");
        int contentLength = contentLengthHeader != null ? Integer.parseInt(contentLengthHeader) : 0;
        
        
        // Read request body using Content-Length
        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            int charsRead = reader.read(buffer);
            requestBody.append(buffer, 0, charsRead);
        }
    
        String jsonPayload = requestBody.toString();
        String contentType = headers.get("Content-Type");
        String contentServerId = headers.get("Content-Server-Id"); // Get contentServerId from headers
       
         // If no content is sent, return 204 No Content
         if (jsonPayload.isEmpty()) {
            writer.println("HTTP/1.1 204 No Content");
            return;
        }
        if (contentServerId == null) {
            writer.println("HTTP/1.1 400 Bad Request");
            writer.println("Content-Type: text/plain");
            writer.println();
            writer.println("Missing Content-Server-Id header");
            return;
        }


        if ("application/json".equals(contentType)) {
            try {
                processPutRequest(jsonPayload, contentServerId);
                long timestamp = lamportClock.get();
                if (initializedServers.add(contentServerId)) {
                    writer.println("HTTP/1.1 201 Created");
                } else {
                    writer.println("HTTP/1.1 200 OK");
                }
                writer.println("Content-Type: text/plain");
                writer.println("Lamport-Clock: " + timestamp);
                writer.println();
            } catch (Exception e) {
                writer.println("HTTP/1.1 500 Internal Server Error");
                writer.println("Content-Type: text/plain");
                writer.println();
                writer.println("Invalid JSON data received");
            }
        } else {
            writer.println("HTTP/1.1 400 Bad Request");
        }

        // Update last contact time
        lastContactTime.put(contentServerId, System.currentTimeMillis());
    

        // Clean up expired entries
        cleanupExpiredEntries();

        // Save server state after updating data
        saveState();
    }

    

     /**
     * Processes the weather data received in a PUT request, stores it,
     * and manages the order of content servers.
     * 
     * @param jsonPayload The weather data in JSON format.
     * @param contentServerId The ID of the content server providing the data.
     * 
     * Special case: If the data store has reached the maximum limit, it removes the oldest entry.
     */
    private static void processPutRequest(String jsonPayload, String contentServerId) throws Exception {
        long timestamp = lamportClock.incrementAndGet();

        // Update the dataStore with new weather data, overwriting if it exists
        dataStore.put(contentServerId, new WeatherData(jsonPayload));

        synchronized (dataOrder) {
            // If the content server already exists, update its position in dataOrder
            if (dataOrder.contains(contentServerId)) {
                dataOrder.remove(contentServerId);
            } else {
                // If the dataStore is full, remove the oldest entry
                if (dataStore.size() >= MAX_ENTRIES) {
                    String oldestServer = dataOrder.pollFirst(); 
                    dataStore.remove(oldestServer);
                    lastContactTime.remove(oldestServer);
                    initializedServers.remove(oldestServer); 
                    System.out.println("Removed old entry from Content Server ID: " + oldestServer);
                }
            }

            // Add the updated content server ID to the end of the order list
            dataOrder.addLast(contentServerId);
        }

        System.out.println("PUT request processed successfully. Content Server ID: " + contentServerId + ", Lamport Timestamp: " + timestamp);
    }

    /**
     * Cleans up entries in the data store that have not been updated for over 30 seconds.
     * 
     * This method iterates through the `lastContactTime` map to check the last update time
     * for each content server. If the time since the last update exceeds 30 seconds,
     * the corresponding entries are removed from the `dataStore`, `lastContactTime`,
     * `dataOrder`, and `initializedServers`.
     */
    private static void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastContactTime.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            String contentServerId = entry.getKey();
            long lastUpdateTime = entry.getValue();

            // Remove the entry if it has been more than 30 seconds since last update
            if (currentTime - lastUpdateTime > EXPIRATION_TIME_MS) {
                iterator.remove();
                dataStore.remove(contentServerId);
                synchronized (dataOrder) {
                    dataOrder.remove(contentServerId); // Remove from order list as well
                }
                initializedServers.remove(contentServerId); // Remove from initialized servers
                System.out.println("Removed expired entry for Content Server ID: " + contentServerId);
            }
        }
    }

    // Loads the server's state from a file after a restart
    @SuppressWarnings("unchecked")
    private static void loadState() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(BACKUP_FILE))) {
            dataStore = (Map<String, WeatherData>) ois.readObject();
            lastContactTime = (Map<String, Long>) ois.readObject();
            lamportClock = (AtomicLong) ois.readObject();
            dataOrder = (LinkedList<String>) ois.readObject();
            initializedServers = (Set<String>) ois.readObject();
            System.out.println("State loaded from file.");
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("No previous state found, starting fresh.");
        }
    }

    // Saves the server's current state to a file
    @SuppressWarnings("unchecked")
    private static void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BACKUP_FILE))) {
            oos.writeObject(dataStore);
            oos.writeObject(lastContactTime);
            oos.writeObject(lamportClock);
            oos.writeObject(dataOrder);
            oos.writeObject(initializedServers);
            
        } catch (IOException e) {
            System.err.println("Error saving state: " + e.getMessage());
        }
    }

    /**
     * Handles incoming GET requests from clients to retrieve weather data.
     *
     * @param reader  The BufferedReader to read the incoming request.
     * @param writer  The PrintWriter to send the response back to the client.
     * @throws IOException If an I/O error occurs while reading from the client or writing to the response.
     *
     * - If the client includes a "Station-Id" header, the method attempts to retrieve the corresponding weather data.
     * - If no "Station-Id" is provided, the latest weather data based on the order of requests is returned.
     * - If no data is available for the requested Station ID or if the data store is empty, appropriate HTTP responses are sent.
     */
    private static void handleGetRequest(BufferedReader reader, PrintWriter writer) throws IOException {
        // Read the GET request and extract station ID, if provided
        String line;
        String stationId = null;
    
        // Read headers to check for Station-Id (if provided by the client)
        while (!(line = reader.readLine()).isEmpty()) {
            if (line.startsWith("Station-Id:")) {
                stationId = line.split(":")[1].trim();
            }
        }
    
        // Increment the Lamport clock upon receiving a request
        lamportClock.incrementAndGet();
    
        long lamportTimestamp = lamportClock.get(); // Capture the current Lamport clock value
    
        // Print out the GET request details
        if (stationId != null && !stationId.isEmpty()) {
            System.out.println("GET request processed successfully. Station ID: " + stationId + ", Lamport Timestamp: " + lamportTimestamp);
        } else {
            System.out.println("GET request processed successfully. No Station ID provided, Lamport Timestamp: " + lamportTimestamp);
        }
    
        // If a station ID was provided, search for it in the data store
        if (stationId != null && !stationId.isEmpty()) {
            WeatherData weatherData = dataStore.get(stationId);
            
        
          
            if (weatherData != null) {

                //(OPTIONAL) update the station id order based on get requests
                synchronized (dataOrder) {
                    dataOrder.remove(stationId);
                    dataOrder.addLast(stationId);
                }

                writer.println("HTTP/1.1 200 OK");
                writer.println("Content-Type: application/json");
                writer.println("Lamport-Clock: " + lamportTimestamp); // Include Lamport clock in the response
                writer.println();
                writer.println(weatherData.getJsonPayload()); // Send the weather data in JSON format
            } else {
                writer.println("HTTP/1.1 404 Not Found");
                writer.println("Content-Type: text/plain");
                writer.println("Lamport-Clock: " + lamportTimestamp); // Include Lamport clock in the response
                writer.println();
                writer.println("Station ID " + stationId + " not found");
            }
        } 
        // If no station ID was provided, return the latest data
        else {
            synchronized (dataOrder) {
                if (!dataOrder.isEmpty()) {
                    String latestStationId = dataOrder.getLast(); // Get the latest station ID
                    WeatherData latestData = dataStore.get(latestStationId);
    
                    writer.println("HTTP/1.1 200 OK");
                    writer.println("Content-Type: application/json");
                    writer.println("Lamport-Clock: " + lamportTimestamp); // Include Lamport clock in the response
                    writer.println();
                    writer.println(latestData.getJsonPayload()); // Send the latest weather data
                } else {
                    writer.println("HTTP/1.1 204 No Content");
                    writer.println("Content-Type: text/plain");
                    writer.println("Lamport-Clock: " + lamportTimestamp); // Include Lamport clock in the response
                    writer.println();
                    writer.println("No weather data available.");
                }
            }
        }
    }
    
    
    

    // Helper class to store weather data
    private static class WeatherData implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String jsonPayload;

        public WeatherData(String jsonPayload) {
            this.jsonPayload = jsonPayload;
        }

        public String getJsonPayload() {
            return jsonPayload;
        }
    }
}
