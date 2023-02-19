import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // private RemoteServerInfo serverInfo;
    // private int interval;
    private final static int bufferSize = 8192;
    private final static int TTL = 10;
    private Integer prevLSPSeqNum;

    // send HELLO to neighbors
    public static void hello() {
        // store the old neighbors
        VodServer.prevActiveNeighbors.clear();
        for (NodeInfo neighbor : VodServer.activeNeighbors) {
            VodServer.prevActiveNeighbors.put(neighbor.getUUID(), false);
        }
        // clear old active neighbors
        VodServer.activeNeighbors.clear();

        // say hello to all neighbors
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send hello link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            NodeInfo curr = VodServer.getHomeNodeInfo();
            ArrayList<NodeInfo> neighbors = curr.getNeighbors();
            for (NodeInfo neighbor : neighbors) {
                curr.setMetric(neighbor.getMetric());
                String message = "HELLO AreYouAlive? " + curr.getName() + " " + curr.toNeighborFormat();
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
            try (DatagramSocket socket = new DatagramSocket(0)) {
                // send link state packet
                byte[] data = new byte[bufferSize];
                VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
                NodeInfo curr = VodServer.getHomeNodeInfo();
                System.out.println("curr node: " + curr.getName());
                // System.out.println("curr LSPseqNum: " + VodServer.LSPSeqNum);
                String message = "LSPSeqNum=" + VodServer.LSPSeqNum + " "
                        + "TTL=" + LSPSender.TTL + " "
                        + "senderName=" + curr.getName() + " "
                        + "senderInfo=" + curr.toNeighborFormat() + " "
                        + "originName=" + curr.getName() + " "
                        + "originInfo=" + curr.toNeighborFormat() + " ";
                ArrayList<NodeInfo> neighbors = curr.getNeighbors();
                int peer_count = 0;
                for (NodeInfo neighbor : neighbors) {
                    message += "peer_" + peer_count + "=" + neighbor.toNeighborFormat() + " ";
                    peer_count++;
                }
                byte[] messageBytes = message.getBytes();
                System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
                for (NodeInfo neighbor : neighbors) {
                    DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.getHost(),
                            neighbor.getBackendPort());
                    socket.send(outPkt);
                }
            } catch (IOException ex) {
                System.out.println("SocketCannotOpenError");
            }
            // if the LSPSeqNum doesn't change, which mean neighbors status are the same,
            // wait 5000 ms before sending the next LSP
            if (VodServer.LSPSeqNum == this.prevLSPSeqNum) {
                try {
                    long sleepTime = 5000; // send a LSP every 5000 ms
                    Thread.sleep((sleepTime));
                } catch (InterruptedException e) {
                    System.out.println("Fail to sleep");
                }
            } else {
                this.prevLSPSeqNum = VodServer.LSPSeqNum;
            }
        }
    }
}
