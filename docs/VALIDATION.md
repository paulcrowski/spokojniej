# Spokojniej 1.3.0 — validation / walidacja

12 października / 12 October 2026 · Android 15 emulator, API 35, ARM64

| Property / Właściwość | Value / Wartość |
| --- | --- |
| Package / Pakiet | `pl.spokojniej.app` |
| Version / Wersja | `1.3.0`, code / numer `4` |
| Minimum Android / Minimalny Android | Android 8, API 26 |
| SQLite / Format kopii | schema 4 / JSON 3 (reads / odczytuje 1, 2, 3) |
| APK size / Rozmiar APK | 58,126 bytes / bajty |
| Signature / Podpis | APK signature v2/v3 |
| APK SHA-256 | `e4863bedecf5162ebd3b5ee936c9fbca237fe70a5ed39ad6f7e2f9ee8396d096` |
| Signing certificate SHA-256 / SHA-256 certyfikatu | `fe54ab2b36364dd14c002fecf1fb8fd38b04afb9c48b416a71aaf821189826d5` |

## Polski

Potwierdzone:

- 12 sprawdzeń czasu, licznika i polskiej odmiany podczas budowania APK.
- 73 sprawdzenia aktualizacji: 23 z 1.0, 25 z 1.1, 25 z 1.2. Zachowanie wydarzeń, czasu rozpoczęcia, wdzięczności, małych kroków, przypomnień oraz odczytu plików wyeksportowanych przez stare APK. Nowe tabele nie wymyślają wpisów ani okoliczności.
- 80 sprawdzeń Androida: baza SQLite, zapis spokoju dla wczoraj, zakaz przyszłych dni, lista Zrobione i jej edycja/usuwanie, opcjonalne okoliczności kilku rozmów o tej samej godzinie, transakcyjny import formatów 1/2/3 i ponowny import bez duplikatów. Zachowanie obecnych tekstów, odrzucanie nieprawidłowych danych, realne akcje powiadomień i pauza. Długa lista została przewinięta w prawdziwej aktywności; odświeżenie zachowało tę samą pozycję.
- 42 sprawdzenia UI i pliku: zwykłe uruchomienie przez launcher bez okna pauzy, wczoraj oraz starszy dzień wybrany w kalendarzu, zapis spokoju, pusta treść, rosnąca lista, wybór edycji i potwierdzane usuwanie, wdzięczność, okoliczności i historia. Rzeczywisty widżet zaczyna odliczanie; jego drugi obszar otwiera dziennik. Powrót z tła, ponowne otwarcie i restart procesu nie resetują pauzy ani nie otwierają automatycznie jej okna. Eksport przez systemowy wybór pliku, odtworzenie na wyczyszczonej aplikacji i powtórny import bez nowych wpisów.
- APK pobrany z zainstalowanej aplikacji w emulatorze jest identyczny z udostępnianym plikiem. Ten sam podpis umożliwia aktualizację wcześniejszych wersji.

Sprawdzenia wykonano na emulatorze; nie obejmują wszystkich fizycznych telefonów, launcherów ani ograniczeń baterii producentów. Android może opóźnić alarmy i odświeżanie widżetu. Są to testy oprogramowania, a nie dowód skuteczności terapeutycznej. Aplikacja nie diagnozuje ani nie leczy ADHD lub lęku.

## English

Confirmed:

- 12 time, counter and Polish-wording checks during the APK build.
- 73 upgrade checks: 23 from 1.0, 25 from 1.1 and 25 from 1.2. Events, tracking start, gratitude, small steps, reminders and backups exported by older APKs survive the upgrade. New tables do not invent entries or conversation circumstances.
- 80 Android checks: SQLite, yesterday’s calm check-in, future-date rejection, completed-item editing/deletion, optional circumstances for separate conversations with identical timestamps, transactional backup formats 1/2/3 and idempotent imports. Existing text is preserved; invalid data is rejected. Actual notification actions and pause behavior are exercised. A long list was scrolled in a real activity and kept the same position after refresh.
- 42 UI/file checks: normal launcher entry without a pause dialog, yesterday and an older calendar-selected day, calm check-ins, empty-text validation, a growing list, edit access, confirmed deletion, gratitude, circumstances and history. The actual widget starts a countdown; its other tap area opens the diary. Backgrounding, reopening and process restart do not reset the deadline or automatically reopen the break dialog. System-picker export, restore after clearing test-app data, and repeated import with zero new entries.
- The APK pulled from the installed emulator app matches the distributed binary. Its signing certificate is compatible with earlier releases.

These checks use an emulator and do not cover every physical phone, launcher or manufacturer’s battery restrictions. Android may delay alarms and widget updates. These are software checks, not evidence of therapeutic effectiveness. The app does not diagnose or treat ADHD or anxiety.

[Recorded results / Zapisane wyniki](validation/v1.3.0/) · [Screenshots / Zrzuty](images/) · [Previous report / Poprzedni raport](validation/v1.2.0/REPORT.md)

All visible sample entries are synthetic test data. / Widoczne przykładowe wpisy są danymi testowymi.
