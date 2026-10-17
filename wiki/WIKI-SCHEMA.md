# Wiki Schema

## Googles Open Knwoledge Format

The wiki conforms to the Open Knowledge Format (OKF), and the bundle is at wiki/pages. The files
outside of this folder do not need to conform to OKF. 

## Wiki Link Targets

There are three kinds of link targets in wiki pages:
- slug (another wiki page)
- source path (a link to a source material outside of the wiki, but in the same repo)
- URL (a resource outside of the repo)

Every wiki document — including the special `wiki/overview.md` — may link to wiki pages and to 
source material, and is **expected to do both**: any mention of source material in prose, in a 
list entry, or in a citation must be written as a source path link, never as a bare path in 
text or backticks. A mention that names a source without linking it is a defect. The single 
exception is the generated `wiki/pages/index.md`, which contains slug references only — never 
source path links. 

## Wiki Link Style

This section defines how cross-references and citation targets are written and parsed for wikis.

Two terms:

- **slug** — the identity of a wiki page: `wiki/pages/<slug>.md`. A *slug reference* is a link to a
  wiki page.
- **source path** — a repo-relative path to source material outside of `wiki/pages/` — any file or directory:
  code, docs, data, images, anything (e.g. `CONTEXT.md`, `docs/agents/`, `build.gradle`,
  `assets/diagram.png`). A *source path link* is a link pointing at one directly.
  This is also known as Source Material Path or a reference to a Source Material or Source file. 
  Do not confuse this with wiki pages that are of the type "Source". Those pages discuss a source
  material, that's why they are of the type Source. But the wiki page itself isn't referred to as a
  "Source".

### Emit

Use `[[display](<path>)]` for every cross-reference and every citation target — a standard 
markdown link wrapped in outer brackets. The outer brackets preserve the visual `[slug]` cue 
from Obsidian; the inner link is what GitHub and other plain-markdown renderers will make 
clickable. The display text depends on the target kind:

- **Slug reference**: the display text is the slug verbatim.
  - `see [[helaman](helaman.md)] for the account of the wars`
