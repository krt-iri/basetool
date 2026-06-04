#!/usr/bin/env python3
"""Extract one release tag's CHANGELOG section as release-notes-styled markdown.

Given a tag such as ``v0.3.55`` this finds the dated ``## [v0.3.55](...) - DATE``
section that ``reconcile_changelog.py`` produced and re-emits its body with the
German Keep-a-Changelog headers (``### Added`` / ``### Changed`` / ``### Fixed``
/ ...) remapped to the release-notes skill's German rubric headings
(``## Neu`` / ``## Verbesserungen`` / ``## Fehlerbehebungen`` / ...). The bullet
text is copied verbatim: this is a deterministic CI extraction for the GitHub
Release body, not the skill's LLM-driven editorial rewrite, so it neither filters
internal entries nor simplifies the prose -- it only reshapes the headings so the
release body mirrors the skill's rubric structure.

If the tag has no section -- e.g. the release contained only internal commits and
so reconcile created no entries for it -- a neutral German placeholder line is
emitted and the exit code stays 0, so the release is still created.

Usage:
    extract_release_notes.py <tag> [CHANGELOG.md]
"""

from __future__ import annotations

import re
import sys

# Ensure umlauts print on any console (Windows defaults to cp1252 and crashes).
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # already wrapped / not reconfigurable
        pass

# A CHANGELOG ``### `` section's first word -> the release-notes skill's German
# rubric. Sections without a mapping keep their own first word as the heading so
# nothing is silently dropped (this CI extraction is content-preserving).
RUBRIC: dict[str, str] = {
    "Added": "Neu",
    "Changed": "Verbesserungen",
    "Fixed": "Fehlerbehebungen",
    "Security": "Sicherheit",
    "Removed": "Entfernt",
    "Deprecated": "Veraltet",
}


def main() -> None:
    """Print the tag's CHANGELOG section with skill-style rubric headings."""
    if len(sys.argv) < 2:
        sys.exit("usage: extract_release_notes.py <tag> [CHANGELOG.md]")
    tag = sys.argv[1]
    path = sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md"

    placeholder = (
        f"Für {tag} sind keine nutzersichtbaren Änderungen "
        "im Changelog vermerkt."
    )

    with open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    # Match the reconciled, dated version heading "## [<tag>](...) - DATE".
    head = re.compile(rf"^## \[{re.escape(tag)}\]")
    start = next((i for i, line in enumerate(lines) if head.match(line)), None)
    if start is None:
        print(placeholder)
        return
    end = next(
        (i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")),
        len(lines),
    )

    out: list[str] = []
    for line in lines[start + 1:end]:
        if line.startswith("### "):
            body = line[4:].strip()
            word = body.split()[0] if body else ""
            out.append(f"## {RUBRIC.get(word, word or 'Sonstiges')}")
        else:
            out.append(line)

    text = "\n".join(out).strip("\n")
    print(text if text else placeholder)


if __name__ == "__main__":
    main()
