#!/usr/bin/env bash
#
# Regression tests for scripts/check-flyway-migrations.sh.
#
# Builds throwaway git repositories that reproduce the migration-numbering
# scenarios the checker is meant to (and meant NOT to) flag, then asserts its
# exit status for each. No network, no Gradle — pure git + bash, runs in a
# couple of seconds.
#
# Usage:
#   scripts/check-flyway-migrations.test.sh
#
# The headline case is the squash-merge-in-flight race that produced a false
# "V194 does not sort after V194" failure on an already-merged PR: the Flyway
# CI run fetched origin/main *after* the PR was squash-merged, so the branch's
# own migration was already on the base, and the checker compared it against
# itself.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECKER="${SCRIPT_DIR}/check-flyway-migrations.sh"
MIG_SUBDIR="db/migration"

if [[ ! -f "$CHECKER" ]]; then
  echo "FATAL: checker not found at ${CHECKER}" >&2
  exit 1
fi

tests_run=0
tests_failed=0

# Creates a throwaway temp directory and prints its absolute path. Each
# scenario gets its own so they cannot interfere with one another.
mktmp() {
  mktemp -d "${TMPDIR:-/tmp}/flyway-check-test.XXXXXX"
}

# Initialises a fresh git repo at $1 with a deterministic identity, gpg signing
# off, and `main` as the initial branch — independent of the host's git config.
init_repo() {
  local repo="$1"
  git -C "$repo" init -q -b main
  git -C "$repo" config user.email "test@example.com"
  git -C "$repo" config user.name "Flyway Test"
  git -C "$repo" config commit.gpgsign false
  # Keep line endings verbatim so a Windows host's autocrlf does not spew
  # "LF will be replaced by CRLF" warnings on every `git add` in these repos.
  git -C "$repo" config core.autocrlf false
}

# Writes a trivial migration file $2 (basename) into the migration dir of repo
# $1, creating the directory on first use.
write_migration() {
  local repo="$1" name="$2"
  mkdir -p "${repo}/${MIG_SUBDIR}"
  printf -- '-- test migration %s\n' "$name" >"${repo}/${MIG_SUBDIR}/${name}"
}

# Stages everything and commits with message $2 in repo $1.
commit_all() {
  local repo="$1" msg="$2"
  git -C "$repo" add -A
  git -C "$repo" commit -q -m "$msg"
}

# Runs the checker inside repo $1 with base ref $2. Echoes nothing; returns the
# checker's exit code. Captured output is stashed in the global LAST_OUTPUT.
run_checker() {
  local repo="$1" base_ref="$2" rc=0
  LAST_OUTPUT="$(
    cd "$repo" &&
      FLYWAY_MIGRATION_DIR="$MIG_SUBDIR" FLYWAY_BASE_REF="$base_ref" \
        bash "$CHECKER" 2>&1
  )" || rc=$?
  return "$rc"
}

# Records a passed/failed assertion with a description; dumps LAST_OUTPUT on
# failure so a red test is self-diagnosing.
record() {
  local ok="$1" desc="$2"
  tests_run=$((tests_run + 1))
  if [[ "$ok" -eq 1 ]]; then
    echo "  ok   - ${desc}"
  else
    tests_failed=$((tests_failed + 1))
    echo "  FAIL - ${desc}"
    echo "----- checker output -----"
    echo "${LAST_OUTPUT}"
    echo "--------------------------"
  fi
}

# assert_exit <expected-rc> <actual-rc> <description>.
assert_exit() {
  local expected="$1" actual="$2" desc="$3"
  if [[ "$actual" -eq "$expected" ]]; then
    record 1 "${desc} (exit ${expected})"
  else
    record 0 "${desc} (expected exit ${expected}, got ${actual})"
  fi
}

# assert_contains <substring> <description> — fails unless LAST_OUTPUT contains
# the substring. Distinguishes a correct verdict from an incidental exit code
# (a set -e/git/parse error also exits 1), so a test cannot pass red-for-the-
# wrong-reason.
assert_contains() {
  local needle="$1" desc="$2"
  if [[ "$LAST_OUTPUT" == *"$needle"* ]]; then
    record 1 "$desc"
  else
    record 0 "$desc (output missing: '${needle}')"
  fi
}

# assert_excludes <substring> <description> — fails if LAST_OUTPUT contains it.
assert_excludes() {
  local needle="$1" desc="$2"
  if [[ "$LAST_OUTPUT" != *"$needle"* ]]; then
    record 1 "$desc"
  else
    record 0 "$desc (output unexpectedly contained: '${needle}')"
  fi
}

