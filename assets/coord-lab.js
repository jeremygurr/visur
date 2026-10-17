/* ── Reusable cursor coordinate lab ─────────────────────────────────────────
   Mirrors CoordinatesDC.calcCAAndNL / calcCXCYAndNL in the browser so a
   learner can convert between ca (absolute index) and (cx, cy) (line/column)
   and get instant feedback.

   Usage:
     <div class="coord-lab">
       <script type="application/json">{ "nl": [12, 25, 32] }</script>
     </div>

   nl = next-line indices: nl[k] is the absolute index where logical line k+1
   begins; the final entry is the content length (the tail position).

   Two modes:
     - sandbox  : pick a source side, the other side derives live.
     - predict  : the derived side is hidden; type a guess, hit Check.
*/
(function () {
  "use strict";

  function parseNL(str) {
    return String(str)
      .split(/[,\s]+/)
      .map(function (s) { return s.trim(); })
      .filter(function (s) { return s !== ""; })
      .map(Number)
      .filter(function (n) { return Number.isFinite(n) && n >= 0; });
  }

  function nlToString(nl) { return "[" + nl.join(", ") + "]"; }

  // CoordinatesDC.calcCAAndNL
  function deriveCA(nl, cx, cy) {
    var lineStart = cy > 0 ? nl[cy - 1] : 0;
    return lineStart + cx;
  }

  // CoordinatesDC.calcCXCYAndNL
  function deriveCXCY(nl, ca) {
    var cy;
    for (cy = 0; cy < nl.length; cy++) {
      var nextLineStart = nl[cy];
      if (nextLineStart > ca || cy === nl.length - 1) break;
    }
    var currentLineStart = cy > 0 ? nl[cy - 1] : 0;
    return { cx: ca - currentLineStart, cy: cy };
  }

  function validNL(nl) {
    if (!nl.length) return false;
    for (var i = 1; i < nl.length; i++) {
      if (nl[i] < nl[i - 1]) return false;
    }
    return true;
  }

  function el(tag, className, text) {
    var n = document.createElement(tag);
    if (className) n.className = className;
    if (text !== undefined && text !== null) n.textContent = text;
    return n;
  }

  function field(labelText, size) {
    var wrap = el("label", "coord-field");
    wrap.appendChild(el("span", "coord-field-label", labelText));
    var input = document.createElement("input");
    input.type = "text";
    input.autocomplete = "off";
    input.spellcheck = false;
    if (size) input.size = size;
    wrap.appendChild(input);
    return { wrap: wrap, input: input };
  }

  function render(root, cfg) {
    var state = {
      nl: Array.isArray(cfg.nl) && cfg.nl.length ? cfg.nl.slice() : [12, 25, 32],
      ca: 17,
      cx: 5,
      cy: 1,
      dir: "fromCA",
      predict: false
    };
    var fresh = deriveCXCY(state.nl, state.ca);
    state.cx = fresh.cx;
    state.cy = fresh.cy;

    var nlField = field("nl", 18);
    nlField.input.value = nlToString(state.nl);
    nlField.input.style.width = "13em";

    var caField = field("ca", 6);
    var cxField = field("cx", 6);
    var cyField = field("cy", 6);

    var arrow = el("span", "coord-arrow", "\u2192");
    var reverse = el("button", "coord-btn", "swap direction");
    reverse.type = "button";

    var sandboxRow = el("div", "coord-row");
    sandboxRow.appendChild(nlField.wrap);
    var convertRow = el("div", "coord-row");
    convertRow.appendChild(caField.wrap);
    convertRow.appendChild(arrow);
    convertRow.appendChild(cxField.wrap);
    convertRow.appendChild(cyField.wrap);
    convertRow.appendChild(reverse);

    var status = el("div", "coord-status");

    var predictBtn = el("button", "coord-btn", "predict mode");
    predictBtn.type = "button";
    var checkBtn = el("button", "coord-btn coord-check", "check");
    checkBtn.type = "button";
    checkBtn.style.display = "none";
    var randomBtn = el("button", "coord-btn", "random ca");
    randomBtn.type = "button";

    var buttons = el("div", "coord-buttons");
    buttons.appendChild(predictBtn);
    buttons.appendChild(randomBtn);
    buttons.appendChild(checkBtn);

    var feedback = el("div", "coord-feedback");

    root.appendChild(sandboxRow);
    root.appendChild(convertRow);
    root.appendChild(buttons);
    root.appendChild(status);
    root.appendChild(feedback);

    // ── render helpers ────────────────────────────────────────────────────
    function derived() {
      if (state.dir === "fromCA") return deriveCXCY(state.nl, state.ca);
      return { ca: deriveCA(state.nl, state.cx, state.cy) };
    }

    function refresh() {
      var ok = validNL(state.nl);
      root.classList.toggle("coord-invalid", !ok);
      status.classList.remove("ok", "no");

      if (!ok) {
        status.textContent = "nl must be a non-decreasing list of line-start indices, e.g. [12, 25, 32].";
        status.classList.add("no");
        return;
      }

      if (state.dir === "fromCA") {
        caField.input.disabled = state.predict;
        cxField.input.disabled = !state.predict;
        cyField.input.disabled = !state.predict;
        caField.input.value = String(state.ca);
        if (!state.predict) {
          var d = deriveCXCY(state.nl, state.ca);
          state.cx = d.cx; state.cy = d.cy;
          cxField.input.value = String(d.cx);
          cyField.input.value = String(d.cy);
          var ls = d.cy > 0 ? state.nl[d.cy - 1] : 0;
          status.textContent = "line " + d.cy + " starts at " + ls +
            ", so ca " + state.ca + " = " + ls + " + " + d.cx + ".";
        } else {
          status.textContent = "Predict: for ca = " + state.ca + ", what are cx and cy?";
        }
      } else {
        caField.input.disabled = !state.predict;
        cxField.input.disabled = state.predict;
        cyField.input.disabled = state.predict;
        cxField.input.value = String(state.cx);
        cyField.input.value = String(state.cy);
        if (!state.predict) {
          var ca = deriveCA(state.nl, state.cx, state.cy);
          state.ca = ca;
          caField.input.value = String(ca);
          var ls2 = state.cy > 0 ? state.nl[state.cy - 1] : 0;
          status.textContent = "line " + state.cy + " starts at " + ls2 +
            ", so ca = " + ls2 + " + " + state.cx + " = " + ca + ".";
        } else {
          status.textContent = "Predict: for cx = " + state.cx + ", cy = " + state.cy +
            ", what is ca?";
        }
      }
      checkBtn.style.display = state.predict ? "" : "none";
      predictBtn.textContent = state.predict ? "sandbox mode" : "predict mode";
    }

    function readCA() {
      var v = parseInt(caField.input.value, 10);
      if (!Number.isFinite(v) || v < 0) { v = 0; }
      return v;
    }
    function readCX() {
      var v = parseInt(cxField.input.value, 10);
      if (!Number.isFinite(v) || v < 0) { v = 0; }
      return v;
    }
    function readCY() {
      var v = parseInt(cyField.input.value, 10);
      if (!Number.isFinite(v) || v < 0) { v = 0; }
      return v;
    }

    // ── events ────────────────────────────────────────────────────────────
    nlField.input.addEventListener("input", function () {
      state.nl = parseNL(nlField.input.value);
      refresh();
    });

    caField.input.addEventListener("input", function () {
      if (state.dir !== "fromCA") return;
      state.ca = readCA();
      state.predict = false;
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      refresh();
    });

    cxField.input.addEventListener("input", function () {
      if (state.dir !== "fromCXCY") return;
      state.cx = readCX();
      state.predict = false;
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      refresh();
    });
    cyField.input.addEventListener("input", function () {
      if (state.dir !== "fromCXCY") return;
      state.cy = readCY();
      state.predict = false;
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      refresh();
    });

    reverse.addEventListener("click", function () {
      // seed the other representation from the current derived value
      if (state.dir === "fromCA") {
        var d = deriveCXCY(state.nl, state.ca);
        state.cx = d.cx; state.cy = d.cy;
        state.dir = "fromCXCY";
      } else {
        state.ca = deriveCA(state.nl, state.cx, state.cy);
        state.dir = "fromCA";
      }
      state.predict = false;
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      refresh();
    });

    predictBtn.addEventListener("click", function () {
      state.predict = !state.predict;
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      if (state.predict) {
        if (state.dir === "fromCA") { cxField.input.value = ""; cyField.input.value = ""; cxField.input.focus(); }
        else { caField.input.value = ""; caField.input.focus(); }
      }
      refresh();
    });

    randomBtn.addEventListener("click", function () {
      if (!validNL(state.nl)) return;
      var max = state.nl[state.nl.length - 1];
      state.ca = Math.floor(Math.random() * (max + 1));
      state.dir = "fromCA";
      state.predict = true;
      cxField.input.value = "";
      cyField.input.value = "";
      feedback.textContent = "";
      feedback.className = "coord-feedback";
      refresh();
      cxField.input.focus();
    });

    checkBtn.addEventListener("click", function () {
      if (!validNL(state.nl)) return;
      if (state.dir === "fromCA") {
        var d = deriveCXCY(state.nl, state.ca);
        var okCX = readCX() === d.cx;
        var okCY = readCY() === d.cy;
        if (okCX && okCY) {
          feedback.textContent = "\u2713 Correct: ca " + state.ca + " sits at cx " + d.cx +
            ", cy " + d.cy + ".";
          feedback.className = "coord-feedback ok";
        } else {
          feedback.textContent = "\u2717 Not quite. ca " + state.ca + " derives cx " + d.cx +
            ", cy " + d.cy + ". (line " + d.cy + " starts at " +
            (d.cy > 0 ? state.nl[d.cy - 1] : 0) + ".)";
          feedback.className = "coord-feedback no";
        }
      } else {
        var ca = deriveCA(state.nl, state.cx, state.cy);
        if (readCA() === ca) {
          feedback.textContent = "\u2713 Correct: ca = " + ca + ".";
          feedback.className = "coord-feedback ok";
        } else {
          feedback.textContent = "\u2717 Not quite. cx " + state.cx + ", cy " + state.cy +
            " derives ca " + ca + ".";
          feedback.className = "coord-feedback no";
        }
      }
    });

    refresh();
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll(".coord-lab"), function (root) {
      if (root.dataset.labReady) return;
      var data = root.querySelector('script[type="application/json"]');
      var cfg = {};
      if (data) { try { cfg = JSON.parse(data.textContent); } catch (e) { cfg = {}; } }
      render(root, cfg);
      root.dataset.labReady = "true";
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
