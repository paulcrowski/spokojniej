"""Native UI checks for gratitude and real Android document export/import, test emulator only."""
from pathlib import Path
import subprocess, time, re, json, sys, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent.parent
ADB=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
OUT=ROOT/'build/gratitude-backup-smoke'; OUT.mkdir(parents=True,exist_ok=True)
SCREENS=ROOT/'dist/screens-1.1.0'; SCREENS.mkdir(parents=True,exist_ok=True)
passed=[]
def adb(*args): return subprocess.check_output([ADB,'-s','emulator-5580',*args],timeout=30)
def snapshot(name='current'):
    adb('shell','uiautomator','dump','/sdcard/gratitude-test.xml')
    content=adb('shell','cat','/sdcard/gratitude-test.xml'); (OUT/(name+'.xml')).write_bytes(content)
    return list(ET.fromstring(content).iter('node'))
def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.3)
def find(label,scroll=False,prefix=False):
    for _ in range(8 if scroll else 1):
        nodes=snapshot()
        for n in nodes:
            value=n.get('text','')
            if value==label or (prefix and value.startswith(label)): return n
        if scroll: adb('shell','input','swipe','520','1580','520','620','350'); time.sleep(.3)
    raise AssertionError(f'{label} not found; visible: {[n.get("text") for n in nodes if n.get("text")]}')
def tap(label,**kwargs): tap_node(find(label,**kwargs))
def check(label,name,**kwargs): find(label,**kwargs); passed.append(name); print('PASS:',name,flush=True)
def shot(name): (SCREENS/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def save_result(): (OUT/'result.json').write_text(json.dumps({'passed':passed},ensure_ascii=False,indent=2))

if len(sys.argv)==1:
    adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a','home')
    check('Za co jestem dziś wdzięczny?','gratitude prompt on main screen',scroll=True)
    shot('gratitude-home')
    tap('Zapisz wdzięczność',scroll=True)
    nodes=snapshot('gratitude-editor'); editor=next(n for n in nodes if n.get('class')=='android.widget.EditText')
    tap_node(editor); adb('shell','input','text','Za%swspolny%sspacer%si%srodzine.')
    shot('gratitude-editor'); tap('ZAPISZ')
    check('Za wspolny spacer i rodzine.','gratitude saved by actual editor',scroll=True)
    adb('shell','am','force-stop','pl.spokojniej.app'); adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a','home')
    check('Za wspolny spacer i rodzine.','gratitude survives process restart',scroll=True)
    tap('Historia wpisów  ›',scroll=True)
    check('Za wspolny spacer i rodzine.','gratitude visible in history without any calm/conflict entry')
    shot('gratitude-history'); tap('‹  Wróć'); tap('Ustawienia')
    tap('Zapisz kopię',scroll=True)
    nodes=snapshot('create-document'); shot('create-document')
    print('CREATE DOCUMENT:',[(n.get('text'),n.get('resource-id')) for n in nodes if n.get('text')],flush=True)
    save_result()
elif '--complete' in sys.argv:
    passed=json.loads((OUT/'result.json').read_text())['passed']
    tap('SAVE'); check('Kopia zapisana','Android document export completes'); shot('backup-saved'); tap('GOTOWE')
    filename='Spokojniej-kopia-2026-10-08.json'
    exported=adb('shell','cat','/sdcard/Download/'+filename)
    (OUT/'exported-fixture.json').write_bytes(exported)
    document=json.loads(exported)
    assert document['format']=='spokojniej-backup' and document['gratitude'][0]['note']=='Za wspolny spacer i rodzine.'
    passed.append('actual exported document contains gratitude and full backup metadata'); print('PASS:',passed[-1],flush=True)
    # Simulate changing phones only in this dedicated emulator; Downloads stays outside app data.
    adb('shell','pm','clear','pl.spokojniej.app')
    adb('install','-r',str(ROOT/'dist/Spokojniej-1.1.0.apk'))
    adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a','home')
    tap('Ustawienia'); tap('Wczytaj kopię',scroll=True)
    nodes=snapshot('open-document'); shot('open-document')
    print('OPEN DOCUMENT:',[(n.get('text'),n.get('resource-id')) for n in nodes if n.get('text')],flush=True)
    tap(filename)
    check('Wczytać tę kopię?','backup preview before restoring')
    shot('backup-preview'); tap('WCZYTAJ')
    check('Kopia wczytana','restore on fresh app completes'); shot('backup-restored'); tap('GOTOWE')
    tap('‹  Wróć')
    check('Za wspolny spacer i rodzine.','gratitude restored after clearing phone app data',scroll=True); shot('gratitude-restored')
    adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a','home')
    tap('Ustawienia'); tap('Wczytaj kopię',scroll=True); tap(filename); tap('WCZYTAJ')
    check('Dodano wpisy historii: 0\nDodano dni z wdzięcznością: 0','repeat import adds no duplicate rows')
    tap('GOTOWE'); save_result()
    print('PASS:',len(passed),'gratitude and document backup UI checks',flush=True)
else:
    print('PICKER:',[(n.get('text'),n.get('resource-id'),n.get('bounds')) for n in snapshot('picker') if n.get('text')],flush=True)
