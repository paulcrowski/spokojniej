# Spokojniej

Prosta, prywatna aplikacja Android: chwila przed odpowiedzią, dobrowolna wdzięczność i zapisy małych kroków. Zachowuje też historię rozmów i licznik czasu bez kłótni.

## Instalacja i aktualizacja

Otwórz `dist/Spokojniej-1.2.0.apk` na Androidzie 8 lub nowszym. Android może poprosić o zezwolenie aplikacji Pliki na instalowanie APK. Zainstaluj jako aktualizację na istniejącą aplikację, bez odinstalowywania. Ten sam pakiet i klucz podpisu, wyższy numer wersji (3) oraz migracja SQLite v1/v2 → v3 zachowują dotychczasowe dane. Poprzednie APK pozostają w `dist/`.

Przypomnienia włącz w Ustawieniach i zezwól na powiadomienia Androida. Domyślnie są wyłączone. Poranek: 08:00, przed wieczorem: 18:00. Wcześniej zapisane godziny pozostają zachowane; wybierz godzinę przed swoją trudniejszą porą wieczoru. Możesz włączyć tylko jedno przypomnienie.

Widget dodaj przez przytrzymanie ekranu głównego → Widgety → Spokojniej albo przyciskiem „Dodaj widget” w Ustawieniach, jeśli launcher to obsługuje. Istniejący widget zmienia wygląd po aktualizacji.

## Jak korzystać

- „Potrzebuję chwili”: jedno dotknięcie w aplikacji, widżecie lub powiadomieniu otwiera 10-minutową przerwę z gotowym zdaniem i godziną powrotu. Można przełączyć na 30 sekund. „Zamknij ekran” pozwala odłożyć telefon, zachowując czas końca; „Wracam” kończy przerwę. Przy zgodzie na powiadomienia pojawia się pojedyncze, ciche przypomnienie o powrocie. Android może dostarczyć je później. Przycisk nie oznacza automatycznie udanej rozmowy i niczego nie dopisuje do historii.
- W spokojnym momencie uzgodnij z bliskimi znaczenie zdania „Potrzebuję chwili. Wrócę o…”. Możesz wypowiedzieć je bez telefonu. Wróć do rozmowy, a jeśli potrzebujesz więcej czasu, uprzedź o tym.
- „Za co jestem dziś wdzięczny?”: dobrowolna notatka do 2000 znaków. Jedna rzecz wystarczy; można pominąć dzień. Edycja także z historii; pusty zapis usuwa notatkę.
- „Zapisz mały krok”: zaznacz „Zrobiłem pauzę”, „Powiedziałem swoje spokojnie”, „Wróciłem do rozmowy spokojniej” lub „Było mi trudno”. Kilka wyborów może występować tego samego dnia. „Inny dzień” pozwala uzupełnić zapis później. Odznaczenie usuwa wybrany zapis. Bez punktów i oceny członków rodziny.
- Historia zachowuje dobre i trudne momenty. Zapis kłótni nie usuwa małych kroków ani wdzięczności.
- Zapis dnia i licznik znajdują się niżej: „Dziś spokojnie” raz na dzień, „Zapisz kłótnię” z datą i godziną. Wpis kłótni można poprawić lub usunąć w historii; nowy wpis można cofnąć.

Licznik mierzy pełne 24 godziny od najnowszej zapisanej kłótni, a przed pierwszą kłótnią od pierwszego uruchomienia. Brak wpisu nie potwierdza spokojnego dnia. Kłótnia ma pierwszeństwo w statusie dnia, ale nie zmienia pozytywnych zapisów.

Przypomnienie przed wieczorem pyta, jak się czujesz i czy potrzebujesz odpoczynku, jedzenia lub ciszy. Nie wymaga podsumowania ani wdzięczności. Każda włączona pora pojawia się najwyżej raz na dzień, bez ponagleń. Powiadomienia są wyciszone przez grupowanie; na świeżej instalacji kanał ma niską ważność. Prywatna treść na ekranie blokady zależy też od ustawień Androida. Harmonogram wraca po restarcie, zmianie czasu/strefy i aktualizacji. Po wymuszonym zatrzymaniu należy ponownie otworzyć aplikację.

