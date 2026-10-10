#!/usr/bin/env python3
"""Build the unobfuscated Minecraft 26.3 mod using JDK 25 and official artifacts.

Alternative when Loom cannot run in a restricted container. Uses only Python's
standard library. Download caches stay in .portable/ and are not redistributed.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / '.portable'
VERSION = '1.5.1+26.3'
MC = '26.3'
API = '0.161.0+26.3'
LOADER = '0.19.5'


def get(url):
    with urllib.request.urlopen(url, timeout=90) as response:
        return response.read()


def download(url, target, expected=None, algorithm='sha1'):
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (not expected or hashlib.new(algorithm, target.read_bytes()).hexdigest() == expected):
        return target
    data = get(url)
    if expected and hashlib.new(algorithm, data).hexdigest() != expected:
        raise RuntimeError(f'Checksum mismatch: {target.name}')
    temporary = target.with_suffix(target.suffix + '.download')
    temporary.write_bytes(data)
    temporary.replace(target)
    return target


def artifact(url, name):
    sha = get(url + '.sha1').decode().split()[0]
    return download(url, CACHE / name, sha)


def java_tool(name):
    suffix = '.exe' if os.name == 'nt' else ''
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + suffix) if home else None
    if candidate and candidate.exists():
        return str(candidate)
    found = shutil.which(name)
    if not found:
        raise RuntimeError('Instala JDK 25 y configura JAVA_HOME para compilar.')
    return found


def main():
    CACHE.mkdir(exist_ok=True)
    manifest = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    version = next(v for v in manifest['versions'] if v['id'] == MC)
    metadata_file = download(version['url'], CACHE / 'minecraft.json', version['sha1'])
    metadata = json.loads(metadata_file.read_text())
    client = metadata['downloads']['client']
    print('Descargando dependencias oficiales de Minecraft 26.3 y Fabric…', flush=True)
    jobs = [(client['url'], CACHE / 'minecraft-client.jar', client['sha1'])]
    for library in metadata['libraries']:
        a = library.get('downloads', {}).get('artifact')
        if a and ':natives-' not in library['name']:
            jobs.append((a['url'], CACHE / 'libraries' / a['path'], a['sha1']))
    with ThreadPoolExecutor(max_workers=8) as pool:
        files = list(pool.map(lambda args: download(*args), jobs))
    api_url = f'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{API}/fabric-api-{API}.jar'
    loader_url = f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{LOADER}/fabric-loader-{LOADER}.jar'
    for url, name in [(api_url, 'fabric-api.jar'), (loader_url, 'fabric-loader.jar')]:
        files.append(artifact(url, name))
    with zipfile.ZipFile(CACHE / 'fabric-api.jar') as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/jars/') and name.endswith('.jar'):
                target = CACHE / 'fabric-modules' / Path(name).name
                target.parent.mkdir(exist_ok=True)
                target.write_bytes(archive.read(name))
                files.append(target)
    junit_url = 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.12.2/junit-platform-console-standalone-1.12.2.jar'
    junit = artifact(junit_url, 'junit-console.jar')
    modmenu_url = 'https://api.modrinth.com/v2/project/modmenu/version/20.0.1'
    modmenu_info = json.loads(get(modmenu_url))
    modmenu_file = next(f for f in modmenu_info['files'] if f['primary'])
    files.append(download(modmenu_file['url'], CACHE / 'modmenu.jar', modmenu_file['hashes']['sha512'], 'sha512'))
    classpath = os.pathsep.join(str(p) for p in files)
    classes = ROOT / 'build' / 'portable' / 'classes'
    test_classes = ROOT / 'build' / 'portable' / 'tests'
    for folder in [classes, test_classes]:
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)
    sources = sorted((ROOT / 'src/main/java').rglob('*.java')) + sorted((ROOT / 'src/client/java').rglob('*.java'))
    javac = java_tool('javac')
    print('Compilando el mod contra las clases reales de Minecraft 26.3…', flush=True)
    subprocess.run([javac, '--release', '25', '-encoding', 'UTF-8', '-cp', classpath,
                    '-d', str(classes), *map(str, sources)], check=True)
    tests = sorted((ROOT / 'src/test/java').rglob('*.java'))
    test_cp = os.pathsep.join([str(classes), str(ROOT / 'src/main/resources'), str(junit), classpath])
    subprocess.run([javac, '--release', '25', '-encoding', 'UTF-8', '-cp', test_cp,
                    '-d', str(test_classes), *map(str, tests)], check=True)
    print('Ejecutando las pruebas…', flush=True)
    report = ROOT / 'build' / 'test-results' / 'portable'
    subprocess.run([java_tool('java'), '-jar', str(junit), 'execute',
                    '--class-path', os.pathsep.join([str(test_classes), test_cp]),
                    '--scan-class-path=' + str(test_classes), '--disable-banner', '--disable-ansi-colors',
                    '--reports-dir', str(report)], check=True)
    output = ROOT / 'build' / 'libs' / f'coretrace-{VERSION}.jar'
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
        for path in sorted(classes.rglob('*.class')):
            jar.write(path, path.relative_to(classes).as_posix())
        resources = ROOT / 'src/main/resources'
        for path in sorted(resources.rglob('*')):
            if path.is_file():
                data = path.read_bytes()
                if path.name == 'fabric.mod.json':
                    data = data.replace(b'${version}', VERSION.encode())
                jar.writestr(path.relative_to(resources).as_posix(), data)
        jar.write(ROOT / 'LICENSE', 'LICENSE_coretrace')
    print(f'JAR generado: {output}', flush=True)
    print('SHA-256: ' + hashlib.sha256(output.read_bytes()).hexdigest(), flush=True)


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
