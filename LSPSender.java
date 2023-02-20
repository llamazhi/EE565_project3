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
        // store the old neighbors
        VodServer.prevActiveNeighbors.clear();
        for (Map.Entry<String, NodeInfo> entry : VodServer.activeNeighbors.entrySet()) {
            String uuid = entry.getKey();
            VodServer.prevActiveNeighbors.put(uuid, false);
        }
        // clear old active neighbors
        // VodServer.activeNeighbors.clear();

        // say hello to all neighbors
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send hello link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            NodeInfo curr = VodServer.getHomeNodeInfo();
            HashSet<NodeInfo> neighbors = curr.getNeighbors();
            for (NodeInfo neighbor : neighbors) {
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
            // if the LSPSeqNum doesn't change, which mean neighbors status are the same,
            // wait 5000 ms before sending the next LSP
            try (DatagramSocket socket = new DatagramSocket(0)) {
                // send link state packet
                byte[] data = new byte[bufferSize];
                VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                NodeInfo curr = VodServer.getHomeNodeInfo();
                HashMap<String, NodeInfo> neighbors = VodServer.activeNeighbors;
                // System.out.println("curr node: " + curr.getName());
                // System.out.println("active neighbors: " + neighbors);
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
                VodServer.activeNeighbors.clear();
            } catch (IOException ex) {
                System.out.println("SocketCannotOpenError");
            }
        }
    }
}
