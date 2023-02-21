import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

// This is the main driver class for the project
public class VodServer {
    public static HashMap<String, ArrayList<NodeInfo>> parameterMap = new HashMap<>();
    public static ArrayList<Long> clientReceiveTimestamps = new ArrayList<>();
    public static boolean bitRateChanged = false;
    public final static Integer bufferSize = 8192;
    private static Double completeness = 0.0;
    private static Integer bitRate = 0;
    private static NodeInfo homeNodeInfo;
    public static HashMap<String, HashMap<String, NodeInfo>> adjMap = new HashMap<>();
    public static HashMap<String, Long> LSDB = new HashMap<>(); // Link State Database (origin uuid, timestamp)
    public static HashMap<String, NodeInfo> activeNeighbors = new HashMap<>();
    public static HashMap<String, Boolean> prevActiveNeighbors = new HashMap<>();
    public static HashMap<String, Integer> neighborNoResponseCount = new HashMap<>();
    public static HashMap<String, Double> distanceFromOrigin = new HashMap<>();
    public static HashMap<String, NodeInfo> uuidToInfo = new HashMap<>();
    public static HashMap<String, HashSet<String>> fileNameToNodes = new HashMap<>();
    public static Integer LSPSeqNum = 1;
    public final static Integer TIME_TO_LIVE = 10;

    public static void addPeer(String filepath, NodeInfo info, String uuid) {
        if (!VodServer.parameterMap.containsKey(filepath)) {
            VodServer.parameterMap.put(filepath, new ArrayList<NodeInfo>());
        }
        VodServer.parameterMap.get(filepath).add(info);

        // associate the fileName to the specified node and store them in VodServer
        if (VodServer.uuidToInfo.containsKey(uuid)) {
            if (!VodServer.fileNameToNodes.containsKey(filepath)) {
                HashSet<String> nodes = new HashSet<>();
                nodes.add(uuid);
                VodServer.fileNameToNodes.put(filepath, nodes);
            }
            VodServer.fileNameToNodes.get(filepath).add(uuid);
        }
    }

    public static HashMap<String, ArrayList<NodeInfo>> getParameterMap() {
        return VodServer.parameterMap;
    }

    public static void setNeighbor(NodeInfo info) {
        homeNodeInfo.setNeighbor(info); // update the homeNodeInfo
    }

    public static HashSet<NodeInfo> getNeighbors() {
        return homeNodeInfo.getNeighbors();
    }

    public static void setCompleteness(double completeness) {
        VodServer.completeness = completeness;
    }

    public static double getCompleteness() {
        return VodServer.completeness;
    }

    // client receive rate limit
    public static void setBitRate(Integer bitRate) {
        VodServer.clientReceiveTimestamps = new ArrayList<>();
        VodServer.bitRate = bitRate; // kbps
        VodServer.bitRateChanged = true;
    }

    // client receive rate limit
    public static int getBitRate() {
        return VodServer.bitRate;
    }

    public static ArrayList<NodeInfo> getRemoteServerInfo(String filepath) {
        return VodServer.parameterMap.get(filepath);
    }

    public static NodeInfo getHomeNodeInfo() {
        return VodServer.homeNodeInfo;
    }

    public static HashMap<String, HashMap<String, NodeInfo>> getAdjMap() {
        return VodServer.adjMap;
    }

    // public static HashMap<String, String> getUUIDToName() {
    // return uuidToName;
    // }

    // public static void setUUIDToName(String uuid, String name) {
    // VodServer.uuidToName.put(uuid, name);
    // }

    public void setServerInfo(NodeInfo config) {
        VodServer.homeNodeInfo = config;
    }

    public static void intToByteArray(int value, byte[] buffer) {
        buffer[0] = (byte) (value >>> 24);
        buffer[1] = (byte) (value >>> 16);
        buffer[2] = (byte) (value >>> 8);
        buffer[3] = (byte) value;
    };

    public static int byteArrayToInt(byte[] bytes) {
        return (bytes[0] << 24) & 0xff000000 |
                (bytes[1] << 16) & 0x00ff0000 |
                (bytes[2] << 8) & 0x0000ff00 |
                (bytes[3] & 0xff);
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java VodServer -c configfile");
            return;
        }

        VodServer vodServer = new VodServer();
        try {
            NodeInfo config = NodeInfo.parseConfigFile(args[1]);
            System.out.println("server uuid: " + config.getUUID());
            vodServer.setServerInfo(config);
        } catch (IOException ex) {
            System.out.println("error while reading config file");
            return;
        }

        ServerSocket server = null;

        NodeInfo nodeConfig = VodServer.getHomeNodeInfo();
        int httpPort = nodeConfig.getFrontendPort();
        int udpPort = nodeConfig.getBackendPort();

        UDPServer udpserver = new UDPServer(udpPort);
        udpserver.start();

        LSPSender lspSender = new LSPSender();
        lspSender.start();

        try {
            server = new ServerSocket(httpPort);
            System.out.println("Server started, listening on: " + httpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            while (true) {
                Socket client = server.accept();
                System.out.println("Connection accepted");

                ThreadedHTTPWorker workerThread = new ThreadedHTTPWorker(client);
                workerThread.start();
            }
        } catch (IOException e) {
            System.out.println("Thread building issue");
            e.printStackTrace();
        }
    }
}