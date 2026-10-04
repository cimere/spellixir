#!/usr/bin/env python3
"""Prove Grammar-Kit emits the same checked-in build output on consecutive runs."""
import hashlib
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / 'build/generated-src/grammar'
GRADLE = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')


def generated_digest():
    files = sorted(path for path in GENERATED.rglob('*') if path.is_file())
    if not files:
        raise RuntimeError('Grammar-Kit generated no parser or PSI sources')
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(GENERATED).as_posix().encode())
        digest.update(b'\0')
        digest.update(path.read_bytes())
    return digest.hexdigest(), len(files)


def generate():
    subprocess.run([str(GRADLE), 'generateParser', '--rerun-tasks', '--console=plain'], cwd=ROOT, check=True)
    return generated_digest()


def main():
    first = generate()
    second = generate()
    if first != second:
        raise RuntimeError(f'Grammar-Kit output changed between identical runs: {first} != {second}')
    print(f'Grammar-Kit output is deterministic: {first[1]} files, sha256 {first[0]}')


if __name__ == '__main__':
    main()
