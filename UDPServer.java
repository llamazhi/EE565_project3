import java.util.regex.Pattern;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;
import java.lang.Thread;

public class UDPServer extends Thread {
    private final static Logger audit = Logger.getLogger("requests");

    private Integer port;
    private static Integer numChunks;
    private final static Integer bufferSize = 8192;
    private HashMap<SocketAddress, String> clientInfos;
    private HashMap<String, Integer> pathToBitRate;
    private HashMap<String, HashMap<Integer, byte[]>> fileChunks;
    private static Integer bitSent = 0;
    private final static Integer MAX_WINDOW_SIZE = 100;
    private NodeInfo origin;
    private NodeInfo sender;
    private Integer LSPSeqNum;
    private Long LSPTimestamp;
    private HashMap<String, NodeInfo> neighbors;

    public UDPServer(Integer port) {
        this.port = port;
        this.fileChunks = new HashMap<>();
        this.clientInfos = new HashMap<>();
        this.pathToBitRate = new HashMap<>();
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

    private void parseLSP(String LSPData) throws IOException {
        String[] lines = LSPData.split(" ");
        Map<String, String> configMap = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            String[] keyValue = lines[i].split("=");
            if (keyValue.length == 2) {
                configMap.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        this.origin = NodeInfo.parseLSPFormat(configMap.get("origin"));
        this.sender = NodeInfo.parseLSPFormat(configMap.get("sender"));

        this.LSPTimestamp = Long.parseLong(configMap.get("timestamp"));
        this.LSPSeqNum = Integer.parseInt(configMap.get("LSPSeqNum"));
        Pattern pattern = Pattern.compile("peer_[0-9]*");
        this.neighbors = new HashMap<>();
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (pattern.matcher(key).matches()) {
                NodeInfo neighbor = NodeInfo.parseLSPFormat(value);
                this.neighbors.put(neighbor.getUUID(), neighbor);
            }
        }
    }

    @Override
    public void run() {
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
        int seqNum = byteArrayToInt(inPkt.getData());
        String requestString = new String(inPkt.getData(), 4, inPkt.getLength() - 4).trim();
        int bitRate = 0;

        if (seqNum == -1 && !requestString.isEmpty()) {
            // the packet is LSP

            String[] values = requestString.split(" ");
            if (values[0].equals("HELLO")) {
                // HELLO packet
                if (values[1].equals("AreYouAlive?")) {
                    // reply Yes
                    byte[] data = new byte[bufferSize];
                    VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                    NodeInfo neighborInfo = NodeInfo.parseLSPFormat(values[2]);
                    NodeInfo nodeConfig = VodServer.getHomeNodeInfo();
                    nodeConfig.setMetric(neighborInfo.getMetric());
                    String message = "HELLO YES " + nodeConfig.toLSPFormat();
                    byte[] messageBytes = message.getBytes(Charset.forName("US-ASCII"));
                    System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                    DatagramPacket outPkt = new DatagramPacket(data, data.length, neighborInfo.getHost(),
                            neighborInfo.getBackendPort());
                    socket.send(outPkt);
                    nodeConfig.setMetric(0);
                    return;
                }
                if (values[1].equals("YES")) {
                    // store in activeNeighbor list
                    NodeInfo neighborInfo = NodeInfo.parseLSPFormat(values[2]);
                    VodServer.activeNeighbors.put(neighborInfo.getUUID(), neighborInfo);
                    VodServer.neighborNoResponseCount.put(neighborInfo.getUUID(), 0); // reset to 0
                    return;
                }
            }
            this.parseLSP(requestString);
            // VodServer.setUUIDToName(this.sender.getUUID(), this.sender.getName());
            // VodServer.setUUIDToName(this.origin.getUUID(), this.origin.getName());
            VodServer.uuidToInfo.put(this.origin.getUUID(), this.origin);
            VodServer.uuidToInfo.put(this.sender.getUUID(), this.sender);

            if (VodServer.LSDB.containsKey(this.origin.getUUID())) {
                if (this.LSPTimestamp <= VodServer.LSDB.get(this.origin.getUUID())) {
                    // already flooded this LSP
                    return;
                }
            }
            // new LSP
            VodServer.LSDB.put(this.origin.getUUID(), this.LSPTimestamp);

            if (!VodServer.adjMap.containsKey(this.origin.getUUID())) {
                VodServer.adjMap.put(this.origin.getUUID(), new HashMap<>());
            }

            Long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, NodeInfo> entry : neighbors.entrySet()) {
                NodeInfo end = entry.getValue();
                VodServer.uuidToInfo.put(end.getUUID(), end);
                end.setTimestamp(currentTime);
                if (!VodServer.adjMap.containsKey(end.getUUID())) {
                    VodServer.adjMap.put(end.getUUID(), new HashMap<>());
                }
                NodeInfo start = new NodeInfo();
                start.setTimestamp(currentTime);
                start.setName(this.origin.getName());
                start.setUUID(this.origin.getUUID());
                start.setContentDir(this.origin.getContentDir());
                start.setMetric(end.getMetric());
                VodServer.adjMap.get(start.getUUID()).put(end.getUUID(), end);
                VodServer.adjMap.get(end.getUUID()).put(start.getUUID(), start);
            }

            // dijkstra
            PriorityQueue<NodeInfo> minHeap = new PriorityQueue<>(
                    (a, b) -> Double.compare(a.getMetric(), b.getMetric()));

            HashMap<String, Double> distance = new HashMap<>();
            distance.put(VodServer.getHomeNodeInfo().getUUID(), 0.0);
            minHeap.offer(VodServer.getHomeNodeInfo());
            while (!minHeap.isEmpty()) {
                // get the shortest edge
                NodeInfo curr = minHeap.poll();
                // if we didn't visit before, create an entry in distance map
                if (!distance.containsKey(curr.getUUID())) {
                    distance.put(curr.getUUID(), Double.MAX_VALUE);
                }
                // iterate through its neighbors
                HashMap<String, NodeInfo> neighbors = VodServer.adjMap.get(curr.getUUID());
                for (String neighborUUID : neighbors.keySet()) {
                    NodeInfo neighborInfo = neighbors.get(neighborUUID);
                    // check TTL
                    if (currentTime - neighborInfo.getTimestamp() >= VodServer.TIME_TO_LIVE * 1000) {
                        // information outdated
                        continue;
                    }
                    Double newDistance = distance.get(curr.getUUID()) + neighborInfo.getMetric();
                    if (!distance.containsKey(neighborUUID)
                            || Double.compare(newDistance, distance.get(neighborUUID)) == -1) {
                        distance.put(neighborUUID, newDistance);
                        minHeap.offer(neighborInfo);
                    }
                }
            }
            VodServer.distanceFromOrigin = distance;

            // System.out.println("Dijkstra");
            // for (Map.Entry<String, Double> entry : distance.entrySet()) {
            // System.out.println("node: " + entry.getKey());
            // System.out.println("distance: " + entry.getValue());
            // }

            // flood the LSP to neighbors
            this.sender = VodServer.getHomeNodeInfo();
            for (NodeInfo neighborInfo : VodServer.getHomeNodeInfo().getNeighbors()) {
                if (!neighborInfo.getUUID().equals(sender.getUUID())) {
                    byte[] data = new byte[bufferSize];
                    VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                    String message = LSPSender.LSPMessageConstructor(this.LSPSeqNum, this.LSPTimestamp, this.sender,
                            this.origin,
                            neighbors);
                    byte[] messageBytes = message.getBytes(Charset.forName("US-ASCII"));
                    System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                    DatagramPacket outPkt = new DatagramPacket(data, data.length,
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
