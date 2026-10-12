"""End-to-end diary, widget routing and SAF backup on emulator-5580 only."""
from pathlib import Path
import subprocess,time,re,json,datetime,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent.parent
ADB=str(Path.home()/'Library/Android/sdk/platform-tools/adb')
OUT=ROOT/'build/diary-ui-smoke'; OUT.mkdir(parents=True,exist_ok=True)
SHOTS=ROOT/'dist/screens-1.3.0'; SHOTS.mkdir(parents=True,exist_ok=True)
passed=[]
def adb(*args):return subprocess.check_output([ADB,'-s','emulator-5580',*args],timeout=30)
def nodes(name='current'):
 for attempt in range(3):
  try:adb('shell','uiautomator','dump','/sdcard/diary-test.xml');break
  except subprocess.CalledProcessError:
   if attempt==2:raise
   time.sleep(.4)
 raw=adb('shell','cat','/sdcard/diary-test.xml');(OUT/(name+'.xml')).write_bytes(raw)
 return list(ET.fromstring(raw).iter('node'))
def tap_node(node):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
 assert x2>x1 and y2>y1,node.attrib
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.3)
def find(label,scroll=False,contains=False,desc=False,index=0):
 for attempt in range(12 if scroll else 1):
  current=nodes();found=[n for n in current if (label in n.get('content-desc' if desc else 'text','') if contains else label==n.get('content-desc' if desc else 'text',''))]
  if len(found)>index:return found[index]
  if scroll:adb('shell','input','swipe','520','1470','520','580','250');time.sleep(.2)
 raise AssertionError(f'{label} missing: {[n.get("text") for n in current if n.get("text")]}')
def tap(label,**kw):tap_node(find(label,**kw))
def verify(condition,name):
 assert condition,name
 passed.append(name);print('PASS:',name,flush=True)
 (OUT/'result.json').write_text(json.dumps({'passed':passed},ensure_ascii=False,indent=2))
def check(label,name,**kw):verify(find(label,**kw) is not None,name)
def shot(name):(SHOTS/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def launch(action='android.intent.action.MAIN'):
 if action=='android.intent.action.MAIN':adb('shell','monkey','-p','pl.spokojniej.app','-c','android.intent.category.LAUNCHER','1')
 else:adb('shell','am','start','-n','pl.spokojniej.app/.MainActivity','-a',action)
 time.sleep(.5)
def fill(text,replace=False):
 editor=next(n for n in nodes() if n.get('class')=='android.widget.EditText');tap_node(editor)
 if replace:
  adb('shell','input','keyevent','KEYCODE_MOVE_END');adb('shell','input','keyevent','--longpress','KEYCODE_DEL')
 adb('shell','input','text',text.replace(' ','%s'))
 adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.2)
def section_add(title):
 current=nodes();heading=next(n for n in current if n.get('text')==title);_,y,_,y2=map(int,re.findall(r'\d+',heading.get('bounds')))
 add=next(n for n in current if n.get('text') in ('+ Dodaj','Edytuj') and abs(int(re.findall(r'\d+',n.get('bounds'))[1])-y)<100)
 tap_node(add)
def timer():return next(n.get('text') for n in nodes() if re.fullmatch(r'\d{1,2}:\d{2}',n.get('text','')))
def seconds(value):a,b=map(int,value.split(':'));return a*60+b

for file in adb('shell','ls','/sdcard/Download').decode().splitlines():
 if file.startswith('Spokojniej-kopia-') and file.endswith('.json'):adb('shell','rm','/sdcard/Download/'+file)
