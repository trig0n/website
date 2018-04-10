let cookie = {};

function setActive(name) {
    setCookiePage(name);
    setSideNavButtonActive(name);
    request("/api/content/page", "POST", JSON.stringify({"name": name}), setContent);
}

function setSideNavButtonActive(name) {
    let btns = ["home", "about", "contact", "feed"];
    btns.splice(btns.indexOf(name), 1);
    for (let i = 0; i < btns.length; i++) document.getElementById(btns[i]).classList.remove("is-active");
    document.getElementById(name).classList.add('is-active');
}

function setCookiePage(page) {
    cookie["page"] = page;
    writeCookie(cookie);
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

function setContent(data) {
    document.getElementById("searchResults").innerHTML = "";
    document.getElementById("content").innerHTML = JSON.parse(data)["data"];
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

function searchResults(data) {
    alert(data);
    data = JSON.parse(data);
    if (data !== undefined) {
        setContent(data["data"]);
    }
    else document.getElementById("searchResults").innerHTML = '<div class="notification is-warning"><button class="delete">sorry. could not find anything</button>';
}

function search(data) {
    request("/api/content/search", "POST", JSON.stringify({"name": data}), searchResults);
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

function unregisterBackgrounds() {
    p.pauseAnimation();
    m.stopDrawing();
}

function dynamicBackground() {
    unregisterBackgrounds();
    p.resumeAnimation();
}

function whiteBackground() {
    unregisterBackgrounds();
    fillCanvas("white");
}

function mandelbrotBackground() {
    unregisterBackgrounds();
    m.draw();
}

function dynamicMandelbrotBackground(c) {
    unregisterBackgrounds();
    m.drawProcedurally(42, 8400);
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