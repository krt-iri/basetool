#!/usr/bin/env python3
"""Local, never-committed pointer for *how far release notes have been written*.

This module is the shared backend for the release-notes progress tracker. It is
not meant to be run on its own -- two other scripts import it:

  * ``gather_changes.py`` calls :func:`read_state` to resolve the default
    ``--since`` on a no-argument run, so ``/release-notes`` can resume from
    exactly where the last notes ended without the user remembering the spot.
  * ``track_release_notes.py`` calls :func:`write_state` to advance the pointer
    once a batch of notes has actually been produced.

The pointer lives in a single JSON file named ``.release-notes-state.json`` (see
:data:`STATE_FILENAME`), stored in the *shared* git directory (``git rev-parse
--git-common-dir``, e.g. ``.git/.release-notes-state.json``). That directory
resolves to the same place -- the main checkout's ``.git`` -- from the main working
tree and from every linked worktree alike, and it sits outside every working tree,
so a single pointer is shared across all worktrees and can never be committed.

Earlier versions kept the file at the working-tree *root* behind a ``.gitignore``
entry; that is now only a fallback for when the git directory cannot be resolved.
It is also the reason resume used to fail silently in this project's
per-session-worktree workflow: a git-ignored root file is not shared between
worktrees, so each fresh session's worktree started blind. The pointer records the
commit up to which the last notes went, the newest release tag reachable from that
commit, and that commit's date.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

# The changelog tooling and these messages carry umlauts and arrows; Windows
# consoles default to cp1252 and would crash on them. Force UTF-8 on import so
# every script that pulls this module in prints safely anywhere.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # already wrapped / not reconfigurable
        pass

# The local, git-ignored pointer file (repo-root relative) and its schema marker.
# Bump STATE_SCHEMA only if the on-disk shape changes incompatibly.
STATE_FILENAME = ".release-notes-state.json"
STATE_SCHEMA = 1
# What we append to .gitignore. The leading slash anchors the pattern to the repo
# root so it can never accidentally match a same-named file in a subdirectory.
GITIGNORE_ENTRY = f"/{STATE_FILENAME}"
GITIGNORE_COMMENT = "### Release-notes local progress tracker (never commit) ###"

# A well-formed release tag; typo tags (v-0.2.23, v.0.1.1) are ignored everywhere.
VERSION_TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
# A bare calendar date and a date carrying a time (space or 'T' separator); used
# so :func:`resolve_commit` can accept the same date forms as gather_changes.py.
DATE_ONLY_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DATE_TIME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?$")


def run_git(repo: str, args: list[str]) -> str | None:
    """Run ``git <args>`` in ``repo``; return stdout, or ``None`` on any failure.

    Unlike the strict ``run_git`` in the sibling scripts this never exits the
    process: callers here routinely probe git (does this commit exist? is A an
    ancestor of B?) where a non-zero exit is a meaningful answer, not a fatal
    error. ``None`` therefore means "git said no / could not run".
    """
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=repo,
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None
    return result.stdout


def is_git_repo(repo: str) -> bool:
    """Return whether ``repo`` is a git working tree (``.git`` dir *or* file).

    Worktrees keep ``.git`` as a file pointing at the real git dir rather than a
    directory, so both shapes must count as a repository.
    """
    return os.path.exists(os.path.join(repo, ".git"))


def version_key(tag: str) -> tuple[int, int, int]:
    """Return the ``(major, minor, patch)`` sort key for a ``vN.N.N`` tag."""
    match = VERSION_TAG_RE.match(tag)
    if not match:  # pragma: no cover - callers pre-filter with VERSION_TAG_RE
        raise ValueError(f"not a version tag: {tag!r}")
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def common_git_dir(repo: str) -> str | None:
    """Return the absolute *shared* git directory for ``repo``, or ``None``.

    ``git rev-parse --git-common-dir`` resolves to the same directory -- the main
    checkout's ``.git`` -- from the main working tree and from every linked worktree
    alike (a worktree's own ``.git/worktrees/<name>`` points back at it via its
    ``commondir``). A file kept here is therefore shared by all worktrees and, living
    outside every working tree, can never be committed. Returns ``None`` when git is
    absent or the path does not resolve to a real directory, so callers fall back to
    the working-tree root.
    """
    out = run_git(repo, ["rev-parse", "--git-common-dir"])
    if not out or not out.strip():
        return None
    path = out.strip()
    if not os.path.isabs(path):  # main checkout prints a repo-relative ".git"
        path = os.path.join(repo, path)
    path = os.path.abspath(path)
    return path if os.path.isdir(path) else None


def state_path(repo: str) -> str:
    """Return the absolute path of the pointer file for ``repo``.

    Preferred location is the shared git directory (:func:`common_git_dir`), so the
    pointer is one-per-repository, survives across throwaway per-session worktrees,
    and is never committed. Falls back to the working-tree root (the historical
    location, paired with a ``.gitignore`` entry) only when the git directory cannot
    be resolved.
    """
    return os.path.join(common_git_dir(repo) or repo, STATE_FILENAME)


def read_state(repo: str) -> dict | None:
    """Load the pointer file, or ``None`` if it is missing or unreadable.

    A corrupt file is treated as "no pointer" (a warning is printed to stderr)
    rather than crashing the caller, so a damaged tracker degrades gracefully to
    the ask-the-user path instead of breaking the whole skill.
    """
    path = state_path(repo)
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError) as exc:
        print(f"# warning: ignoring unreadable {STATE_FILENAME}: {exc}", file=sys.stderr)
        return None


def commit_exists(repo: str, sha: str) -> bool:
    """Return whether ``sha`` names a commit object that exists in ``repo``.

    Guards against a stale pointer that survives a history rewrite or points at a
    commit absent from a fresh clone -- callers fall back to asking the user.
    """
    return run_git(repo, ["cat-file", "-e", f"{sha}^{{commit}}"]) is not None


def is_ancestor(repo: str, ancestor: str, descendant: str) -> bool:
    """Return whether ``ancestor`` is an ancestor of (or equal to) ``descendant``.

    Used to keep the pointer monotonic: a new endpoint may only be recorded if
    the current pointer leads up to it. ``git merge-base --is-ancestor`` exits 0
    on yes, non-zero on no (or on a bad ref), so a ``None`` from :func:`run_git`
    safely reads as "not ahead" and the caller refuses to move the pointer.
    """
    return run_git(repo, ["merge-base", "--is-ancestor", ancestor, descendant]) is not None


def resolve_commit(repo: str, ref: str) -> tuple[str, str] | None:
    """Resolve a ref/date into ``(full_sha, YYYY-MM-DD committer date)``, or ``None``.

    ``ref`` may be a tag, branch, or commit SHA (resolved directly), or a date /
    date-with-time -- the same forms gather_changes.py accepts. A date selects the
    *last* commit up to the end of that moment (so a ``--until``-style endpoint
    stays inclusive), mirroring how the gather step scopes a date window.
    """
    if DATE_TIME_RE.match(ref):
        out = run_git(repo, ["log", "-1", f"--until={ref.replace('T', ' ', 1)}",
                             "--format=%H%x09%cd", "--date=short"])
    elif DATE_ONLY_RE.match(ref):
        out = run_git(repo, ["log", "-1", f"--until={ref} 23:59:59",
                             "--format=%H%x09%cd", "--date=short"])
    else:
        out = run_git(repo, ["log", "-1", "--format=%H%x09%cd", "--date=short", ref])
    if not out or not out.strip():
        return None
    sha, _, date = out.strip().partition("\t")
    return (sha.strip(), date.strip()) if sha.strip() else None


def merged_version_tags(repo: str, sha: str) -> set[str]:
    """Return every well-formed ``vN.N.N`` tag reachable from ``sha``.

    These are the releases that already contain the anchor commit, i.e. the ones
    a resume window must *exclude* when picking dated changelog sections, so notes
    do not re-announce releases that were covered last time.
    """
    out = run_git(repo, ["tag", "--merged", sha]) or ""
    return {tag for tag in out.split() if VERSION_TAG_RE.match(tag)}


def newest_version_tag(repo: str, sha: str) -> str | None:
    """Return the highest-versioned ``vN.N.N`` tag reachable from ``sha``, or ``None``.

    Stored alongside the pointer purely for human readability ("notes went up to
    v0.3.57"); the resume logic itself keys off the commit SHA, not this tag.
    """
    tags = merged_version_tags(repo, sha)
    return max(tags, key=version_key) if tags else None


def iso_now() -> str:
    """Return the current UTC time as an ``YYYY-MM-DDTHH:MM:SSZ`` stamp.

    Project convention is to record times in UTC; this is metadata only (when the
    pointer was last advanced) and never feeds back into the git window.
    """
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def ensure_gitignore(repo: str) -> str:
    """Make sure ``.gitignore`` ignores the pointer file; return what happened.

    Returns ``"present"`` if the anchored entry was already there, ``"added"`` if
    it was appended (creating ``.gitignore`` if absent), or ``"error: ..."`` if
    the file could not be written. The existing content, its newline style and any
    BOM are preserved -- the entry is appended under a short comment so the diff,
    if the user ever commits ``.gitignore``, is one obvious block.
    """
    path = os.path.join(repo, ".gitignore")
    content = ""
    if os.path.isfile(path):
        try:
            # newline="" keeps the real line endings intact, so CRLF detection
            # below is accurate and an existing CRLF file is never rewritten to LF.
            with open(path, encoding="utf-8", newline="") as handle:
                content = handle.read()
        except OSError as exc:
            return f"error: {exc}"
    if any(line.strip() == GITIGNORE_ENTRY for line in content.splitlines()):
        return "present"
    newline = "\r\n" if "\r\n" in content else "\n"
    addition = ""
    if content and not content.endswith(("\n", "\r")):
        addition += newline  # finish the last, unterminated line first
    if content:
        addition += newline  # one blank line before our block
    addition += GITIGNORE_COMMENT + newline + GITIGNORE_ENTRY + newline
    try:
        with open(path, "w", encoding="utf-8", newline="") as handle:
            handle.write(content + addition)
    except OSError as exc:
        return f"error: {exc}"
    return "added"


def verify_ignored(repo: str) -> bool:
    """Return whether the pointer file is safe from ever being committed.

    When the pointer lives in the shared git directory (the normal case) it is
    outside every working tree and cannot be staged into a commit, so this is
    trivially true. In the working-tree-root fallback it consults ``git check-ignore
    -q``, which honours the working-tree ``.gitignore`` regardless of whether that
    file has been committed -- confirming the pointer is ignored the moment
    :func:`ensure_gitignore` has run.
    """
    if common_git_dir(repo) is not None:
        return True
    return run_git(repo, ["check-ignore", "-q", STATE_FILENAME]) is not None


def write_state(repo: str, *, ref: str, sha: str, date: str, tag: str | None) -> dict:
    """Write the pointer file for ``repo`` and lock it out of git; return a summary.

    ``ref`` is what the caller asked to mark (e.g. ``HEAD`` or a tag), ``sha`` its
    resolved full commit, ``date`` that commit's short committer date and ``tag``
    the newest release reachable from it (or ``None``). When the pointer is stored
    inside the shared git directory no ``.gitignore`` work is needed (it is outside
    every working tree); in the working-tree-root fallback ``.gitignore`` is asserted
    *before* the file is written so the pointer is born ignored. The returned dict
    carries ``path``, ``location`` (``"git-common-dir"`` or ``"worktree-root"``), the
    ``gitignore`` status and the :func:`verify_ignored` ``ignored`` flag for the
    caller to report.
    """
    git_dir = common_git_dir(repo)
    in_git_dir = git_dir is not None
    gitignore_status = (
        "n/a (inside the git dir -- outside every working tree)"
        if in_git_dir else ensure_gitignore(repo))
    payload = {
        "schema": STATE_SCHEMA,
        "last_covered": {"ref": ref, "sha": sha, "date": date, "tag": tag},
        "updated_at": iso_now(),
        "note": (
            "Local progress pointer for the release-notes skill, kept in the shared "
            "git dir so it is shared across worktrees and never committed. Records "
            "how far the last release notes went so the next no-argument run resumes "
            "here. Safe to delete; it is never committed."
        ),
    }
    path = os.path.join(git_dir or repo, STATE_FILENAME)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    return {
        "path": path,
        "location": "git-common-dir" if in_git_dir else "worktree-root",
        "gitignore": gitignore_status,
        "ignored": verify_ignored(repo),
    }