adb('shell','pm','clear','pl.spokojniej.app');adb('shell','pm','grant','pl.spokojniej.app','android.permission.POST_NOTIFICATIONS')
launch();check('Zrobione','normal cold launch opens diary');verify(not any(n.get('resource-id')=='android:id/alertTitle' for n in nodes()),'normal launch has no automatic pause dialog');shot('home')
verify(find('Następny dzień',desc=True).get('enabled')=='false','future date arrow disabled')
tap('Poprzedni dzień',desc=True);check('Wczoraj ·','yesterday accessible in one tap',contains=True)
tap('Spokojny dzień');check('✓ Spokojny dzień','yesterday calm day can be recorded');shot('yesterday-calm')
section_add('Zrobione');tap('ZAPISZ');check('Wpisz jedną zrobioną rzecz.','empty completed item gives inline validation',contains=True)
fill('Zrobilem kawe.');tap('ZAPISZ');check('✓  Zrobilem kawe.','completed action saved on yesterday')
section_add('Zrobione');fill('Spacer z rodzina.');tap('ZAPISZ');check('✓  Spacer z rodzina.','done list grows through the day')
tap('✓  Zrobilem kawe.');tap('Popraw wpis');check('Zrobilem kawe.','completed item is editable');tap('ANULUJ')
tap('✓  Spacer z rodzina.');tap('Usuń wpis');tap('ZOSTAW');check('✓  Spacer z rodzina.','delete can be cancelled')
tap('✓  Spacer z rodzina.');tap('Usuń wpis');tap('USUŃ');verify(not any(n.get('text')=='✓  Spacer z rodzina.' for n in nodes()),'confirmed deletion removes only chosen item')
section_add('Wdzięczność');fill('Za dobra rozmowe.');tap('ZAPISZ');check('Za dobra rozmowe.','gratitude belongs to selected past day')
tap('Zapisz kłótnię');tap('Praca');tap('Rodzina');tap('Zmęczenie',scroll=True);shot('circumstances-editor');tap('ZAPISZ')
check('✓  Zrobilem kawe.','done list survives difficult conversation');check('Za dobra rozmowe.','gratitude survives difficult conversation',scroll=True)
check('Praca · Rodzina · Zmęczenie','optional circumstances visible with conversation',scroll=True,contains=True);shot('circumstances-saved')
tap('Historia');check('Zrobione: 1','history indexes completed actions',contains=True);shot('history')
row=next(n for n in nodes() if 'Zrobione: 1' in n.get('text',''));tap_node(row);check('Wczoraj ·','history opens editable past diary',contains=True)
tap('Wróć do dzisiaj');verify(not any(n.get('text')=='✓  Zrobilem kawe.' for n in nodes()),'past entries do not appear in todays diary')
# Date picker, including an older day with no existing entries.
tap('Wybierz dzień:',desc=True,contains=True);shot('date-picker')
phone_date=datetime.date.fromisoformat(adb('shell','date','+%F').decode().strip());older=phone_date-datetime.timedelta(days=2)
if older.month!=phone_date.month:tap_node(next(n for n in nodes() if n.get('resource-id')=='android:id/prev'))
tap(str(older.day));tap('OK')
months=('stycznia','lutego','marca','kwietnia','maja','czerwca','lipca','sierpnia','września','października','listopada','grudnia')
check(f'{older.day} {months[older.month-1]} {older.year}','calendar selects an older editable day',desc=True,contains=True)
tap('Spokojny dzień');check('✓ Spokojny dzień','calendar-selected older day can be marked calm');tap('Wróć do dzisiaj')
# Actual native widget, with distinct tap areas.
tap('Ustawienia');tap('Dodaj widget',scroll=True)
choices=[n for n in nodes() if n.get('text') in ('ADD TO HOME SCREEN','Add automatically','Add to home screen','ADD','Dodaj automatycznie','Dodaj do ekranu głównego','DODAJ','Add','Dodaj')]
verify(bool(choices),'native widget can be pinned');tap_node(choices[-1]);adb('shell','input','keyevent','KEYCODE_HOME');check('Otwórz dziennik  ›','widget has separate diary action');shot('widget')
tap('Potrzebuję chwili');check('Potrzebuję chwili','widget opens pause directly');before=seconds(timer());verify(570<=before<=600,'widget immediately starts ten-minute countdown');shot('pause')
adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(1);launch();check('Zrobione','normal warm launch returns to diary during pause');verify(not any(n.get('resource-id')=='android:id/alertTitle' for n in nodes()),'warm launch does not reopen pause dialog')
tap('Trwa pauza do',contains=True);after=seconds(timer());verify(after<before,'pause continues in background without restart');tap('ZAMKNIJ EKRAN');check('Trwa pauza do','closing pause screen immediately exposes active diary timer',contains=True)
adb('shell','am','force-stop','pl.spokojniej.app');launch();check('Zrobione','process restart opens diary during active pause');verify(not any(n.get('resource-id')=='android:id/alertTitle' for n in nodes()),'process restart does not reopen timer')
adb('shell','input','keyevent','KEYCODE_HOME');tap('Otwórz dziennik  ›');check('Zrobione','actual widget diary action does not open countdown')
adb('shell','input','keyevent','KEYCODE_HOME');tap('Przerwa do',contains=True);verify(seconds(timer())<after,'second widget pause tap resumes existing deadline')
tap('30 SEKUND');verify(seconds(timer())<=30,'short pause is available');shot('short-pause');tap('WRACAM');verify(not any(n.get('text','').startswith('Trwa pauza do') for n in nodes()),'return action ends pause')
# Real export and restore through Android document picker, not a simulated backup.
tap('Ustawienia');tap('Zapisz kopię',scroll=True);shot('create-document')
current=nodes();print('PICKER:',[(n.get('text'),n.get('content-desc')) for n in current if n.get('text') or n.get('content-desc')],flush=True)
# Default save location is Documents; select Downloads explicitly through navigation drawer.
nav=next((n for n in current if n.get('content-desc') in ('Show roots','Pokaż foldery','Open navigation drawer')),None)
if nav is not None:
 tap_node(nav);tap('Downloads');
tap('SAVE');check('Kopia zapisana','SAF export succeeds');tap('GOTOWE')
files=adb('shell','ls','/sdcard/Download').decode().splitlines();filename=next(f for f in files if f.startswith('Spokojniej-kopia-') and f.endswith('.json'))
exported=adb('shell','cat','/sdcard/Download/'+filename);(OUT/'exported-fixture.json').write_bytes(exported);data=json.loads(exported)
verify(data['version']==3 and len(data['done'])==1 and data['done'][0]['note']=='Zrobilem kawe.','actual backup contains completed item')
conflict=next(e for e in data['events'] if e['kind']=='conflict');verify(set(conflict['contexts'])=={'work','family','fatigue'},'actual backup contains optional conversation circumstances')
adb('shell','pm','clear','pl.spokojniej.app');launch();tap('Ustawienia');tap('Wczytaj kopię',scroll=True)
current=nodes();nav=next((n for n in current if n.get('content-desc') in ('Show roots','Pokaż foldery','Open navigation drawer')),None)
if nav is not None:tap_node(nav);tap('Downloads')
tap(filename);check('Lista Zrobione: 1','import preview includes done count',contains=True);shot('backup-preview');tap('WCZYTAJ');check('Kopia wczytana','SAF restore into empty app succeeds');tap('GOTOWE')
tap('Dziennik');tap('Poprzedni dzień',desc=True);check('✓  Zrobilem kawe.','completed item restored on correct past day');check('Praca · Rodzina · Zmęczenie','conversation circumstances restored',contains=True,scroll=True)
tap('Ustawienia');tap('Wczytaj kopię',scroll=True);tap(filename);tap('WCZYTAJ');check('Dodano do listy Zrobione: 0','repeated file restore adds no done duplicates',contains=True);tap('GOTOWE')
print('PASS:',len(passed),'diary, widget routing and backup UI checks',flush=True)
