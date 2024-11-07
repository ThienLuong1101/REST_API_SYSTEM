import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class WeatherFileGenerator {

    private static final String FILE_PATH = "Weather";  // Define your path here

    public static void main(String[] args) {
        try {
            generateWeatherFiles();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateWeatherFiles() throws IOException {
        Set<String> stateSet = new HashSet<>();
        
        // Loop to generate 30 unique weather files
        for (int i = 1; i <= 30; i++) {
            String id = String.format("IDS609%02d", i);  // Unique ID for each file, padded with zeros
            String state = generateUniqueState(stateSet); // Ensure each state is unique
            
            StringBuilder content = new StringBuilder();
            content.append("id:").append(id).append("\n")
                   .append("name:Adelaide (West Terrace / ngayirdapira)\n")
                   .append("state:").append(state).append("\n")
                   .append("time_zone:CST\n")
                   .append("lat:-34.9\n")
                   .append("lon:138.6\n")
                   .append("local_date_time:15/04:00pm\n")
                   .append("local_date_time_full:20230715160000\n")
                   .append("air_temp:13.3\n")
                   .append("apparent_t:9.5\n")
                   .append("cloud:Partly cloudy\n")
                   .append("dewpt:5.7\n")
                   .append("press:1023.9\n")
                   .append("rel_hum:60\n")
                   .append("wind_dir:S\n")
                   .append("wind_spd_kmh:15\n")
                   .append("wind_spd_kt:8\n");

            // Write to file
            writeFile(content.toString(), FILE_PATH + i + ".txt");
        }
    }

    // Generate a unique state that hasn't been used yet
    private static String generateUniqueState(Set<String> stateSet) {
        String state;
        do {
            state = "SG" + (int) (Math.random() * 100);  // Random state code (SG followed by random number)
        } while (stateSet.contains(state)); // Ensure state is unique

        stateSet.add(state); // Add unique state to the set
        return state;
    }

    // Write the file content to a .txt file
    private static void writeFile(String content, String filePath) throws IOException {
        FileWriter writer = new FileWriter(filePath);
        writer.write(content);
        writer.close();
        System.out.println("Generated file: " + filePath);
    }
}
