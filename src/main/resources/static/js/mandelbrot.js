function Mandelbrot(canvas, magnification) {
    this.canvas = canvas === null ? document.querySelector('canvas') : canvas;
    this.magnification = magnification === null ? this.randomIntBetween(420, 666) : magnification;
    this.panX = 1.42;
    this.panY = 0.42;
    this.interval = null;
    this._setCanvasSize();
    window.onresize = this._setCanvasSize;
}

Mandelbrot.prototype._setCanvasSize = function () {
    this.canvas.height = window.innerHeight;
    this.canvas.width = window.innerWidth;
};

Mandelbrot.prototype._inSet = function (x, y, maxIterations = 420) {
    let cReal = x;
    let cImaginary = y;
    for (let i = 0; i < maxIterations; i++) {
        let tcReal = cReal * cReal - cImaginary * cImaginary + x;
        let tcImaginary = 2 * cReal * cImaginary + y;
        cReal = tcReal;
        cImaginary = tcImaginary;
        if (cReal * cImaginary > 4) return i / maxIterations * 100;
    }
    return 0;
};

Mandelbrot.prototype.generate = function (panX, panY) {
    let canvasContext = this.canvas.getContext('2d');
    for (let x = 0; x < this.canvas.width; x++) {
        for (let y = 0; y < this.canvas.height; y++) {
            let inSet = this._inSet(x / this.magnification - panX, y / this.magnification - panY);
            if (inSet === 0) {
                canvasContext.fillStyle = "#000000";
            }
            else {
                canvasContext.fillStyle = "hsl(69, 69%, " + inSet + "%)";
            }
            canvasContext.fillRect(x, y, 1, 1);
        }
    }
};

Mandelbrot.prototype.randomIntBetween = function (min, max, floatPoints = 1) {
    return (Math.random() * (max - min) + min).toFixed(floatPoints);
};

Mandelbrot.prototype.draw = function () {
    this.generate(this.panX, this.panY);
};

Mandelbrot.prototype.drawProcedurally = function (zoom = 21, interval = 4200) {
    this.interval = setInterval(function () {
        this.draw();
        this.magnification += zoom;
    }.bind(this), interval);
};

Mandelbrot.prototype.stopDrawing = function () {
    window.clearInterval(this.interval);
};