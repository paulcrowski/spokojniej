package pl.spokojniej.app;

import android.content.Context;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Short invitations, never assessments or claims about the user. */
public final class ReflectionPrompts {
    private ReflectionPrompts() {}
    static final String[] QUESTIONS = {
        "Co teraz czujesz w ciele?", "Czy szczęka jest rozluźniona?", "Jak oddychasz w tej chwili?",
        "Czy ramiona mogą opaść odrobinę?", "Co zaprząta Ci teraz głowę?", "Czy potrzebujesz przerwy?",
        "Ile masz dziś energii?", "Co dziś zabrało Ci energię?", "Co dziś dodało Ci energii?",
        "Czy jadłeś coś w ostatnich godzinach?", "Czy chce Ci się pić?", "Czy potrzebujesz chwili ciszy?",
        "Czy robisz teraz za dużo naraz?", "Co możesz odłożyć na później?", "Jaka jest jedna rzecz na teraz?",
        "Czy możesz zwolnić na minutę?", "Co się zmieni, jeśli weźmiesz oddech?", "Czy to dobry moment na odpowiedź?",
        "Możesz wrócić do rozmowy później?", "Czy potrzebujesz wyjść na chwilę?", "Co pomogło Ci już dziś?",
        "Co było dziś choć trochę przyjemne?", "Czy zauważyłeś dziś coś dobrego?", "Za co jesteś dziś wdzięczny?",
        "Co dziś udało Ci się zrobić?", "Czy odpoczynek też może być dziś zrobioną rzeczą?", "Jak minęła ostatnia godzina?",
        "Czy tempo dnia Ci służy?", "Co teraz najbardziej przeszkadza?", "Co teraz najbardziej pomaga?",
        "Czy potrzebujesz ruchu?", "Czy potrzebujesz usiąść?", "Czy jest Ci wygodnie?",
        "Jakie napięcie możesz teraz nazwać?", "Czy chcesz zapisać jedną myśl?", "Czy ta sprawa wymaga reakcji teraz?",
        "Co powiedziałbyś sobie łagodniej?", "Czy możesz poprosić o chwilę?", "Czy możesz zrobić tylko pierwszy krok?",
        "Co możesz dziś sobie odpuścić?", "Czy masz dziś chwilę bez ekranu?", "Co chciałbyś zauważyć wieczorem?",
        "Czy dzisiejszy plan jest jeszcze aktualny?", "Co możesz zrobić wolniej?", "Czy odpoczywałeś choć przez moment?",
        "Czy coś dziś Cię zaskoczyło?", "Czy potrzebujesz wsparcia od kogoś?", "Czy możesz nazwać jedną potrzebę?",
        "Jak chcesz spędzić następne dziesięć minut?", "Co teraz jest wystarczająco dobre?"
    };
    static String forSlot(Context c, LocalDate day, int slot) {
        long seed = Store.prefs(c).getLong("promptSeed", 0);
        if (seed == 0) {
            seed = new java.security.SecureRandom().nextLong();
            Store.prefs(c).edit().putLong("promptSeed", seed).apply();
        }
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<QUESTIONS.length;i++)order.add(i);
        Collections.shuffle(order,new Random(seed ^ day.toEpochDay()*1009L));
        return QUESTIONS[order.get(Math.floorMod(slot,QUESTIONS.length))];
    }
}
