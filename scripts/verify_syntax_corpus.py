#!/usr/bin/env python3
"""Run the pinned, test-only Tree-sitter oracle against Native Core corpus reports."""
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tarfile
import urllib.request

PROJECT = Path(__file__).resolve().parents[1]
CORPUS = PROJECT / 'src/test/resources/corpus/phase1'
BUILD = PROJECT / 'build/syntax-corpus-oracle'
REPORT = PROJECT / 'build/reports/syntax-corpus'


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def source_archive(pin):
    archive = BUILD / (pin['revision'] + '.tar.gz')
    if not archive.exists():
        url = f"https://codeload.github.com/{pin['repository']}/tar.gz/{pin['revision']}"
        print(f"Downloading pinned source: {url}", flush=True)
        with urllib.request.urlopen(url, timeout=60) as response:
            contents = response.read()
        if sha256(contents) != pin['archiveSha256']:
            raise ValueError(f"Archive checksum mismatch: {url}")
        archive.write_bytes(contents)
    if sha256(archive.read_bytes()) != pin['archiveSha256']:
        raise ValueError(f"Cached archive checksum mismatch: {archive}; remove it and retry")
    destination = BUILD / pin['revision']
    # Extract regular files only, under a validated destination. Never follow archive links.
    with tarfile.open(archive) as tar:
        for member in tar.getmembers():
            if not member.isfile():
                continue
            relative = Path(*Path(member.name).parts[1:])
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError(f"Unsafe archive path: {member.name}")
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(tar.extractfile(member).read())
    for key, upstream_name in [('licenseFile', 'LICENSE'), ('noticeFile', 'NOTICE')]:
        if key in pin and (CORPUS / pin[key]).read_bytes() != (destination / upstream_name).read_bytes():
            raise ValueError(f"Pinned {upstream_name} differs from checked-in attribution")
    return destination


def byte_offset(source, utf16_offset):
    # IntelliJ offsets count UTF-16 code units; Tree-sitter offsets count UTF-8 bytes.
    return len(source.encode('utf-16-le')[:utf16_offset * 2].decode('utf-16-le').encode('utf-8'))


def run():
    BUILD.mkdir(parents=True, exist_ok=True)
    REPORT.mkdir(parents=True, exist_ok=True)
    lock = json.loads((CORPUS / 'oracle-lock.json').read_text())
    if lock['schemaVersion'] != 1:
        raise ValueError('Unsupported oracle-lock schema')
    grammar = source_archive(lock['grammar'])
    runtime = source_archive(lock['runtime'])
    executable = BUILD / ('parse-corpus.exe' if os.name == 'nt' else 'parse-corpus')
    command = shlex.split(os.environ.get('CC', 'cc')) + [
        '-std=c11', '-D_DEFAULT_SOURCE', '-O2', '-I' + str(runtime / 'lib/include'),
        '-I' + str(runtime / 'lib/src'), '-I' + str(grammar / 'src'),
        str(PROJECT / 'scripts/tree_sitter_corpus.c'), str(runtime / 'lib/src/lib.c'),
        str(grammar / 'src/parser.c'), str(grammar / 'src/scanner.c'), '-o', str(executable),
    ]
    subprocess.run(command, check=True, timeout=180)
    manifest = json.loads((CORPUS / 'manifest.json').read_text())
    failures = []
    comparisons = 0
    for fixture in manifest['fixtures']:
        name = fixture['id']
        path = CORPUS / fixture['path']
        contents = path.read_bytes()
        if sha256(contents) != fixture['sha256']:
            raise ValueError(f"{name}: source checksum mismatch")
        native = json.loads((REPORT / (name + '.json')).read_text())
        if native['sourceSha256'] != fixture['sha256']:
            raise ValueError(f"{name}: stale native report; run ./gradlew test first")
        # Never compare the oracle to an unreviewed native candidate.
        reviewed_native = json.loads((CORPUS / 'expected' / (name + '.json')).read_text())
        if native != reviewed_native:
            failures.append(f'{name}: native behavior differs from reviewed expectations')
        result = subprocess.run([str(executable), str(path)], check=True, capture_output=True, text=True, timeout=30)
        oracle = json.loads(result.stdout)
        (REPORT / (name + '.oracle.json')).write_text(result.stdout)
        if oracle['hasError'] != (fixture['status'] != 'valid'):
            failures.append(f"{name}: oracle validity disagrees with declared {fixture['status']} status")
        expected = CORPUS / 'oracle-expected' / (name + '.json')
        if not expected.exists() or json.loads(expected.read_text()) != oracle:
            failures.append(f'{name}: missing or changed reviewed oracle snapshot')
        source = contents.decode('utf-8')
        for anchor in fixture['differential']:
            rows = native['psi' if anchor['kind'] == 'psi' else 'tokens']
            candidates = [row for row in rows if row[2] == anchor['type'] and
                          contents[byte_offset(source, row[0]):byte_offset(source, row[1])].decode('utf-8') == anchor['text']]
            if not candidates:
                failures.append(f'{name}: native anchor absent: {anchor}')
            for row in candidates:
                span = [byte_offset(source, row[0]), byte_offset(source, row[1]), anchor['oracleType'], False]
                if span not in oracle['nodes']:
                    failures.append(f'{name}: oracle has no matching span for {anchor}')
                comparisons += 1
        # Broad differential coverage of literal numbers, characters and comments on valid files.
        if fixture['status'] == 'valid':
            kinds = {'NUMBER': {'integer', 'float'}, 'CHARACTER': {'char'}, 'COMMENT': {'comment'}}
            spans = {(node[0], node[1], node[2]) for node in oracle['nodes'] if not node[3]}
            for row in native['tokens']:
                if row[2] in kinds:
                    start, end = byte_offset(source, row[0]), byte_offset(source, row[1])
                    if not any((start, end, kind) in spans for kind in kinds[row[2]]):
                        failures.append(f'{name}: literal/comment boundary disagreement: {row}')
                    comparisons += 1
    if failures:
        raise ValueError('\n'.join(failures))
    print(f"Verified {len(manifest['fixtures'])} fixtures and {comparisons} shared Native Core/Tree-sitter spans.")


if __name__ == '__main__':
    try:
        run()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
