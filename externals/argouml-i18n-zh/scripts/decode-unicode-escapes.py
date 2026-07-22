#!/usr/bin/env python3
"""decode-unicode-escapes.py

Decode Java \\uXXXX Unicode escape sequences in one or more
``.properties`` files into native UTF-8 text.

Usage:
    scripts/decode-unicode-escapes.py [--dry-run] FILE [FILE ...]
    scripts/decode-unicode-escapes.py --help

Background: ArgoUML bundles shipped pre-2025 were stored as ISO-8859-1
files with non-Latin characters encoded as Java ``\\uXXXX`` escapes (the
only legal way to carry Unicode in a Latin-1 .properties file under
``java.util.Properties.load``). After the 2025 UTF-8 migration
(``Translator.UTF8_CONTROL``) the runtime happily accepts native UTF-8
content; new files may be authored directly in UTF-8. This tool
reformats legacy bundles to native UTF-8 for readability.

Behavior:

* Each ``\\uXXXX`` is replaced by the corresponding Unicode character
  encoded in UTF-8 (1-4 bytes).
* Non-escape content is preserved byte-for-byte after re-encoding to
  UTF-8 (which is a no-op for ASCII bytes 0x00-0x7F).
* Trailing CR (carriage return) is preserved per file: if the file
  originally used CRLF, the rewritten file also uses CRLF.
* The script is idempotent: running it twice produces no change.
* If the file already has no ``\\uXXXX`` sequences, it is left
  untouched (mtime unchanged).

Exit codes:
    0  all files processed (or skipped because already converted)
    1  one or more files failed validation
    2  invalid command-line usage

Validation performed after rewrite:
    * no remaining ``\\uXXXX`` patterns
    * key count unchanged from input (count of lines starting with
      ``[a-z]``)
    * file is valid UTF-8
"""

import argparse
import io
import re
import sys

ESCAPE_RE = re.compile(r"\\u([0-9a-fA-F]{4})")


def decode_escapes(line: str) -> str:
    """Replace \\uXXXX sequences in *line* with native Unicode chars."""
    return ESCAPE_RE.sub(lambda m: chr(int(m.group(1), 16)), line)


def detect_line_ending(data: bytes) -> str:
    """Return 'crlf' if the file uses CRLF, else 'lf'."""
    return "crlf" if b"\r\n" in data else "lf"


def rewrite_file(path: str) -> None:
    """Rewrite *path* in place, decoding \\uXXXX to native UTF-8."""
    with open(path, "rb") as fh:
        raw = fh.read()

    if not ESCAPE_RE.search(raw.decode("iso-8859-1")):
        # No escapes present; nothing to do.
        return

    line_ending = detect_line_ending(raw)
    text = raw.decode("iso-8859-1")
    decoded = decode_escapes(text)
    out = decoded.encode("utf-8")
    if line_ending == "crlf":
        # Write back as CRLF. Convert every LF to CRLF but be careful
        # not to double-convert existing CR bytes (\r\n already in
        # the source becomes \r\n → we want to keep that LF intact,
        # not turn it into \r\r\n).
        #
        # Strategy: encode with LF only, then replace each stand-alone
        # LF (one not preceded by CR) with CRLF. Bytes whose preceding
        # byte is CR are left alone.
        out = out.replace(b"\r\n", b"__CRLF__")
        out = out.replace(b"\n", b"\r\n")
        out = out.replace(b"__CRLF__", b"\r\n")
        # Now strip any bare CR that was followed by something other
        # than LF (a stray CR in original text). Realistic: original
        # was ISO-8859-1 file with no CR bytes in content, only as
        # part of CRLF, so there should be none.

    with open(path, "wb") as fh:
        fh.write(out)


def validate_file(path: str, original_key_count: int) -> list:
    """Return a list of human-readable validation issues for *path*."""
    import re as _re

    issues = []
    with open(path, "rb") as fh:
        data = fh.read()

    # 1. No remaining escapes.
    if ESCAPE_RE.search(data.decode("utf-8", errors="replace")):
        issues.append("residual \\uXXXX sequences remain")

    # 2. Valid UTF-8.
    try:
        data.decode("utf-8")
    except UnicodeDecodeError as exc:
        issues.append(f"invalid UTF-8: {exc}")

    # 3. Key count unchanged. Match .properties key syntax (ASCII
    # letters, '.', '_', '-', terminated by '=' or ':') so the count
    # is robust against native UTF-8 strings appearing on continuation
    # lines.
    text = data.decode("utf-8")
    key_re = _re.compile(r"^[A-Za-z][A-Za-z0-9._-]*\s*[=:]")
    new_key_count = sum(1 for line in text.splitlines() if key_re.match(line))
    if new_key_count != original_key_count:
        issues.append(
            f"key-line count changed: {original_key_count} -> {new_key_count}"
        )

    return issues


def count_key_lines(path: str) -> int:
    """Count lines that begin with a .properties key.

    A key line begins with an ASCII alphanumeric character, may contain
    ASCII letters, digits, '.', '_', '-', followed by whitespace and
    then either ``=`` or ``:``. Continuation lines (starting with
    whitespace) and comment lines (starting with ``#``) are skipped.
    Restricting to ASCII avoids miscounting native UTF-8 characters
    that look alphabetic to ``str.isalpha()``.
    """
    import re as _re

    key_re = _re.compile(r"^[A-Za-z][A-Za-z0-9._-]*\s*[=:]")
    with open(path, "rb") as fh:
        data = fh.read()
    text = data.decode("iso-8859-1", errors="replace")
    return sum(1 for line in text.splitlines() if key_re.match(line))


def main(argv: list) -> int:
    parser = argparse.ArgumentParser(
        prog="decode-unicode-escapes.py",
        description=(
            "Decode Java \\uXXXX Unicode escape sequences in "
            ".properties files into native UTF-8."
        ),
    )
    parser.add_argument(
        "files",
        nargs="+",
        metavar="FILE",
        help="Path to a .properties file (one or more).",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List files that contain \\uXXXX escapes without rewriting.",
    )
    args = parser.parse_args(argv)

    overall_ok = True
    files_with_escapes = 0
    for path in args.files:
        try:
            with open(path, "rb") as fh:
                data = fh.read()
            has_escapes = bool(
                ESCAPE_RE.search(data.decode("iso-8859-1", errors="replace"))
            )
        except OSError as exc:
            print(f"{path}: cannot read: {exc}", file=sys.stderr)
            overall_ok = False
            continue

        if not has_escapes:
            print(f"{path}: no escapes, skipping")
            continue

        files_with_escapes += 1
        if args.dry_run:
            print(f"{path}: has escapes (dry run)")
            continue

        original_key_count = count_key_lines(path)
        try:
            rewrite_file(path)
        except OSError as exc:
            print(f"{path}: cannot write: {exc}", file=sys.stderr)
            overall_ok = False
            continue

        issues = validate_file(path, original_key_count)
        if issues:
            print(f"{path}: REWRITTEN but validation failed:")
            for msg in issues:
                print(f"  - {msg}", file=sys.stderr)
            overall_ok = False
        else:
            print(f"{path}: rewritten (UTF-8, {original_key_count} keys)")

    if args.dry_run:
        print(
            f"--dry-run: {files_with_escapes} of {len(args.files)} "
            f"files contain \\uXXXX sequences",
            file=sys.stderr,
        )

    return 0 if overall_ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
