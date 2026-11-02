package Main;
import java.util.Arrays;
import java.util.Locale;

import Elements.Create;
import Elements.Process;
import System.Model;
import Util.Strategy;

public class Main {
    public static void main(String[] args) {
        double time = 1000;
        Strategy strat = Strategy.PRIORITY_C;
        boolean printEvents = true;

        Create a = new Create('A', 20, 5);
        Create b = new Create('B', 20, 10);
        Create c = new Create('C', 30, 10);
        Process p = new Process(strat);

        a.setNextElement(p);
        b.setNextElement(p);
        c.setNextElement(p);

        Model model = new Model(Arrays.asList(a, b, c, p));
        model.simulate(time, printEvents);

        System.out.println("\n------------ RESULTS ------------");
        System.out.printf(Locale.US, "modeling time = %.2f min%n", time);
        a.printInfo();
        b.printInfo();
        c.printInfo();
        p.printResult(time);
    }
}
