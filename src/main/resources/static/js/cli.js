// for convenience
Date.prototype.ddmmyyyyHHMM = function () {
    return ('0' + this.getDate()).slice(-2) + "." + ('0' + (this.getMonth() + 1)).slice(-2) + "." + this.getFullYear() + " " + ('0' + this.getHours()).slice(-2) + ":" + ('0' + this.getMinutes()).slice(-2);
};

// aesthetics
document.body.style.background = 'black';
document.body.style.color = 'lime';

// global vars
let date = new Date();
let ws = null;
let term = document.createElement('div'); // setup terminal div; gonna put everything in here
document.body.appendChild(term);
let _input = null;
let _cursor = null;

// ez save&load
let vars = {
    "history": ["lynx https://github.com/Trig0n"], // self promotion
    "lastLoggedIn": date.ddmmyyyyHHMM(), // are we gonna have
    "currentLoggedIn": date.ddmmyyyyHHMM(), // different values?
    "currentDirectory": "~", // pwd of website.exe
    "tabCount": 0,
    "hasDoneInput": false
};

// ez cmd access
let commands = {
    "help": printUtil,
    "cd": cd,
    "ls": printUtil,
    "cat": printUtil,
    "exit": _exit,
    "startx": startx,
    "__init__": _init,
    "__wait__": _wait,
    "": printUtil
};

function printUtil(data) {
    terminalPrint(data["result"]);
}

function startx(data) {
    window.location.href.replace("https://eberlein.io/gui")
}

function _exit(data) {
    terminalPrint("logout");
    terminalPrint("Connection to eberlein.io closed.");
    onClose();
}

function cd(data) {
    if (data["success"]) {
        vars["currentDirectory"] = data["result"];
        terminalPrint("");
    } else {
        terminalPrint(data["result"])
    }
}

function _init(data) {
    if (vars["lastLoggedIn"] !== vars["currentLoggedIn"]) terminalPrint("last login: " + vars["lastLoggedIn"] + " by " + data["result"]);
}

function _wait(data) {
    ws.close();
    terminalPrint("there are " + data["result"]["position"] - 1 + " people in front of you. waiting " + data["result"]["time"] + "seconds.");
    setTimeout(initWebsocket, parseInt(data["result"]["time"]));
}

// init ws & register callbacks
function initWebsocket() {
    ws = new WebSocket("ws://" + location.hostname + ":" + location.port + "/termsock");
    ws.onmessage = onMessage;
    ws.onclose = onClose;
}

// process command results from server
function onMessage(ev) {
    let data = JSON.parse(ev.data);
    commands[data["cmd"]](data);
    terminalInput(inputPrefix());
    document.cookie = JSON.stringify(vars);
}

// should only happen at exit
function onClose() {
    _cursor.style.visibility = "hidden";
    _cursor = null;
    _input.setAttribute("contenteditable", false);
    _input = null;
    terminalPrint("connection closed by remote host");
    document.cookie = JSON.stringify(vars);
}

// more aesthetics
function blinkCursor() {
    setTimeout(function () {
        if (_cursor !== null) {
            _cursor.style.visibility = _cursor.style.visibility === "visible" ? "hidden" : "visible";
            blinkCursor();
        }
    }, 666);
}

// ""
function inputPrefix() {
    return "anon@eberlein.io:" + vars["currentDirectory"] + "$ ";
}

//
function evalKeyPress(e) {
    if (_input === null) return;
    switch (e.keyCode) {
        case 13: // enter
            processEnter();
            break;
        case 38: // up
            e.preventDefault();
            getLastHistoryItem();
            break;
        case 40: // down
            e.preventDefault();
            getPreviousHistoryItem();
            break;
        case 9: //tab
            e.preventDefault();
            getNearestCommand();
            vars["tabCount"] += 1;
            alert(vars["tabCount"]);
            break;
        default:
            vars["hasDoneInput"] = true;
    }
}

function getNearestCommand(cmd) {
    let p = "";
    for (let c in cmd) {
        let found = [];
        p += c;
        for (let k in commands.keys()) {
            if (k.startsWith(p)) found.push(k);
        }
        if (found.length === 1) {
            _input.textContent = found[0];
            vars["tabCount"] = 0;
        }
        if (found.length > 1 && vars["tabCount"] > 1) {
            let htmlFormattedCmds = "<table><tr>";
            for (let f in found) {
                htmlFormattedCmds += "<td>";
                htmlFormattedCmds += f;
                htmlFormattedCmds += "</td>";
            }
            htmlFormattedCmds += "</tr></table>";
            terminalPrint(htmlFormattedCmds);
            vars["tabCount"] = 0;
        }
    }
}

// because navigation ain't hard enough for normies
function getPreviousHistoryItem() {
    let tmp = _input.textContent;
    if (vars["history"].indexOf(tmp) !== -1) {
        if (vars["history"][vars["history"].indexOf(tmp) + 1] !== null) _input.textContent = vars["history"][vars["history"].indexOf(tmp) + 1];
    }
}

// ""
function getLastHistoryItem() {
    let tmp = _input.textContent;
    if (vars["history"].indexOf(tmp) !== -1) _input.textContent = vars["history"][vars["history"].indexOf(tmp) - 1];
    vars["history"].push(tmp); // in case its the first item
    _input.textContent = vars["history"][vars["history"].indexOf(tmp) - 1];
    vars["history"].splice(vars["history"].indexOf(tmp), 1);
}

// process cli
function processEnter() {
    _input.setAttribute("contenteditable", false);
    _cursor.style.visibility = "hidden";
    vars["history"].push(_input.textContent);
    ws.send(JSON.stringify({
        "cmd": _input.textContent,
        "cwd": vars.currentDirectory
    }));
}

// overwrite _input, _cursor and cli; append to container
function terminalInput(message) {
    let cli = document.createElement("div");
    cli.style.display = "inline-block";
    let prefix = document.createElement('span');
    prefix.textContent = message;
    _input = document.createElement('span');
    _input.setAttribute("contenteditable", true);
    _input.addEventListener("keydown", evalKeyPress);
    _cursor = document.createElement('span');
    _cursor.style.background = 'lime';
    _cursor.style.color = 'lime';
    _cursor.textContent = "_";
    cli.appendChild(prefix);
    cli.appendChild(_input);
    cli.appendChild(_cursor);
    term.appendChild(cli);
    _input.focus();
}

// maybe you where here before
function loadCookie() {
    let c = document.cookie === null ? JSON.parse(document.cookie) : vars;
    vars["lastLoggedIn"] = c["lastLoggedIn"];
    vars["currentDirectory"] = c["currentDirectory"];
    vars["history"] = c["history"]; // come back often enough and you're gonna fuck up your browser
}

// append to container
function terminalPrint(msg) {
    let clo = document.createElement("div");
    clo.style.display = "inline-block";
    let cli = document.createElement('div');
    cli.innerHTML = msg;
    cli.appendChild(clo);
    term.appendChild(cli);
}

// that span is a little small
document.onclick = function () {
    _input.focus();
};

// cli ain't for everyone
window.onbeforeunload = function () {
    if (confirm("do you need a graphical user interface?") === true) window.location.replace("https://eberlein.io/gui");
};

loadCookie();
initWebsocket();
blinkCursor();