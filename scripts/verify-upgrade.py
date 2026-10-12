"""APK migration checks, exclusively in the dedicated emulator-5580."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent.parent
adb=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
old=sys.argv[1]
assert old in ('1.0.0','1.1.0','1.2.0')
out=root/'dist/validation-1.3.0'; out.mkdir(parents=True,exist_ok=True)
log=[]
def run(*args,okay=True):
 p=subprocess.run([adb,'-s','emulator-5580',*args],capture_output=True,text=True,timeout=45)
 if okay and p.returncode: raise RuntimeError(p.stdout+p.stderr)
 log.append(p.stdout+p.stderr); print(p.stdout.strip(),flush=True)
 return p.stdout
run('uninstall','pl.spokojniej.app',okay=False)
run('install',str(root/f'dist/Spokojniej-{old}.apk'))
run('install','-r',str(root/'build/device-tests/checks.apk'))
seed=run('shell','am','instrument','-w','-e','mode','seed','pl.spokojniej.app.tests/pl.spokojniej.app.tests.MigrationChecks'); assert 'SEEDED:' in seed
run('install','-r',str(root/'dist/Spokojniej-1.3.0.apk'))
verify=run('shell','am','instrument','-w','pl.spokojniej.app.tests/pl.spokojniej.app.tests.MigrationChecks'); assert 'PASS:' in verify and 'FAIL:' not in verify
(out/f'migration-{old}.txt').write_text('\n'.join(log))
