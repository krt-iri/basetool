"""Migrate test assertions from ResponseStatusException to the new domain exceptions.

For each test file:
1. Find every `assertThrows(ResponseStatusException.class, ...)` call.
2. Within ~10 lines after the call, look for any status-code assertion that
   reveals which HTTP status the production code is actually thrown with:
   - `assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode())`
   - `assertEquals(404, ex.getStatusCode().value())`
   - `assertThat(ex.getStatusCode().value()).isEqualTo(400)`
   - `ex.getStatusCode().value()` standalone
3. Map the status to the matching domain exception type:
     404 -> NotFoundException
     400 -> BadRequestException
     409 -> BusinessConflictException
     500 -> ResponseStatusException (keep)
4. Replace the symbol on the assertThrows line and drop the now-redundant
   status-code assertion line. If we cannot infer the status, leave the call
   untouched and log a warning so a human can handle it.
"""
from pathlib import Path
import re

ROOT = Path('backend/src/test/java')

NUM_TO_EXC = {
    '404': 'NotFoundException',
    '400': 'BadRequestException',
    '409': 'BusinessConflictException',
    '500': None,  # keep as ResponseStatusException
}
NAME_TO_EXC = {
    'NOT_FOUND': 'NotFoundException',
    'BAD_REQUEST': 'BadRequestException',
    'CONFLICT': 'BusinessConflictException',
    'INTERNAL_SERVER_ERROR': None,
}
EXC_FQN = {
    'NotFoundException': 'de.greluc.krt.iri.basetool.backend.exception.NotFoundException',
    'BadRequestException': 'de.greluc.krt.iri.basetool.backend.exception.BadRequestException',
    'BusinessConflictException': 'de.greluc.krt.iri.basetool.backend.exception.BusinessConflictException',
}

# Status assertions in three common shapes plus a generic getStatusCode reference.
STATUS_HINT = re.compile(
    r'(?:'
    r'HttpStatus\.(?P<name>NOT_FOUND|BAD_REQUEST|CONFLICT|INTERNAL_SERVER_ERROR)'
    r'|getStatusCode\(\)\.value\(\)\)?\.isEqualTo\(\s*(?P<num1>\d{3})\s*\)'
    r'|assertEquals\s*\(\s*(?P<num2>\d{3})\s*,\s*\w+\.getStatusCode\(\)\.value\(\)\s*\)'
    r')'
)
STATUS_LINE = re.compile(r'^.*\b(?:getStatusCode|getReason)\s*\(\s*\).*$')
ASSERT_THROWS = re.compile(
    r'\b(?:ResponseStatusException\s+\w+\s*=\s*)?assertThrows\s*\(\s*ResponseStatusException\.class'
)


def classify(lines, idx):
    for j in range(idx, min(idx + 10, len(lines))):
        for m in STATUS_HINT.finditer(lines[j]):
            name = m.group('name')
            if name is not None:
                return NAME_TO_EXC.get(name), j
            num = m.group('num1') or m.group('num2')
            if num is not None:
                return NUM_TO_EXC.get(num), j
    return None, None


def fix(path: Path):
    text = path.read_text(encoding='utf-8')
    if 'ResponseStatusException' not in text:
        return False
    lines = text.split('\n')
    used = set()
    drop = set()
    decisions = []
    skipped = []

    for i, line in enumerate(lines):
        if ASSERT_THROWS.search(line):
            exc, status_idx = classify(lines, i)
            if exc is None:
                # Could not determine - either no hint or 500 (kept as RSE).
                if status_idx is not None:
                    # Found a 500 hint: leave as-is.
                    skipped.append((path.name, i + 1, '500 (kept as ResponseStatusException)'))
                else:
                    skipped.append((path.name, i + 1, 'no status hint - left untouched'))
                continue
            lines[i] = re.sub(r'ResponseStatusException', exc, lines[i])
            used.add(exc)
            decisions.append((path.name, i + 1, exc))
            # Drop the status-code assertion line (if any) plus any standalone getStatusCode line.
            if status_idx is not None:
                drop.add(status_idx)

    if not used and not skipped:
        return False

    filtered = [ln for k, ln in enumerate(lines) if k not in drop]
    out = '\n'.join(filtered)

    # Drop the RSE import iff symbol no longer appears outside imports.
    body = re.sub(
        r'^\s*import\s+org\.springframework\.web\.server\.ResponseStatusException;\s*$',
        '',
        out,
        count=1,
        flags=re.M,
    )
    if 'ResponseStatusException' not in body:
        out = re.sub(
            r'^\s*import\s+org\.springframework\.web\.server\.ResponseStatusException;\s*\n',
            '',
            out,
            count=1,
            flags=re.M,
        )

    # Drop HttpStatus import if it has no remaining references in the body (outside of the import line itself).
    body2 = re.sub(
        r'^\s*import\s+org\.springframework\.http\.HttpStatus;\s*$',
        '',
        out,
        count=1,
        flags=re.M,
    )
    if 'HttpStatus' not in body2:
        out = re.sub(
            r'^\s*import\s+org\.springframework\.http\.HttpStatus;\s*\n',
            '',
            out,
            count=1,
            flags=re.M,
        )

    # Add domain-exception imports for any new symbols referenced.
    needed = [EXC_FQN[e] for e in sorted(used) if EXC_FQN[e] not in out]
    if needed:
        imports = list(re.finditer(r'^\s*import\s+[^;]+;\s*$', out, re.M))
        if imports:
            last = imports[-1]
            insertion = '\n' + '\n'.join(f'import {fqn};' for fqn in needed)
            out = out[:last.end()] + insertion + out[last.end():]

    if out != text:
        path.write_text(out, encoding='utf-8', newline='')

    for d in decisions:
        print(f"  REPLACED {d[0]}:{d[1]} -> {d[2]}")
    for s in skipped:
        print(f"  KEPT     {s[0]}:{s[1]} -- {s[2]}")
    return True


def main():
    print("Test-assertion migration log:")
    changed = []
    for f in ROOT.rglob('*.java'):
        if fix(f):
            changed.append(f)
    print(f"\nUpdated {len(changed)} test file(s).")


if __name__ == '__main__':
    main()
