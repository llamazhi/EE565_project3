import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

// This is the main driver class for the project
public class VodServer {
    public static HashMap<String, ArrayList<RemoteServerInfo>> parameterMap;
    public static ArrayList<Long> clientReceiveTimestamps;
    public static boolean bitRateChanged = false;
    public final static Integer bufferSize = 8192;
    private static Double completeness = 0.0;
    private static Integer bitRate = 0;
    private static RemoteServerInfo homeNodeInfo;

    public VodServer() {
        VodServer.parameterMap = new HashMap<String, ArrayList<RemoteServerInfo>>();
        VodServer.clientReceiveTimestamps = new ArrayList<>();
    }

    public static void addPeer(String filepath, RemoteServerInfo info) {
        if (!VodServer.parameterMap.containsKey(filepath)) {
            VodServer.parameterMap.put(filepath, new ArrayList<RemoteServerInfo>());
        }
        VodServer.parameterMap.get(filepath).add(info);
    }

    public static void setNeighbor(RemoteServerInfo info) {
        homeNodeInfo.setNeighbor(info); // update the homeNodeInfo
    }

    public static ArrayList<RemoteServerInfo> getNeighbors() {
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

    public static ArrayList<RemoteServerInfo> getRemoteServerInfo(String filepath) {
        return VodServer.parameterMap.get(filepath);
    }

    public static RemoteServerInfo getHomeNodeInfo() {
        return VodServer.homeNodeInfo;
    }

    public void setServerInfo(RemoteServerInfo config) {
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
        // TODO: implement method to parse this command "./vodserver –c node.conf"
        // parse file node.conf into an object
        // call ConfigParser to do this
        VodServer vodServer = new VodServer();
        try {
            RemoteServerInfo config = RemoteServerInfo.parseConfigFile("node.conf");
            vodServer.setServerInfo(config);
        } catch (IOException ex) {
            System.out.println("error while reading config file");
            return;
        }

        ServerSocket server = null;
        int httpPort;
        int udpPort;

        // TODO: modify the arguments part
        if (args.length != 2) {
            System.out.println("Usage: java VodServer http-port udp-port");
            return;
        }

        httpPort = Integer.parseInt(args[0]);
        udpPort = Integer.parseInt(args[1]);

        UDPServer udpserver = new UDPServer(udpPort);
        udpserver.start();

        // TODO: create another thread for continuously sending LSP
        LSPSender lspSender = new LSPSender();
        lspSender.start();

        try {
            server = new ServerSocket(httpPort);
            System.out.println("Server started, listening on: " + httpPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // TODO: change to a variable, isActive, set to false when getting "peer/kill"
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