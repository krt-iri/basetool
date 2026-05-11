"""Fix imports in Java files after the ResponseStatusException replacement.

For each Java file we:
- Detect which of the new exception types are referenced in the body.
- Add the corresponding imports if missing.
- Drop the `import org.springframework.web.server.ResponseStatusException;` line
  iff `ResponseStatusException` no longer appears outside the import itself.
- Drop the `import org.springframework.http.HttpStatus;` line iff `HttpStatus`
  no longer appears outside the import line.

Idempotent.
"""
from pathlib import Path
import re

ROOT = Path('backend/src/main/java')
EXCEPTION_PKG = 'de.greluc.krt.iri.basetool.backend.exception'

NEW_EXCS = {
    'NotFoundException': f'{EXCEPTION_PKG}.NotFoundException',
    'BadRequestException': f'{EXCEPTION_PKG}.BadRequestException',
    'BusinessConflictException': f'{EXCEPTION_PKG}.BusinessConflictException',
    'DuplicateEntityException': f'{EXCEPTION_PKG}.DuplicateEntityException',
    'EntityInUseException': f'{EXCEPTION_PKG}.EntityInUseException',
}

SPRING_ACCESS_DENIED = 'org.springframework.security.access.AccessDeniedException'


def has_word(text: str, word: str) -> bool:
    return re.search(rf'\b{re.escape(word)}\b', text) is not None


def fix(path: Path) -> bool:
    text = path.read_text(encoding='utf-8')
    original = text

    # Drop the unused org.springframework.web.server.ResponseStatusException import
    # iff no remaining usage of the symbol exists outside the import.
    has_rse_import = bool(
        re.search(r'^\s*import\s+org\.springframework\.web\.server\.ResponseStatusException;\s*$', text, re.M)
    )
    if has_rse_import:
        # Count occurrences of "ResponseStatusException" in body (excluding the import line).
        non_import_body = re.sub(
            r'^\s*import\s+org\.springframework\.web\.server\.ResponseStatusException;\s*$',
            '',
            text,
            count=1,
            flags=re.M,
        )
        if not has_word(non_import_body, 'ResponseStatusException'):
            text = re.sub(
                r'^\s*import\s+org\.springframework\.web\.server\.ResponseStatusException;\s*\n',
                '',
                text,
                count=1,
                flags=re.M,
            )

    # Drop unused HttpStatus import too if no more references in body.
    has_http_status_import = bool(
        re.search(r'^\s*import\s+org\.springframework\.http\.HttpStatus;\s*$', text, re.M)
    )
    if has_http_status_import:
        non_import_body = re.sub(
            r'^\s*import\s+org\.springframework\.http\.HttpStatus;\s*$',
            '',
            text,
            count=1,
            flags=re.M,
        )
        if not has_word(non_import_body, 'HttpStatus'):
            text = re.sub(
                r'^\s*import\s+org\.springframework\.http\.HttpStatus;\s*\n',
                '',
                text,
                count=1,
                flags=re.M,
            )

    # Add new imports for symbols that are referenced but not imported.
    needed = []
    for sym, fqn in NEW_EXCS.items():
        # Has the symbol used somewhere in this file (e.g. `new NotFoundException(`)?
        if re.search(rf'\bnew\s+{re.escape(sym)}\s*\(', text):
            if not re.search(rf'^\s*import\s+{re.escape(fqn)};\s*$', text, re.M):
                needed.append(fqn)

    # AccessDeniedException is special: my script used the fully qualified name;
    # try to convert that into a clean import + bare name.
    if has_word(text, 'org.springframework.security.access.AccessDeniedException'):
        text = re.sub(
            r'\borg\.springframework\.security\.access\.AccessDeniedException\b',
            'AccessDeniedException',
            text,
        )
        if not re.search(rf'^\s*import\s+{re.escape(SPRING_ACCESS_DENIED)};\s*$', text, re.M):
            needed.append(SPRING_ACCESS_DENIED)

    if needed:
        # Find the last existing import line and append new imports after it.
        imports = list(re.finditer(r'^\s*import\s+[^;]+;\s*$', text, re.M))
        if imports:
            last = imports[-1]
            insertion = '\n' + '\n'.join(f'import {fqn};' for fqn in sorted(set(needed)))
            text = text[:last.end()] + insertion + text[last.end():]
        else:
            # No imports yet (unlikely). Insert after the package statement.
            pkg = re.search(r'^\s*package\s+[^;]+;\s*$', text, re.M)
            if pkg:
                insertion = '\n\n' + '\n'.join(f'import {fqn};' for fqn in sorted(set(needed)))
                text = text[:pkg.end()] + insertion + text[pkg.end():]

    if text != original:
        path.write_text(text, encoding='utf-8', newline='')
        return True
    return False


def main():
    changed = []
    for java in ROOT.rglob('*.java'):
        if 'exception/' in java.as_posix():
            continue
        if fix(java):
            changed.append(java)
    print(f"Updated imports in {len(changed)} files:")
    for p in changed:
        print(f"  {p}")


if __name__ == '__main__':
    main()
