import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class ContentServer {

    // Lamport clock for each instance
    private final AtomicInteger lamportClock;

    // Constructor to initialize Lamport clock
    public ContentServer() {
        this.lamportClock = new AtomicInteger(0);
    }

    /**
     * Main method to create and run a ContentServer instance.
     * 
     * @param args Command-line arguments.
     *             args[0] = server URL (e.g., http://localhost:4567),
     *             args[1] = file path of weather data,
     *             args[2] = thread ID for identifying this instance.
     * 
     * Prints usage instructions if insufficient arguments are provided.
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java ContentServer <server-url> <file-path> <thread-id>");
            return;
        }

        String serverUrl = args[0];
        String filePath = args[1];
        String threadId = args[2];

      
        ContentServer contentServer = new ContentServer();
        contentServer.run(serverUrl, filePath, threadId);
    }

     /**
     * Method to run the ContentServer logic.
     * 
     * Reads weather data from a file, converts it to JSON, and sends it to the
     * server via a PUT request.
     * 
     * @param serverUrl The URL of the aggregation server.
     * @param filePath  The path to the weather data file.
     * @param threadId  Unique ID for this content server thread.
     * 
     * Handles potential IOException and URISyntaxException during execution.
     */
    public void run(String serverUrl, String filePath, String threadId) {
        try {
            Map<String, String> weatherData = readFile(filePath);
            if (!weatherData.containsKey("id")) {
                System.out.println("Error: Missing 'id' field. Rejecting feed.");
                return;
            }

            String jsonPayload = convertToJson(weatherData);
            
            System.out.println("Thread ID " + threadId + " sending PUT request with Lamport Clock: " + lamportClock.get());
            sendPutRequest(serverUrl, jsonPayload, threadId);

        } catch (IOException | URISyntaxException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Reads the weather data from the file and stores it in a map.
     * 
     * @param filePath Path to the file containing weather data.
     * 
     * @return Map with weather data as key-value pairs.
     * 
     * @throws IOException If an error occurs during file reading.
     */
    private Map<String, String> readFile(String filePath) throws IOException {
        Map<String, String> data = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    data.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return data;
    }

     /**
     * Converts the weather data map to a JSON string. (bonus)
     * 
     * @param dataMap The weather data map (key-value pairs).
     * 
     * @return JSON string representation of the weather data.
     */
    private String convertToJson(Map<String, String> dataMap) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        
        int size = dataMap.size();
        int count = 0;
    
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            jsonBuilder.append("  \"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
            count++;
            if (count < size) {
                jsonBuilder.append(",\n");
            }
        }
    
        jsonBuilder.append("\n}");
        return jsonBuilder.toString();
    }
    

    /**
     * Sends an HTTP PUT request to the server with the JSON payload.
     * 
     * @param serverUrl   The server URL.
     * @param jsonPayload The JSON data to send.
     * @param threadId    The thread ID for this server instance.
     * 
     * Updates the Lamport clock based on the server response and handles different
     * HTTP response codes.
     * 
     * @throws IOException        If an I/O error occurs during the HTTP request.
     * @throws URISyntaxException If the server URL is invalid.
     */
    private void sendPutRequest(String serverUrl, String jsonPayload, String threadId)
    throws IOException, URISyntaxException {
        int clockValue = lamportClock.incrementAndGet();

        URI uri = new URI(serverUrl + "/weather.json");
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(jsonPayload.length()));
        connection.setRequestProperty("Lamport-Clock", String.valueOf(clockValue));
        connection.setRequestProperty("Thread-ID", threadId);
        connection.setRequestProperty("Content-Server-Id", threadId);
        connection.setDoOutput(true);


        // Set timeouts
        int connectionTimeout = 5000; // 5 seconds
        int readTimeout = 5000; // 5 seconds
        connection.setConnectTimeout(connectionTimeout);
        connection.setReadTimeout(readTimeout);

        //long startTime = System.currentTimeMillis(); //response time testing optional
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes());
            os.flush();
        }
        
         /**
         * Handles the response from the server (for testing)
         * 
         * Updates the local Lamport clock based on the server's Lamport clock header.
         * 
         * @param connection  The HTTP connection object.
         * @param threadId    The thread ID for logging.
         * @param clockValue  The current Lamport clock value sent in the request.
         * @param successMsg  The success message to display after a successful request.
         */
        int responseCode = connection.getResponseCode();
        long endTime = System.currentTimeMillis();

        //response time testing (optional)
        // long responseTime = endTime - startTime;
        // System.out.println("Response time: " + responseTime + " ms (Thread ID: " + threadId + ")");


        switch (responseCode) {
            case HttpURLConnection.HTTP_OK: // 200
                System.out.println("Data uploaded successfully. Response Code: " + responseCode +
                        " (Thread ID: " + threadId + ", Lamport Clock Sent: " + clockValue + ")");
                
                String serverClockHeader = connection.getHeaderField("Lamport-Clock");
                if (serverClockHeader != null) {
                    int serverClock = Integer.parseInt(serverClockHeader);
                    lamportClock.set(Math.max(clockValue, serverClock) + 1);
                    System.out.println("Updated Lamport Clock to: " + lamportClock.get() + " (Thread ID: " + threadId + ")");
                } else {
                    System.out.println("No Lamport Clock header received (Thread ID: " + threadId + ")");
                }
                break;
        
            case HttpURLConnection.HTTP_CREATED: // 201
                System.out.println("Data uploaded successfully and resource created. Response Code: " + responseCode +
                        " (Thread ID: " + threadId + ", Lamport Clock Sent: " + clockValue + ")");
                
                serverClockHeader = connection.getHeaderField("Lamport-Clock");
                if (serverClockHeader != null) {
                    int serverClock = Integer.parseInt(serverClockHeader);
                    lamportClock.set(Math.max(clockValue, serverClock) + 1);
                    System.out.println("Updated Lamport Clock to: " + lamportClock.get() + " (Thread ID: " + threadId + ")");
                } else {
                    System.out.println("No Lamport Clock header received (Thread ID: " + threadId + ")");
                }
                break;
        
            case HttpURLConnection.HTTP_NO_CONTENT: // 204
                System.out.println("No content to process. Response Code: " + responseCode + " (Thread ID: " + threadId + ")");
                break;
        
            case HttpURLConnection.HTTP_BAD_REQUEST: // 400
                System.out.println("Bad Request. Response Code: " + responseCode + " (Thread ID: " + threadId + ")");
                break;
        
            case HttpURLConnection.HTTP_INTERNAL_ERROR: // 500
                System.out.println("Internal Server Error. Response Code: " + responseCode + " (Thread ID: " + threadId + ")");
                break;
        
            default:
                System.out.println("Failed to upload data. Response Code: " + responseCode + " (Thread ID: " + threadId + ")");
                break;
        }
        
        
        connection.disconnect();
    }
}
