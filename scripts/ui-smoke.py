"""Smoke checks on the dedicated emulator only. Uses actual Android UI snapshots."""
from pathlib import Path
import subprocess, time, re, json, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ADB = str(Path.home() / 'Library/Android/sdk/platform-tools/adb')
SERIAL = 'emulator-5580'
OUT = ROOT / 'build/ui-smoke'
OUT.mkdir(parents=True, exist_ok=True)
SCREENS = ROOT / 'dist/screens'
SCREENS.mkdir(parents=True, exist_ok=True)
passed = []

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], timeout=30)

def snapshot(name='current'):
    adb('shell', 'uiautomator', 'dump', '/sdcard/spokojniej-test.xml')
    content = adb('shell', 'cat', '/sdcard/spokojniej-test.xml')
    (OUT / (name + '.xml')).write_bytes(content)
    return list(ET.fromstring(content).iter('node'))

def texts(nodes):
    return [n.get('text','') for n in nodes if n.get('text')]

def find(label, scroll=False, prefix=False):
    for attempt in range(6 if scroll else 1):
        nodes = snapshot()
        for node in nodes:
            s = node.get('text','')
            if s == label or (prefix and s.startswith(label)):
                return node
        if scroll: adb('shell','input','swipe','520','1560','520','720','300'); time.sleep(.3)
    raise AssertionError(f'UI label not found: {label}; visible: {texts(nodes)}')

def tap_node(node):
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.35)

def tap(label, **kwargs): tap_node(find(label, **kwargs))

def assert_text(label, name, prefix=False):
    find(label,prefix=prefix); passed.append(name); print('PASS:',name,flush=True)

def shot(name): (SCREENS / (name + '.png')).write_bytes(adb('exec-out','screencap','-p'))

adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity')
assert_text('Dziś spokojnie','main screen launches')
snapshot('home'); shot('home')
tap('Dziś spokojnie')
assert_text('✓  Dziś spokojnie — zapisano','calm entry saved by UI')
adb('shell','am','force-stop','pl.spokojniej.app')
adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity')
assert_text('✓  Dziś spokojnie — zapisano','calm entry survives process restart')
snapshot('reopened')
tap('Zapisz kłótnię'); snapshot('conflict-editor'); shot('conflict-editor')
tap('ZAPISZ')
assert_text('Dziś był trudny moment.','conflict changes daily status',prefix=True)
tap('Cofnij ostatni wpis')
assert_text('✓  Dziś spokojnie — zapisano','undo restores calm status')
tap('Historia wpisów  ›',scroll=True)
assert_text('Spokojny dzień','history contains saved entry',prefix=True)
snapshot('history'); shot('history'); tap('‹  Wróć')
tap('Ustawienia')
tap('Rano · spokojna intencja')
nodes=snapshot('notification-permission')
allow = [n for n in nodes if n.get('resource-id') == 'com.android.permissioncontroller:id/permission_allow_button']
if allow: tap_node(allow[0])
tap('Wieczorem · jak minął dzień?')
tap('Wyślij próbne przypomnienie',scroll=True)
snapshot('settings'); shot('settings')
adb('shell','dumpsys','alarm')
(OUT/'alarms.txt').write_bytes(adb('shell','dumpsys','alarm'))
alarm=(OUT/'alarms.txt').read_text()
assert 'pl.spokojniej.app.MORNING' in alarm and 'pl.spokojniej.app.EVENING' in alarm
passed.append('morning and evening pending alarms registered'); print('PASS:',passed[-1],flush=True)
adb('shell','cmd','statusbar','expand-notifications')
assert_text('Dziś możesz odpowiedzieć spokojniej','actual trial notification displayed')
snapshot('notification'); shot('notification')
adb('shell','cmd','statusbar','collapse'); time.sleep(.3)
tap('Dodaj widget',scroll=True)
nodes=snapshot('pin-widget-dialog')
print('WIDGET DIALOG:',texts(nodes),flush=True)
choice=[n for n in nodes if n.get('text') in ('ADD TO HOME SCREEN','Add automatically','Add to home screen','ADD','Dodaj automatycznie','Dodaj do ekranu głównego','DODAJ','Add','Dodaj')]
if not choice: raise AssertionError('Widget pin confirmation not found')
tap_node(choice[-1]); adb('shell','input','keyevent','KEYCODE_HOME')
assert_text('0 dni bez kłótni','widget installed and shows current counter')
snapshot('widget'); shot('widget')
tap('0 dni bez kłótni')
assert_text('Spokojniej','widget opens application')
tap('Zrób 30 sekund pauzy',scroll=True)
assert_text('Chwila przed odpowiedzią','pause dialog opens')
snapshot('pause'); shot('pause')
time.sleep(31)
assert_text('Możesz wrócić do rozmowy','pause completes after 30 seconds')
snapshot('pause-complete'); tap('ZAMKNIJ')
adb('shell','input','keyevent','KEYCODE_BACK')
errors=adb('logcat','-d','-s','AndroidRuntime').decode()
assert 'FATAL EXCEPTION' not in errors
passed.append('no AndroidRuntime crash during smoke checks')
(OUT/'result.json').write_text(json.dumps({'passed':passed},ensure_ascii=False,indent=2))
print(f'PASS: {len(passed)} UI and device smoke checks',flush=True)
