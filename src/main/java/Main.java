import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hubspot.jinjava.Jinjava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import spark.Request;

import java.lang.management.ManagementFactory;
import java.util.*;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
        Server s = new Server();
        s.main(new Settings(args));
    }
}

class Settings {
    int port;
    String jksFile;
    String jksPassword;

    Settings(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-p") || args[i].equals("--port")) this.port = new Integer(args[i + 1]);
            if (args[i].equals("--jks-pass")) this.jksPassword = args[i + 1];
            if (args[i].equals("--jks-file")) this.jksFile = args[i + 1];
            if (args[i].equals("-h") || args[i].equals("--help")) help();
        }
    }

    private void help() {
        System.out.println("usage: java -jar website.jar [arguments]");
        System.out.println("-h\t--help\tthis");
        System.out.println("-p\t--port\tport");
        System.out.println("--jks-file\tkeystore file");
        System.out.println("--jks-pass\tkeystore password");
        System.out.println("exiting..");
        System.exit(0);
    }
}

class Server {
    private Jinjava jinja;
    private Jedis jedis;
    private Gson gson;
    private Logger log;

    Server() {
        jedis = new Jedis("localhost");
        jinja = new Jinjava();
        gson = new Gson();
        log = LoggerFactory.getLogger(Main.class);
        initDatabase();
    }

    private void initDatabase() {
        if (jedis.get("stat.hits") == null) jedis.set("stat.hits", Integer.toString(0));
        if (jedis.get("stat.scans") == null) jedis.set("stat.scans", Integer.toString(0));
        if (jedis.get("stat.uptime") == null)
            jedis.set("stat.uptime", Long.toString(ManagementFactory.getRuntimeMXBean().getUptime()));
    }

    private JsonObject updatePathCounts(Request req, JsonObject old) {
        JsonObject pathCounts;
        if (old == null) old = new JsonObject();
        if (old.has("count")) {
            pathCounts = old.get("count").getAsJsonObject();
            if (pathCounts.has(req.pathInfo()))
                pathCounts.addProperty(req.pathInfo(), pathCounts.get(req.pathInfo()).getAsInt() + 1);
        } else {
            pathCounts = new JsonObject();
            pathCounts.addProperty(req.pathInfo(), 1);
        }
        old.add("count", pathCounts);
        return old;
    }

    public void main(Settings settings) {
        if (settings.jksFile != null && settings.jksPassword != null) {
            secure(settings.jksFile, settings.jksPassword, null, null);
            port(443);
            log.info("using " + settings.jksFile + "for ssl encryption over port" + settings.port);
        } else {
            port(settings.port);
        }
        staticFiles.location("static/");
        webSocket("/termsock", TerminalWebSocket.class);

        before("/*", (req, resp) -> {
            JsonObject old = gson.fromJson(jedis.get("ip." + req.ip()), JsonObject.class);
            old = updatePathCounts(req, old); // todo update more
            jedis.set("ip." + req.ip(), gson.toJson(old));
        });

        get("/cli", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            resp.body(jinja.render(jedis.get("template.cli"), objs));
            return resp;
        });

        get("/", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            resp.body(jinja.render(jedis.get("template.gui"), objs));
            return resp;
        });

        path("/api", () -> {
            path("/content", () -> {
                post("/event", (req, resp) -> {
                    resp.body(getEventHtml(gson.fromJson(req.body(), ContentRequest.class).name));
                    return resp;
                });

                post("/page", (req, resp) -> {
                    ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                    resp.body(gson.toJson(new ContentResponse(cr.name, getPageHtml(cr.name))));
                    return resp;
                });

                get("/feed", (req, resp) -> {
                    resp.body(gson.toJson(new ContentResponse("feedItems", gson.toJson(collectEvents()))));
                    return resp;
                });

                post("/image", (req, resp) -> {
                    ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                    resp.body(gson.toJson(getImage(cr.name)));
                    return resp;
                });
            });

            post("/stat", (req, resp) -> {
                ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                System.out.println(cr);
                resp.body(gson.toJson(new ContentResponse(cr.name, jedis.get("stat." + cr.name))));
                return resp;
            });

            get("/status", (req, resp) -> {
                resp.body("up");
                return resp;
            });

            post("/search", (req, resp) -> {
                ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                resp.body(gson.toJson(new ContentResponse("searchResults", gson.toJson(collectEvents(cr.name)))));
                return resp;
            });

        });

        after((req, resp) -> resp.header("Content-Encoding", "gzip"));

        awaitInitialization();
    }

    private ContentResponse getImage(String name) {
        ContentResponse cr = new ContentResponse();
        if (!name.contains(".")) {
            cr.name = name;
            cr.data = "";
        } else {
            cr.name = name;
            cr.data = jedis.get("img." + name);
        }
        return cr;
    }

    private List<Event> _collectSortedEvents() {
        List<Event> events = new ArrayList<>();
        for (String key : jedis.scan("0", new ScanParams().match("event.*")).getResult())
            events.add(gson.fromJson(jedis.get(key), Event.class));
        Collections.sort(events, Comparator.comparingInt(Event::getId).reversed());
        return events;
    }

    private List<String> collectEvents(String partOf) {
        List<Event> f = new ArrayList<>();
        for (Event e : _collectSortedEvents()) {
            if (e.name.contains(partOf)) f.add(e);
        }
        List<String> r = new ArrayList<>();
        for (Event e : f) r.add(jinja.render(jedis.get("template.feedItem"), e.toHashMap()));
        return r;
    }

    private List<String> collectEvents() {
        List<Event> events = _collectSortedEvents();
        List<String> r = new ArrayList<>();
        for (Event e : events) r.add(jinja.render(jedis.get("template.feedItem"), e.toHashMap()));
        return r;
    }

    private String getPageHtml(String key) {
        String _p = jedis.get("page." + key);
        String p = _p == null ? "<h4>404</h4><br>" + key + " not found." : _p;
        HashMap<String, Object> objects = new HashMap<>();
        objects.put("title", key);
        return jinja.render(p, objects);
    }

    private String getEventHtml(String key) {
        return gson.fromJson(jedis.get("event." + key), Event.class).html;
    }

    /*
    private class SearchRequest {
        String data;
        SearchRequest(){}
    }
    */

    private class Event {
        Integer id;
        String name;
        String description;
        String html;

        Event() {
        }

        HashMap<String, Object> toHashMap() {
            HashMap<String, Object> hm = new HashMap<>();
            hm.put("name", name);
            hm.put("description", description);
            return hm;
        }

        Integer getId() {
            return id;
        }
    }

    private class ContentRequest {
        String name;

        ContentRequest() {
        }
    }

    private class ContentResponse {
        String name;
        String data;

        ContentResponse() {
        }

        ContentResponse(String name, String data) {
            this.name = name;
            this.data = data;
        }
    }
    /*
    private String getCountryCode(String ip){
        try{
            InetAddress addr = InetAddress.getByName(ip);
            return geoip.city(addr).getCountry().getIsoCode().toLowerCase();
        } catch (IOException | GeoIp2Exception e){
            return "en";
        }
    }
    */
}