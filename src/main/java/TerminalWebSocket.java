import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

@WebSocket
public class TerminalWebSocket {
    private static final String CD_NOT_FOUND = "cd: {{d}}: No such file or directory";
    private static final String LS_NOT_FOUND = "ls: cannot access {{d}}: No such file or directory";
    private static final String CAT_NOT_FOUND = "cat: {{d}}: No such file or directory";
    private Logger log;
    private Map<Session, FileSystem> sessions;
    private Map<Session, Date> sessionTimes;
    private List<Session> queue;
    private MongoCollection<Template> templates;
    private MongoCollection<LongStatistic> stats;
    private MongoCollection<FileSystemStructure> fs;

    public TerminalWebSocket() {
        log = LoggerFactory.getLogger(TerminalWebSocket.class);
        sessions = new HashMap<>();
        sessionTimes = new HashMap<>();
        queue = new ArrayList<>();
        initDatabase();
    }

    private void initDatabase() {
        MongoDatabase db = new MongoClient().getDatabase("eberlein");
        templates = db.getCollection("template", Template.class);
        stats = db.getCollection("terminal_stats", LongStatistic.class);
        fs = db.getCollection("fs", FileSystemStructure.class);
    }

    @OnWebSocketConnect
    public void connected(Session session) {
        if (sessions.size() == 1024) send(session, new Result(generateWaitResponse(session), "__wait__"));
        else {
            queue.remove(session);
            sessions.put(session, createLegitFs(Jimfs.newFileSystem(Configuration.unix())));
            sessionTimes.put(session, new Date());
            send(session, new Result(session.getRemoteAddress().getAddress().toString().replace("/", ""), "__init__"));
        }
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason) {
        queue.remove(session);
        updateAvgTime(session);
        sessionTimes.remove(session);
        // todo store fs for reloading even after reboot
    }

    @OnWebSocketMessage
    public void message(Session session, String message) { // todo startx for gui
        Request r = JSON.parseObject(message, Request.class);
        Command c = new Command(r);
        log.info(session.getRemoteAddress().getAddress() + ":" + session.getRemoteAddress().getPort() + " - " + c.cmd + " " + c.args);
        switch (c.cmd) {
            case "ls":
                send(session, lsDirectory(sessions.get(session), new ls(r)));
                break;
            case "cat":
                send(session, catFile(sessions.get(session), new cat(r)));
                break;
            case "cd":
                send(session, cdDirectory(sessions.get(session), new cd(r)));
                break;
            case "help":
                send(session, help());
                break;
            case "exit":
                send(session, new Result("bye bye.", "exit"));
                break;
            default:
                send(session, new Result("command '" + c.cmd + "' not found; try 'help'", ""));
        }
    }

    private void updateAvgTime(Session s) {
        stats.replaceOne(eq("name", "avg"), new LongStatistic("avg", new Date().getTime() - sessionTimes.get(s).getTime()));
    }

    private String generateWaitResponse(Session session) {
        queue.add(session);
        JSONObject o = new JSONObject();
        o.put("position", queue.indexOf(session));
        o.put("time", stats.find(eq("name", "avg")).first().value);
        return JSON.toJSONString(o);
    }

    private Result help() {
        try {
            return new Result(Resources.toString(Resources.getResource("templates/help.html"), Charsets.UTF_8), "help");
        } catch (IOException e) {
            e.printStackTrace();
            return new Result("something went horribly wrong", "help");
        }

    }

    private Result catFile(FileSystem fs, cat c) {
        Result r = new Result(c.cmd, "cat");
        if (c.help) {
            r.result = "Usage: cat [FILE]<br>";
            r.success = false;
            return r;
        }
        if (c.files == null) {
            r.result = "<br>";
            r.success = false;
        } else {
            StringBuilder sb = new StringBuilder();
            for (String file : c.files) sb.append(readFile(fs, file));
            r.result = sb.toString();
            if (r.result.isEmpty()) {
                r.result = CAT_NOT_FOUND.replace("{{d}}", c.directory);
                r.success = false;
            } else {
                r.success = true;
            }
        }
        return r;
    }

