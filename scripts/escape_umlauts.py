"""Replace literal German umlauts with \uXXXX escapes in the given files.

Required by the project convention (CLAUDE.md): umlauts in .properties files
MUST be encoded as \uXXXX so the file is safe under any encoding assumption.
"""
import re
import sys

MAPPINGS = {
    'ä': r'ä', 'ö': r'ö', 'ü': r'ü',
    'Ä': r'Ä', 'Ö': r'Ö', 'Ü': r'Ü',
    'ß': r'ß',
}


def fix(path):
    with open(path, 'rb') as fh:
        text = fh.read().decode('utf-8')
    total = 0
    for k, v in MAPPINGS.items():
        n = text.count(k)
        total += n
        text = text.replace(k, v)
    with open(path, 'w', encoding='utf-8', newline='') as fh:
        fh.write(text)
    remaining = re.search(r'[ÄÖÜßäöü]', text)
    print(f"{path}: replaced {total} chars, remaining literal: {bool(remaining)}")


if __name__ == '__main__':
    for f in sys.argv[1:]:
        fix(f)
