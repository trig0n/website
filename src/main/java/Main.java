import com.alibaba.fastjson.JSON;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.hubspot.jinjava.Jinjava;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static spark.Spark.*;

// todo fix js search onchange


public class Main {
    public static void main(String[] args) {
        Server s = new Server(new Settings(args));
        s.main();
    }
}

class Settings {
    String jksFile = null;
    String jksPassword = null;
    int port = 80;
    int mongodbPort = 27017;
    String mongodbUsername = null;
    String mongodbPassword = null;

    Settings(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--mongodb-port")) this.mongodbPort = Integer.valueOf(args[i + 1]);
            if (args[i].equals("--port")) this.port = Integer.valueOf(args[i + 1]);
            if (args[i].equals("--mongodb-username")) mongodbUsername = args[i + 1];
            if (args[i].equals("--mongodb-password")) mongodbPassword = args[i + 1];
            if (args[i].equals("--jks-pass")) this.jksPassword = args[i + 1];
            if (args[i].equals("--jks-file")) this.jksFile = args[i + 1];
            if (args[i].equals("-h") || args[i].equals("--help")) help();
        }
    }

    private void help() {
        System.out.println("usage: java -jar website.jar [arguments]");
        System.out.println("-h\t--help\tthis");
        System.out.println("--port");
        System.out.println("--mongodb-port");
        System.out.println("--mongodb-username");
        System.out.println("--mongodb-password");
        System.out.println("--jks-file\tkeystore file");
        System.out.println("--jks-pass\tkeystore password");
        System.out.println("exiting..");
        System.exit(0);
    }
}

class Server {
    private Date bootTime;
    private Jinjava jinja;
    private MongoCollection<Host> hosts;
    private MongoCollection<CountStatistic> stats;
    private Logger log;

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private static final String[] allowed_urls = {
            "/",
            "/gen/gui.css",
            "/api/content/event",
            "/api/content/page",
            "/api/content/search",
            "/api/stat",
            "/api/status"
    };
    private Map<String, String> allowed_fonts = new HashMap<>();
    private String guiCss;
    private List<Event> events;

    private Settings settings;

    Server(Settings settings) {
        this.settings = settings;
        jinja = new Jinjava();
        log = LoggerFactory.getLogger(Main.class);
        bootTime = new Date();
        initDatabase();
        loadGuiCss();
        loadEvents();
        initFonts();
    }

    private void initFonts() {
        allowed_fonts.put("'Unica One', cursive;", "https://fonts.googleapis.com/css?family=Unica+One");
        allowed_fonts.put("'Roboto', sans-serif;", "https://fonts.googleapis.com/css?family=Roboto");
        allowed_fonts.put("'Open Sans', sans-serif;", "https://fonts.googleapis.com/css?family=Open+Sans");
        allowed_fonts.put("'Lato', sans-serif;", "https://fonts.googleapis.com/css?family=Lato");
        allowed_fonts.put("'Raleway', sans-serif;", "https://fonts.googleapis.com/css?family=Raleway");
        allowed_fonts.put("'Noto Sans', sans-serif;", "https://fonts.googleapis.com/css?family=Noto+Sans");
        allowed_fonts.put("'Ubuntu', sans-serif;", "https://fonts.googleapis.com/css?family=Ubuntu");
        allowed_fonts.put("'Lora', serif;", "https://fonts.googleapis.com/css?family=Lora");
        allowed_fonts.put("'Titillium Web', sans-serif;", "https://fonts.googleapis.com/css?family=Titillium+Web");
        allowed_fonts.put("'Arimo', sans-serif;", "https://fonts.googleapis.com/css?family=Arimo");
        allowed_fonts.put("'Nunito', sans-serif;", "https://fonts.googleapis.com/css?family=Nunito");
        allowed_fonts.put("'Bitter', serif;", "https://fonts.googleapis.com/css?family=Bitter");
        allowed_fonts.put("'Quicksand', sans-serif;", "https://fonts.googleapis.com/css?family=Quicksand");
        allowed_fonts.put("'Great Vibes', cursive;", "https://fonts.googleapis.com/css?family=Great+Vibes");
        allowed_fonts.put("'Orbitron', sans-serif;", "https://fonts.googleapis.com/css?family=Orbitron");
        allowed_fonts.put("'Jura', sans-serif;", "https://fonts.googleapis.com/css?family=Jura");
        allowed_fonts.put("'Monoton', cursive;", "https://fonts.googleapis.com/css?family=Monoton");
        allowed_fonts.put("'Marck Script', cursive;", "https://fonts.googleapis.com/css?family=Marck+Script");
        allowed_fonts.put("'VT323', monospace;", "https://fonts.googleapis.com/css?family=VT323");
        allowed_fonts.put("'Glegoo', serif;", "https://fonts.googleapis.com/css?family=Glegoo");
        allowed_fonts.put("'Patrick Hand', cursive;", "https://fonts.googleapis.com/css?family=Patrick+Hand");
    }

