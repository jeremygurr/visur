# Learner context: a Java developer studying an unfamiliar codebase

The learner is a Java developer working through the Visur source, not its author.
The codebase was written primarily by Jonathan Gurr (1,228 commits); the learner
has 9 commits, all of which created the wiki, `AGENTS.md`, and lessons 1–8. The
prior-knowledge floor is therefore: fluent Java and general architecture literacy,
but no authorship intuition for why Visur's pieces were built the way they were.

Future sessions should not re-teach Java, Vert.x basics, or "what an operator
is"; the zone of proximal development is reverse-engineering *intent* from source
and naming the why behind existing choices. Lessons are also the learner's own
product, so each one should leave behind a correct, citable model rather than a
transcript of the reading.

Evidence: `git log --author` shows the learner's commits are exclusively
`wiki/`/`lessons/`/`AGENTS.md`; the codebase owner is a different author.

Implications: lessons must stay anchored to source line numbers and tests (the
only authority available), and it is worth flagging every place the original
`lessons/itinerary.md` (drafted from a first pass) mis-frames the code — see
[0002 — Data-class and cursor model corrections](0002-data-class-and-cursor-model-corrections.md).
