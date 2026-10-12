"""Storage, reminder and pause checks in dedicated emulator-5580 only."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent.parent;adb=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
def run(*args):
 p=subprocess.run([adb,'-s','emulator-5580',*args],capture_output=True,text=True,timeout=45)
 assert p.returncode==0,p.stdout+p.stderr
 print(p.stdout.strip(),flush=True);return p.stdout
run('install','-r',str(root/'dist/Spokojniej-1.4.0.apk'))
run('install','-r',str(root/'build/device-tests/checks.apk'))
run('shell','pm','grant','pl.spokojniej.app','android.permission.POST_NOTIFICATIONS')
output=run('shell','am','instrument','-w','pl.spokojniej.app.tests/pl.spokojniej.app.tests.DeviceChecks')
(root/'dist/validation-1.4.0/device-checks.txt').write_text(output)
assert 'PASS:' in output and 'FAIL:' not in output
