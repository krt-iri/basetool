#!/usr/bin/env python3
"""Reconcile CHANGELOG.md: move released ``[Unreleased]`` entries under their tag.

In this project the whole history historically piled up under a single
``## [Unreleased]`` heading -- the version tags (``vMAJOR.MINOR.PATCH``) were
never cut into their own changelog sections. This script repairs that: every
bullet still sitting in ``[Unreleased]`` is attributed, via ``git blame``, to the
commit that wrote it, that commit is mapped to the *earliest* release tag that
contains it, and the bullet is rewritten under a ``## [vX.Y.Z] - DATE`` section.
Entries whose introducing commit is not contained in any tag stay in
``[Unreleased]`` -- those are the genuinely-unreleased changes.

How an entry is mapped:
  * An entry is a top-level ``- `` bullet plus every following indented
    sub-bullet / continuation / blank line up to the next top-level bullet or
    ``#`` heading. Its section (Added / Changed / Fixed / ...) is the nearest
    preceding ``### `` header, normalised to its first word (so
    ``### Changed (Paket 3A ...)`` folds into ``Changed``).
  * Each non-blank physical line of the entry is blamed to a commit; each commit
    is mapped to the earliest well-formed ``vN.N.N`` tag containing it. The
    entry's release is the *minimum* version across those lines (a later cosmetic
    edit to one line cannot push the whole entry into a newer release).

Only tags matching ``^v\\d+\\.\\d+\\.\\d+$`` are considered -- malformed typo
tags (e.g. ``v-0.2.23``, ``v.0.1.1``) are ignored everywhere.

Default is a DRY-RUN report (nothing is written). Pass ``--write`` to rewrite the
file in place. The verbatim tail (already-cut sections such as ``## [v1.0.0]``)
and the preamble are preserved byte-for-byte; only the ``[Unreleased]`` block is
restructured.

Usage:
    python reconcile_changelog.py                       # dry-run report, cwd repo
    python reconcile_changelog.py --write               # rewrite CHANGELOG.md
    python reconcile_changelog.py --repo /path/to/basetool --write
    python reconcile_changelog.py --rev HEAD --repo-url https://github.com/krt-profit/basetool
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys

# The changelog is full of umlauts and em dashes; Windows consoles default to
# cp1252 and would crash printing them. Force UTF-8 so the report prints anywhere.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # already wrapped / not reconfigurable
        pass

# A release tag we are willing to attribute entries to. Deliberately strict so
# typo tags (``v-0.2.23``, ``v.0.1.1``) never win a mapping.
VERSION_TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
# A blame --line-porcelain header line: "<40-hex sha> <origline> <finalline>...".
PORCELAIN_HEADER_RE = re.compile(r"^(?P<sha>[0-9a-f]{40}) \d+ (?P<final>\d+)")
# Canonical Keep-a-Changelog section order; anything else is appended after,
# in first-seen order (e.g. this repo's bespoke "Migration" section).
CANONICAL_ORDER = ["Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"]


def run_git(repo: str, args: list[str]) -> str:
    """Run ``git <args>`` in ``repo`` and return stdout, or exit with its stderr."""
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=repo,
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=True,
        )
    except FileNotFoundError:
        sys.exit("error: git is not on PATH")
    except subprocess.CalledProcessError as exc:
        sys.exit(f"error: git {' '.join(args)} failed:\n{(exc.stderr or '').strip()}")
    return result.stdout


def version_key(tag: str) -> tuple[int, int, int]:
    """Return the ``(major, minor, patch)`` sort key for a well-formed ``vN.N.N`` tag."""
    match = VERSION_TAG_RE.match(tag)
    if not match:  # pragma: no cover - callers pre-filter, this is a guard
        raise ValueError(f"not a version tag: {tag!r}")
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def derive_repo_url(repo: str) -> str:
    """Derive the ``https://github.com/<owner>/<repo>`` base from ``origin``.

    Handles both ``https://github.com/o/r.git`` and ``git@github.com:o/r.git``
    remotes; falls back to a neutral placeholder if origin is missing so the
    script never crashes on a detached clone.
    """
    try:
        url = run_git(repo, ["remote", "get-url", "origin"]).strip()
    except SystemExit:
        return "https://github.com/krt-profit/basetool"
    url = re.sub(r"\.git$", "", url)
    ssh = re.match(r"git@([^:]+):(.+)$", url)
    if ssh:
        return f"https://{ssh.group(1)}/{ssh.group(2)}"
    return url


def blame_line_shas(repo: str, rev: str, path: str) -> dict[int, str]:
    """Map each 1-based final line number of ``path`` at ``rev`` to its blame SHA."""
    out = run_git(repo, ["blame", "--line-porcelain", rev, "--", path])
    line_sha: dict[int, str] = {}
    for line in out.splitlines():
        match = PORCELAIN_HEADER_RE.match(line)
        if match:
            line_sha[int(match.group("final"))] = match.group("sha")
    return line_sha


class TagResolver:
    """Resolve a commit SHA to the earliest well-formed release tag containing it.

    ``git tag --contains`` is run once per unique SHA and memoised; the result is
    the minimum ``vN.N.N`` tag by version, i.e. the first release the commit
    shipped in. SHAs contained in no version tag resolve to ``None`` (unreleased).
    """

    def __init__(self, repo: str) -> None:
        """Bind the resolver to ``repo`` and start with an empty cache."""
        self._repo = repo
        self._cache: dict[str, str | None] = {}

    def earliest_tag(self, sha: str) -> str | None:
        """Return the earliest ``vN.N.N`` tag containing ``sha``, or ``None``."""
        if sha not in self._cache:
            out = run_git(self._repo, ["tag", "--contains", sha])
            versions = [t for t in out.split() if VERSION_TAG_RE.match(t)]
            self._cache[sha] = min(versions, key=version_key) if versions else None
        return self._cache[sha]


class DateResolver:
    """Resolve a tag to the short committer date of the commit it points at."""

    def __init__(self, repo: str) -> None:
        """Bind the resolver to ``repo`` and start with an empty cache."""
        self._repo = repo
        self._cache: dict[str, str] = {}

    def tag_date(self, tag: str) -> str:
        """Return the ``YYYY-MM-DD`` committer date of ``tag``'s commit."""
        if tag not in self._cache:
            out = run_git(self._repo, ["log", "-1", "--format=%cd", "--date=short", tag])
            self._cache[tag] = out.strip()
        return self._cache[tag]


