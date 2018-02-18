let cookie = {};

function setActive(menuId) {
    cookie["page"] = menuId;
    writeCookie(cookie);
    let btns = ["home", "about", "contact", "feed"];
    btns.splice(btns.indexOf(menuId), 1);
    for (let i = 0; i < btns.length; i++) document.getElementById(btns[i]).classList.remove("is-active");
    document.getElementById(menuId).classList.add('is-active');
    request("/api/content/page", "POST", JSON.stringify({"name": menuId}), gotContent);
}

function parseCookie() {
    let c = {};
    if (document.cookie.indexOf(";") !== -1) {
        let kv = document.cookie.split(";");
        for (let p in kv) {
            let _p = kv[p].split("=");
            if (!undefined in _p) c[_p[0]] = c[_p[1]];
        }
    }
    return c;
}

function writeCookie(c) {
    let v = "";
    for (let k in c) {
        v += (k + "=" + c[k] + ";")
    }
    document.cookie = v;
}

function gotContent(data) {
    data = JSON.parse(data);
    document.getElementById("searchResults").innerHTML = "";
    document.getElementById("content").innerHTML = data["data"];
    doEval(data["data"]);
}

function request(url, method, data, callback) {
    let xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function () {
        if (this.readyState === 4 && this.status === 200) callback(this.responseText);
    };
    xhr.open(method, url);
    if (data !== null) xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send(data);
}

function overwriteContent(data) {
    document.getElementById("searchResults").innerHTML = "";
    document.getElementById("content").innerHTML = data;
}

function loadImage(data) {
    document.getElementById(data["name"]).src = "data:image/png;base64," + data["data"];
}

function doEval(data) {
    if (data.indexOf("<script>") !== -1 && data.indexOf("</script>") !== -1) {
        eval(data.split("<script>")[1].split("</script>")[0]);
    }
}

function append(data) {
    data = JSON.parse(data);
    document.getElementById(data["name"]).innerHTML = "";
    if (data["name"] === "searchResults") document.getElementById("content").innerHTML = "";
    let _d = JSON.parse(data["data"]);
    for (let s in _d) {
        document.getElementById(data["name"]).innerHTML += _d[s];
        doEval(_d[s]);
    }
}

function execScript(data) {
    eval(data["data"]);
}

function search(data) {
    request("/api/search", "POST", JSON.stringify({"name": data}), append)
}

let p = null;
let m = null;

function initBackground() {
    p = Particles.init({selector: '.background', color: '#0f0f0f', connectParticles: true});
    m = new Mandelbrot(document.querySelector('canvas'), 2100);
}

function fillCanvas(color) {
    let canvas = document.querySelector('canvas');
    let c = canvas.getContext('2d');
    c.fillStyle = color;
    c.fillRect(0, 0, canvas.width, canvas.height);
}

function toggleLoading(c) {
    c.classList.toggle('is-loading')
}

function unregisterBackgrounds() {
    p.pauseAnimation();
    m.stopDrawing();
}

function dynamicBackground(c) {
    toggleLoading(c);
    unregisterBackgrounds();
    p.resumeAnimation();
    toggleLoading(c);
}

function whiteBackground(c) {
    toggleLoading(c);
    unregisterBackgrounds();
    fillCanvas("white");
    toggleLoading(c);
}

function mandelbrotBackground(c) {
    toggleLoading(c);
    unregisterBackgrounds();
    m.draw();
    toggleLoading(c);
}

function dynamicMandelbrotBackground(c) {
    toggleLoading(c);
    unregisterBackgrounds();
    m.drawProcedurally(42, 8400);
    toggleLoading(c);
}

window.onload = function () {
    cookie = parseCookie();
    initBackground();
    setActive("page" in cookie ? cookie["page"] : "home");
    document.getElementById('navToggle').addEventListener('click', function () {
        document.getElementById('navToggle').classList.toggle('is-active');
        document.getElementById('navMenu').classList.toggle('is-active');
    });
    dynamicBackground();
};

window.onbeforeunload = function () {
    writeCookie(cookie);
};