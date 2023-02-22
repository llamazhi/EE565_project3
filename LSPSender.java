import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    private final static int bufferSize = 8192;
    private Integer prevLSPSeqNum;

    public static String LSPMessageConstructor(Integer LSPSeqNum, Long LSPTimestamp, NodeInfo sender, NodeInfo origin,
            HashMap<String, NodeInfo> neighbors) {
        String message = "LSPSeqNum=" + LSPSeqNum + " "
                + "timestamp=" + LSPTimestamp + " "
                + "sender=" + sender.toLSPFormat() + " "
                + "origin=" + origin.toLSPFormat() + " ";
        Integer i = 0;
        for (Map.Entry<String, NodeInfo> entry : neighbors.entrySet()) {
            NodeInfo neighbor = entry.getValue();
            message += "peer_" + i + "=" + neighbor.toLSPFormat() + " ";
            i++;
        }
        return message;
    }

    // send HELLO to neighbors
    public static void hello() {
        // say hello to all neighbors
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send hello link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            NodeInfo curr = VodServer.getHomeNodeInfo();
            HashSet<NodeInfo> neighbors = curr.getNeighbors();
            for (NodeInfo neighbor : neighbors) {
                // neighbor no response count++ when send hello
                // (count reset to 0 when receive hello)
                // If neighbor already exists in map, increment its count
                if (VodServer.neighborNoResponseCount.containsKey(neighbor.getUUID())) {
                    int frequency = VodServer.neighborNoResponseCount.get(neighbor.getUUID());
                    VodServer.neighborNoResponseCount.put(neighbor.getUUID(), frequency + 1);
                }
                // Otherwise, add neighbor to map with count of 1
                else {
                    VodServer.neighborNoResponseCount.put(neighbor.getUUID(), 1);
                }
                curr.setMetric(neighbor.getMetric());
                String message = "HELLO AreYouAlive? " + curr.toLSPFormat();
                byte[] messageBytes = message.toString().getBytes();
                System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.getHost(),
                        neighbor.getBackendPort());
                socket.send(outPkt);
                curr.setMetric(0);
            }
        } catch (IOException ex) {
            System.out.println("SocketCannotOpenError");
        }
    }

    @Override
    public void run() {
        while (true) {
            for (int i = 0; i < 5; i++) {
                LSPSender.hello();
                if (VodServer.LSPSeqNum != this.prevLSPSeqNum) {
                    this.prevLSPSeqNum = VodServer.LSPSeqNum;
                    break;
                }
                try {
                    long sleepTime = 1000; // send a HELLO every 1000 ms
                    Thread.sleep((sleepTime));
                } catch (InterruptedException e) {
                    System.out.println("Fail to sleep");
                }
            }
            Boolean neighborDead = false;
            for (Map.Entry<String, Integer> entry : VodServer.neighborNoResponseCount.entrySet()) {
                String uuid = entry.getKey();
                Integer count = entry.getValue();
                if (count >= 3) {
                    if (VodServer.activeNeighbors.remove(uuid) != null) {
                        neighborDead = true;
                    }
                }
            }
            if (neighborDead) {
                VodServer.LSPSeqNum++;
                System.out.println("Some neighbors become inactive! Increase LSPSeqNum.");
                System.out.println("LSPSeqNum = " + VodServer.LSPSeqNum);
            }
            // if the LSPSeqNum doesn't change, which mean neighbors status are the same,
            // wait 5000 ms before sending the next LSP
            try (DatagramSocket socket = new DatagramSocket(0)) {
                // send link state packet
                byte[] data = new byte[bufferSize];
                VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                NodeInfo curr = VodServer.getHomeNodeInfo();
                HashMap<String, NodeInfo> neighbors = VodServer.activeNeighbors;
                Long currentTime = System.currentTimeMillis();
                String message = LSPSender.LSPMessageConstructor(VodServer.LSPSeqNum, currentTime, curr, curr,
                        neighbors);
                byte[] messageBytes = message.getBytes();
                System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                for (Map.Entry<String, NodeInfo> entry : neighbors.entrySet()) {
                    NodeInfo neighbor = entry.getValue();
                    DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.getHost(),
                            neighbor.getBackendPort());
                    socket.send(outPkt);
                }
            } catch (IOException ex) {
                System.out.println("SocketCannotOpenError");
            }
        }
    }
}
