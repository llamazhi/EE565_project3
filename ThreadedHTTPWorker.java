import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Collections;
// import org.json.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

// ThreadedHTTPWorker class is responsible for all the
// actual string & data transfer
public class ThreadedHTTPWorker extends Thread {
    private Socket client;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private final String CRLF = "\r\n";
    private int[] rangeNum;

    public ThreadedHTTPWorker(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            System.out.println("Worker Thread starts running ... ");
            this.outputStream = new DataOutputStream(this.client.getOutputStream());
            this.inputStream = new DataInputStream(this.client.getInputStream());

            // retrieve request header as String
            BufferedReader in = new BufferedReader(new InputStreamReader(this.client.getInputStream()));
            String inputLine;
            String req = "";
            while ((inputLine = in.readLine()) != null) {
                req += inputLine;
                req += "\r\n";
                if (inputLine.length() == 0) {
                    break;
                }
            }
            String relativeURL = preprocessReq(req);
            parseURI(req, relativeURL);
        } catch (IOException e) {
            System.out.println("Something wrong with connection");
            e.printStackTrace();
        } finally {
            if (this.outputStream != null) {
                try {
                    this.outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.inputStream != null) {
                try {
                    this.inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String preprocessReq(String req) {
        int index = req.indexOf("GET");
        int nextIndex = req.indexOf("HTTP/");
        if (index != -1 && nextIndex != -1) {
            String relativeURL = req.substring(index + 4, nextIndex - 1).trim();
            System.out.println("relativeURL: " + "\"" + relativeURL + "\"");
            return relativeURL;
        } else {
            return "";
        }
    }

    private void parseURI(String req, String relativeURL) {
        HTTPURIParser parser = new HTTPURIParser(relativeURL);

        if (!parser.hasUDPRequest()) {
            // This is a local request
            String path = parser.getPath();
            String MIMEType = categorizeFile(path);
            File f = new File(path);
            if (f.exists()) {
                if (req.contains("Range: ")) {
                    setRange(req);
                    sendPartialContent(MIMEType, this.rangeNum, f);
                } else {
                    sendFullContent(MIMEType, f);
                }
            } else {
                sendErrorResponse("This file is not found in local server.");
            }
        } else if (parser.hasAdd()) {
            // store the parameter information
            String[] queries = parser.getQueries();
            addPeer(queries);
        } else if (parser.hasView()) {
            String path = parser.getPath();
            path = path.replace("/peer/view/", "");
            viewContent(path);
        } else if (parser.hasConfig()) {
            configureRate(parser);
        } else if (parser.hasStatus()) {
            getStatus();
        } else if (parser.hasKill()) {
            // TODO: interupt the while loop in VodServer
        } else if (parser.hasUUID()) {
            // TODO: return the current node uuid
            showUUID();

        } else if (parser.hasNeighbors()) {
            // TODO: return the the neighbors of current node
            // Response: a list of objects representing all active neighbors
        } else if (parser.hasAddNeibor()) {
            // TODO: add neighbor, modify the current add peer function to do this
            // example:
            // /peer/addneighbor?uuid=e94fc272-5611-4a61-8b27de7fe233797f&host=nu.ece.cmu.edu&frontend=18345&backend=18346&metric=30

        } else if (parser.hasMap()) {
            // TODO: respond adjacency list for the latest network map.
            // It should contain only active node/link.
            // example:
            // { “node1”:{“node2”:10,”node3”:20}, “node2”:{“node1”:10,”node3”:20},
            // “node3”:{“node1”:20,”node2”:10,“node4”:30}, “node4”:{“node3”:30} }

        } else if (parser.hasRank()) {
            // TODO:
            // /peer/rank/<content path>
            // respond an ordered list (sorted by distance metrics) showing the distance
            // between the requested node and all content nodes.

            // For example,
            // [{“node2”:10}, {“node3”:20}, {“node4”:50}]
        } else {
            sendErrorResponse("Invalid request");
        }
    }

    private void showUUID() {
        try {
            RemoteServerInfo info = VodServer.getServerInfo();
            JsonObject uuid = new JsonObject();
            uuid.addProperty("uuid", info.getUuid());
            String uuidStr = uuid.toString();
            // String html = "<html><body><p>" + uuidStr + "</p></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: application/json" + this.CRLF +
                    "Content-Length:" + uuidStr.length() + this.CRLF +
                    this.CRLF + uuid;
            // System.out.println(uuid.toString());
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // store the parameter information
    private void addPeer(String[] queries) {
        try {
            HashMap<String, String> keyValue = new HashMap<>();
            for (String q : queries) {
                String[] queryComponents = q.split("=");
                keyValue.put(queryComponents[0], queryComponents[1]);
            }
            System.out.println(keyValue);

            // may pass the parameters to UDP later
            String path = keyValue.get("path");
            int port = Integer.parseInt(keyValue.get("port"));
            String host = keyValue.get("host");
            int rate = Integer.parseInt(keyValue.get("rate"));
            RemoteServerInfo info = new RemoteServerInfo(host, port, rate);
            VodServer.addPeer(path, info);
            // Pass the queries to backend port
            // At this stage, we just print them out
            String html = "<html><body><h1>Peer Added!</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);

        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("invalid query");
            e.printStackTrace();
        }

    }

    private void viewContent(String path) {
        UDPClient udpclient = new UDPClient();

        System.out.println(path);
        ArrayList<RemoteServerInfo> infos = VodServer.getRemoteServerInfo(path);
        if (infos == null) {
            sendErrorResponse("Please add peer first!");
            return;
        }
        // TODO: get content from the right server(s)
        String result = udpclient.startClient(path, infos, this.outputStream);
        if (!result.equals("Success")) {
            sendErrorResponse(result);
        }
    }

    private void sendErrorResponse(String msg) {
        try {
            String html = "<html><body><h1>404 Not Found!</h1><p>" + msg + "</p></body></html>";
            String response = "HTTP/1.1 404 Not Found" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
            this.outputStream.writeBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void configureRate(HTTPURIParser parser) {
        try {
            int rate = Integer.parseInt(parser.getQueries()[0].split("=")[1]);
            VodServer.setBitRate(rate);
            String html = "<html><body><h1>Client receiving bit rate set to " + rate
                    + " kbps</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("Invalid rate configure");
        }
    }

    private double getAverageBitRateWithinInterval(long interval) {
        long currentTime = System.currentTimeMillis();
        int index = Collections.binarySearch(VodServer.clientReceiveTimestamps, currentTime - interval);
        long startTime;
        if (index < 0) {
            index = -index - 1;
        }
        if (index == VodServer.clientReceiveTimestamps.size()) {
            return 0;
        } else {
            startTime = VodServer.clientReceiveTimestamps.get(index);
            return ((double) VodServer.clientReceiveTimestamps.size() - index) * 8 * (double) VodServer.bufferSize
                    / (double) (currentTime - startTime);
        }
    }

    private void getStatus() {
        try {
            String html = "<html><body><h1>Current status: </h1><p>File Complenteness: " + VodServer.getCompleteness()
                    + " %"
                    + "<br>Average bit rate in 1 second: " + getAverageBitRateWithinInterval(1000) + " kbps"
                    + "<br>Average bit rate in 10 second: " + getAverageBitRateWithinInterval(10000) + " kbps"
                    + "<br>Average bit rate in 60 second: " + getAverageBitRateWithinInterval(60000) + " kbps"
                    + "</p></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // extract range from request
    private void setRange(String req) {
        String[] lines = req.split("\r\n");
        int start = 0;
        int end = 0;
        this.rangeNum = new int[] { 0, 0 };
        for (String l : lines) {
            // check if the line contains "Range: " field
            if (l.contains("Range: bytes=")) {
                int len = "Range: bytes=".length();
                String range = l.substring(len);
                String startNum = range.split("-")[0];
                String endNum = range.split("-")[1];
                start = Integer.parseInt(startNum);
                end = Integer.parseInt(endNum);
                this.rangeNum[0] = start;
                this.rangeNum[1] = end;
            }
        }
    }

    private String categorizeFile(String path) {
        try {
            // convert the file name into string
            String MIMEType = "";
            Path p = Paths.get(path);
            MIMEType = Files.probeContentType(p);
            return MIMEType;
        } catch (IOException e) {
            e.printStackTrace();
            return "Unacceptable file found";
        }
    }

    private void sendPartialContent(String MIMEType, int[] numRange, File f) {
        try {
            int rangeEnd = numRange[1];
            int rangeStart = numRange[0];
            int actualLength = rangeEnd - rangeStart + 1;
            String partialResponse = "HTTP/1.1 206 Partial Content" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + actualLength + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + f.length() + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(partialResponse);
            FileInputStream fileInputStream = new FileInputStream(f);
            byte[] buffer = new byte[rangeEnd];
            fileInputStream.read(buffer, rangeStart, rangeEnd);
            this.outputStream.write(buffer, 0, rangeEnd);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFullContent(String MIMEType, File f) {
        try {
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + f.length() + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Last-Modified: " + getGMTDate(f.lastModified()) + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(response);
            System.out.println("Response header sent ... ");
            int bytes = 0;
            // Open the File
            FileInputStream fileInputStream = new FileInputStream(f);

            // Here we break file into chunks
            byte[] buffer = new byte[1024];
            while ((bytes = fileInputStream.read(buffer)) != -1) {
                // Send the file
                this.outputStream.write(buffer, 0, bytes); // file content
                this.outputStream.flush(); // flush all the contents into stream
            }
            // close the file here
            fileInputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getGMTDate(Object date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(date);
    }

}
