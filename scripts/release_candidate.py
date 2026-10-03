#!/usr/bin/env python3
"""Inspect, freeze, and smoke-test the exact release ZIP. Python standard library only."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    checksum = hashlib.sha256()
    with open(path, 'rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(chunk)
    return checksum.hexdigest()


def safe_names(archive):
    names = archive.namelist()
    require(len(names) == len(set(names)), 'Duplicate ZIP entries')
    for name in names:
        path = PurePosixPath(name)
        require(not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name,
                f'Unsafe ZIP entry: {name}')
        mode = (archive.getinfo(name).external_attr >> 16) & 0o170000
        require(mode != 0o120000, f'Symlink ZIP entry: {name}')
    return names


def inspect_archive(path):
    descriptors = []
    classes = set()
    with zipfile.ZipFile(path) as archive:
        names = safe_names(archive)
        roots = {PurePosixPath(n).parts[0] for n in names}
        require(len(roots) == 1, 'Plugin ZIP must contain one plugin directory')
        for name in names:
            require(not any(x in name.lower() for x in ('corpus', 'smoke', 'tree_sitter', 'test-results')), f'Test asset in ZIP: {name}')
            if name.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(archive.read(name))) as jar:
                    for entry in safe_names(jar):
                        require(not any(x in entry.lower() for x in ('/smoke/', '/corpus/', 'tree_sitter', 'org/junit/', 'elixirresponsivenesstest')), f'Test asset in JAR: {entry}')
                        if entry == 'META-INF/plugin.xml':
                            descriptors.append(ET.fromstring(jar.read(entry)))
                        if entry.endswith('.class'):
                            classes.add(entry)
                            class_bytes = jar.read(entry)
                            require(len(class_bytes) >= 8 and class_bytes[:4] == b'\xca\xfe\xba\xbe', f'Invalid class file: {entry}')
                            require(int.from_bytes(class_bytes[6:8], 'big') <= 65, f'Class requires newer than Java 21: {entry}')
    require(len(descriptors) == 1, 'Expected exactly one plugin descriptor')
    descriptor = descriptors[0]
    require(descriptor.findtext('id') == 'com.cimere.spellixir', 'Wrong plugin id')
    require(descriptor.findtext('version'), 'Missing plugin version')
    bounds = descriptor.find('idea-version')
    require(bounds is not None and bounds.get('since-build') == '261.26222.65' and bounds.get('until-build') == '262.*', 'Unexpected compatibility bounds')
    require([d.text for d in descriptor.findall('depends')] == ['com.intellij.modules.platform'], 'Unexpected runtime dependencies')
    for suffix in ('lang/ElixirFileType.class', 'lang/ElixirLexer.class', 'lang/parser/ElixirParser.class', 'mix/MixProjectService.class'):
        require('com/cimere/spellixir/' + suffix in classes, f'Missing production class: {suffix}')
    return {'pluginId': descriptor.findtext('id'), 'version': descriptor.findtext('version'), 'sinceBuild': bounds.get('since-build'), 'untilBuild': bounds.get('until-build')}


def inspect_probe(path):
    with zipfile.ZipFile(path) as archive:
        names = safe_names(archive)
        require({PurePosixPath(name).parts[0] for name in names} == {'spellixir-smoke-probe'}, 'Unexpected smoke probe root')
        jars = [name for name in names if name.endswith('.jar')]
        require(len(jars) == 1 and PurePosixPath(jars[0]).parent.as_posix() == 'spellixir-smoke-probe/lib',
                'Smoke probe must have one plugin JAR in lib')
        with zipfile.ZipFile(io.BytesIO(archive.read(jars[0]))) as jar:
            jar_names = safe_names(jar)
            descriptor_name = 'META-INF/plugin.xml'
            require(descriptor_name in jar_names, 'Missing smoke probe plugin descriptor')
            descriptor = ET.fromstring(jar.read(descriptor_name))
            require('com/cimere/spellixir/smoke/PackagedSmokeStarter.class' in jar_names,
                    'Missing packaged smoke starter')
        require(descriptor.findtext('id') == 'com.cimere.spellixir.smoke', 'Wrong smoke probe plugin id')
        require([d.text for d in descriptor.findall('depends')] == ['com.intellij.modules.platform', 'com.cimere.spellixir'],
                'Unexpected smoke probe dependencies')


def extract_plugin_jar(archive_path, output):
    inspect_archive(archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        jars = [name for name in safe_names(archive) if name.endswith('.jar')]
        require(len(jars) == 1, 'Candidate must contain exactly one plugin JAR')
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(archive.read(jars[0]))
    print(json.dumps({'archive': str(archive_path), 'pluginJar': str(output), 'sha256': digest(output)}, indent=2))


def prepare_smoke(work):
    trap = work / 'runtime-traps'
    trap.mkdir(parents=True, exist_ok=True)
    for marker in ('runtime-attempted', 'smoke.json'):
        (work / marker).unlink(missing_ok=True)
    for tool in ('elixir', 'elixirc', 'mix', 'erl', 'escript'):
        script = trap / tool
        script.write_text('#!/bin/sh\nprintf attempted > "$SPELLIXIR_RUNTIME_ATTEMPT"\nexit 93\n')
        script.chmod(0o755)


def freeze(archive, probe, target):
    require(not target.exists(), 'Candidate directory already exists; use a new location')
    require(subprocess.run(['git', 'diff-index', '--quiet', 'HEAD', '--'], cwd=ROOT).returncode == 0,
            'Refusing to freeze a candidate while tracked source files are modified')
    metadata = inspect_archive(archive)
    inspect_probe(probe)
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='release-candidate-', dir=target.parent) as temporary:
        frozen = Path(temporary)
        shutil.copyfile(archive, frozen / 'candidate.zip')
        shutil.copyfile(probe, frozen / 'smoke-probe.zip')
        metadata.update({'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                         'files': {name: digest(frozen / name) for name in ('candidate.zip', 'smoke-probe.zip')}})
        (frozen / 'manifest.json').write_text(json.dumps(metadata, indent=2) + '\n')
        frozen.rename(target)
    print(json.dumps(metadata, indent=2))


def verify(target):
    metadata = json.loads((target / 'manifest.json').read_text())
    require(set(metadata['files']) == {'candidate.zip', 'smoke-probe.zip'}, 'Unexpected candidate files')
    require(metadata['commit'] == subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'Candidate was built from a different source commit')
    for name, expected in metadata['files'].items():
        require(digest(target / name) == expected, f'Candidate hash mismatch: {name}')
    require(all(metadata[k] == v for k, v in inspect_archive(target / 'candidate.zip').items()), 'Candidate descriptor mismatch')
    inspect_probe(target / 'smoke-probe.zip')
    return metadata


def verify_signed(candidate, signed):
    metadata = verify(candidate)
    source = zipfile.ZipFile(candidate / 'candidate.zip')
    published = zipfile.ZipFile(signed)
    try:
        source_names = set(safe_names(source))
        signed_names = set(safe_names(published))
        # Marketplace ZIP Signer uses an APK-style signing block, not JAR signature
        # entries. verifyPluginSignature performs cryptographic verification;
        # this check proves that signing preserved the candidate payload.
        require(digest(signed) != metadata['files']['candidate.zip'], 'Signed archive is identical to unsigned candidate')
        require(signed_names == source_names, 'Signing changed the release candidate file set')
        require(all(source.read(name) == published.read(name) for name in source_names), 'Signing changed release candidate contents')
    finally:
        source.close()
        published.close()
    result = {'candidateSha256': metadata['files']['candidate.zip'], 'signedSha256': digest(signed), 'payloadUnchanged': True}
    print(json.dumps(result, indent=2))
    return result


def verify_smoke(candidate, archive, report, runtime_attempted, host, version):
    metadata = verify(candidate)
    archive_hash = digest(archive)
    if archive_hash != metadata['files']['candidate.zip']:
        verify_signed(candidate, archive)
    result = json.loads(report.read_text())
    require(result.get('passed') is True, 'Packaged smoke probe did not pass')
    require(result.get('host') == host, f"Smoke ran on {result.get('host')}, expected {host}")
    require(result.get('build'), 'Smoke report has no IDE build')
    product_code, build_number = result['build'].split('-', 1)
    expected_codes = {'idea': {'IU', 'IC'}, 'goland': {'GO'}, 'pycharm': {'PY'}}
    require(product_code in expected_codes[host], f'Unexpected product code for {host}: {product_code}')
    if version == '2026.1.4':
        require(build_number == '261.26222.65', f'Expected minimum IDEA baseline, got {build_number}')
    else:
        require(build_number.startswith('262.'), f'Expected latest supported 2026.2 line, got {build_number}')
        require(result.get('version', '').startswith('2026.2'), f'Expected stable 2026.2, got {result.get("version")}')
    required_checks = {
        'installed-plugin-classloader', 'ex-recognition-highlighting-recovery',
        'exs-recognition-highlighting-recovery', 'ordinary-mix', 'umbrella-root',
        'umbrella-child', 'standalone', 'malformed-metadata',
    }
    require(required_checks.issubset(set(result.get('checks', []))), 'Smoke report is missing required behavior checks')
    require(not runtime_attempted.exists(), f'Native Core attempted to launch Elixir/OTP: {runtime_attempted}')
    result.update({'candidateSha256': metadata['files']['candidate.zip'], 'smokeArchiveSha256': archive_hash,
                   'runtimeIndependent': True})
    report.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


def product_info(ide):
    for path in (ide / 'product-info.json', ide / 'Resources/product-info.json', ide / 'Contents/Resources/product-info.json'):
        if path.is_file():
            return path, json.loads(path.read_text())
    raise ValueError(f'No product-info.json in {ide}')


def smoke(candidate, ide, work):
    metadata = verify(candidate)
    info_path, info = product_info(ide)
    require(not work.exists(), 'Smoke workspace already exists; use a fresh path')
    work.mkdir(parents=True)
    plugins = work / 'plugins'
    plugins.mkdir()
    with zipfile.ZipFile(candidate / 'candidate.zip') as archive:
        safe_names(archive)
        archive.extractall(plugins)
    with zipfile.ZipFile(candidate / 'smoke-probe.zip') as archive:
        safe_names(archive)
        archive.extractall(plugins)
    os_name = {'Darwin': 'macOS', 'Linux': 'Linux', 'Windows': 'Windows'}[platform.system()]
    arch = 'aarch64' if platform.machine().lower() in ('arm64', 'aarch64') else 'amd64'
    launch = next(x for x in info['launch'] if x['os'] == os_name and x['arch'] in (arch, 'x86_64' if arch == 'amd64' else arch))
    launcher = (info_path.parent / launch['launcherPath']).resolve()
    base_vm = (info_path.parent / launch['vmOptionsFilePath']).resolve().read_text()
    vm_options = work / 'smoke.vmoptions'
    vm_options.write_text(base_vm + '\n' + '\n'.join([
        '-Djava.awt.headless=true', '-Didea.trust.all.projects=true',
        '-Didea.initially.ask.config=false', '-Dide.show.tips.on.startup.default.value=false',
        '-Didea.load.plugins.id=com.cimere.spellixir,com.cimere.spellixir.smoke',
        *[f'-Didea.{key}.path={work / key}' for key in ('config', 'system', 'plugins', 'log')],
    ]) + '\n')
    env = os.environ.copy()
    env[info['envVarBaseName'] + '_VM_OPTIONS'] = str(vm_options)
    trap = work / 'runtime-traps'
    trap.mkdir()
    for tool in ('elixir', 'elixirc', 'mix', 'erl', 'escript'):
        if os.name == 'nt':
            (trap / (tool + '.cmd')).write_text('@echo off\necho attempted > "%SPELLIXIR_RUNTIME_ATTEMPT%"\nexit /b 93\n')
        else:
            script = trap / tool
            script.write_text('#!/bin/sh\necho attempted > "$SPELLIXIR_RUNTIME_ATTEMPT"\nexit 93\n')
            script.chmod(0o755)
    env['SPELLIXIR_RUNTIME_ATTEMPT'] = str(work / 'runtime-attempted')
    env['PATH'] = str(trap) + os.pathsep + (os.environ['SystemRoot'] + '\\System32' if os.name == 'nt' else '/usr/bin:/bin')
    for name in ('JAVA_HOME', 'JDK_HOME', 'IDEA_JDK', 'GOLAND_JDK', 'PYCHARM_JDK', 'ERL_LIBS', 'ERL_FLAGS', 'ELIXIR_ERL_OPTIONS'):
        env.pop(name, None)
    report = work / 'smoke.json'
    with (work / 'launcher.log').open('w') as log:
        result = subprocess.run([str(launcher), 'spellixirSmoke', str(work / 'project'), str(report)],
                                env=env, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    require(result.returncode == 0, f'IDE failed ({result.returncode}); inspect {work / "launcher.log"}')
    require(report.is_file(), 'IDE exited without running the smoke probe')
    result = json.loads(report.read_text())
    require(result['passed'], f'Smoke checks failed: {result}')
    require(result['build'] == info['productCode'] + '-' + info['buildNumber'], 'Unexpected running IDE build')
    require(not (work / 'runtime-attempted').exists(), 'Native Core attempted to launch an Elixir/OTP tool')
    verify(candidate)
    result.update({'candidateSha256': metadata['files']['candidate.zip'], 'runtimeIndependent': True,
                   'host': info['name'], 'version': info['version'], 'os': os_name, 'arch': arch})
    report.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    package = sub.add_parser('freeze')
    package.add_argument('--archive', type=Path, required=True)
    package.add_argument('--probe', type=Path, required=True)
    package.add_argument('--out', type=Path, required=True)
    check = sub.add_parser('verify')
    check.add_argument('--candidate', type=Path, required=True)
    run = sub.add_parser('smoke')
    run.add_argument('--candidate', type=Path, required=True)
    run.add_argument('--ide', type=Path, required=True)
    run.add_argument('--work', type=Path, required=True)
    signed = sub.add_parser('verify-signed')
    signed.add_argument('--candidate', type=Path, required=True)
    signed.add_argument('--signed', type=Path, required=True)
    smoke_report = sub.add_parser('verify-smoke')
    smoke_report.add_argument('--candidate', type=Path, required=True)
    smoke_report.add_argument('--archive', type=Path, required=True)
    smoke_report.add_argument('--report', type=Path, required=True)
    smoke_report.add_argument('--runtime-attempted', type=Path, required=True)
    smoke_report.add_argument('--host', choices=('idea', 'goland', 'pycharm'), required=True)
    smoke_report.add_argument('--version', required=True)
    extract = sub.add_parser('extract-plugin-jar')
    extract.add_argument('--archive', type=Path, required=True)
    extract.add_argument('--out', type=Path, required=True)
    prepare = sub.add_parser('prepare-smoke')
    prepare.add_argument('--work', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'freeze':
        freeze(args.archive.resolve(), args.probe.resolve(), args.out.resolve())
    elif args.command == 'verify':
        print(json.dumps(verify(args.candidate.resolve()), indent=2))
    elif args.command == 'verify-signed':
        verify_signed(args.candidate.resolve(), args.signed.resolve())
    elif args.command == 'verify-smoke':
        verify_smoke(args.candidate.resolve(), args.archive.resolve(), args.report.resolve(),
                     args.runtime_attempted.resolve(), args.host, args.version)
    elif args.command == 'extract-plugin-jar':
        extract_plugin_jar(args.archive.resolve(), args.out.resolve())
    elif args.command == 'prepare-smoke':
        prepare_smoke(args.work.resolve())
    else:
        smoke(args.candidate.resolve(), args.ide.resolve(), args.work.resolve())


if __name__ == '__main__':
    main()
