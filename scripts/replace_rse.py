"""Replace ResponseStatusException usages with domain-specific exceptions.

Operates on Java source files under backend/src/main/java. The replacements
are syntactic so the script must be re-run with the workshop semantics
documented in 2.5 of the analysis:

- HttpStatus.NOT_FOUND               -> NotFoundException
- HttpStatus.BAD_REQUEST             -> BadRequestException
- HttpStatus.CONFLICT                -> BusinessConflictException (manual
                                       review for cases that should be
                                       DuplicateEntityException)
- HttpStatus.FORBIDDEN               -> Spring's AccessDeniedException
- HttpStatus.INTERNAL_SERVER_ERROR   -> left untouched (ResponseStatusException
                                       is acceptable for genuine 5xx fallbacks)

Import management is intentionally out of scope; we patch imports in a
separate pass via gradle compile feedback.
"""
import re
import sys
from pathlib import Path

ROOT = Path('backend/src/main/java')

# Two flavours: simple `throw new X(...)` and lambda form `() -> new X(...)`.
# The "new ResponseStatusException(" prefix may be qualified or unqualified.
PATTERN = re.compile(
    r'new\s+(?:org\.springframework\.web\.server\.)?ResponseStatusException\s*\(\s*'
    r'HttpStatus\.(NOT_FOUND|BAD_REQUEST|CONFLICT|FORBIDDEN|INTERNAL_SERVER_ERROR)\s*,'
    r'(?P<rest>[^)]*\))',
    re.DOTALL,
)

# Multi-line message support: arguments may span multiple lines and may contain
# concatenated string literals.  We need a more permissive matcher.
PATTERN_ML = re.compile(
    r'new\s+(?:org\.springframework\.web\.server\.)?ResponseStatusException\s*\(\s*'
    r'HttpStatus\.(?P<status>NOT_FOUND|BAD_REQUEST|CONFLICT|FORBIDDEN|INTERNAL_SERVER_ERROR)\s*,\s*'
    r'(?P<msg>(?:"(?:[^"\\]|\\.)*"|\s|\+|[A-Za-z_][\w.]*|\(|\))+)\s*\)',
    re.DOTALL,
)

STATUS_TO_EXCEPTION = {
    'NOT_FOUND': 'NotFoundException',
    'BAD_REQUEST': 'BadRequestException',
    'CONFLICT': 'BusinessConflictException',
    'FORBIDDEN': None,  # special: use Spring's AccessDeniedException
    'INTERNAL_SERVER_ERROR': None,  # leave untouched
}


def replace(match):
    status = match.group('status')
    msg = match.group('msg').rstrip()
    exc = STATUS_TO_EXCEPTION.get(status)
    if exc is None:
        if status == 'FORBIDDEN':
            return f'new org.springframework.security.access.AccessDeniedException({msg})'
        return match.group(0)  # leave 5xx and unknown alone
    return f'new {exc}({msg})'


def process(path: Path) -> tuple[int, str]:
    text = path.read_text(encoding='utf-8')
    if 'ResponseStatusException' not in text:
        return 0, text
    new_text, n = PATTERN_ML.subn(replace, text)
    return n, new_text


def main():
    total = 0
    touched = []
    for java in ROOT.rglob('*.java'):
        if 'exception/' in java.as_posix():
            continue  # don't touch the handler itself
        n, new_text = process(java)
        if n > 0:
            java.write_text(new_text, encoding='utf-8', newline='')
            touched.append((java, n))
            total += n
    print(f"Replaced {total} ResponseStatusException calls in {len(touched)} files:")
    for p, n in touched:
        print(f"  {p}: {n}")


if __name__ == '__main__':
    main()
