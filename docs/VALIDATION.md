# Spokojniej 1.2.0 — validation / walidacja

8 października / 8 October 2026 · Android 15 emulator, API 35, ARM64

| Property / Właściwość | Value / Wartość |
| --- | --- |
| Package / Pakiet | `pl.spokojniej.app` |
| Version / Wersja | `1.2.0`, code / numer `3` |
| Minimum Android / Minimalny Android | Android 8, API 26 |
| APK size / Rozmiar APK | 49,934 bytes / bajty |
| Signature / Podpis | APK signature v2/v3 |
| APK SHA-256 | `82f8764239ab4fc21404fb39ba0bc7df49ec0933fec231dd99c086683854757e` |
| Signing certificate SHA-256 / SHA-256 certyfikatu | `fe54ab2b36364dd14c002fecf1fb8fd38b04afb9c48b416a71aaf821189826d5` |

## Polski

Potwierdzone:

- 12 sprawdzeń czasu, licznika i polskiej odmiany przy budowaniu APK.
- 10 sprawdzeń aktualizacji 1.0 → 1.2 i 12 sprawdzeń 1.1 → 1.2: zachowanie historii, początku liczenia, wdzięczności oraz wcześniej zapisanych godzin przypomnień; odczyt kopii wyeksportowanej przez poprzednie APK.
- 57 sprawdzeń Androida: SQLite, cofanie i edycja, wdzięczność, małe kroki także w dniu z kłótnią, trwałość, JSON, odtwarzanie i ponowny import bez duplikatów, odrzucanie błędnego formatu/dat/wyborów, ochrona obecnych tekstów i ustawień, prawdziwe akcje powiadomień oraz przerwa z pojedynczym przypomnieniem o powrocie.
- 22 sprawdzenia UI i pliku: główny ekran, edycja wyborów dnia, zachowanie kroków po kłótni i restarcie procesu, historia, dodanie widżetu, bezpośrednie uruchomienie przerwy, zachowanie czasu końca po opuszczeniu aplikacji, ukończenie krótkiej pauzy, prawdziwe powiadomienie, eksport przez systemowe okno, podgląd i odtworzenie pliku na wyczyszczonej aplikacji oraz ponowny import z zerem nowych wpisów.
- APK pobrany z zainstalowanej aplikacji w emulatorze był identyczny z udostępnianym plikiem. Podpis jest zgodny z poprzednimi wersjami.

Nie sprawdzono wszystkich fizycznych telefonów, launcherów, całonocnego działania ani ograniczeń baterii producentów. Android może opóźnić alarmy i odświeżenie widżetu. Nie jest to dowód skuteczności terapeutycznej; aplikacja nie diagnozuje ani nie leczy ADHD lub lęku.

## English

Confirmed:

- 12 time, counter and Polish-wording checks during APK build.
- 10 upgrade checks for 1.0 → 1.2 and 12 for 1.1 → 1.2: preserving history, tracking start, gratitude and saved reminder times, plus reading a backup exported by the old APK.
- 57 Android checks: SQLite, undo/edit, gratitude, small steps alongside an argument, persistence, JSON, restore and idempotent import, invalid format/date/choice rejection, preservation of existing text/settings, actual notification actions and pause state with a single return reminder.
- 22 UI/file checks: home screen, editing daily choices, preserving steps after an argument and process restart, history, pinning the widget, opening a break directly, maintaining its end time after leaving the app, completing a short pause, a real notification, system-picker export, preview and restore after clearing test-app data, and repeated import with zero new entries.
- The APK pulled from the installed emulator app matched the distributed file byte for byte. Its signature is compatible with previous versions.

This does not cover all physical phones, launchers, overnight operation or manufacturer-specific battery restrictions. Android may delay alarms and widget refreshes. These are software checks, not evidence of therapeutic effectiveness; the app does not diagnose or treat ADHD or anxiety.

[Recorded results / Zapisane wyniki](validation/v1.2.0/) · [Screenshots / Zrzuty](images/)

All visible sample entries are synthetic test data. / Widoczne przykładowe wpisy są danymi testowymi.
