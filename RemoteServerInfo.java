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

public class RemoteServerInfo {
    public InetAddress host;
    public String hostname;
    public Integer port;
    public Integer rate;
    private String uuid;
    private String name;
    private int frontendPort;
    private int backendPort;
    private String contentDir;
    private int peerCount;
    private double metric;

    private ArrayList<RemoteServerInfo> neighbors = new ArrayList<RemoteServerInfo>();

    public RemoteServerInfo(String hostname, Integer port, Integer rate) throws IOException {
        this.host = InetAddress.getByName(hostname);
        this.hostname = hostname;
        this.port = port;
        this.rate = rate;
    }

    public String getUUID() {
        return this.uuid;
    }

    public String getHost() {
        return this.host.getHostName();
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

    public void setNeighbor(RemoteServerInfo info) {
        this.neighbors.add(info);
    }

    public ArrayList<RemoteServerInfo> getNeighbors() {
        return this.neighbors;
    }

    public RemoteServerInfo() {
        this.neighbors = new ArrayList<>();
    }

    // take in a query of values
    // Note: the name will come from the other node's RemoteServerInfo
    public static RemoteServerInfo parsePeer(HashMap<String, String> values) throws IOException {
        RemoteServerInfo peerConfig = new RemoteServerInfo();
        // peerConfig.setName(name);
        peerConfig.setUUID(values.get("uuid"));
        peerConfig.setHost(values.get("host"));
        peerConfig.setFrontendPort(Integer.parseInt(values.get("frontend")));
        peerConfig.setBackendPort(Integer.parseInt(values.get("backend")));
        peerConfig.setMetric(Integer.parseInt(values.get("metric")));
        return peerConfig;
    }

    public static RemoteServerInfo parsePeer(String name, String info) throws IOException {
        RemoteServerInfo peer_config = new RemoteServerInfo();
        String[] values = info.split(",");
        peer_config.setName(name);
        peer_config.setUUID(values[0]);
        peer_config.setHost(values[1]);
        peer_config.setFrontendPort(Integer.parseInt(values[2]));
        peer_config.setBackendPort(Integer.parseInt(values[3]));
        peer_config.setMetric(Double.parseDouble(values[4]));
        return peer_config;
    }

    public static RemoteServerInfo parseConfigFile(String filepath) throws IOException {
        Map<String, String> configMap = new HashMap<>();

        RemoteServerInfo config = new RemoteServerInfo();
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
        config.setPeerCount(Integer.parseInt(configMap.get("peer_count")));
        config.setHost("localhost");

        if (!configMap.containsKey("uuid")) {
            String uuid = UUID.randomUUID().toString();
            config.setUUID(uuid);
            // TODO add uuid back to node.conf
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
                System.out.println(value);
                config.setNeighbor(RemoteServerInfo.parsePeer(key, value));
            }
        }

        return config;
    }

    @Override
    public String toString() {
        return "RemoteServerInfo{"
                + "uuid='" + uuid + '\''
                + ", host='" + host + '\''
                + ", name='" + name + '\''
                + ", frontendPort=" + frontendPort
                + ", backendPort=" + backendPort
                + ", contentDir='" + contentDir + '\''
                + ", metric='" + metric + '\''
                + ", peerCount=" + peerCount
                + ", peers=" + neighbors
                + '}';
    }

    // public static void main(String args[]) throws IOException {
    // RemoteServerInfo info = RemoteServerInfo.parseConfigFile(args[0]);
    // System.out.println(info);
    // }

}
