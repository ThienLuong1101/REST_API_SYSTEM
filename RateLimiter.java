import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiter is a utility class that restricts the number of requests 
 * from a client within a specified time window.
 */
public class RateLimiter {
    private final int maxRequests; 
    private final long timeWindowMs; 
    private final Map<String, ClientRequestData> clientDataMap = new ConcurrentHashMap<>(); 

    /**
     * Constructs a RateLimiter with a specified maximum number of requests.
     *
     * @param maxRequests The maximum number of requests allowed within the time window.
     */
    public RateLimiter(int maxRequests) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = 1000; // 1 second time window
    }

    /**
     * Checks if a request from the specified client is allowed based on the rate limit.
     *
     * @param clientId The unique identifier of the client making the request.
     * @return true if the request is allowed; false if the rate limit has been exceeded.
     *
     * Special Cases:
     * - If the client has not made any requests yet, it is automatically allowed.
     * - Requests are counted only within the defined time window.
     */
    public boolean isAllowed(String clientId) {
        long currentTime = System.currentTimeMillis(); 
        clientDataMap.putIfAbsent(clientId, new ClientRequestData()); 
        ClientRequestData clientData = clientDataMap.get(clientId);

        synchronized (clientData) { 
            // Reset the request count if the time window has expired
            if (currentTime - clientData.startTime >= timeWindowMs) {
                clientData.requestCount = 0; 
                clientData.startTime = currentTime; 
            }

            // Allow the request if under the max requests threshold
            if (clientData.requestCount < maxRequests) {
                clientData.requestCount++; 
                return true; 
            } else {
                return false; // Rate limit exceeded
            }
        }
    }

    /**
     * ClientRequestData is a private static inner class that holds
     * the request data for each client.
     */
    private static class ClientRequestData {
        long startTime = System.currentTimeMillis(); 
        int requestCount = 0;
    }
}