    private String readFile(FileSystem fs, String path) {
        try {
            return new String(Files.readAllBytes(fs.getPath(path).toAbsolutePath()));
        } catch (IOException e) {
            return "";
        }
    }

    private Result cdDirectory(FileSystem fs, cd c) {
        Result r = new Result("", "cd");
        if (c.help) {
            r.result = "cd: usage: cd [dir]<br>";
            r.success = false;
            return r;
        }
        if (Files.isDirectory(fs.getPath(c.directory))) {
            if (c.directory.startsWith("/home/trig0n/")) c.directory = "~/" + c.directory.replace("/home/trig0n/", "");
            r.result = c.directory;
            r.success = true;
        } else {
            r.result = CD_NOT_FOUND.replace("{{d}}", c.directory);
            r.success = false;
        }
        return r;
    }

    private Result lsDirectory(FileSystem fs, ls c) {
        Result r = new Result("", "ls");
        if (c.help) {
            r.result = "Usage: ls [OPTION]... [FILE]<br><table><tr><td>-a</td><td>--all</td><td>do not ignore entries prefixed with .</td></tr>";
            r.result += "<tr><td>-R</td><td>--recursive</td><td>list subdirectories recursively</td></tr>";
            r.success = true;
            return r;
        }
        /*
        todo recursive
        ./AndroidStudioProjects/bluepwn/app/build/intermediates/res:
        .  ..  androidTest  debug  merged  symbol-table-with-package
         */
        if (Files.isDirectory(fs.getPath(c.directory))) {
            StringBuilder sb = new StringBuilder();
            try {
                Files.list(fs.getPath(c.directory).toAbsolutePath()).forEach(new Consumer<Path>() {
                    @Override
                    public void accept(Path path) {
                        if (!path.getFileName().toString().startsWith("."))
                            sb.append(path.getFileName()).append("<br>");
                        if (path.getFileName().toString().startsWith(".") && c.all)
                            sb.append(path.getFileName()).append("<br>");
                        if (c.recursive) listDirectory(fs, path.toString(), this); // todo fix
                    }
                });
            } catch (IOException e) {
                r.result = LS_NOT_FOUND.replace("{{d}}", c.directory);
                r.success = false;
            }
            r.success = true;
            r.result = sb.toString();
        } else {
            r.result = LS_NOT_FOUND.replace("{{d}}", c.directory);
            r.success = false;
        }
        return r;
    }

    private void listDirectory(FileSystem fs, String path, Consumer<Path> consumer) {
        try {
            Files.list(fs.getPath(path)).forEach(consumer);
        } catch (IOException e) { /* todo */}
    }

