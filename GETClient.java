import java.io.*;
import java.net.*;
import java.util.HashMap;

public class GETClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 4567; 
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 10000; // 10 seconds

    // Cache to store weather data
    private static HashMap<String, String> cache = new HashMap<>();

    // Initialize the Lamport clock
    private static long lamportClock = 0;

    public static void main(String[] args) {
        String stationId = null;

        // Check if station ID is provided as an argument
        if (args.length > 0) {
            stationId = args[0];
        }

        try (Socket socket = new Socket()) {
            // Set connection timeout
            socket.connect(new InetSocketAddress(SERVER_ADDRESS, SERVER_PORT), CONNECTION_TIMEOUT);
            // Set read timeout
            socket.setSoTimeout(READ_TIMEOUT);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Display the current Lamport clock
                System.out.println("Current Lamport Clock before sending request: " + lamportClock);

                // Build the GET request
                StringBuilder request = new StringBuilder("GET /weather HTTP/1.1\r\n");
                request.append("Host: ").append(SERVER_ADDRESS).append(":").append(SERVER_PORT).append("\r\n");
                // Adding the station ID header if provided
                if (stationId != null) {
                    request.append("Station-Id: ").append(stationId).append("\r\n");
                }
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
                    if (responseLine.startsWith("Lamport-Clock: ")) {
                        long serverLamportClock = Long.parseLong(responseLine.split(": ")[1]);
                        lamportClock = Math.max(lamportClock, serverLamportClock) + 1; 
                    } else {
                        responseBuilder.append(responseLine).append("\n");
                    }
                }

                System.out.println("Response from server:\n" + responseBuilder.toString().trim());
                System.out.println("Updated Lamport Clock: " + lamportClock);

                // OPTIONAL: Implement caching
                // if (stationId != null) {
                //     cache.put(stationId, responseBuilder.toString()); // Cache the response
                // }

            } catch (SocketTimeoutException e) {
                System.out.println("Read timeout occurred. The server took too long to respond.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Connection timeout occurred. Could not connect to the server.");
        } catch (ConnectException e) {
            System.out.println("Could not connect to the server.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
