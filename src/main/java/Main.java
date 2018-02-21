import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hubspot.jinjava.Jinjava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import spark.Request;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private DateTimeFormatter DATE_FORMAT;

    Server() {
        jedis = new Jedis("localhost");
        jinja = new Jinjava();
        gson = new Gson();
        log = LoggerFactory.getLogger(Main.class);
        DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        initDatabase();
    }

    private void initDatabase() {
        if (jedis.get("stat.hits") == null) jedis.set("stat.hits", Integer.toString(0));
        if (jedis.get("stat.scans") == null) jedis.set("stat.scans", Integer.toString(0));
        jedis.set("stat.bootTime", LocalDateTime.now().format(DATE_FORMAT));
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
            checkRequest(req);
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
                    ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                    resp.body(getEventHtml(cr.name));
                    return resp;
                });

                post("/page", (req, resp) -> {
                    ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                    resp.body(gson.toJson(new ContentResponse(cr.name, getPageHtml(cr.name))));
                    return resp;
                });

                post("/search", (req, resp) -> {
                    ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                    resp.body(gson.toJson(new ContentResponse("searchResults", getSearchHtml(cr.name))));
                    return resp;
                });
            });

            post("/stat", (req, resp) -> {
                System.out.println(req.ip());
                ContentRequest cr = gson.fromJson(req.body(), ContentRequest.class);
                resp.body(gson.toJson(new ContentResponse(cr.name, jedis.get("stat." + cr.name))));
                return resp;
            });

            get("/status", (req, resp) -> {
                resp.body("up");
                return resp;
            });

        });

        get("/robots.txt", (req, resp) -> {
            resp.body("User-Agent: *\r\nDisallow: /cli\n");
            resp.header("Content-Type", "text/plain");
            return resp;
        });

        after((req, resp) -> resp.header("Content-Encoding", "gzip"));

        awaitInitialization();
    }

    private String getSearchHtml(String key) {
        HashMap<String, Object> o = new HashMap<>();
        o.put("feed", eventsToHashMaps(collectEvents(key)));
        return jinja.render(jedis.get("page.feed"), o);
    }

    private String getEventHtml(String key) {
        return gson.fromJson(jedis.get("event." + key), Event.class).html;
    }

    private void checkRequest(Request r) {
        if (scanRequest(r)) jedis.incr("stat.scans");
        else jedis.incr("stat.hits");
        JsonObject old = gson.fromJson(jedis.get("ip." + r.ip()), JsonObject.class);
        old = updatePathCounts(r, old); // todo update more
        jedis.set("ip." + r.ip(), gson.toJson(old));
    }

    private boolean scanRequest(Request r) {
        if (r.url().equals("/robots.txt")) return true;
        if (r.url().contains("wp-admin")) return true;
        // lookup table of paths and parameters
        return false;
    }

    private List<Event> _collectSortedEvents() {
        List<Event> events = new ArrayList<>();
        for (String key : jedis.scan("0", new ScanParams().match("event.*")).getResult()) {
            events.add(gson.fromJson(jedis.get(key), Event.class));
        }
        Collections.sort(events, Comparator.comparingInt(Event::getId).reversed());
        return events;
    }

    private List<Event> collectEvents(String partOf) {
        List<Event> f = new ArrayList<>();
        for (Event e : _collectSortedEvents()) {
            System.out.println(e.name);
            if (e.name.contains(partOf)) f.add(e);
        }
        return f;
    }

    private List<HashMap<String, Object>> eventsToHashMaps(List<Event> e) {
        List<HashMap<String, Object>> events = new ArrayList<>();
        for (Event _e : e) events.add(_e.toHashMap());
        return events;
    }

    private String getPageHtml(String key) {
        HashMap<String, Object> objects = new HashMap<>();
        objects.put("title", key);
        String data;
        switch (key) {
            case "about":
                data = jedis.get("page.about");
                break;
            case "contact":
                data = jedis.get("page.contact");
                break;
            case "feed":
                data = jedis.get("page.feed");
                objects.put("feed", eventsToHashMaps(_collectSortedEvents()));
                break;
            case "home":
                data = jedis.get("page.home");
                objects.put("hits", jedis.get("stat.hits"));
                objects.put("scans", jedis.get("stat.scans"));
                String bootTime = jedis.get("stat.bootTime");
                objects.put("bootTime", bootTime);
                objects.put("runTime", LocalDateTime.parse(bootTime, DATE_FORMAT).until(LocalDateTime.now(), ChronoUnit.HOURS));
                break;
            default:
                data = "<h4>404</h4><br>" + key + " not found.";
        }
        return jinja.render(data, objects);
    }

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