class Entry:
    """One changelog bullet: its section, its text, and the lines it spans.

    ``line_numbers`` are 1-based file lines (used for blame); ``text`` is the
    rendered bullet with trailing blank lines trimmed. ``tags`` is filled in
    after blame and holds the distinct release tags its lines mapped to.
    """

    def __init__(self, section: str, line_numbers: list[int], text: list[str]) -> None:
        """Capture an entry's section, spanned file lines, and trimmed text."""
        self.section = section
        self.line_numbers = line_numbers
        self.text = text
        self.tags: list[str] = []
        self.release: str | None = None

    def render(self) -> str:
        """Return the entry as a newline-joined markdown block (no trailing newline)."""
        return "\n".join(self.text)


def split_sections(lines: list[str]) -> tuple[list[str], int, int, list[str]]:
    """Split the file into (preamble, unreleased_start, unreleased_end, tail).

    ``unreleased_start`` is the index of the ``## [Unreleased]`` header;
    ``unreleased_end`` is the index of the next ``## `` header (the first
    already-cut section) or ``len(lines)``. The preamble is everything before the
    Unreleased header; the tail is everything from ``unreleased_end`` onward.
    """
    start = next(
        (i for i, ln in enumerate(lines)
         if ln.startswith("## ") and "unreleased" in ln.lower()),
        None,
    )
    if start is None:
        sys.exit("error: no '## [Unreleased]' header found in CHANGELOG")
    end = next(
        (i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")),
        len(lines),
    )
    return lines[:start], start, end, lines[end:]


def parse_entries(lines: list[str], start: int, end: int) -> tuple[list[Entry], list[int]]:
    """Parse the ``[Unreleased]`` body into entries; return (entries, anomaly_lines).

    Walks ``lines[start+1:end]`` tracking the current ``### `` section. Each
    top-level ``- `` bullet absorbs following indented / continuation / blank
    lines until the next top-level bullet or ``#`` heading. ``anomaly_lines`` are
    1-based numbers of non-blank lines that fell outside any entry (e.g. prose
    sitting directly under ``[Unreleased]`` before the first ``### `` header) --
    these are reported so nothing is silently dropped.
    """
    entries: list[Entry] = []
    anomalies: list[int] = []
    section: str | None = None
    i = start + 1
    while i < end:
        line = lines[i]
        if line.startswith("### "):
            section = line[4:].strip().split()[0] if line[4:].strip() else "Misc"
            i += 1
            continue
        if line.startswith("- "):
            block_start = i
            i += 1
            while i < end and not lines[i].startswith("- ") and not lines[i].startswith("#"):
                i += 1
            block = lines[block_start:i]
            line_numbers = [block_start + 1 + off for off, _ in enumerate(block)]
            # Trim trailing blank lines from the rendered text (keep numbers for blame).
            text = list(block)
            while text and text[-1].strip() == "":
                text.pop()
            entries.append(Entry(section or "Misc", line_numbers, text))
            continue
        if line.strip():
            anomalies.append(i + 1)
        i += 1
    return entries, anomalies


def order_sections(present: list[str]) -> list[str]:
    """Order section names: canonical Keep-a-Changelog order, then extras first-seen."""
    ordered = [s for s in CANONICAL_ORDER if s in present]
    seen = set(ordered)
    for section in present:  # append non-canonical sections in first-seen order
        if section not in seen:
            ordered.append(section)
            seen.add(section)
    return ordered


def render_group(entries: list[Entry]) -> list[str]:
    """Render one release's entries as markdown lines, grouped + ordered by section.

    Entries keep their original top-to-bottom (newest-first) order within a
    section; one blank line separates consecutive entries and trails each section.
    """
    by_section: dict[str, list[Entry]] = {}
    for entry in entries:
        by_section.setdefault(entry.section, []).append(entry)
    out: list[str] = []
    for section in order_sections(list(by_section)):
        out.append(f"### {section}")
        out.append("")
        for entry in by_section[section]:
            out.append(entry.render())
            out.append("")
    return out


def build_changelog(
    preamble: list[str],
    tail: list[str],
    entries: list[Entry],
    dates: DateResolver,
    repo_url: str,
) -> str:
    """Assemble the rewritten CHANGELOG text from its parts.

    ``[Unreleased]`` is emitted first (with any genuinely-unreleased entries),
    then one ``## [vX.Y.Z] - DATE`` section per release in descending version
    order, then the verbatim ``tail``.
    """
    by_release: dict[str | None, list[Entry]] = {}
    for entry in entries:
        by_release.setdefault(entry.release, []).append(entry)

    out: list[str] = []
    # Preamble verbatim, then exactly one blank line before the first heading.
    out.extend(ln.rstrip("\n") for ln in preamble)
    while out and out[-1].strip() == "":
        out.pop()
    if out:
        out.append("")

    out.append("## [Unreleased]")
    out.append("")
    if None in by_release:
        out.extend(render_group(by_release[None]))

    released = sorted((t for t in by_release if t is not None), key=version_key, reverse=True)
    for tag in released:
        url = f"{repo_url}/releases/tag/{tag}"
        out.append(f"## [{tag}]({url}) - {dates.tag_date(tag)}")
        out.append("")
        out.extend(render_group(by_release[tag]))

    # Tail (already-cut sections) verbatim, with a single blank line before it.
    while out and out[-1].strip() == "":
        out.pop()
    out.append("")
    out.extend(ln.rstrip("\n") for ln in tail)

    return "\n".join(out).rstrip("\n") + "\n"


def print_report(entries: list[Entry], anomalies: list[int], dates: DateResolver) -> None:
    """Print the dry-run digest: per-release counts, multi-tag entries, anomalies."""
    by_release: dict[str | None, list[Entry]] = {}
    for entry in entries:
        by_release.setdefault(entry.release, []).append(entry)

    print("=" * 78)
    print("CHANGELOG RECONCILE -- DRY RUN (no files written; pass --write to apply)")
    print("=" * 78)
    print(f"total entries parsed: {len(entries)}")

    def describe(group: str | None) -> str:
        label = group if group else "[Unreleased] (no containing tag)"
        date = f"  ({dates.tag_date(group)})" if group else ""
        bucket = by_release.get(group, [])
        sections: dict[str, int] = {}
        for entry in bucket:
            sections[entry.section] = sections.get(entry.section, 0) + 1
        order = order_sections(list(sections))
        breakdown = ", ".join(f"{s}:{sections[s]}" for s in order)
        return f"  {label}{date}: {len(bucket)} entries [{breakdown}]"

    print("\n-- entries per release (descending) --")
    print(describe(None))
    for tag in sorted((t for t in by_release if t is not None), key=version_key, reverse=True):
        print(describe(tag))

    multi = [e for e in entries if len(set(e.tags)) > 1]
    print(f"\n-- entries whose lines span >1 release tag: {len(multi)} "
          "(release = earliest; verify these) --")
    for entry in multi[:25]:
        head = entry.text[0][:88] if entry.text else "(empty)"
        print(f"   {entry.release} <= {sorted(set(entry.tags), key=version_key)}  {head}")
    if len(multi) > 25:
        print(f"   ... and {len(multi) - 25} more")

    if anomalies:
        print(f"\n!! {len(anomalies)} non-blank line(s) fell outside any entry "
              f"(lines: {anomalies[:20]}{' ...' if len(anomalies) > 20 else ''})")
        print("   These would be LOST on --write. Inspect before applying.")
    else:
        print("\nOK: every non-blank line in [Unreleased] was captured by an entry.")


def main() -> None:
    """Parse args, map entries to releases, and either report or rewrite the file."""
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", default=os.getcwd(), help="Repo root. Default: cwd.")
    parser.add_argument("--changelog", default=None,
                        help="Path to CHANGELOG.md. Default: <repo>/CHANGELOG.md.")
    parser.add_argument("--rev", default="HEAD",
                        help="Revision to blame the changelog at. Default: HEAD.")
    parser.add_argument("--repo-url", default=None,
                        help="GitHub base URL for version links. Default: derived "
                             "from the 'origin' remote.")
    parser.add_argument("--write", action="store_true",
                        help="Rewrite CHANGELOG.md in place (default is a dry run).")
    args = parser.parse_args()

    repo = os.path.abspath(args.repo)
    if not os.path.exists(os.path.join(repo, ".git")):  # worktrees keep .git as a file
        sys.exit(f"error: {repo} is not a git repository")
    changelog = args.changelog or os.path.join(repo, "CHANGELOG.md")
    if not os.path.isfile(changelog):
        sys.exit(f"error: no CHANGELOG.md at {changelog}")
    repo_url = args.repo_url or derive_repo_url(repo)

    with open(changelog, encoding="utf-8") as handle:
        text = handle.read()
    if text.startswith("﻿"):
        sys.exit("error: CHANGELOG.md has a UTF-8 BOM; this repo's markdown is BOM-less")
    lines = text.split("\n")

    preamble, start, end, tail = split_sections(lines)
    entries, anomalies = parse_entries(lines, start, end)

    line_sha = blame_line_shas(repo, args.rev, os.path.relpath(changelog, repo))
    tags = TagResolver(repo)
    dates = DateResolver(repo)
    for entry in entries:
        resolved = []
        for number in entry.line_numbers:
            sha = line_sha.get(number)
            if sha and lines[number - 1].strip():  # ignore blank lines for mapping
                resolved.append(tags.earliest_tag(sha))
        entry.tags = [t for t in resolved if t is not None]
        entry.release = min(entry.tags, key=version_key) if entry.tags else None

    print_report(entries, anomalies, dates)

    if not args.write:
        print("\n(dry run -- re-run with --write to rewrite CHANGELOG.md)")
        return
    if anomalies:
        sys.exit("\nrefusing to write: uncaptured non-blank lines would be lost "
                 "(see report above)")

    new_text = build_changelog(preamble, tail, entries, dates, repo_url)
    with open(changelog, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(new_text)
    print(f"\nwrote {changelog} ({len(new_text.splitlines())} lines)")


if __name__ == "__main__":
    main()
