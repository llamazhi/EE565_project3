import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class ConfigParser {
    // TODO: this class parse the file node.conf
    // If the uuid option does not exist, you must generate a new UUID (see 3.2) and
    // update the configuration file (either node.conf or those specified in –c
    // parameter) with the generated identifier.

    private static HashMap<String, String> config = new HashMap<String, String>();

    public ConfigParser(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader("node.conf"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    int index = line.indexOf("=");
                    if (index > 0) {
                        String key = line.substring(0, index).trim();
                        String value = line.substring(index + 1).trim();
                        config.put(key, value);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading config file: " + e.getMessage());
        }
    }

    public String getConfig(String key) {
        return config.get(key);
    }

    public void setConfig(String key, String value) {
        config.put(key, value);
    }

    // check if the config file contatins uuid
    public boolean hasUUID() {
        return (config.containsKey("uuid"));
    }

    // generate a new unique uuid for the node
    public void generateNewUUID() {
        UUID uuid = UUID.randomUUID();
        String uuidStr = uuid.toString();
        config.put("uuid", uuidStr);
    }
}
