/* ── Reusable command-sentence / global-variable tracer ─────────────────────
   A Visur command is a tiny stack program. Literals and numbers push onto the
   execution-data stack; `->` copies one global variable into another; a bare
   variable name recalls its value; and operators pop and push. This widget
   steps through a compiled sentence, animating the stack and the global
   variable map at each token, with optional predict-the-effect checkpoints.

   Usage:
     <div class="var-lab">
       <script type="application/json">
         {
           "vars": { "span": 1, "cursorQuantum": "character" },
           "sentences": [
             {
               "label": "i — enter insert mode",
               "tokens": [
                 { "t": "\"span\"", "kind": "literal" },
                 { "t": "->", "kind": "assign",
                   "predict": { "q": "...",
                                "options": ["...", "..."],
                                "answer": 0,
                                "explain": "..." } }
               ],
               "summary": "..."
             }
           ]
         }
       </script>
     </div>

   token kinds:
     literal    — push the token text with the surrounding quotes stripped
     number     — push the numeric value of the token text
     assign     — pop target, pop source, vars[target] := vars[source]
     recall     — push vars[name]  (name: "…", defaults to token text)
     removeVar  — pop name, delete vars[name]
     add        — pop a, pop b, push a + b
     consume    — pop {pop} values, push {push} values (optional {pushVal}),
                  optionally set a variable ({setVar:{name,value}})
     noop       — no stack change ({note})
*/
(function () {
  "use strict";

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function stripQuotes(s) {
    return s.replace(/^"/, "").replace(/"$/, "");
  }

  function cloneVars(vars) {
    var out = {};
    Object.keys(vars || {}).forEach(function (k) { out[k] = vars[k]; });
    return out;
  }

  function fmt(v) {
    if (typeof v === "string") return JSON.stringify(v);
    return String(v);
  }

  // Apply one token to the state; returns a plain-English effect string.
  function applyToken(state, tok) {
    var kind = tok.kind;

    if (kind === "literal") {
      var lit = stripQuotes(tok.t);
      state.stack.push(lit);
      return "pushes " + fmt(lit) + " onto the stack";
    }
    if (kind === "number") {
      state.stack.push(Number(tok.t));
      return "pushes " + tok.t + " onto the stack";
    }
    if (kind === "assign") {
      var target = state.stack.pop();
      var source = state.stack.pop();
      state.vars[target] = state.vars[source];
      return "pops " + fmt(target) + ", pops " + fmt(source) + "  →  " +
        target + " := " + source + "  (" + fmt(state.vars[target]) + ")";
    }
    if (kind === "recall") {
      var name = tok.name || stripQuotes(tok.t);
      state.stack.push(state.vars[name]);
      return "pushes the value of " + name + "  (" + fmt(state.vars[name]) + ")";
    }
    if (kind === "removeVar") {
      var gone = state.stack.pop();
      delete state.vars[gone];
      return "pops " + fmt(gone) + "  →  removes the variable " + gone + " from the map";
    }
    if (kind === "add") {
      var a = state.stack.pop();
      var b = state.stack.pop();
      state.stack.push(a + b);
      return "pops " + a + ", pops " + b + "  →  pushes " + (a + b);
    }
    if (kind === "consume") {
      var popped = [];
      for (var i = 0; i < (tok.pop || 0); i++) popped.push(state.stack.pop());
      for (var j = 0; j < (tok.push || 0); j++) {
        state.stack.push(tok.pushVal !== undefined ? tok.pushVal : null);
      }
      var head = popped.length ? "pops " + popped.map(fmt).join(", ") + "  →  " : "";
      if (tok.setVar) state.vars[tok.setVar.name] = tok.setVar.value;
      return head + (tok.note || "no stack change");
    }
    if (kind === "noop") {
      return tok.note || "no stack change";
    }
    return "";
  }

  function renderStack(panel, stack) {
    panel.textContent = "";
    panel.appendChild(el("div", "var-panel-title", "execution-data stack"));
    if (!stack.length) {
      panel.appendChild(el("div", "var-stack-empty", "(empty)"));
      return;
    }
    // top of stack first
    for (var i = stack.length - 1; i >= 0; i--) {
      var item = el("div", "var-stack-item", fmt(stack[i]));
      if (i === stack.length - 1) item.classList.add("top");
      panel.appendChild(item);
    }
  }

  function renderVars(panel, vars) {
    panel.textContent = "";
    panel.appendChild(el("div", "var-panel-title", "global variables"));
    var names = Object.keys(vars).sort();
    if (!names.length) {
      panel.appendChild(el("div", "var-stack-empty", "(empty)"));
      return;
    }
    var table = el("table", "var-vars");
    var tbody = el("tbody");
    names.forEach(function (n) {
      var tr = el("tr");
      tr.appendChild(el("td", "var-var-name", n));
      tr.appendChild(el("td", "var-var-val", fmt(vars[n])));
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    panel.appendChild(table);
  }

  function renderSentence(root, spec, sent, state) {
    root.textContent = "";

    var tokens = el("div", "var-tokens");
    sent.tokens.forEach(function (tok, i) {
      var chip = el("span", "var-token", tok.t);
      if (i < state.i) chip.classList.add("done");
      if (i === state.i) chip.classList.add("current");
      tokens.appendChild(chip);
    });
    root.appendChild(tokens);

    var stage = el("div", "var-stage");
    var stackPanel = el("div", "var-panel");
    var varsPanel = el("div", "var-panel");
    renderStack(stackPanel, state.stack);
    renderVars(varsPanel, state.vars);
    stage.appendChild(stackPanel);
    stage.appendChild(varsPanel);
    root.appendChild(stage);

    if (state.effect) {
      root.appendChild(el("div", "var-effect", state.effect));
    }

    if (state.i >= sent.tokens.length) {
      root.appendChild(el("div", "var-summary", sent.summary));
      var again = el("button", "var-next", "Run again");
      again.type = "button";
      again.addEventListener("click", function () {
        reset(sent, state, spec);
        renderSentence(root, spec, sent, state);
      });
      root.appendChild(again);
      return;
    }

    var nextTok = sent.tokens[state.i];

    // Predict-the-effect checkpoint: must answer before the token applies.
    if (nextTok.predict && !state.answered[state.i]) {
      var p = nextTok.predict;
      var box = el("div", "var-predict");
      box.appendChild(el("p", "var-predict-q", "Before we apply " + nextTok.t + ": " + p.q));
      var opts = el("div", "var-predict-options");
      var locked = false;
      p.options.forEach(function (optText, oi) {
        var btn = el("button", "var-predict-btn", optText);
        btn.type = "button";
        btn.addEventListener("click", function () {
          if (locked) return;
          if (oi !== p.answer) {
            btn.classList.add("incorrect");
            btn.disabled = true;
            return;
          }
          locked = true;
          state.answered[state.i] = true;
          Array.prototype.forEach.call(opts.children, function (b) {
            b.disabled = true;
            if (b.textContent === p.options[p.answer]) b.classList.add("correct");
          });
          box.appendChild(el("div", "var-predict-ok", "✓ " + (p.explain || "")));
          renderSentence(root, spec, sent, state);
        });
        opts.appendChild(btn);
      });
      box.appendChild(opts);
      root.appendChild(box);
      return;
    }

    var step = el("button", "var-next", "Apply " + nextTok.t + "  →");
    step.type = "button";
    step.addEventListener("click", function () {
      state.effect = applyToken(state, nextTok);
      state.i++;
      renderSentence(root, spec, sent, state);
    });
    root.appendChild(step);
  }

  function reset(sent, state, spec) {
    state.i = 0;
    state.answered = {};
    state.stack = [];
    state.vars = cloneVars(sent.vars || spec.vars || {});
    state.effect = "";
  }

  function render(root, spec) {
    var tabs = el("div", "var-tabs");
    var panel = el("div", "var-panel-body");

    var state = {};

    function show(index) {
      var sent = spec.sentences[index];
      reset(sent, state, spec);
      Array.prototype.forEach.call(tabs.children, function (b, bi) {
        b.setAttribute("aria-pressed", bi === index ? "true" : "false");
      });
      renderSentence(panel, spec, sent, state);
    }

    spec.sentences.forEach(function (sent, i) {
      var tab = el("button", "var-tab", sent.label);
      tab.type = "button";
      tab.addEventListener("click", function () { show(i); });
      tabs.appendChild(tab);
    });

    root.appendChild(tabs);
    root.appendChild(panel);
    show(0);
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll(".var-lab"), function (root) {
      if (root.dataset.varReady) return;
      var data = root.querySelector('script[type="application/json"]');
      if (!data) return;
      try {
        render(root, JSON.parse(data.textContent));
        root.dataset.varReady = "true";
      } catch (e) {
        console.error("Variable lab could not be initialized:", e);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