# ---------------------------------------------------------------------------
# Scenario 1 (the bug): squash-merge in flight.
#
# Branch adds V194__seed. The PR is squash-merged onto main while CI runs, so
# main now ALSO contains V194__seed under the same filename, but the branch
# head is not an ancestor of main (squash = new commit). The checker must NOT
# flag this — the migration is the branch's own, already on the base.
# ---------------------------------------------------------------------------
scenario_squash_merge_race() {
  echo "Scenario: squash-merge in flight (must PASS)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V100__m100.sql"
  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  # Branch off and add the PR's migration.
  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V194__seed_bank_notifications.sql"
  commit_all "$repo" "feat: add V194 seed"

  # Squash-merge equivalent: a brand-new commit on main carrying the same file,
  # WITHOUT merging the feature branch (so feature head stays a non-ancestor).
  git -C "$repo" checkout -q main
  write_migration "$repo" "V194__seed_bank_notifications.sql"
  commit_all "$repo" "feat(bank): ... (#854)"

  # CI checks out the PR head and compares against the (now-updated) base.
  git -C "$repo" checkout -q feature
  run_checker "$repo" "main" || rc=$?
  assert_exit 0 "$rc" "branch migration already merged to base is not a self-collision"
  assert_contains "is already present on 'main'" "the skip is reported as a notice"
  assert_excludes "::error" "no error annotation is emitted"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 2: genuine collision — same number, DIFFERENT filename on the base.
# This is the real merge-race the guard exists for and must still FAIL.
# ---------------------------------------------------------------------------
scenario_genuine_collision() {
  echo "Scenario: genuine same-number collision (must FAIL)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V194__feature_thing.sql"
  commit_all "$repo" "feat: add V194 feature_thing"

  # A DIFFERENT V194 landed on main from another branch.
  git -C "$repo" checkout -q main
  write_migration "$repo" "V194__other_thing.sql"
  commit_all "$repo" "feat: add V194 other_thing"

  git -C "$repo" checkout -q feature
  run_checker "$repo" "main" || rc=$?
  assert_exit 1 "$rc" "different file sharing the number is still flagged"
  assert_contains "Out-of-order Flyway migration" "it fails for the ordering reason"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 3: normal in-order addition (must PASS).
# ---------------------------------------------------------------------------
scenario_in_order() {
  echo "Scenario: normal in-order addition (must PASS)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V194__new_thing.sql"
  commit_all "$repo" "feat: add V194 new_thing"

  run_checker "$repo" "main" || rc=$?
  assert_exit 0 "$rc" "V194 sorts after base V193"
  assert_contains "ok: V194__new_thing.sql" "the new file was actually evaluated and accepted"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 4: out-of-order addition — a NEW file numbered at/below the base
# tip (must FAIL). Branch adds a fresh V150 while main is already at V193.
# ---------------------------------------------------------------------------
scenario_out_of_order() {
  echo "Scenario: new migration numbered below base tip (must FAIL)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V150__late_low_number.sql"
  commit_all "$repo" "feat: add V150 late_low_number"

  run_checker "$repo" "main" || rc=$?
  assert_exit 1 "$rc" "new file at/below the base tip is flagged"
  assert_contains "Out-of-order Flyway migration" "it fails for the ordering reason"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 5: duplicate version in the working tree (must FAIL) — the other
# half of the guard, independent of the base ref.
# ---------------------------------------------------------------------------
scenario_duplicate_in_tree() {
  echo "Scenario: duplicate version in the tree (must FAIL)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V100__first.sql"
  write_migration "$repo" "V100__second.sql"
  commit_all "$repo" "two files claiming V100"

  run_checker "$repo" "main" || rc=$?
  assert_exit 1 "$rc" "two files claiming the same number are flagged"
  assert_contains "Duplicate Flyway version" "it fails for the duplicate reason"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 6: the decisive one. Branch adds BOTH its own already-squash-merged
# V194 AND a genuinely-new V150 numbered below the base tip. The per-file skip
# must drop only V194 and STILL flag V150 (must FAIL). A naive "blanket skip"
# regression — skip the whole ordering check the moment any addition is already
# on base — would pass every other scenario but let V150 through; this catches
# it.
# ---------------------------------------------------------------------------
scenario_partial_skip_still_flags() {
  echo "Scenario: merged file + new below-tip file (must FAIL on the new one)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V194__seed.sql"
  write_migration "$repo" "V150__late_low_number.sql"
  commit_all "$repo" "feat: add V194 seed and V150 late"

  # Squash-merge equivalent of only the V194 onto main.
  git -C "$repo" checkout -q main
  write_migration "$repo" "V194__seed.sql"
  commit_all "$repo" "feat(bank): ... (#854)"

  git -C "$repo" checkout -q feature
  run_checker "$repo" "main" || rc=$?
  assert_exit 1 "$rc" "the genuinely-new below-tip file is still flagged"
  assert_contains "V150__late_low_number.sql" "the flagged file is V150, not V194"
  assert_contains "is already present on 'main'" "V194 was still recognised as merged-on-base"
  rm -rf "$repo"
}

# ---------------------------------------------------------------------------
# Scenario 7: merged file + new in-order file. The skip must drop the merged
# V194 yet still EVALUATE and accept the new V195 (must PASS, with V195 ok'd) —
# proving additions are not skipped wholesale.
# ---------------------------------------------------------------------------
scenario_partial_skip_evaluates_new() {
  echo "Scenario: merged file + new in-order file (must PASS, new one evaluated)"
  local repo rc=0
  repo="$(mktmp)"
  init_repo "$repo"

  write_migration "$repo" "V193__m193.sql"
  commit_all "$repo" "base up to V193"

  git -C "$repo" checkout -q -b feature
  write_migration "$repo" "V194__seed.sql"
  write_migration "$repo" "V195__next_thing.sql"
  commit_all "$repo" "feat: add V194 seed and V195 next"

  git -C "$repo" checkout -q main
  write_migration "$repo" "V194__seed.sql"
  commit_all "$repo" "feat(bank): ... (#854)"

  git -C "$repo" checkout -q feature
  run_checker "$repo" "main" || rc=$?
  assert_exit 0 "$rc" "merged V194 skipped, in-order V195 accepted"
  assert_contains "ok: V195__next_thing.sql" "the new file was evaluated, not skipped wholesale"
  assert_excludes "::error" "no error annotation is emitted"
  rm -rf "$repo"
}

scenario_squash_merge_race
scenario_genuine_collision
scenario_in_order
scenario_out_of_order
scenario_duplicate_in_tree
scenario_partial_skip_still_flags
scenario_partial_skip_evaluates_new

echo
if [[ "$tests_failed" -eq 0 ]]; then
  echo "All ${tests_run} Flyway-checker tests passed."
  exit 0
fi
echo "${tests_failed}/${tests_run} Flyway-checker test(s) failed."
exit 1
