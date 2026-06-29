#!/usr/bin/env bash
#
# Guards the Flyway migration directory against the two version-numbering
# mistakes that break the build in ways the per-branch Gradle run does NOT
# catch locally:
#
#   1. Duplicate version numbers — two files sharing the same V<n>. Flyway
#      treats the version tuple as a unique, immutable identifier, so two
#      files claiming V123 abort migration on every Spring Boot start.
#
#   2. Out-of-order additions — a newly added migration whose number is not
#      strictly greater than the highest number already on the base branch.
#      This is the classic "merge collision": branch A opens with V122 while
#      V122 also lands on main from branch B. Each branch builds green in
#      isolation; the failure only surfaces once they share a tree (the PR
#      merge commit CI tests), where it blows up every @SpringBootTest
#      context. Requiring new files to sort *after* main's current tip turns
#      that late, cryptic failure into an early, explicit one.
#
# Gaps in the sequence are allowed (e.g. a skipped V87) — contiguity is not
# a Flyway requirement and is not enforced here.
#
# Usage:
#   scripts/check-flyway-migrations.sh
#
# Configuration via environment variables:
#   FLYWAY_MIGRATION_DIR  Directory to scan (default: the backend migration
#                         dir). Relative to the repository root.
#   FLYWAY_BASE_REF       Git ref representing the base branch tip to compare
#                         against (e.g. "origin/main"). When unset or not
#                         resolvable, the ordering check is skipped and only
#                         the duplicate check runs — this keeps the script
#                         useful for a quick local sanity pass without a
#                         fetched base.
#
# Emits GitHub Actions `::error` / `::notice` annotations so failures show up
# inline in the workflow log; exits non-zero on the first kind of violation
# it finds (both checks always run so a single invocation reports everything).

set -euo pipefail

MIGRATION_DIR="${FLYWAY_MIGRATION_DIR:-backend/src/main/resources/db/migration}"
BASE_REF="${FLYWAY_BASE_REF:-}"

# Anchor at the repository root so the relative MIGRATION_DIR resolves the
# same way whether invoked from CI, a Git hook, or an interactive shell.
if repo_root=$(git rev-parse --show-toplevel 2>/dev/null); then
  cd "$repo_root"
fi

if [[ ! -d "$MIGRATION_DIR" ]]; then
  echo "::error title=Flyway check::Migration directory not found: ${MIGRATION_DIR}"
  exit 1
fi

# Extracts the Flyway version token from a versioned-migration basename:
# "V123__add_foo.sql" -> "123". Flyway treats '_' inside the version part as
# a separator equivalent to '.', so we normalise it to '.' to compare
# dotted and underscored forms consistently (the repo currently uses plain
# integers, but this keeps the comparison correct if that ever changes).
flyway_version() {
  local name="$1"
  name="${name#V}"      # strip the leading 'V'
  name="${name%%__*}"   # keep everything before the '__' description marker
  printf '%s' "${name//_/.}"
}

# Returns 0 (true) iff version $1 is strictly greater than version $2 under
# Flyway's numeric, dotted-component ordering. Implemented via `sort -V`
# (version sort), which orders "9" before "10" and "1.2" before "1.10"
# correctly — a plain string sort would not.
version_gt() {
  [[ "$1" == "$2" ]] && return 1
  local highest
  highest=$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n1)
  [[ "$highest" == "$1" ]]
}

# ---------------------------------------------------------------------------
# Check 1: no duplicate version numbers in the working tree.
# ---------------------------------------------------------------------------
mapfile -t files < <(
  find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*__*.sql' -printf '%f\n' | sort
)

