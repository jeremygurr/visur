# Navigating this Repo

This repo consists of two sections: the wiki, and the source material. The wiki section is purely 
generated from the source material. The wiki section is found in the wiki/pages folder at the root of 
this repo. The source material consists of all other files outside of the wiki/ folder in this repo. 

When searching for information from this repo, unless a specific file was referenced in the query, 
and unless you are executing a wiki modification operation, use the wiki to find the relevant files:

## Wiki Search Process

1. Read `wiki/pages/index.md` first

Scan the full index to identify which pages are likely relevant. Do NOT answer from
general knowledge — the wiki is the source of truth here, even if you think you know the
answer.

2. Read relevant pages

Read the identified pages in full. Follow one level of cross-reference links (matched via the 
Parsing references in wiki pages rule) if they point to pages that seem relevant to the question.

3. Read the relevant source documents mentioned in the wiki pages you read

4. Synthesize the answer

Write a response that is grounded in the source materials you read. Trust the source materials above
the wiki. The wiki is only there to guide you to the source materials.

## Other Searches

You do NOT need to do any additional searching beyond this unless the user explicitly says to. You
don't need to try and grep the entire repo to see what the wiki might have missed. 

# Subagent Settings

Maximum number of subagents at one time is 1, unless the user otherwise specifies. This adjusts max
concurrency accordingly. 

# Managing Documentation

Whenever creating or editing documentation, in either html or markdown, make sure to always use
links when referring to related files in this repo. If a concept is discussed in the documentation
that is implemented elsewhere in the repo, make links to those related files or folders. Use line
numbers in the links where appropriate (example ../src/Main.java#L14-17). 

When reading over documentation that is missing good links, or has invalid links, suggest fixing it. 

If the user ever asks about or wants to change a file in the wiki folder (or any of its subfolders),
then first read the wiki/WIKI-SCHEMA.md file for instructions about the wiki conventions. Then make
the requested changes according to those conventions.
