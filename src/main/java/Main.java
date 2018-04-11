import com.alibaba.fastjson.JSON;
import com.hubspot.jinjava.Jinjava;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.text;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static spark.Spark.*;

// todo fix js search onchange


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
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private Date bootTime;
    private Jinjava jinja;
    private MongoCollection<Event> events;
    private MongoCollection<DataEntity> pages;
    private MongoCollection<Host> hosts;
    private MongoCollection<CountStatistic> stats;
    private MongoCollection<DataEntity> templates;
    private Logger log;

    Server() {
        jinja = new Jinjava();
        log = LoggerFactory.getLogger(Main.class);
        bootTime = new Date();
        initDatabase();
    }

    private void initDatabase() {
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        MongoClient client = new MongoClient("localhost", MongoClientOptions.builder().codecRegistry(pojoCodecRegistry).build());
        MongoDatabase db = client.getDatabase("eberlein");
        events = db.getCollection("event", Event.class);
        pages = db.getCollection("page", DataEntity.class);
        hosts = db.getCollection("host", Host.class);
        templates = db.getCollection("template", DataEntity.class);
        stats = db.getCollection("stat", CountStatistic.class);
        stats.createIndex(Indexes.ascending("count"));

        if (stats.find(eq("name", "hits")).first() == null) stats.insertOne(new CountStatistic("hits", 0));
        if (stats.find(eq("name", "scans")).first() == null) stats.insertOne(new CountStatistic("scans", 0));
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
            return jinja.render(templates.find(eq("name", "cli")).first().getData(), objs);
        });

        get("/", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            return jinja.render(templates.find(eq("name", "gui")).first().getData(), objs);
        });

        path("/api", () -> {
            path("/content", () -> {
                post("/event", (req, resp) -> {
                    ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                    return JSON.toJSONString(new ContentResponse(cr.getName(), getEventHtml(cr.getName())));
                });

                post("/page", (req, resp) -> {
                    System.out.println(req.body());
                    ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                    return JSON.toJSONString(new ContentResponse(cr.getName(), getPageHtml(cr.getName())));
                });

                get("/search", (req, resp) -> JSON.toJSONString(getSearchItems(JSON.parseObject(req.body(), ContentRequest.class).getData())));
            });

            post("/stat", (req, resp) -> {
                System.out.println(req.ip());
                ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                return JSON.toJSONString(new ContentResponse(cr.getName(), Integer.toString(stats.find(eq("name", cr.getName())).first().getValue())));
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

    private List<Event> getSearchItems(String query) {
        List<Event> items = new ArrayList<>();
        for (Event e : events.find(text(query)).projection(Projections.metaTextScore("score")).sort(Sorts.metaTextScore("score")))
            items.add(e);
        return items;
    }

    private String getEventHtml(String key) {
        return events.find(eq("name", key)).first().getHtml();
    }

    private void checkRequest(Request r) {
        if (scanRequest(r)) stats.updateOne(eq("name", "scans"), new BasicDBObject("$inc", 1));
        else {
            CountStatistic c = stats.find(eq("name", "hits")).first();
            if (c == null) c = new CountStatistic("hits", 1);
            else c.increment(1);
            stats.replaceOne(eq("name", "hits"), c);
        }
        Host h = hosts.find(eq("ip", r.ip())).first();
        if (h == null) {
            List<CountStatistic> c = new ArrayList<>();
            c.add(new CountStatistic(r.pathInfo(), 1));
            h = new Host(r.ip(), c);
        } else h.incrementCount(r.pathInfo());
        hosts.replaceOne(eq("ip", r.ip()), h);
    }

    private boolean scanRequest(Request r) {
        return r.url().contains("wp-admin") || r.url().equals("/robots.txt");
        // todo lookup table of paths and parameters
    }

    private String getPageHtml(String key) {
        HashMap<String, Object> objects = new HashMap<>();
        objects.put("title", key);
        String data;
        switch (key) {
            case "about":
                data = pages.find(eq("name", "about")).first().getData();
                break;
            case "contact":
                data = pages.find(eq("name", "contact")).first().getData();
                break;
            case "feed":
                data = pages.find(eq("name", "feed")).first().getData();
                objects.put("feed", events.find().sort(Sorts.descending("id")));
                break;
            case "home":
                data = pages.find(eq("name", "home")).first().getData();
                objects.put("hits", stats.find(eq("name", "hits")).first().getValue());
                objects.put("scans", stats.find(eq("name", "scans")).first().getValue());
                objects.put("bootTime", dateFormat.format(bootTime.getTime()));
                objects.put("runTime", TimeUnit.MILLISECONDS.toHours(new Date().getTime() - bootTime.getTime()));
                break;
            default:
                data = "<h4>404</h4><br>" + key + " not found.";
        }
        return jinja.render(data, objects);
    }
}