    private FileSystem createLegitFs(FileSystem fs) {
        try {
            _createRoot(fs);
            _createHome(fs);
            _createLogs(fs);
            _createEtc(fs);
            _createFiles(fs);
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
        return fs;
    }

    private void _createFiles(FileSystem fs) throws IOException, NullPointerException {
        for (File f : this.fs.find().first().files) {
            Path path = fs.getPath(f.path);
            Files.createDirectories(path);
            Path p = path.resolve(f.path);
            Files.write(p, ImmutableList.of(f.data), StandardCharsets.UTF_8);
        }
    }

    private void _createRoot(FileSystem fs) throws IOException {
        for (String p : new String[]{"bin", "boot", "etc", "home", "lib", "lib32", "lib64", "media", "mnt", "opt", "proc", "root", "srv", "run", "sys", "usr", "var"}) {
            Files.createDirectories(fs.getPath("/" + p));
        }
    }

    private void _createEtc(FileSystem fs) throws IOException {
        for (String p : new String[]{
                "apt", "cron.d", "cron.daily", "cron.hourly", "cron.monthly", "dconf", "default", "dhcp", "dpkg",
                "grub.d", "init", "init.d", "kernel", "ld.so.conf.d", "logrotate.d", "initramfs-tools", "iproute2",
                "perl", "php", "ppp", "mysql", "network", "mono", "opt", "ufw", "tmpfiles.d", "udev", "xml", "X11",
                "python", "mono", "fonts", "dictionaries-common", "doc-base", "lvm", "pam.d", "modprobe.d"
        }) {
            Files.createDirectories(fs.getPath("/etc/" + p));
        }
    }

    private void _createHome(FileSystem fs) throws IOException {
        for (String p : new String[]{"Documents", "Downloads", "backup", ".ssh", "Pictures", "Videos", "Public"}) {
            Files.createDirectories(fs.getPath("/home/trig0n/" + p));
        }
    }

    private void _createLogs(FileSystem fs) throws IOException {
        for (String p : new String[]{"apt", "dist-upgrade", "fsck", "ligthdm", "mongodb", "upstart"}) {
            Files.createDirectories(fs.getPath("/var/" + p));
        }
    }

    private void send(Session session, Result result) {
        try {
            session.getRemote().sendString(JSON.toJSONString(result));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Result {
    String result;
    String cmd;
    boolean success;

    Result() {
    }

    Result(String result, String cmd) {
        this.result = result;
        this.cmd = cmd;
    }
}

class Command {
    String cmd;
    String data;
    String directory = null;
    List<String> args;
    boolean help = false;

    Command() {
    }

    Command(String cmd, List<String> args, String cwd) {
        this.cmd = cmd;
        this.args = args;
        _fixCwd(cwd);
        _needsHelp();
    }

    Command(String msg, String cwd) {
        _init(msg);
        _fixCwd(cwd);
        _needsHelp();
    }

    Command(Request request) {
        _init(request.cmd);
        _fixCwd(request.cwd);
        _needsHelp();
    }

    private void _fixCwd(String cwd) {
        data = cwd.endsWith("/") ? cwd : (cwd += "/");
    }

    private void _needsHelp() {
        for (String arg : args) {
            if (arg.equals("--help")) help = true;
        }
    }

    private void _init(String msg) {
        List<String> _msg = Arrays.asList(msg.split(" "));
        cmd = _msg.get(0);
        if (_msg.size() > 1) args = _msg.subList(1, _msg.size());
        else args = new ArrayList<>();
    }

    String directoryChecks(String path) {
        if (path == null || path.equals(".")) path = data;
        if (path.equals("..")) {
            path = String.join(Arrays.asList(path.split("/")).remove(path.split("/").length - 2));
        }
        if (path.isEmpty()) path = data;
        if (!path.endsWith("/")) path += "/";
        if (!path.startsWith("/") && !path.startsWith("~") && !path.startsWith(".")) path = data + path;
        if (path.startsWith("./")) path = data + path.replace("./", "");
        if (path.startsWith("~") || path.startsWith("~/"))
            path = "/home/trig0n/" + path.replace(path.startsWith("~/") ? "~/" : "~", "");
        return path;
    }
}

class ls extends Command {
    boolean all = false;
    boolean recursive = false;
    String directory;

    ls(Request request) {
        super(request);
        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (arg.equals("--all")) all = true;
                if (arg.equals("--recursive")) recursive = true;
            } else if (arg.startsWith("-")) {
                if (arg.contains("a")) all = true;
                else if (arg.contains("R")) recursive = true;
                else help = true;
            } else {
                directory = arg;
            }
        }
        directory = directoryChecks(directory);
    }
}

class cd extends Command {
    cd(Request request) {
        super(request);
        if (args.size() < 1) directory = "/home/trig0n";
        if (args.size() == 1) directory = args.get(0);
        directory = directoryChecks(directory);
    }
}

class cat extends Command {
    List<String> files;

    cat(Request request) {
        super(request);
        if (args.size() > 0) {
            for (String arg : args) files.add(directoryChecks(arg));
        } else files = null;
    }
}

class Request {
    String cmd;
    String cwd;

    Request() {
    }
}