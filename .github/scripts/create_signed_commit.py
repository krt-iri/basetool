#!/usr/bin/env python3
"""Create a GitHub-signed commit on a branch via the GraphQL
``createCommitOnBranch`` mutation.

Release automation runs on a GitHub-hosted runner that holds no commit-signing
key, so a plain ``git commit`` produces an *unsigned* commit. The ``main`` branch
enforces a ``required_signatures`` rule, which would block the release pull
request. ``createCommitOnBranch`` sidesteps this entirely: every commit it
creates is signed with GitHub's own web-flow key and therefore shows as
``Verified`` -- no private key is ever stored on, or handed to, the runner.

The mutation commits the given files (read from the working tree, Base64-encoded)
onto an already-existing branch whose tip equals ``--expected-head-oid``. Create
the branch ref first (e.g. ``POST /git/refs`` at the main tip) and pass that tip
as the expected head; the mutation then fails loudly if the branch moved under
us instead of committing onto an unexpected base. The API token is read from the
``GH_TOKEN`` environment variable; the commit's author is the token's identity,
so the default ``GITHUB_TOKEN`` yields a ``github-actions[bot]`` author that the
DCO check treats as exempt.

Usage:
    create_signed_commit.py --repo owner/name --branch release/v1.2.3 \\
        --expected-head-oid <sha> --message "chore(release): prepare v1.2.3" \\
        [--trailer "Signed-off-by: Name <email>"] [--dry-run] FILE [FILE ...]

Prints the created commit's OID on success; exits non-zero with the GraphQL
error otherwise.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request

# Ensure output prints on any console (Windows defaults to cp1252 and crashes).
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):  # already wrapped / not reconfigurable
        pass

GRAPHQL_ENDPOINT = "https://api.github.com/graphql"

_MUTATION = (
    "mutation($input: CreateCommitOnBranchInput!) {\n"
    "  createCommitOnBranch(input: $input) { commit { oid url } }\n"
    "}"
)


def build_payload(
    repo: str,
    branch: str,
    head_oid: str,
    message: str,
    trailers: list[str],
    files: list[str],
) -> dict:
    """Build the GraphQL request body for ``createCommitOnBranch``.

    :param repo: ``owner/name`` of the target repository.
    :param branch: branch name to commit onto; it must already exist.
    :param head_oid: the branch's current tip SHA, asserted as ``expectedHeadOid``.
    :param message: the commit headline.
    :param trailers: lines joined into the commit-message body (e.g. a
                     ``Signed-off-by`` trailer); blank entries are dropped and an
                     empty list yields a headline-only message.
    :param files: working-tree paths to commit; each is read and Base64-encoded
                  into one ``FileAddition``.
    :return: a ``{"query", "variables"}`` dict ready to POST to the GraphQL API.
    """
    additions = []
    for path in files:
        with open(path, "rb") as handle:
            encoded = base64.b64encode(handle.read()).decode("ascii")
        additions.append({"path": path, "contents": encoded})

    message_obj: dict[str, str] = {"headline": message}
    body = "\n".join(trailer for trailer in trailers if trailer)
    if body:
        message_obj["body"] = body

    return {
        "query": _MUTATION,
        "variables": {
            "input": {
                "branch": {
                    "repositoryNameWithOwner": repo,
                    "branchName": branch,
                },
                "expectedHeadOid": head_oid,
                "message": message_obj,
                "fileChanges": {"additions": additions},
            }
        },
    }


def create_commit(payload: dict, token: str) -> str:
    """POST the GraphQL payload and return the created commit's OID.

    :param payload: the ``{"query", "variables"}`` body from :func:`build_payload`.
    :param token: a GitHub token with ``contents: write`` on the repository.
    :return: the OID (SHA) of the commit created by ``createCommitOnBranch``.
    :raises SystemExit: on a transport error or any GraphQL ``errors`` entry, so
                        the workflow step fails with the underlying message.
    """
    request = urllib.request.Request(
        GRAPHQL_ENDPOINT,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "User-Agent": "krt-profit-release-prepare",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise SystemExit(f"GraphQL HTTP {error.code}: {detail}")
    except urllib.error.URLError as error:
        raise SystemExit(f"GraphQL request failed: {error.reason}")

    if result.get("errors"):
        raise SystemExit("GraphQL errors: " + json.dumps(result["errors"]))
    try:
        return result["data"]["createCommitOnBranch"]["commit"]["oid"]
    except (KeyError, TypeError):
        raise SystemExit("Unexpected GraphQL response: " + json.dumps(result))


def main() -> None:
    """Parse arguments, build the payload, and create (or preview) the commit."""
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--repo", required=True, help="owner/name of the repo.")
    parser.add_argument("--branch", required=True,
                        help="Existing branch to commit onto.")
    parser.add_argument("--expected-head-oid", required=True,
                        help="Current branch tip SHA (optimistic-lock guard).")
    parser.add_argument("--message", required=True, help="Commit headline.")
    parser.add_argument("--trailer", action="append", default=[],
                        help="Commit-body line, repeatable (e.g. Signed-off-by).")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print the payload (contents elided) without POSTing.")
    parser.add_argument("files", nargs="+", help="Working-tree paths to commit.")
    args = parser.parse_args()

    payload = build_payload(
        args.repo, args.branch, args.expected_head_oid,
        args.message, args.trailer, args.files,
    )

    if args.dry_run:
        preview = json.loads(json.dumps(payload))
        for addition in preview["variables"]["input"]["fileChanges"]["additions"]:
            addition["contents"] = f"<{len(addition['contents'])} base64 chars>"
        print(json.dumps(preview, indent=2))
        return

    token = os.environ.get("GH_TOKEN")
    if not token:
        raise SystemExit("GH_TOKEN environment variable is not set.")
    print(create_commit(payload, token))


if __name__ == "__main__":
    main()
