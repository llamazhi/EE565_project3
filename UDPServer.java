import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;
import java.lang.Thread;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UDPServer extends Thread {
    private final static Logger audit = Logger.getLogger("requests");

    private int port;
    private static int numChunks;
    private final static int bufferSize = 8192;
    private HashMap<SocketAddress, String> clientInfos;
    private HashMap<String, Integer> pathToBitRate;
    private HashMap<String, HashMap<Integer, byte[]>> fileChunks;
    private static int bitSent = 0;
    private final static int MAX_WINDOW_SIZE = 100;
    private final static double TIME_TO_LIVE = 2000; // set TTL as 2 seconds
    // private HashMap<String, ArrayList<RemoteServerInfo>> adjMap; // {uuid:
    // [RemoteServerInfo node2, node3, ...]}
    // private HashMap<String, String> uuidToName;

    public UDPServer(int port) {
        this.port = port;
        this.fileChunks = new HashMap<>();
        this.clientInfos = new HashMap<>();
        this.pathToBitRate = new HashMap<>();
        // this.adjMap = new HashMap<>();
        // this.uuidToName = new HashMap<>();
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

    // TODO: build an adjacency list when getting LSP
    // deal with TTL
    @Override
    public void run() {
        // TODO: every x second send LSP to all the other nodes
        try (DatagramSocket socket = new DatagramSocket(this.port)) {
            System.out.println("UDP Server listening at: " + this.port);

            while (true) {
                DatagramPacket inPkt = new DatagramPacket(new byte[bufferSize], bufferSize);
                socket.receive(inPkt);
                handleInPacket(inPkt, socket);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleInPacket(DatagramPacket inPkt, DatagramSocket socket) throws IOException {
        // TODO: handle LSP, store in a list and create a graph
        int seqNum = byteArrayToInt(inPkt.getData());
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();
        int bitRate = 0;

        System.out.println("handleInPacket");
        System.out.println(requestString);
        if (seqNum == -1 && !requestString.isEmpty()) {
            // the packet is LSP

            String[] values = requestString.split(" ");
            if (values[0].equals("HELLO")) {
                // HELLO packet
                if (values[1].equals("AreYouAlive?")) {
                    // reply Yes
                    byte[] data = new byte[bufferSize];
                    VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                    RemoteServerInfo neighborInfo = RemoteServerInfo.parsePeer(values[2], values[3]);
                    RemoteServerInfo nodeConfig = VodServer.getHomeNodeInfo();
                    String message = "HELLO YES " + nodeConfig.getName() + " " + nodeConfig.toPeerFormat();
                    byte[] messageBytes = message.getBytes(Charset.forName("US-ASCII"));
                    System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                    DatagramPacket outPkt = new DatagramPacket(data, data.length, neighborInfo.getHost(),
                            neighborInfo.getBackendPort());
                    socket.send(outPkt);
                    return;
                }
                if (values[1].equals("YES")) {
                    // store in activeNeighbor list
                    RemoteServerInfo neighborInfo = RemoteServerInfo.parsePeer(values[2], values[3]);
                    VodServer.activeNeighbors.add(neighborInfo);
                    return;
                }
            }
            JsonObject data = new Gson().fromJson(requestString, JsonObject.class);
            RemoteServerInfo senderInfo = new RemoteServerInfo();
            senderInfo.setName(data.get("senderName").getAsString());
            senderInfo.setUUID(data.get("senderUUID").getAsString());
            if (!VodServer.uuidToName.containsKey(senderInfo.getUUID())) {
                VodServer.uuidToName.put(senderInfo.getUUID(), senderInfo.getName());
            }
            if (!VodServer.adjMap.containsKey(senderInfo.getUUID())) {
                VodServer.adjMap.put(senderInfo.getUUID(), new ArrayList<>());
            }

            for (JsonElement neighbor : data.getAsJsonArray("neighbors")) {
                JsonObject neighborObject = neighbor.getAsJsonObject();
                RemoteServerInfo neighborInfo = new RemoteServerInfo();
                neighborInfo.setName(neighborObject.get("neighborName").getAsString());
                neighborInfo.setUUID(neighborObject.get("neighborUUID").getAsString());
                neighborInfo.setMetric(neighborObject.get("metric").getAsDouble());
                VodServer.adjMap.get(senderInfo.getUUID()).add(neighborInfo);
                if (!VodServer.adjMap.containsKey(neighborInfo.getUUID())) {
                    VodServer.adjMap.put(neighborInfo.getUUID(), new ArrayList<>());
                }
                senderInfo.setMetric(neighborInfo.getMetric());
                VodServer.adjMap.get(neighborInfo.getUUID()).add(senderInfo);
                senderInfo.setMetric(0);
                // this.adjMap.get(temp.getUUID()).add()
                // data.get("senderUUID").getAsString()).add(temp);

            }

            System.out.println("adjMap");
            System.out.println(VodServer.adjMap.keySet());
            // dijkstra
            PriorityQueue<RemoteServerInfo> minHeap = new PriorityQueue<>(
                    (a, b) -> Double.compare(a.getMetric(), b.getMetric()));
            // Set<String> visited = new Set<>();
            HashMap<String, Double> distance = new HashMap<>();
            distance.put(VodServer.getHomeNodeInfo().getUUID(), 0.0);
            minHeap.offer(VodServer.getHomeNodeInfo());
            while (!minHeap.isEmpty()) {
                RemoteServerInfo curr = minHeap.poll();

                System.out.println("curr");
                System.out.println(curr);
                if (!distance.containsKey(curr.getUUID())) {
                    distance.put(curr.getUUID(), Double.MAX_VALUE);
                }
                // if (distance.containsKey(curr.getUUID()) && distance.get(curr.getUUID()) <=
                // curr.getMetric()) {
                // continue;
                // }
                for (RemoteServerInfo neighbor : VodServer.adjMap.get(curr.getUUID())) {
                    Double newDistance = distance.get(curr.getUUID()) + neighbor.getMetric();
                    if (!distance.containsKey(neighbor.getUUID())
                            || Double.compare(newDistance, distance.get(neighbor.getUUID())) == -1) {
                        distance.put(neighbor.getUUID(), newDistance);
                        minHeap.offer(neighbor);
                    }
                }

                // shortestPath.put(curr.getUUID(), .distFromSrc);
                // List<int[]> cities = graph.get(place.city);
                // for (int[] arr : cities) {
                // minHeap.add(new City(arr[0], place.distFromSrc + 1, place.costFromSrc +
                // arr[1]));
                // }

            }
            System.out.println("Dijkstra");
            System.out.println(distance);
            // flood the LSP to neighbors
            for (RemoteServerInfo neighborInfo : VodServer.getHomeNodeInfo().getNeighbors()) {
                if (neighborInfo.getUUID().equals(senderInfo.getUUID())) {
                    System.out.println("flood");
                    System.out.println(neighborInfo.getUUID() + " " + senderInfo.getUUID());
                    DatagramPacket outPkt = new DatagramPacket(inPkt.getData(), inPkt.getLength(),
                            neighborInfo.getHost(),
                            neighborInfo.getBackendPort());
                    socket.send(outPkt);
                }
            }

        } else if (seqNum == 0 && !requestString.isEmpty()) {
            // request for a file
            String[] requestValues = requestString.split(" ");
            String path = requestValues[0];
            audit.info("Client request for file " + path);
            bitRate = Integer.parseInt(requestValues[1]);

            File requestFile = new File(path);
            if (!requestFile.exists()) {
                byte[] data = ("FileNotExistsError").getBytes(Charset.forName("US-ASCII"));
                DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
                socket.send(outPkt);
                return;
            }

            this.pathToBitRate.put(path, bitRate); // unit in bit/sec
            this.clientInfos.put(inPkt.getSocketAddress(), path);

            long fileSize = requestFile.length();
            long lastModified = requestFile.lastModified();
            numChunks = (int) Math.ceil((double) fileSize / (bufferSize - 4));
            audit.info("current file size: " + fileSize);
            audit.info("total number of chunks: " + numChunks);
            byte[] data = (fileSize + " " + lastModified + " " + numChunks + " " + MAX_WINDOW_SIZE)
                    .getBytes(Charset.forName("US-ASCII"));

            audit.info("Begin to send file ... ");

            if (!this.fileChunks.containsKey(path)) {
                audit.info("Read new file into memory" + path);

                this.fileChunks.put(path, new HashMap<>());
                // Read all the file into chunks
                int chunkIndex = 1;
                byte[] chunk = new byte[bufferSize];
                FileInputStream fis = new FileInputStream(requestFile);
                while (fis.read(chunk, 4, bufferSize - 4) > 0) {
                    intToByteArray(chunkIndex, chunk);
                    this.fileChunks.get(path).put(chunkIndex, chunk);
                    chunkIndex++;
                    chunk = new byte[bufferSize];
                }
                fis.close();

            }
            // send response packet
            DatagramPacket outPkt = new DatagramPacket(data, data.length, inPkt.getAddress(), inPkt.getPort());
            socket.send(outPkt);
            audit.info(numChunks + " chunks in total");

        } else if (seqNum > 0 && seqNum <= numChunks) {
            // request for specific chunk of data
            String path = this.clientInfos.get(inPkt.getSocketAddress());
            byte[] sendingChunk = this.fileChunks.get(path).get(seqNum);
            DatagramPacket outPkt = new DatagramPacket(
                    sendingChunk,
                    sendingChunk.length,
                    inPkt.getAddress(),
                    inPkt.getPort());

            socket.send(outPkt);

            UDPServer.bitSent += outPkt.getLength() * 8;
            bitRate = this.pathToBitRate.get(path); // unit in kbps

            if (bitRate != 0 && UDPServer.bitSent > bitRate * 1000) {
                try {
                    double sleepTime = (UDPServer.bitSent / bitRate) - 1000;
                    System.out.println("Need sleep for " + sleepTime + " ms");
                    Thread.sleep((long) sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                UDPServer.bitSent = 0;
            }
        }
    }
}
