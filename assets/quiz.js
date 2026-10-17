/* ── Reusable retrieval-practice quiz component ──────────────────────────────
   Usage:
     <div class="quiz">
       <script type="application/json">
         [{ "q": "question text",
            "options": ["opt A", "opt B", "opt C", "opt D"],
            "answer": 0,
            "explain": "why the correct answer is correct" }]
       </script>
     </div>
   Options are shuffled per render so answer position is not a cue.
   Feedback is immediate: correct answer marked green, a wrong pick marked red.
*/
(function () {
  "use strict";

  var LETTERS = ["A", "B", "C", "D", "E", "F"];

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

  function render(root, questions) {
    var score = document.createElement("div");
    score.className = "quiz-score";
    score.setAttribute("aria-live", "polite");

    var answered = 0;
    var correct = 0;

    function updateScore() {
      score.textContent = correct + " / " + questions.length + " correct";
      if (answered === questions.length) {
        score.textContent +=
          correct === questions.length ? "  — perfect retrieval!" : "  — review the misses below.";
      }
    }

    questions.forEach(function (q, qi) {
      var card = document.createElement("div");
      card.className = "quiz-question";

      var prompt = document.createElement("p");
      prompt.className = "quiz-prompt";
      var idx = document.createElement("span");
      idx.className = "quiz-index";
      idx.textContent = "Q" + (qi + 1) + ".";
      prompt.appendChild(idx);
      prompt.appendChild(document.createTextNode(" " + q.q));
      card.appendChild(prompt);

      var correctText = q.options[q.answer];
      var options = shuffle(q.options);
      var list = document.createElement("div");
      list.className = "quiz-options";
      list.setAttribute("role", "group");

      var locked = false;

      options.forEach(function (optText, oi) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "quiz-option";
        btn.dataset.text = optText;

        var letter = document.createElement("span");
        letter.className = "quiz-letter";
        letter.textContent = LETTERS[oi];
        btn.appendChild(letter);
        btn.appendChild(document.createTextNode(" " + optText));

        btn.addEventListener("click", function () {
          if (locked) return;
          locked = true;
          answered++;

          var isCorrect = optText === correctText;
          if (isCorrect) correct++;

          Array.prototype.forEach.call(list.children, function (b) {
            b.disabled = true;
            if (b.dataset.text === correctText) b.classList.add("correct");
            else if (b === btn) b.classList.add("incorrect");
          });

          var fb = document.createElement("div");
          fb.className = "quiz-feedback " + (isCorrect ? "ok" : "no");
          fb.textContent = (isCorrect ? "\u2713 Correct. " : "\u2717 Not quite. ") + (q.explain || "");
          card.appendChild(fb);

          updateScore();
        });

        list.appendChild(btn);
      });

      card.appendChild(list);
      root.appendChild(card);
    });

    root.appendChild(score);
    updateScore();
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll(".quiz"), function (root) {
      if (root.dataset.quizReady) return;
      var data = root.querySelector('script[type="application/json"]');
      if (!data) return;
      try {
        render(root, JSON.parse(data.textContent));
        root.dataset.quizReady = "true";
      } catch (e) {
        console.error("Quiz could not be initialized:", e);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
