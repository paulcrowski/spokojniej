"""Real Android UI checks, only in dedicated emulator-5580."""
from pathlib import Path
import subprocess,time,re,json,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent.parent
ADB=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
OUT=ROOT/'build/support-ui-smoke';OUT.mkdir(parents=True,exist_ok=True)
SCREENS=ROOT/'dist/screens-1.2.0';SCREENS.mkdir(parents=True,exist_ok=True)
passed=[]
def adb(*args):return subprocess.check_output([ADB,'-s','emulator-5580',*args],timeout=30)
def snapshot(name='current'):
 for attempt in range(3):
  try: adb('shell','uiautomator','dump','/sdcard/support-test.xml');break
  except subprocess.CalledProcessError:
   if attempt==2:raise
   time.sleep(.4)
 content=adb('shell','cat','/sdcard/support-test.xml');(OUT/(name+'.xml')).write_bytes(content)
 return list(ET.fromstring(content).iter('node'))
def tap_node(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.25)
def find(label,scroll=False,contains=False):
 for _ in range(10 if scroll else 1):
  nodes=snapshot()
  for n in nodes:
   value=n.get('text','')
   if value==label or (contains and label in value):return n
  if scroll:adb('shell','input','swipe','520','1550','520','620','300');time.sleep(.25)
 raise AssertionError(f'{label} not found; visible: {[n.get("text") for n in nodes if n.get("text")]}')
