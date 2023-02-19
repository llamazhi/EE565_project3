import java.util.regex.Pattern;
import java.net.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NodeInfo {
    private InetAddress host;
    private String hostname;
    public Integer port;
    public Integer rate;
    private String uuid;
    private String name;
    private int frontendPort;
    private int backendPort;
    private String contentDir;
    private int peerCount;
    private double metric;

    private ArrayList<NodeInfo> neighbors = new ArrayList<NodeInfo>();

    public NodeInfo(String hostname, Integer port, Integer rate) throws IOException {
        this.host = InetAddress.getByName(hostname);
        this.hostname = hostname;
        this.port = port;
        this.rate = rate;
    }

    public String getUUID() {
        return this.uuid;
    }

    public InetAddress getHost() {
        return this.host;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFrontendPort() {
        return this.frontendPort;
    }

    public void setFrontendPort(int frontendPort) {
        this.frontendPort = frontendPort;
    }

    public int getBackendPort() {
        return this.backendPort;
    }

    public void setBackendPort(int backendPort) {
        this.backendPort = backendPort;
    }

    public String getContentDir() {
        return this.contentDir;
    }

    public void setContentDir(String contentDir) {
        this.contentDir = contentDir;
    }

    public int getPeerCount() {
        return this.peerCount;
    }

    public void setPeerCount(int peerCount) {
        this.peerCount = peerCount;
    }

    public void setUUID(String uuid) {
        this.uuid = uuid;
    }

    public void setHost(String hostname) throws IOException {
        this.hostname = hostname;
        this.host = InetAddress.getByName(hostname);
    }

    public String getHostname() {
        return this.hostname;
    }

    public void setMetric(double metric) throws IOException {
        this.metric = metric;
    }

    public double getMetric() {
        return this.metric;
    }

    public void setNeighbor(NodeInfo info) {
        this.neighbors.add(info);
    }

    public ArrayList<NodeInfo> getNeighbors() {
        return this.neighbors;
    }

    public NodeInfo() {
        this.neighbors = new ArrayList<>();
    }

    // take in a query of values and set fields with corresponding values
    public static NodeInfo parseNeighbor(HashMap<String, String> values) throws IOException {
        NodeInfo neighborConfig = new NodeInfo();
        // peerConfig.setName(name);
        neighborConfig.setUUID(values.get("uuid"));
        neighborConfig.setHost(values.get("host"));
        neighborConfig.setFrontendPort(Integer.parseInt(values.get("frontend")));
        neighborConfig.setBackendPort(Integer.parseInt(values.get("backend")));
        neighborConfig.setMetric(Double.parseDouble(values.get("metric")));
        return neighborConfig;
    }

    // take in a name and a string of info
    // set the fields with corresponding info
    public static NodeInfo parseNeighbor(String name, String info) throws IOException {
        NodeInfo neighborConfig = new NodeInfo();
        String[] values = info.split(",");
        // System.out.println(values.toString());
        neighborConfig.setName(name);
        neighborConfig.setUUID(values[0].trim());
        neighborConfig.setHost(values[1].trim());
        neighborConfig.setFrontendPort(Integer.parseInt(values[2].trim()));
        neighborConfig.setBackendPort(Integer.parseInt(values[3].trim()));
        neighborConfig.setMetric(Double.parseDouble(values[4].trim()));
        return neighborConfig;
    }

    public static NodeInfo parseConfigFile(String filepath) throws IOException {
        Map<String, String> configMap = new HashMap<>();

        NodeInfo config = new NodeInfo();
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    String[] keyValue = line.split("=");
                    if (keyValue.length == 2) {
                        configMap.put(keyValue[0].trim(), keyValue[1].trim());
                    }
                }
            }
        }

        config.setUUID(configMap.get("uuid"));
        config.setName(configMap.get("name"));
        config.setFrontendPort(Integer.parseInt(configMap.get("frontend_port")));
        config.setBackendPort(Integer.parseInt(configMap.get("backend_port")));
        config.setContentDir(configMap.get("content_dir"));
        // config.setPeerCount(Integer.parseInt(configMap.get("peer_count")));
        config.setHost("localhost");

        // generate a new uuid and write back to config file if no uuid assigned
        if (!configMap.containsKey("uuid")) {
            String uuid = UUID.randomUUID().toString();
            config.setUUID(uuid);
            BufferedWriter output = new BufferedWriter(new FileWriter(new File(filepath), true));
            output.newLine();
            output.write("uuid = " + uuid);
            output.close();
        }
        config.setMetric(0);

        Pattern pattern = Pattern.compile("peer_[0-9]*");
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (pattern.matcher(key).matches()) {
                config.setNeighbor(NodeInfo.parseNeighbor(key, value));
            }
        }

        return config;
    }

    public String toNeighborFormat() {
        return this.getUUID() + ","
                + this.getHostname() + ","
                + this.getFrontendPort() + ","
                + this.getBackendPort() + ","
                + this.getMetric();
    }

    @Override
    public String toString() {
        return "RemoteServerInfo{"
                + "uuid='" + uuid + '\''
                + ", name='" + name + '\''
                + ", metric='" + metric + '\''
                + '}';
    }
}
