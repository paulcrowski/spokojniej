# Spokojniej 1.4.0 — validation / walidacja

12 października / 12 October 2026 · Android 15 emulator, API 35, ARM64

| Property / Właściwość | Value / Wartość |
| --- | --- |
| Package / Pakiet | `pl.spokojniej.app` |
| Version / Wersja | `1.4.0`, code / numer `5` |
| Minimum Android / Minimalny Android | Android 8, API 26 |
| SQLite / Format kopii | schema 5 / JSON 4 (reads / odczytuje 1, 2, 3, 4) |
| APK size / Rozmiar APK | 58,126 bytes / bajty |
| Signature / Podpis | APK signature v2/v3 |
| APK SHA-256 | `d3a229a431ebbc92bf364497578aa2fff27c43227c2a4c1dd9f1d5b3ad6a662b` |
| Signing certificate SHA-256 / SHA-256 certyfikatu | `fe54ab2b36364dd14c002fecf1fb8fd38b04afb9c48b416a71aaf821189826d5` |

## Polski

Potwierdzone:

- 12 sprawdzeń czasu, licznika i polskiej odmiany podczas budowania APK.
- 95 sprawdzeń aktualizacji: 25 z 1.0, 27 z 1.1, 27 z 1.2 i 16 z 1.3. Zachowanie wydarzeń, ich czasu i ID, początku licznika, wdzięczności, małych kroków, ustawień przypomnień i dawnych kopii. Z 1.3 zachowano także tekst i UUID wpisu Zrobione oraz powiązania okoliczności. Starsza kłótnia staje się zaostrzoną rozmową; nowe pola nie wymyślają oznaczeń.
- 97 sprawdzeń Androida: dotychczasowe przypadki bazy, listy Zrobione, wdzięczności, powiadomień, pauzy i przewijania oraz trzy rodzaje trudnego momentu. Samo napięcie nie zmienia licznika ani nie blokuje spokojnego dnia. Zmiana rodzaju przelicza status i licznik. Okoliczności, zaskoczenie oraz pomocne działania można poprawiać. Błędny zapis wycofuje transakcję; usunięcie czyści zależne oznaczenia. Kopia zachowuje osobne rodzaje także przy identycznym czasie, a ponowny import nie tworzy duplikatów. Odczyt formatów 1–4.
- 51 sprawdzeń dziennika, widżetu i pliku: normalny start bez pauzy, wczoraj i starsza data, spokój, Zrobione, wdzięczność, wymagany jawny wybór rodzaju, opcjonalne okoliczności i „Co pomogło?”, poprawienie rodzaju, historia. Rzeczywisty widżet startuje odliczanie; otwarcie dziennika i restart procesu go nie resetują. Eksport i import przez systemowe okno plików odtwarza spokojny dzień z napięciem oraz pomocne działania. Powtórny import nie dodaje nowych wpisów. Krok szukający przycisku nad widocznym obszarem poprawiono, otwierając dziennik od góry; powtórzono ten krok i dalszą część importu.
- 13 dodatkowych sprawdzeń UI: nowy wpis nie ma wybranego rodzaju; impulsywną reakcję można zapisać bez opcjonalnych pól; edytor odtwarza dokładnie zaznaczony przycisk dla każdego z trzech rodzajów; zmiana na napięcie umożliwia nowy zapis spokoju. Anulowanie usunięcia zachowuje wpis, a potwierdzenie usuwa tylko moment i pozostawia spokój.
- APK pobrany z zainstalowanej aplikacji jest identyczny z udostępnianym plikiem. Ten sam certyfikat umożliwia aktualizację wcześniejszych wersji.

Sprawdzenia wykonano na emulatorze; nie obejmują wszystkich fizycznych telefonów, launcherów ani ograniczeń baterii producentów. Android może opóźnić alarmy i odświeżanie widżetu. Są to testy oprogramowania, a nie dowód skuteczności terapeutycznej.

## English

Confirmed:

- 12 time, counter and Polish-wording checks during the APK build.
- 95 upgrade checks: 25 from 1.0, 27 from 1.1, 27 from 1.2 and 16 from 1.3. Existing events, timestamps and IDs, tracking start, gratitude, small steps, reminder settings and old backups survive. The 1.3 upgrade also preserves done-item text and UUIDs and circumstance relationships. Legacy arguments remain escalated conversations with no invented new metadata.
- 97 Android checks cover existing storage, completed items, gratitude, notifications, pause and scroll behavior plus all three moment types. Tension does not reset the counter or block a calm check-in. Reclassification recalculates both. Circumstances, surprise and helpful actions can be edited. Invalid writes roll back; deletion cleans metadata. Backup preserves distinct types at identical timestamps, repeated import remains idempotent, and formats 1–4 are readable.
- 51 diary, widget and file checks cover normal launch, past days, completed items, gratitude, explicit type selection, optional circumstances and helpful actions, reclassification and history. Actual widget actions and process restart preserve the pause deadline. System-picker export/import restores a calm day with tension and helpful actions; repeat import adds no duplicates. A test locator looking above the visible viewport was corrected by reopening the diary at the top; that step and the remaining import checks were repeated.
- 13 further UI checks verify no default classification, saving an impulse without optional fields, restoration of the selected radio for each type, changing to tension and then recording a calm day, cancellation and confirmation of deletion while preserving the calm check-in.
- The installed APK matches the distributed binary byte for byte. The signing certificate is compatible with previous releases.

These checks use an emulator and do not cover every physical phone, launcher or manufacturer’s battery restrictions. Android may delay alarms and widget updates. These are software checks, not evidence of therapeutic effectiveness.

[Recorded results / Zapisane wyniki](validation/v1.4.0/) · [Screenshots / Zrzuty](images/) · [Previous report / Poprzedni raport](validation/v1.3.0/REPORT.md)

All visible sample entries are synthetic test data. / Widoczne przykładowe wpisy są danymi testowymi.
