import java.net.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RemoteServerInfo {
    public InetAddress host;
    public Integer port;
    public Integer rate;
    private String uuid;
    private String name;
    private int frontendPort;
    private int backendPort;
    private String contentDir;
    private int peerCount;
    private int metric;

    private ArrayList<RemoteServerInfo> neighbors = new ArrayList<RemoteServerInfo>();

    public RemoteServerInfo(String hostname, Integer port, Integer rate) throws IOException {
        this.host = InetAddress.getByName(hostname);
        this.port = port;
        this.rate = rate;
    }

    public RemoteServerInfo(String uuid, String host, String frontend, String backend, String metric)
            throws IOException {
        this.setUUID(uuid);
        this.setHost(host);
        this.setFrontendPort(Integer.parseInt(frontend));
        this.setBackendPort(Integer.parseInt(backend));
        this.setMetric(Integer.parseInt(metric));
    }

    public void setNeighbor(RemoteServerInfo neighbor) {
        this.neighbors.add(neighbor);
    }

    public void setMetric(int metric) {
        this.metric = metric;
    }

    public int getMetric() {
        return this.metric;
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
        this.host = InetAddress.getByName(hostname);
    }

    public RemoteServerInfo() {
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
            config.setUUID(UUID.randomUUID().toString());
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
                + ", peerCount=" + peerCount
                + '}';
    }

    // public static void main(String args[]) throws IOException {
    // RemoteServerInfo info = RemoteServerInfo.parseConfigFile(args[0]);
    // System.out.println(info);
    // }

}
