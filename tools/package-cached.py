#!/usr/bin/env python3
"""Compile/package from an already populated ForgeGradle cache, without sockets.

This does not run Gradle, tests, Minecraft, or a dependency resolver. The normal
release command remains ./gradlew clean assemble. Use only with this project's
exact Forge cache and a Java 25 JDK.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jdk', type=Path, default=os.environ.get('JAVA_HOME'))
parser.add_argument('--cache', type=Path, default=Path.home() / '.gradle/caches')
args = parser.parse_args()
if not args.jdk:
    parser.error('Set JAVA_HOME or pass --jdk pointing to Java 25')
root = Path(__file__).resolve().parent.parent
forge = args.cache / 'minecraftforge/forgegradle/mavenizer/caches'
platform = forge / 'forge/net/minecraftforge/forge/26.3-66.1.0/official/26.3/recompiled.jar'
if not platform.is_file():
    parser.error('Missing exact compiled Forge 26.3-66.1.0 dependency cache')
java_version = subprocess.check_output([str(args.jdk / 'bin/javac'), '-version'], text=True).strip()
if not java_version.startswith('javac 25.'):
    parser.error('Java 25 is required, found ' + java_version)

libs = sorted(p for base in [forge / 'maven', args.cache / 'modules-2/files-2.1']
              for p in base.rglob('*.jar')
              if not any(s in p.name for s in ['-sources', '-javadoc', 'natives-']))
classpath = os.pathsep.join(map(str, [platform] + libs))
processors = ','.join('net.minecraftforge.eventbus.validator.' + name for name in
                      ['EventBusValidator', 'EventTypeValidator', 'SubscribeEventValidator'])
output = root / 'build/cached-package'
if output.exists():
    shutil.rmtree(output)
classes = output / 'classes'
classes.mkdir(parents=True)
compiler_args = ['--release', '25', '-g', '-encoding', 'UTF-8', '-classpath', classpath,
                 '-processorpath', classpath, '-processor', processors, '-d', str(classes)]
sources = sorted((root / 'src/main/java').rglob('*.java'))
compiler_args += [str(p) for p in sources]
argfile = output / 'javac.args'
argfile.write_text('\n'.join('"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"'
                             for arg in compiler_args), encoding='utf-8')
subprocess.run([str(args.jdk / 'bin/javac'), '@' + str(argfile)], check=True, cwd=root)

version = re.search(r"^version = '([^']+)'", (root / 'build.gradle').read_text(), re.M)[1]
metadata = (root / 'src/main/resources/META-INF/mods.toml').read_text()
assert 'version="' + version + '"' in metadata, 'Version mismatch'
for lang in ['en_us', 'ru_ru']:
    json.loads((root / f'src/main/resources/assets/smartsettlements/lang/{lang}.json').read_text())
artifact = root / 'artifacts' / ('smart-settlements-' + version + '.jar')
artifact.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(artifact, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
    for directory in [classes, root / 'src/main/resources', root / 'src/generated/resources']:
        for path in sorted(directory.rglob('*')):
            if not path.is_file():
                continue
            name = path.relative_to(directory).as_posix()
            if not name.startswith('data/smartsettlements/test_instance/'):
                jar.write(path, name)
with zipfile.ZipFile(artifact) as jar:
    assert jar.testzip() is None, 'Archive integrity check failed'
    assert len(jar.namelist()) == len(set(jar.namelist())), 'Duplicate entries'
checksums = root / 'artifacts/SHA256SUMS'
checksums.write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n'
                            for path in sorted(artifact.parent.glob('*.jar'))))
print(f'Compiled {len(sources)} Java sources with Forge event validators; packaged {artifact.name}')
print(f'SHA256 {hashlib.sha256(artifact.read_bytes()).hexdigest()}')
print('No Gradle execution, tests, client, or server were run by this script.')
