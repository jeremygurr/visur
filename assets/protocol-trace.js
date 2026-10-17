/* ── Reusable EventBus protocol tracer ──────────────────────────────────────
   A retrieval-practice sequence diagram. Each hop of a message shows the
   sender and the payload, but hides the address and the receiver. Pick the
   address from memory; the receiver, payload, and an explanation are revealed
   on a correct pick. Wrong picks are marked and leave the step open to retry.

   Usage:
     <div class="protocol-trace">
       <script type="application/json">
         {
           "addresses": ["keyWasPressed", "canvasWasChanged",
                         "modelWasChanged", "viewWasChanged"],
           "traces": [
             {
               "label": "Boot: the first paint",
               "steps": [
                 { "from": "Browser · eb.onopen",
                   "addr": "canvasWasChanged",
                   "to": "CanvasWasChangedVerticle",
                   "payload": "{ width, height }",
                   "note": "why it is this address" }
               ],
               "coda": "what the whole trace shows"
             }
           ]
         }
       </script>
     </div>

   Every field is plain text and inserted with textContent, so payloads can
   contain braces, quotes, and newlines without escaping concerns.
*/
(function () {
  "use strict";

  function shuffle(arr) {
    var a = arr.slice();
    for (var i = a.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = a[i];
      a[i] = a[j];
      a[j] = tmp;
    }
    return a;
  }

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function renderTrace(root, spec, trace, state) {
    root.textContent = "";

    var stepsBox = el("div", "trace-steps");

    trace.steps.forEach(function (step, i) {
      if (i > state.reached) return;

      var card = el("div", "trace-step");
      if (i === state.reached) card.classList.add("current");
      if (i < state.reached) card.classList.add("done");

      var head = el("div", "trace-step-head");
      head.appendChild(el("span", "trace-actor", step.from + "  ──▶  "));
      var slot = el("span", "trace-addr-slot");
      slot.textContent = i < state.reached ? step.addr : "? address ?";
      head.appendChild(slot);
      if (i < state.reached) {
        head.appendChild(el("span", "trace-actor", "  ──▶  " + step.to));
      }
      card.appendChild(head);

      if (i < state.reached) {
        if (step.payload) card.appendChild(el("div", "trace-payload", step.payload));
        stepsBox.appendChild(card);
        return;
      }

      // The live step: choose the address.
      var options = shuffle(spec.addresses);
      var row = el("div", "trace-addr-options");
      var locked = false;

      options.forEach(function (addr) {
        var btn = el("button", "trace-addr-btn", addr);
        btn.type = "button";
        btn.addEventListener("click", function () {
          if (locked) return;
          if (addr !== step.addr) {
            btn.classList.add("incorrect");
            btn.disabled = true;
            state.misses++;
            var bad = el("div", "trace-note no",
              "Not this one. " + addr + " is a different hop in the loop — look again at who is sending.");
            card.appendChild(bad);
            updateScore();
            return;
          }
          locked = true;
          state.attempts[i] = state.misses === 0;
          Array.prototype.forEach.call(row.children, function (b) {
            b.disabled = true;
            if (b.textContent === step.addr) b.classList.add("correct");
          });
          slot.textContent = step.addr;
          card.appendChild(el("div", "trace-note ok", "✓ " + step.note));
          if (step.payload) card.appendChild(el("div", "trace-payload", step.payload));
          var canAdvance = i < trace.steps.length - 1;
          var next = el("button", "trace-next", canAdvance ? "Next hop →" : "Finish trace");
          next.type = "button";
          next.addEventListener("click", function () {
            state.reached++;
            state.misses = 0;
            updateScore();
            renderTrace(root, spec, trace, state);
          });
          card.appendChild(next);
          updateScore();
        });
        row.appendChild(btn);
      });

      card.appendChild(row);
      stepsBox.appendChild(card);
    });

    if (state.reached >= trace.steps.length) {
      stepsBox.appendChild(el("div", "trace-coda", trace.coda));
    }

    root.appendChild(stepsBox);
  }

  function render(root, spec) {
    var tabs = el("div", "trace-tabs");
    var panel = el("div", "trace-panel");
    var score = el("div", "trace-score");
    score.setAttribute("aria-live", "polite");

    var state = { trace: 0, reached: 0, misses: 0, attempts: {} };

    function updateScore() {
      var tried = 0, clean = 0;
      Object.keys(state.attempts).forEach(function (k) {
        tried++;
        if (state.attempts[k]) clean++;
      });
      score.textContent = tried
        ? "hops resolved: " + tried + " (first try: " + clean + ")"
        : "Pick the address on the envelope at each hop.";
    }

    function showTrace(index) {
      state.trace = index;
      state.reached = 0;
      state.misses = 0;
      state.attempts = {};
      Array.prototype.forEach.call(tabs.children, function (b, bi) {
        b.setAttribute("aria-pressed", bi === index ? "true" : "false");
      });
      renderTrace(panel, spec, spec.traces[index], state);
      updateScore();
    }

    spec.traces.forEach(function (trace, i) {
      var tab = el("button", "trace-tab", trace.label);
      tab.type = "button";
      tab.addEventListener("click", function () { showTrace(i); });
      tabs.appendChild(tab);
    });

    root.appendChild(tabs);
    root.appendChild(panel);
    root.appendChild(score);
    showTrace(0);
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll(".protocol-trace"), function (root) {
      if (root.dataset.traceReady) return;
      var data = root.querySelector('script[type="application/json"]');
      if (!data) return;
      try {
        render(root, JSON.parse(data.textContent));
        root.dataset.traceReady = "true";
      } catch (e) {
        console.error("Protocol trace could not be initialized:", e);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