    private void loadEvents() {
        events = new ArrayList<>();
        for (String e : getResources("static/templates/event")) {
            String n = e.substring(0, e.indexOf("."));
            Event t = new Event();
            t.setHtml(getResourceString("static/templates/event/" + e));
            t.setName(n);
            t.setDescription("todo");
            events.add(t);
        }
        Collections.reverse(events);
        System.out.println("found " + String.valueOf(events.size()) + " events");
    }

    private void loadGuiCss() {
        try {
            guiCss = Resources.toString(Resources.getResource("static/css/gui.css"), Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void initDatabase() {
        CodecRegistry pojoCodecRegistry = fromRegistries(com.mongodb.MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        MongoClientSettings.Builder mcsb = MongoClientSettings.builder();
        mcsb.applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress("127.0.0.1", settings.mongodbPort))));
        mcsb.codecRegistry(pojoCodecRegistry);
        if (settings.mongodbUsername != null && settings.mongodbPassword != null)
            mcsb.credential(MongoCredential.createCredential(settings.mongodbUsername, "eberlein", settings.mongodbPassword.toCharArray()));
        MongoClient client = MongoClients.create(mcsb.build());
        MongoDatabase db = client.getDatabase("eberlein");
        hosts = db.getCollection("host", Host.class);
        stats = db.getCollection("stat", CountStatistic.class);
        stats.createIndex(Indexes.ascending("count"));

        if (stats.find(eq("name", "hits")).first() == null) stats.insertOne(new CountStatistic("hits", 0));
        if (stats.find(eq("name", "scans")).first() == null) stats.insertOne(new CountStatistic("scans", 0));
    }

    private String getResourceString(String path) {
        try {
            return Resources.toString(Resources.getResource(path), Charsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private InputStream getResourceAsStream(String resource) {
        final InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        return in == null ? getClass().getResourceAsStream(resource) : in;
    }

    private List<String> getResources(String path) {
        try {
            List<String> fns = new ArrayList<>();
            InputStream in = getResourceAsStream(path);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String r;
            while ((r = br.readLine()) != null) fns.add(r);
            return fns;
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String getTemplatesString(String path) {
        return getResourceString("static/templates/" + path);
    }

    void main() {
        if (settings.jksFile != null && settings.jksPassword != null) {
            secure(settings.jksFile, settings.jksPassword, null, null);
            port(443);
            log.info("using " + settings.jksFile + " for ssl encryption over port 443");
        } else {
            port(settings.port);
        }

        staticFiles.location("static/");

        before("/*", (req, resp) -> checkRequest(req));

        get("/", (req, resp) -> {
            Map<String, Object> objs = new HashMap<>();
            return jinja.render(getTemplatesString("template/gui.html"), objs);
        });

        path("/gen", () -> {
            get("/gui.css", (req, resp) -> {
                String k = (String) allowed_fonts.keySet().toArray()[ThreadLocalRandom.current().nextInt(0, allowed_fonts.size())];
                return guiCss.replace("{{fontURL}}", allowed_fonts.get(k)).replace("{{fontFamily}}", k);
            });
        });

        path("/api", () -> {
            path("/content", () -> {
                post("/event", (req, resp) -> {
                    ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                    return JSON.toJSONString(new ContentResponse(cr.getName(), getResourceString("static/templates/event/" + cr.getName() + ".html")));
                });

                post("/page", (req, resp) -> {
                    ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                    return JSON.toJSONString(new ContentResponse(cr.getName(), getPageHtml(cr.getName())));
                });

                post("/search", (req, resp) -> {
                    ContentRequest cr = JSON.parseObject(req.body(), ContentRequest.class);
                    return getSearchItems(cr.getName()); // todo fix json encoding escaping everything too much
                });
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

    private String getSearchItems(String query) {
        HashMap<String, Object> o = new HashMap<>();
        List<Event> _events = new ArrayList<>();
        for (Event e : events) if (e.getDescription().contains(query) || e.getName().contains(query)) _events.add(e);
        o.put("feed", _events);
        if (_events.size() == 0) o.put("searchNone", true);
        return jinja.render(getTemplatesString("page/feed.html"), o);
    }

    private void checkRequest(Request r) {
        if (!Arrays.asList(allowed_urls).contains(r.pathInfo()))
            stats.updateOne(eq("name", "scans"), Updates.inc("value", 1));
        else stats.updateOne(eq("name", "hits"), Updates.inc("value", 1));

        Host h = hosts.find(eq("ip", r.ip())).first();
        if (h == null) {
            h = new Host();
            h.setIp(r.ip());
            h.addCount(new CountStatistic(r.pathInfo(), 1));
        } else h.incrementCount(r.pathInfo());
        h.addUseragent(r.userAgent());
        hosts.replaceOne(eq("ip", r.ip()), h);
    }

    private String getPageHtml(String key) {
        HashMap<String, Object> objects = new HashMap<>();
        objects.put("title", key);
        String data;
        switch (key) {
            case "about":
                data = getTemplatesString("page/about.html");
                break;
            case "contact":
                data = getTemplatesString("page/contact.html");
                break;
            case "feed":
                data = getTemplatesString("page/feed.html");
                objects.put("feed", events);
                break;
            case "home":
                data = getTemplatesString("page/home.html");
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