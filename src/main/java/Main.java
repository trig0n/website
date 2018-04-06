import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

// todo fix events not being initialized so feed is empty


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
    private Logger log;
    private DateTimeFormatter DATE_FORMAT;

    Server() {
        jedis = new Jedis("localhost");
        jinja = new Jinjava();
        log = LoggerFactory.getLogger(Main.class);
        DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        initDatabase();
    }

    private void initDatabase() {
        if (jedis.get("stat.hits") == null) jedis.set("stat.hits", Integer.toString(0));
        if (jedis.get("stat.scans") == null) jedis.set("stat.scans", Integer.toString(0));
        jedis.set("stat.bootTime", LocalDateTime.now().format(DATE_FORMAT));
    }

    void main(Settings settings) {
        if (settings.jksFile != null && settings.jksPassword != null) {
            secure(settings.jksFile, settings.jksPassword, null, null);
            port(443);
            log.info("using " + settings.jksFile + "for ssl encryption over port" + settings.port);
        } else {
            port(settings.port);
        }
        staticFiles.location("static/");

        webSocket("/termsock", TerminalWebSocket.class);

        before("/*", (req, resp) -> checkRequest(req));

        get("/cli", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            return jinja.render(jedis.get("template.cli"), objs);
        });

        get("/", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            return jinja.render(jedis.get("template.gui"), objs);
        });

        path("/api", () -> {
            path("/content", () -> {
                post("/event", (req, resp) -> {
                    JSONObject cr = JSON.parseObject(req.body(), JSONObject.class);
                    return getEventHtml(cr.getString("name"));
                });

                post("/page", (req, resp) -> {
                    JSONObject cr = JSON.parseObject(req.body(), JSONObject.class);
                    JSONObject r = new JSONObject();
                    r.put("data", getPageHtml(cr.getString("name")));
                    return r.toJSONString();
                });

                get("/search", (req, resp) -> JSON.toJSONString(getSearchItems(JSON.parseObject(req.body()))));
            });

            post("/stat", (req, resp) -> {
                System.out.println(req.ip());
                JSONObject cr = JSON.parseObject(req.body(), JSONObject.class);
                JSONObject r = new JSONObject();
                r.put(cr.getString("string"), jedis.get("stat." + cr.getString("name")));
                return r.toJSONString();
            });

            get("/status", (req, resp) -> {
                resp.body("up");
                return resp;
            });

        });

        get("/robots.txt", (req, resp) -> {
            resp.body("User-Agent: *\r\nDisallow: /cli\n");
            resp.header("Content-Type", "text/plain");
            return resp.body();
        });

        awaitInitialization();
    }

    private List<JSONObject> getSearchItems(JSONObject o) {
        List<JSONObject> items = new ArrayList<>();
        for (String k : jedis.scan("0", new ScanParams().match("event." + o.getString("name").toLowerCase())).getResult())
            items.add(JSON.parseObject(jedis.get(k)));
        return items;
    }

    private String getEventHtml(String key) {
        return JSON.parseObject(jedis.get("event." + key)).getString("html");
    }

    private void checkRequest(Request r) {
        if (scanRequest(r)) jedis.incr("stat.scans");
        else jedis.incr("stat.hits");
        JSONObject old = JSON.parseObject(jedis.get("ip." + r.ip()), JSONObject.class);
        old = updatePathCounts(r, old); // todo update more
        jedis.set("ip." + r.ip(), JSON.toJSONString(old));
    }

    private JSONObject updatePathCounts(Request req, JSONObject old) {
        JSONObject pathCounts;
        if (old == null) old = new JSONObject();
        if (old.containsKey("count")) {
            pathCounts = old.getJSONObject("count");
            if (pathCounts.containsKey(req.pathInfo()))
                pathCounts.put(req.pathInfo(), pathCounts.getInteger(req.pathInfo()) + 1);
        } else {
            pathCounts = new JSONObject();
            pathCounts.put(req.pathInfo(), 1);
        }
        old.put("count", pathCounts);
        return old;
    }

    private boolean scanRequest(Request r) {
        return r.url().contains("wp-admin") || r.url().equals("/robots.txt");
        // lookup table of paths and parameters
    }

    private List<JSONObject> _collectSortedEvents() {
        List<JSONObject> events = new ArrayList<>();
        for (String key : jedis.scan("0", new ScanParams().match("event.*")).getResult()) {
            events.add(JSON.parseObject(jedis.get(key)));
        }
        Collections.sort(events, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject t0, JSONObject t1) {
                return t1.getInteger("id").compareTo(t0.getInteger("id"));
            }
        });
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
                objects.put("feed", _collectSortedEvents());
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
}