def tap(label,**kwargs):tap_node(find(label,**kwargs))
def check(label,name,**kwargs):find(label,**kwargs);passed.append(name);print('PASS:',name,flush=True);save()
def shot(name):(SCREENS/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def home():adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a','home');time.sleep(.3)
def save():(OUT/'result.json').write_text(json.dumps({'passed':passed},ensure_ascii=False,indent=2))

if len(sys.argv)==1:
 adb("shell","pm","clear","pl.spokojniej.app");adb("shell","pm","grant","pl.spokojniej.app","android.permission.POST_NOTIFICATIONS")
 home();check('Potrzebuję chwili','pause immediately accessible on home');shot('home')
 tap('Zapisz mały krok',scroll=True)
 tap('Zrobiłem pauzę');tap('Powiedziałem swoje spokojnie');tap('Było mi trudno');shot('day-editor');tap('ZAPISZ')
 check('Zrobiłem pauzę\nPowiedziałem swoje spokojnie\nByło mi trudno','positive and difficult choices saved together',scroll=True);shot('day-saved')
 tap('Zapisz kłótnię',scroll=True);tap('ZAPISZ')
 check('Zrobiłem pauzę\nPowiedziałem swoje spokojnie\nByło mi trudno','positive choices survive actual conflict entry',scroll=True)
 adb('shell','am','force-stop','pl.spokojniej.app');home()
 check('Zrobiłem pauzę\nPowiedziałem swoje spokojnie\nByło mi trudno','daily choices survive process restart',scroll=True)
 tap('Edytuj zapis dnia');tap('Powiedziałem swoje spokojnie');tap('ZAPISZ')
 check('Zrobiłem pauzę\nByło mi trudno','editing only removes selected choice',scroll=True)
 home();tap('Zapisz wdzięczność');nodes=snapshot('gratitude-editor');editor=next(n for n in nodes if n.get('class')=='android.widget.EditText');tap_node(editor)
 adb('shell','input','text','Za%swspolny%sspacer.');tap('ZAPISZ')
 tap('Historia wpisów  ›',scroll=True);check('Zrobiłem pauzę\nByło mi trudno','steps visible in history');check('Za wspolny spacer.','gratitude visible beside difficult day');shot('history')
 tap('‹  Wróć');tap('Ustawienia');tap('Dodaj widget',scroll=True)
 nodes=snapshot('pin-widget');choices=[n for n in nodes if n.get('text') in ('ADD TO HOME SCREEN','Add automatically','Add to home screen','ADD','Dodaj automatycznie','Dodaj do ekranu głównego','DODAJ','Add','Dodaj')]
 assert choices,'Widget confirmation missing';tap_node(choices[-1]);adb('shell','input','keyevent','KEYCODE_HOME')
 check('Potrzebuję chwili','native home-screen widget offers break');shot('widget')
 tap('Potrzebuję chwili');check('„Potrzebuję chwili. Wrócę do rozmowy o','widget opens break directly with return time',contains=True);shot('pause')
 # A widget click already starts ten minutes; returning does not start over.
 before=snapshot('pause-before-background');timer=next(n.get('text') for n in before if re.fullmatch(r'\d{1,2}:\d{2}',n.get('text','')))
 before_seconds=sum(int(part)*factor for part,factor in zip(timer.split(':'),(60,1)))
 phrase=next(n.get('text') for n in before if n.get('text','').startswith('„Potrzebuję chwili. Wrócę'))
 adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(1)
 home();check('„Potrzebuję chwili. Wrócę do rozmowy o','leaving and returning keeps the break open',contains=True)
 after=snapshot('pause-after-background');timer=next(n.get('text') for n in after if re.fullmatch(r'\d{1,2}:\d{2}',n.get('text','')))
 after_seconds=sum(int(part)*factor for part,factor in zip(timer.split(':'),(60,1)))
 assert after_seconds<before_seconds and any(n.get('text')==phrase for n in after)
 passed.append('break end time survives leaving app without resetting');print('PASS:',passed[-1],flush=True)
 tap('30 SEKUND');check('„Potrzebuję chwili, zanim odpowiem.”','can switch to short pause');shot('short-pause')
 time.sleep(31);check('Chwila minęła','short pause completes on real screen');shot('pause-complete');tap('WRACAM')
 home();tap('Ustawienia');check('Przed wieczorem · chwila dla siebie','preventive reminder replaces evening assessment',scroll=True)
 tap('Wyślij próbne przypomnienie',scroll=True);adb('shell','cmd','statusbar','expand-notifications')
 check('Twoje zdanie ma znaczenie','actual gentle notification displayed',contains=True);shot('notification');adb('shell','cmd','statusbar','collapse')
 tap('Zapisz kopię',scroll=True);nodes=snapshot('create-document');shot('create-document');save()
 print('PICKER:',[(n.get('text'),n.get('content-desc'),n.get('resource-id')) for n in nodes if n.get('text') or n.get('content-desc')],flush=True)
elif '--complete' in sys.argv:
 passed=json.loads((OUT/'result.json').read_text())['passed']
 tap('SAVE');check('Kopia zapisana','native document export succeeds');shot('backup-saved');tap('GOTOWE')
 files=adb('shell','ls','/sdcard/Download').decode().splitlines();filename=next(f for f in files if f.startswith('Spokojniej-kopia-') and f.endswith('.json'))
 exported=adb('shell','cat','/sdcard/Download/'+filename);(OUT/'exported-fixture.json').write_bytes(exported)
 data=json.loads(exported);assert data['version']==2 and len(data['marks'])==2 and {m['kind'] for m in data['marks']}=={'pause','difficult'}
 assert data['gratitude'][0]['note']=='Za wspolny spacer.' and len(data['events'])==1
 passed.append('exported JSON contains actual conflict, gratitude, small step and difficult feeling');print('PASS:',passed[-1],flush=True)
 adb('shell','pm','clear','pl.spokojniej.app');home();tap('Ustawienia');tap('Wczytaj kopię',scroll=True);shot('open-document');tap(filename)
 check('Zapisy małych kroków i trudności: 2','backup preview includes new daily choices',contains=True);shot('backup-preview');tap('WCZYTAJ')
 check('Kopia wczytana','restore in fresh app succeeds');shot('backup-restored');tap('GOTOWE');tap('‹  Wróć')
 check('Zrobiłem pauzę\nByło mi trudno','positive and difficult choices restored',scroll=True);check('Za wspolny spacer.','gratitude restored',scroll=True);shot('home-restored')
 home();tap('Ustawienia');tap('Wczytaj kopię',scroll=True);tap(filename);tap('WCZYTAJ')
 check('Dodano zapisy małych kroków i trudności: 0','repeat file import adds no duplicate choices',contains=True);tap('GOTOWE');save()
 print('PASS:',len(passed),'support and backup UI checks',flush=True)
else:
 print('PICKER:',[(n.get('text'),n.get('content-desc'),n.get('resource-id')) for n in snapshot('picker') if n.get('text') or n.get('content-desc')],flush=True)