Widget ma bezpośredni przycisk przerwy; dolny napis otwiera główny ekran. Aktualizuje się po zmianach, przy końcu przerwy, zmianie dnia i okresowo (Android: co najmniej 30 minut). Android może opóźnić alarmy oraz odświeżenie podczas uśpienia. Odliczanie na ekranie używa zapamiętanego czasu końca; nie trzeba patrzeć na telefon.

## Kopia i zmiana telefonu

Ustawienia → „Zapisz kopię” → wybierz miejsce w systemowym oknie Androida. Plik JSON zawiera wydarzenia, wdzięczność, małe kroki i trudne chwile, początek liczenia oraz ustawienia przypomnień. Plik przechowuje zwykły tekst wpisów. Sam wybierasz miejsce; aplikacja niczego automatycznie nie wysyła.

Na nowym telefonie zainstaluj APK, wybierz „Wczytaj kopię”, wskaż plik i potwierdź podgląd. Import dodaje brakujące wpisy i nie usuwa obecnych. Powtórny import nie mnoży wpisów, również osobnych kłótni o identycznym czasie. Jeśli wdzięczność na daną datę już istnieje, zachowuje tekst telefonu i informuje o rozbieżności. Wybory dnia łączy po dacie i rodzaju.

Wersja 1.2 odczytuje także kopie z 1.1. Nowe kopie mają format v2, obejmujący małe kroki. Na telefonie bez żadnych wpisów odtwarza też ustawienia przypomnień. Uprawnienie do powiadomień i widget trzeba ustawić osobno w nowym Androidzie. Trwająca przerwa nie jest przenoszona na drugi telefon. Plik jest sprawdzany w całości przed zapisaniem danych, limit 5 MB.

## Prywatność

Lokalna baza SQLite `spokojniej.db`, bez konta, internetu, reklam, analityki i systemowej kopii danych. Jedynie uprawnienia do powiadomień i uruchomienia po restarcie. Aplikacja jest osobistym narzędziem, nie diagnozuje ani nie leczy ADHD lub lęku.

Odinstalowanie lub wyczyszczenie danych usuwa historię z aplikacji. Zachowaj kopię poza nią. Nie ma automatycznej synchronizacji. Licznik i przerwa zależą od zegara telefonu.

## Budowanie i testy

Projekt natywny Java, bez bibliotek zewnętrznych; można otworzyć w Android Studio. Android SDK 35 i JDK Android Studio:

```sh
bash scripts/build-apk.sh
bash scripts/build-device-tests.sh
```

Inne lokalizacje: `ANDROID_HOME` i `JAVA_HOME`. Skrypt uruchamia 12 sprawdzeń logiki czasu, buduje i podpisuje APK, sprawdza podpis oraz zapisuje sumę. Zachowaj lokalny klucz `signing/local.keystore`, potrzebny do kolejnych aktualizacji; nie publikuj go.

Testy urządzenia i skrypty sprawdzające migracje oraz UI uruchamiaj wyłącznie na dedykowanym emulatorze `emulator-5580`: zawierają czyszczenie jego testowych danych. `scripts/verify-upgrade.py` sprawdza aktualizację poprzednich APK; `scripts/verify-android.py` bazę, kopie, przypomnienia i przerwę; `scripts/support-ui-smoke.py` widżet, rzeczywiste ekrany i plik kopii. Stare skrypty UI dotyczą poprzednich wersji.

Dowody i ograniczenia: [raport](VALIDATION.md) oraz [zapisane wyniki](validation/v1.2.0/). Zasady [alarmów Androida](https://developer.android.com/develop/background-work/services/alarms) i [wyciszania powiadomień w grupie](https://developer.android.com/reference/android/app/Notification.Builder#setGroupAlertBehavior(int)).

Historyczne APK 1.0 i 1.1 potrzebne do skryptu migracji nie są częścią publicznego repozytorium. Wyniki migracji zachowano w dokumentacji. Własny klucz z nowego klonu tworzy osobno podpisane APK; nie zastępuje klucza autora i nie umożliwia aktualizacji oficjalnego APK z zachowaniem instalacji.