if [[ ${#files[@]} -eq 0 ]]; then
  echo "::notice title=Flyway check::No versioned migrations found in ${MIGRATION_DIR}."
  exit 0
fi

failed=0
declare -A version_to_file

for f in "${files[@]}"; do
  v=$(flyway_version "$f")
  if [[ -n "${version_to_file[$v]:-}" ]]; then
    echo "::error title=Duplicate Flyway version::Version ${v} is claimed by both '${version_to_file[$v]}' and '${f}'. Renumber one of them to the next unused integer."
    failed=1
  else
    version_to_file[$v]="$f"
  fi
done

# ---------------------------------------------------------------------------
# Check 2: every newly added migration sorts strictly after the base tip.
# ---------------------------------------------------------------------------
if [[ -z "$BASE_REF" ]]; then
  echo "::notice title=Flyway check::FLYWAY_BASE_REF unset — skipping the new-migration ordering check (duplicate check still ran)."
elif ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
  echo "::notice title=Flyway check::Base ref '${BASE_REF}' not resolvable — skipping the new-migration ordering check."
else
  # Highest version present on the base branch tip. Read straight from the
  # ref (not the working tree) so we compare against what is actually on
  # main, independent of what this branch added.
  mapfile -t base_files < <(
    git ls-tree -r --name-only "$BASE_REF" -- "$MIGRATION_DIR" 2>/dev/null \
      | sed 's#.*/##' \
      | grep -E '^V.*__.*\.sql$' || true
  )

  if [[ ${#base_files[@]} -eq 0 ]]; then
    echo "::notice title=Flyway check::Base ref '${BASE_REF}' has no migrations — every added migration is trivially in order."
  else
    # Basenames present on the base ref. A migration this branch "adds" that is
    # already here under the *same filename* is the branch's own migration that
    # has since landed on the base — typically because the PR was squash-merged
    # while this very run was still in flight. A squash merge creates a fresh
    # commit on main and does NOT make the branch head an ancestor of it, so
    # the merge-base never advances and `git diff merge-base..HEAD` keeps
    # listing the file as "added on the branch". Comparing its version against
    # max_base — which now includes that very file — would flag the migration
    # as colliding with itself (e.g. "V194 does not sort after V194"). Skip
    # those; a *genuine* collision is a different filename sharing the number,
    # which is still caught below.
    declare -A base_names=()
    base_versions=()
    for bf in "${base_files[@]}"; do
      base_names["$bf"]=1
      base_versions+=("$(flyway_version "$bf")")
    done
    max_base=$(printf '%s\n' "${base_versions[@]}" | sort -V | tail -n1)

    # Files this branch *adds* relative to where it diverged from the base.
    # Diffing from the merge-base (rather than the base tip) avoids flagging
    # migrations that merely arrived on main after the branch started.
    merge_base=$(git merge-base "$BASE_REF" HEAD 2>/dev/null || printf '%s' "$BASE_REF")
    mapfile -t added_files < <(
      git diff --diff-filter=A --name-only "$merge_base" HEAD -- "$MIGRATION_DIR" 2>/dev/null \
        | sed 's#.*/##' \
        | grep -E '^V.*__.*\.sql$' || true
    )

    if [[ ${#added_files[@]} -eq 0 ]]; then
      echo "::notice title=Flyway check::No new migrations added relative to '${BASE_REF}'."
    else
      # Drop additions that already exist verbatim on the base ref (the
      # squash-merge-in-flight race described above).
      new_files=()
      for af in "${added_files[@]}"; do
        if [[ -n "${base_names[$af]:-}" ]]; then
          echo "::notice title=Flyway check::'${af}' is already present on '${BASE_REF}' — the branch's own migration has landed on the base (e.g. a squash merge while this run was in flight); not treated as a new addition."
        else
          new_files+=("$af")
        fi
      done

      if [[ ${#new_files[@]} -eq 0 ]]; then
        echo "::notice title=Flyway check::No new migrations added relative to '${BASE_REF}' (all additions are already present on the base)."
      else
        echo "Highest version on '${BASE_REF}': V${max_base}"
        for af in "${new_files[@]}"; do
          av=$(flyway_version "$af")
          if version_gt "$av" "$max_base"; then
            echo "  ok: ${af} (V${av}) > V${max_base}"
          else
            echo "::error title=Out-of-order Flyway migration::New migration '${af}' (V${av}) does not sort after the current highest version on '${BASE_REF}' (V${max_base}). Renumber it to a version greater than V${max_base} — rebase onto the latest base and re-pick the next free integer."
            failed=1
          fi
        done
      fi
    fi
  fi
fi

if [[ $failed -eq 1 ]]; then
  echo
  echo "::error title=Flyway check failed::Fix the migration numbering above. See backend/src/main/resources/db/migration/README.md > 'Hard rules'."
  exit 1
fi

echo
echo "Flyway migration numbering OK: ${#files[@]} migration(s), no duplicates, all additions in order."
