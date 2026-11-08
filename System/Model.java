package System;
import java.util.ArrayList;
import java.util.List;

import Elements.Create;
import Elements.Element;
import Elements.Process;

public class Model {
    private final List<Element> elements;
    private double tcurr = 0.0;

    public Model(List<Element> elements) {
        this.elements = elements;
    }

    public void simulate(double time, boolean printEvents) {
        while (true) {
            double tnext = Double.MAX_VALUE;
            for (Element element : elements) {
                tnext = Math.min(tnext, element.getTnext());
            }
            if (tnext > time) break;

            for (Element element : elements) element.doStatistics(tnext - tcurr);
            tcurr = tnext;
            for (Element element : elements) element.setTcurr(tcurr);

            List<String> events = new ArrayList<>();
            for (Element element : elements) {
                if (element instanceof Process && element.getTnext() <= tcurr + 1e-9) {
                    element.outAct();
                    events.add(element.getName());
                }
            }
            for (Element element : elements) {
                if (element instanceof Create && element.getTnext() <= tcurr + 1e-9) {
                    element.outAct();
                    events.add(element.getName());
                }
            }
            for (Element element : elements) {
                if (element instanceof Process) ((Process) element).updateRoutes();
            }

            if (printEvents) {
                System.out.printf("\n--- t - %.5f, %s\n", tcurr, String.join(", ", events));
                printInfo();
            }
        }
        for (Element element : elements) element.doStatistics(time - tcurr);
        tcurr = time;
        for (Element element : elements) element.setTcurr(tcurr);
    }

    public void printInfo() {
        for (Element element : elements) element.printInfo();
    }
}