- **Source path link**: the display text is a short readable name — the basename, a human 
  name, or if the base name is too similar to other files, then add one or more path components
  (e.g. if there are multiple README's in the project: `testing/docs/README.md`). Source paths in
  code repos can be very deep; keep the depth in the link and the display text short and clickable.
  - `the glossary [[CONTEXT.md](../../CONTEXT.md)] retires "piece"`
  - `see [[README.md](../docs/README.md)] for the overview`
  - `the seam sits in
    [[ButterFly.java](../../src/main/java/com/amex/payments/verticle/components/ButterFly.java)]`
    — long path in the link, no path or shortened path display text (prefer no path if unambiguous)
  - `see [[Genesis 1:35 L34-36, 38-39](../docs/kjv/Genesis-1.txt#L34-36)]` - line range reference
    put the full set of line ranges in the display text, and only the first range in the link target
    itself.
- **URL** [[google.com/coolaistuff](https://google.com/coolaistuff?someparam=somevalue)

When a reference is to a certain line number or range of lines in a document, add that to the 
link in the format github understands: `[[slug](<path>#L4-8)]` or 
`[[CONTEXT.md](../../CONTEXT.md#L4-8)]` refer to lines 4-8, for example,
or just `[[CONTEXT.md](../../CONTEXT.md#L5)]` to refer lines 5.

All source path and slug links that are referring to a line or a range of
lines within that document must specify the lines in the link. For example:
[cool-doc](../../raw/cool-doc.md#L12-14). This line reference can handle a single line, or
a single range of lines, but it CAN'T handle multiple comma separated lines or line ranges.
If you want to refer to multiple line ranges, only specify the first range in the link, and in the
display text of link you can place the full list of line ranges.

The same form is used in body prose, in index/list entries, and inside citation footnotes.

### Path rule: relative to the page emitting the link

The inner `<path>` must be a path **relative to the directory of the page containing the link**, 
not to the wiki root. Since all slug pages live flat in `wiki/pages/`:

- **From a page inside `wiki/pages/`** (e.g. `wiki/pages/alma.md` linking to `wiki/pages/helaman.md`): the target is in the *same* directory, so the path is just `<slug>.md`.
  - `see [[helaman](helaman.md)] for the account of the wars`
  - Using `pages/helaman.md` here would be **wrong**: GitHub resolves it against the page's own directory, giving `wiki/pages/pages/helaman.md`, which does not exist.
- **From a page outside `wiki/pages/`** (e.g. `wiki/pages/index.md`, `wiki/overview.md`): the target is one directory down, so the path is `pages/<slug>.md`.
  - `- [[helaman](pages/helaman.md)] — the record of the priests`
  - Citation footnote: `[^1]: [[helaman](pages/helaman.md)] §3.2 — "..."`
- **Source path links** leave `wiki/` entirely, so they climb with `../` — the only place 
  `../` legitimately appears. From `wiki/pages/<slug>.md` the repo root is `../../`; from 
  `wiki/overview.md` it is `../`; from `wiki/WIKI-SCHEMA.md` it is also `../`.
  - From a page in `wiki/pages/`: `[[build.gradle](../../build.gradle)]`
  - From `wiki/overview.md`: `the [[domain glossary](../CONTEXT.md)]`, a directory: `[agent conventions](../docs/agents/)`

Quick check before writing a link: *"From the file I'm editing, does this path reach the target 
file?"* Open the target from the link and verify it exists.

Source path links are **not** slug references — the slug regex below must not match them, and
slug-family / broken-link lints skip them (they are verified by file existence instead; see
Concept Identity). Bare URLs in drive-by (=source material) citations are likewise unchanged by link
style.

### Percent-encoding a source path that contains a space or a parenthesis

The inner `<path>` of a link may not contain a space or a `)` — the target ends at the first `)` —
so a source file whose name carries either must be percent-encoded **in the link only**: `%20` for a
space, `%28` / `%29` for `(` / `)`. The display text is never encoded.

```
[[Vita Antonii (ACW 010)](../../raw/ancient-christian-writers-collection/Athanasius%20-%20Vita%20Antonii%20EN%20%28Robert%20T.%20Meyer,%201950%29%20ACW%20010_djvu.txt#L806-815)]
```

`check-links.py` percent-decodes the target before resolving it, so an encoded link is verified —
existence **and** `#L` range — exactly like a plain one. An unencoded target with a space or a `)`
is worse than a broken link: the link parser never sees it, so it renders as literal text and no gate
checks it at all. This is what made the ACW corpus (`raw/ancient-christian-writers-collection/`, all
51 filenames with spaces and parentheses) uncitable in the schema's link style until the encoding was
supported; see the lint reports in `wiki/reports/`.

### Parse

To find slug references in any wiki page, match this grep pattern:

```
\[\[\([a-z0-9-]\+\)\]([^)]*\1\.md)\]
```

To find external = source material = source path references in wiki pages, you can match this
pattern:

```
\[\[[^]]\+\](\.\./[^)]\+)\]
```

Reading is permissive; writing is strict.

## Wiki Page Templates

All wiki pages strictly follow Google's Open Knowledge Format. It must follow valid YAML 
formatting. There are two types of wiki pages: resource pages, and concept pages. 

All dates and times must follow ISO 8601 format according to the OKF standard. 

### Wiki Resource Page

A resource page directly describes a file from the source material of this repo. 
You must strictly use the following template. Don't add anything to the frontmatter: 

```markdown
---
type: Sources
title: <Source Title>
description: <one-line description of the source file>
resource: <repo relative path to source material file>
tags: [<relevant tags>]
generated: { by: <current agent name>/<current AI model>, at: <current datetime> }
updated: { by: <current agent name>/<current AI model>, at: <current datetime> }
---

# <Source Title>

**Source:** <original source URL, or the repo-relative file path as a source path link>
**Date updated:** <today>

## Summary

<2-3 paragraph synthesis — your own words, not abstract copy-paste>

## Key Takeaways

- <bullet>

## Entities & Concepts

<list of entities/concepts as cross-reference links>

## Relation to Other Wiki Pages

<how this connects to or updates existing knowledge>

## References

<citations>
```

### Wiki Concept Page

A concept page describe a concept that is part of 1 or more source material files. 
You must strictly use the following template. Don't add anything to the frontmatter: 

```markdown
---
type: <Category based on the concept>
title: <Entity or Concept Name>
description: <one-line description for the index>
tags: [entity | concept]
generated: { by: <current agent or harness name>/<current model>, at: <current datetime> }
updated: { by: <current agent or harness name>/<current model>, at: <current datetime> }
sources:
  <add a yaml list item with a `resource:` key for each source used in this doc, the value is the repo relative path to the source file>
---

# <Name>

## Description

<synthesis across all sources that discuss this>

## Appearances in Sources

- <slug-reference to source-slug, in the wiki's link style> — <one-line note>

## Related Concepts

- <slug-reference to related-slug, in the wiki's link style> — <relationship>

## References

<citations>
```

## Concept Identity

The slug **is** the concept's identity — there is no separate id. A concept is the
page at `wiki/pages/<slug>.md`; everything that links to it uses `[[slug]]`. This only
works if the link graph is trustworthy, so two rules hold everywhere links are written:

1. **Links are verified, never invented.** Before writing any `[[slug]]`, the slug must
   resolve to an existing `wiki/pages/<slug>.md` **or** to a page being created in the
   same operation. List the existing page set first (`ls wiki/pages/`); never emit a
   link to a slug you have not confirmed. A `[[slug]]` that resolves to nothing is a
   hallucinated link — the failure this discipline exists to prevent. The same rule
   applies to **source path links**: before emitting one, confirm the resolved file or
   directory exists (`ls <path>` from the repo root, or follow the page-relative path).

2. **Homonyms get qualified slugs.** When a new concept collides with an existing slug
   for a *different* sense, qualify both with a discriminator rather than overloading
   one page:
   - `mercury-planet` / `mercury-element` / `mercury-mythology`
   - `transformer-ml` / `transformer-electrical`

   Pick the narrowest discriminator that disambiguates. `wiki-lint` warns when slugs
   sharing a base token look like an unintended collision.

   **Related slugs are not homonyms.** A bare slug beside its qualified relatives can be
   genuinely related pages rather than different senses — a glossary term and the class or
   concept derived from it (`row` / `row-clear`, `skin` / `skin-effect`), or a source page
   and its test or sibling (`piece` / `piece-shape`, `gradle` / `gradle-wrapper`). When that
   is the case, declare the whole group as one family in `wiki/config/slug-families.txt`
   (one space-separated line per family, `#` for comments); the mechanical lint and the
   pre-commit gate then treat a collision group fully contained in a declared family as
   intended relatives and skip it. A group that is only partly declared, or a new relative
   not yet added to its family line, is still flagged — extend the family file when you add
   a member.

Consolidating two pages that turn out to be the same concept (merge), or separating one
overloaded page into qualified pages (split), is the job of the `wiki-merge` skill.

## Citations

The body heading for the footnote definitions is `## References`, not
`## Citations`: OKF v0.2 retires a body `# Citations` list (§13.1), which the
`okf/legacy/citations` lint warns on, while the provenance itself is declared in
the `sources:` frontmatter. The definitions under the heading still carry the
per-claim locators and line ranges.

Cite every non-common-knowledge factual claim. "Common knowledge" = uncontroversial,
undergraduate-level facts in this wiki's domain. Granularity is paragraph or claim,
never per-sentence. If you cannot produce a citation in one of the forms below,
find one, weaken the claim, or drop it.

Citations are always of source material, and so the links should always be the source path type,
including line numbers as part of the link where applicable.

Never cite entity, concept, or analysis pages — those are syntheses, not sources.

Two citation kinds:

```
Quote:     [^N]: <target> <locator> — "<verbatim quote>"
Synthesis: [^N]: <target> <locator> [synthesis] — <what supports the claim>
```

**Quote citation** (preferred):
```
The model uses 8 attention heads.[^1]

[^1]: [[attention-is-all-you-need](../../docs/ai-related/attention-is-all-you-need.md#L142-143)] §3.2.2 L142-143 — "We employ h = 8 parallel attention layers"
```

**Synthesis citation** (when no single quote captures the claim):
```
The architecture is fundamentally an encoder-decoder with attention.[^2]

[^2]: [[attention-is-all-you-need](../../docs/ai-related/attention-is-all-you-need.md#L138-202)] §3.2-3.4 [synthesis] L138-202 — encoder, decoder, and
      attention sections together describe the full multi-head architecture
```

`L142-143` / `L138-202` are line ranges in the raw source file. For a quote they mark
the lines the quote is taken from; for a synthesis they mark the block being summarized.
You can only use a single range of lines in the link itself, although the visible text can have
multiple. Github markdown only recognized one line range, so if there are multiple that are being
referenced, just link to the first line range and specify all line ranges in the visible text. 

Two rules for every footnote:

1. **A locator is present.** Always a semantic locator: `§<section>`, `p.<n>`,
   `[HH:MM:SS]` for transcripts, URL anchor for web, or `(YYYY-MM-DD)` for dated posts.

   **Plus a line-range when the source is text-addressable.** If the resolved raw
   file is markdown, plaintext, code, or cached HTML, append a line-range token after
   the semantic locator:

   - `L<start>-<end>` — a range, e.g. `L142-145`
   - `L<n>` — a single line, e.g. `L142`
   - `L142-145,L201-203` — disjoint ranges. NOT for the link, only for the visible text or
     description of the link.

   The line range refers to lines in the **source material file** being linked to.

   A line-range is **required** for text-addressable sources and applies to BOTH
   citation kinds — a `[synthesis]` footnote marks the block it summarizes with `L…`
   just as a quote marks the lines it quotes. **Exempt** (semantic locator only, no
   `L...`): PDFs, transcripts, and live URLs with no local cached copy.

2. **Either a verbatim quote, or the `[synthesis]` tag plus a description** of
   what the cited range supports. No third option.

**Source Path citation examples:**
```
[^3]: [[scaling-laws.pdf](../../raw/scaling-laws.pdf)] p.7 — "loss scales as a power law in compute"
[^4]: [[PizzaTopping.java](../../src/java/PizzaTopping.java#L45-48)] L45-48,52-56,60
[^5]: [[twitter.com/user/status/123](https://twitter.com/user/status/123) (2026-04-15) — "<tweet text>"
[^6]: [[README.md](../../README.md#L3)] L3
```

(A bare `raw/<file>` in a footnote is stale style; it must be a source path link.)

## Cross-Model Review

`wiki-audit strong` runs a second-opinion pass with a different-provider model and
stamps the audited page with an optional `review:` frontmatter block:
```
review:
  model: <current LLM model>
  provider: <current AI harness>
  date: YYYY-MM-DD
  status: clean         # or: disputed
  findings: 2           # present only when status: disputed
```
- `status: clean` — the reviewer surfaced no disagreement with the normal audit.
- `status: disputed` — the reviewer flagged overreach or a contradiction the normal
  audit missed; `findings:` carries the count. The detail lives in the (local-only)
  audit report.
- `provider: pi` means no different-provider CLI
  was available, so the check is same-provider and weaker.

This block is optional and is added only by `wiki-audit strong`. Pages never need it to
be valid.

## Contradiction Check

`wiki-update` runs a cheap contradiction check on the pages each update touches, before it
commits. It is a **gate, not an annotation**: every page that lands in git is clean.

- **Scope — touched neighbors only.** The check compares the pages an update wrote or
  edited against (a) themselves and (b) the pages that update already read (the entity /
  concept pages it updated and the neighbor pages from its backlink audit). It does NOT
  re-read the whole wiki — a conflict with a distant, untouched page is left to the
  periodic `wiki-lint` sweep.
- **Blocking vs. soft.** A **blocking** contradiction is a real factual conflict on the same
  entity under the same scope — incompatible dates, counts, names, or mutually-exclusive
  claims. A **soft** tension (differing emphasis, values within plausible version /
  measurement variance, claims that hold under different scope) is not a conflict.
- **The transient blocker flag.** When a blocking contradiction is found, a single line is
  written to the affected page's frontmatter and the update stops before committing:
  ```yaml
  contradiction-check: failed — <one-line reason naming the counterpart [[slug]] or "internal">
  ```
  The machine-readable token is the literal `contradiction-check: failed`. It exists ONLY
  while the conflict is unresolved; resolving the conflict **removes the line**. A committed
  page never carries it — there is no `passed` stamp, no severity history, nothing. Absence
  of the flag is the only "clean" state.
- **Soft tensions are surfaced, not recorded** — mentioned in the update summary so you can
  act if you wish, but never persisted and never blocking.

This flag is also what the **Pre-commit Gate** below scans staged files for.

## Operation Log & Commit Convention
Operations: init, update, query, lint, audit, merge, split

**The git history is the operation log.** After an operation, the skill
suggests a commit message and commits on your confirmation (skills never auto-commit).
Render the human log on demand with `python wiki/bin/render-log.py`.

The suggested subject line follows the repo's commit convention:
1. **Detect an existing convention first** — scan recent `git log` and any `.gitmessage`,
   commitlint config, or `CONTRIBUTING.md`. If the repo already has a subject style,
   follow it.
2. **Default to Conventional Commits** when none is found, choosing the type by operation:

   | Operation        | Type                                  |
   |------------------|---------------------------------------|
   | init             | `chore`                               |
   | update           | `docs`                                |
   | query (saved)    | `docs`                                |
   | lint             | `fix` if fixes applied, else `chore`  |
   | audit            | `fix` if fixes applied, else `chore`  |
   | merge / split    | `refactor`                            |

**Always append a `Wiki-Op:` trailer**, whatever the subject style — it is what
`render-log.py` keys on, decoupling the log from the subject convention. Which pages
changed is read from the commit diff, so no `Pages:` trailer is needed.
```
docs: summarize Attention Is All You Need

Wiki-Op: update
```

## Index Generation
`wiki/pages/index.md` is a generated artifact — never hand-edit it. It is rebuilt
from page frontmatter by `okf`:
- Run `okf index wiki/pages` **after** any operation that adds, removes, renames, or re-categorizes a page.
- The generator sorts pages alphabetically by their Title.
  Each entry is `- [[slug]] — description _(generated.at)_`.
- Pages whose filename matches `audit-*.md` are excluded (gitignored local-only
  artifacts). A page with an unrecognized or missing `type` lands in an
  `Uncategorized` section.
- The index contains **slug references only** — it is the one wiki document exempt
  from the source-path-linking rule and must never contain source path links.

## Index Categories
Categories: Sources | People | Places | Events | Entities | Concepts | Analyses | Modules | APIs | Decisions | Flows

Not all categories need to be used. A codebase will use categories like Sources, Modules, APIs, Decisions, 
and Flows. A general purpose document or research repo may use Sources, Entities, Concepts, 
Analyses. Use what is appropriate for the subject/concept/object being described. When in doubt, ask the user.
For example, if the sources involve important people and places, include People and Places concept pages. 

Note that the Category is written into the `type` field of frontmatter.

## The Overview File

The wiki/overview.md file is a special file that is part of the wiki but lies outside of the main
wiki/pages bundle. 

The overview file contains the map of the source materials in this repo, allowing an agent or human
user to find their way around the repo efficiently. It serves as an entry point into the wiki, and
through it's links, direct or indirect, all other wiki pages should be reachable. 

Because it is the repo map, the overview links **both ways**: to wiki pages (slug references) and
to the source material itself (source path links). Every source file or directory the overview
names — `CONTEXT.md`, `docs/agents/`, `src/`, a build file, an asset — must appear as a source
path link relative to `wiki/overview.md` (`../CONTEXT.md`, `../docs/agents/`, …), not as a bare
backticked path.

It is normally produced or updated when the wiki-update skill is run, but the user may directly
request it to be updated or rebuilt. 

It has a special frontmatter, since it's not a normal wiki page:

```
---
type: Overview
title: Overview
description: Entrance to the Wiki Pages
generated: { by: <current agent or harness name>/<current model>, at: <current datetime> }
updated: { by: <current agent or harness name>/<current model>, at: <current datetime> }
---
```

The first section of the overview page, called "Introduction", should be a high level summary of what
this repo is about and what kinds of data it contains.

The next section, called "Zones": Break down the files into 4-8 categories called Zones. Describe
each zone, what is its purpose, what kind of content it contains, and where it is in the filesystem.
When describing a zone, if there's more then 10 words of information, don't format it into large
paragraphs, but instead use lists, tables, or outlines as appropriate to make it easy to read.

The next section, called "Core Dependencies": List the components/tools/librarys/external
documentation that this repo depends on and uses, with a brief description of each. 

Optional sections:
- Edges Section: If this repo has code, describe how it is started, and where in the code
  the entrypoint(s) are. 
- Building Section: If this repo has code, describe the proper steps to build it, including the
  major variations and settings that may be available to modify the built product. 

The next section, called "Project Status". If the project is complete as is, and there's nothing
interesting to put here, this section can be skipped. But in the case of a work in progress project,
this section will describe recent accomplishments, what is currently being worked on, and what will
be worked on in the near future. It should also contain info about, and links to, recent reports from the
wiki/reports folder. This helps the user to understand how recently linting and auditing have been
done, and gives an idea as to how clean and accurate the wiki is. 

At the bottom is a link to the Index (`wiki/pages/index.md`).

### Building the Overview File

Normally the overview file is updated as new source material is ingested into the wiki as part of
the wiki-update skill, or if existing material is modified. But if the user specifically asks to
have the overview updated or rebuilt, then the agent will go through the index, reading about every
document, and organize the overview as specified above. The agent can pull up linked documents from
the index as needed to get further detail. 

## Conventions
- These wiki skills should never modify anything outside of the wiki folder.
- wikis are expected to be git repositories
- All files outside of the wiki/pages/ folder are considered to be source material, UNLESS a raw/
  folder exists at the top level of the repo. If that's the case, then ONLY files and folders in
  raw/ are considered source material that is ingested into the wiki.
- operation log: git wikis record each op as a commit (see Operation Log & Commit Convention) and render it with bin/render-log.py
- index.md is GENERATED by okf and is saved in the repo to make it easier to browse — 
  never hand-edit it; set page frontmatter (type, description) and regenerate instead
- All pages live flat in wiki/pages/ — no subdirectories
- overview.md reflects the current synthesis across all sources
- Cross-reference and citation slug-targets follow conventions specified in wiki/WIKI-SCHEMA.md .
  every skill reads it before writing or scanning links
- Source material is linked, not just named: every mention of a source file, directory, or
  image in a wiki doc is a source path link (index.md excepted); see Wiki Link Style
- contradiction check: update gates on blocking contradictions in touched pages via a transient 
  `contradiction-check: failed` flag, removed before commit — committed pages are always clean (see 
  Contradiction Check)

# Wiki Tools

## okf

A binary excutable for working with Open Knowledge Format wikis. It returns responses in json to be 
more agent friendly. If you run into trouble using this tool, you can read more about it: 
https://github.com/okfcli/okf .

the `okf schema` command will return a json object containing all of the available commands, what they do, and how to use them.

## rg (ripgrep)

If this command `rg` exists in the user's path:
  Use this instead of `grep` for exact regex matches to recursively search through directories,
  honoring .gitignore by default. Most grep flags can be used with rg also. But `-r` has a different
  meaning, so don't use it. All `rg` searches are recursive, so `-r` isn't needed.

## zg (zvec-grep)

If this command `zg` exists in the user's path:
  If the `.zvec-grep` folder doesn't exist in the root path of this repo:
    This means the index for zg has not yet been created for this repo. Create it with this command
    `zg index` run in the repo's root folder.
  Use `zg query "<subject matter searching for>"` to do a grep-like search of the repo. Unlike
  `grep`, `zg` does a semantic search that will find any text matching the meaning of the subject
  matter, instead of exact words. When you know the exact words to search for, use `grep`. When you
  are searching for broader concepts, use `zg`. 

## Suggestions to User

Suggest to the user of this skill that they should install these tools if they aren't already
installed.

