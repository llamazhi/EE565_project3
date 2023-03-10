import java.net.URI;
import java.net.URISyntaxException;

public class HTTPURIParser {
    URI uriObj;
    String path;

    public HTTPURIParser(String URI) {
        try {
            this.uriObj = new URI(URI);
            this.path = uriObj.getPath();
            System.out.println(this.path);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    // Returns all the queries contained in the uri, splitting them by "&"
    public String[] getQueries() {
        return this.uriObj.getQuery().split("&");
    }

    public boolean hasUDPRequest() {
        return (this.path.startsWith("/peer"));
    }

    public String getPath() {
        return this.path;
    }

    // Return if the uri contains "add" keyword
    public boolean hasAdd() {
        return this.path.equals("/peer/add");
    }

    // Return if the uri contains "view" keyword
    public boolean hasView() {
        return this.path.startsWith("/peer/view");
    }

    // Return if the uri contains "config" keyword
    public boolean hasConfig() {
        return this.path.equals("/peer/config");
    }

    // Return if the uri contains "status" keyword
    public boolean hasStatus() {
        return this.path.equals("/peer/status");
    }

    public boolean hasKill() {
        return this.path.equals("/peer/kill");
    }

    public boolean hasUUID() {
        return this.path.equals("/peer/uuid");
    }

    public boolean hasNeighbors() {
        return this.path.equals("/peer/neighbors");
    }

    public boolean hasAddNeighbor() {
        return this.path.startsWith("/peer/addneighbor");
    }

    public boolean hasMap() {
        return this.path.equals("/peer/map");
    }

    public boolean hasRank() {
        return this.path.startsWith("/peer/rank");
    }

}
