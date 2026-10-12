# Budowanie Spokojniej

Natywna aplikacja Java bez bibliotek zewnętrznych. Minimum: Android 8 (API 26); kompilacja i target SDK 35. Interfejs po polsku. Opis użytkowy i instalacja: [README](../README.md).

## Narzędzia

Android SDK Platform 35, Build Tools 35.0.0 i zgodny JDK. Lokalne sprawdzenia używały JDK z Android Studio na macOS. Można ustawić `JAVA_HOME` oraz `ANDROID_HOME` lub `ANDROID_SDK_ROOT`; domyślne ścieżki wskazują instalację Android Studio na macOS. Skrypty pomocnicze UI wymagają Python 3 i Platform Tools.

```sh
bash scripts/build-apk.sh
bash scripts/build-device-tests.sh
```

Pierwszy skrypt wykonuje 12 sprawdzeń czasu i polskiej odmiany, kompiluje zasoby i Java, generuje DEX, wyrównuje i podpisuje APK, sprawdza podpis i zapisuje SHA-256. Wynik: `dist/Spokojniej-1.4.0.apk`. Projekt można otworzyć w Android Studio; repozytorium nie zawiera Gradle wrappera.

## Podpis i aktualizacje

Oficjalne APK używa tego samego klucza autora co wcześniejsze wydania. Numer wersji wzrósł do 5; migracja SQLite v1/v2/v3/v4 → v5 dodaje szczegóły momentów i pomocne działania oraz rozszerza okoliczności bez usuwania dotychczasowych zapisów. Instaluj na obecną aplikację, bez odinstalowania. Kopię można wcześniej zapisać w Ustawieniach.

Klucz `signing/local.keystore` jest prywatny i wykluczony z repozytorium. Gdy go nie ma, skrypt tworzy własny klucz deweloperski. Stałe hasło w skrypcie służy lokalnemu budowaniu, nie udostępnia klucza autora. APK z innym kluczem nie zaktualizuje oficjalnej instalacji. Zachowaj swój klucz poza repozytorium.

## Dane i zachowanie

SQLite v5 przechowuje wydarzenia, wdzięczność, małe kroki, listę `done` oraz okoliczności `event_contexts`. Wpisy Zrobione mają trwałe identyfikatory UUID. Okoliczności należą do konkretnej rozmowy; usunięcie rozmowy usuwa jej oznaczenia. Spokojny dzień jest identyfikowany datą, a impulsywna reakcja lub zaostrzona rozmowa ma pierwszeństwo w statusie dnia; napięcie nie blokuje zapisu spokoju. Brak wpisu nie potwierdza spokoju. Licznik mierzy pełne 24 godziny od ostatniej impulsywnej reakcji lub zaostrzonej rozmowy albo pierwszego uruchomienia.

Eksport JSON używa formatu 4; import odczytuje również formaty 1, 2 i 3. Najpierw sprawdza cały plik, do 5 MB, potem zapisuje w transakcji. Import dodaje brakujące wpisy; zachowuje obecne teksty wdzięczności i tego samego wpisu Zrobione. Brakujące oznaczenia rozmów są dodawane. Osobne rozmowy o tej samej godzinie zachowuje przez dopasowanie kolejności. Powtórny import tej samej kopii nie mnoży zapisów. Import wcześniejszego stanu rozmowy po zmianie jej daty/godziny może dodać dawny zapis jako osobne wydarzenie. Na pustej bazie odtwarza też ustawienia przypomnień. Starsze aplikacje nie odczytają formatu 4.

Pauza ma zapisany czas końca w SharedPreferences i alarmie Androida. Zwykłe uruchomienie nie otwiera jej okna; zewnętrzna akcja widżetu lub powiadomienia uruchamia ją świadomie. Powrót do dziennika nie resetuje odliczania. Alarmy i aktualizacje widżetu mogą być opóźniane przez Androida. Po wymuszonym zatrzymaniu trzeba ponownie otworzyć aplikację. Powiadomienia oraz widżet należy osobno skonfigurować na nowym telefonie; trwająca przerwa nie jest kopiowana.

Bez konta, internetu, reklam, analityki, nagrywania rozmów i systemowej kopii danych. Manifest wymaga tylko powiadomień i uruchamiania harmonogramu po restarcie. Odinstalowanie lub wyczyszczenie danych usuwa lokalną bazę; ręczna kopia powinna zostać poza aplikacją.

## Sprawdzenia Androida

Wyłącznie dedykowany, jednorazowy **`emulator-5580`**. Skrypty czyszczą jego dane, a test aktualizacji także odinstalowuje testową aplikację. Nie kieruj ich na prywatny telefon. Pomocniki Python domyślnie używają macOS SDK w `~/Library/Android/sdk`; dla innego systemu dostosuj tę ścieżkę.

```sh
python3 scripts/verify-android.py
python3 scripts/diary-ui-smoke.py
python3 scripts/moment-ui-smoke.py
python3 scripts/verify-upgrade.py 1.0.0
python3 scripts/verify-upgrade.py 1.1.0
python3 scripts/verify-upgrade.py 1.2.0
python3 scripts/verify-upgrade.py 1.3.0
```

Testy obejmują rzeczywistą bazę i akcje powiadomień, wybrany dzień, dane Zrobione i okoliczności, widżet z dwoma obszarami dotyku oraz eksport/import przez systemowe okno plików. Historyczne APK potrzebne do migracji nie są w repozytorium; są dostępne w poprzednich wydaniach GitHub. Stare skrypty UI zachowano jako kod testów poprzednich wersji.

[Raport PL/EN](VALIDATION.md) · [Dowody](validation/v1.4.0/)

SQLite v5: `moment_details` przechowuje rodzaj momentu z zachowaniem dotychczasowych ID wydarzeń, a `event_help` — to, co pomogło. Starszy wpis bez szczegółów oznacza zaostrzoną rozmowę. `event_contexts` obsługuje także `surprise`. Status dnia i licznik pomijają napięcie; zmiana rodzaju wpisu przelicza oba. Kopia w formacie 4 zachowuje rodzaje i pomocne działania. Import dopasowuje rodzaj, czas i kolejność; ponowny import dawnej kopii po zmianie rodzaju lub godziny może dodać wcześniejszy stan jako osobny moment.
