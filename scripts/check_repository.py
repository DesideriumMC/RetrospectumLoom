"""Check the Git-tracked source inventory before publication."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode('utf-8').split('\0')
    paths = [path for path in paths if path]
    if not paths:
        raise RuntimeError('Stage the intended source files first')
    forbidden_directories = {'.work', '.gradle', 'build', 'run', 'run-server', '__pycache__', '.idea'}
    forbidden_suffixes = {'.jar', '.class', '.zip', '.exe', '.dll', '.log', '.hprof', '.pem', '.p12'}
    secret = re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}|-----BEGIN (?:OPENSSH|RSA|EC|PRIVATE) KEY-----')
    for name in paths:
        path = Path(name)
        if forbidden_directories.intersection(path.parts):
            raise RuntimeError(f'Generated/local directory is tracked: {name}')
        if path.suffix.lower() in forbidden_suffixes and name != 'gradle/wrapper/gradle-wrapper.jar':
            raise RuntimeError(f'Unexpected binary/local artifact: {name}')
        if path.suffix != '.jar' and secret.search((ROOT / path).read_bytes()):
            raise RuntimeError(f'Credential-like content in {name}')
    print(f'Checked {len(paths)} tracked source/notice files; sole binary allowance is the pinned Gradle wrapper')


if __name__ == '__main__':
    main()
