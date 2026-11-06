package Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import Elements.Create;
import Elements.Element;
import Elements.Process;
import System.Model;

public class Verification {
    private static class Scenario {
        final Parameters parameters;
        Scenario(String changed, Parameters parameters) {
            this.parameters = parameters;
        }
    }

    private static class Result {
        final Scenario scenario;
        final int[] completed = new int[3];
        final double[] meanQueue = new double[3];
        double totalQueue, totalWaiting, utilization;
        int maxQueue;

        Result(Scenario scenario, Process d0, Process processor, double time) {
            this.scenario = scenario;
            for (int i = 0; i < 3; i++) {
                char type = (char)('A' + i);
                completed[i] = processor.getCompleted(type);
                meanQueue[i] = d0.getMeanQueue(type, time);
            }
            totalQueue = d0.getMeanTotalQueue(time);
            totalWaiting = d0.getMeanWaiting(time);
            utilization = processor.getUtilization(time);
            maxQueue = d0.getMaxQueue();
        }
    }

    private static void change(List<Scenario> list, String name, Parameters baseline, Consumer<Parameters> edit) {
        Parameters updated = baseline.copy();
        edit.accept(updated);
        list.add(new Scenario(name, updated));
    }

    private static List<Scenario> scenarios() {
        Parameters baseline = new Parameters();
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("BASE", baseline));

        change(list, "Cr A mean", baseline, p -> p.arrivalMean[0] = 40);
        change(list, "Cr A dev",  baseline, p -> p.arrivalDev[0] =  10);
        change(list, "Cr B mean", baseline, p -> p.arrivalMean[1] = 40);
        change(list, "Cr B dev",  baseline, p -> p.arrivalDev[1] =  5 );
        change(list, "Cr C mean", baseline, p -> p.arrivalMean[2] = 60);
        change(list, "Cr C dev",  baseline, p -> p.arrivalDev[2] =  5 );
        change(list, "Pr A mean",baseline, p -> p.serviceMean[0] = 15);
        change(list, "Pr A dev",  baseline, p -> p.serviceDev[0] = 2 );
        change(list, "Pr B mean", baseline, p -> p.serviceMean[1] =12);
        change(list, "Pr B dev",  baseline, p -> p.serviceDev[1] = 1 );
        change(list, "Pr C mean", baseline, p -> p.serviceMean[2] =10);
        change(list, "Pr C dev",  baseline, p -> p.serviceDev[2] = 3 );
        return list;
    }

    private static Result simulate(Scenario s, Strategy strategy, double time, long seed) {
        FunRand.reset(seed);
        Process d0 = new Process("D0", 1, strategy);
        Process processor = new Process("PROCESSOR", 2, strategy);
        d0.setNextElement(processor);
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            char type = (char)('A' + i);
            Create create = new Create(type, s.parameters.arrivalMean[i], s.parameters.arrivalDev[i]);
            create.setNextElement(d0);
            processor.setService(type, s.parameters.serviceMean[i], s.parameters.serviceDev[i]);
            elements.add(create);
        }
        elements.add(d0);
        elements.add(processor);
        new Model(elements).simulate(time, false);
        return new Result(s, d0, processor, time);
    }

    public static void run(Strategy strategy, double time, long seed) {
        List<Result> results = new ArrayList<>();
        for (Scenario scenario : scenarios()) {
            results.add(simulate(scenario, strategy, time, seed));
        }
        String csv = toCsv(results);
        System.out.print(csv);
        saveCsv(strategy, csv);
    }

    private static String toCsv(List<Result> results) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(";", new String[] {
                "Run",
                "A_CR", "A_CR_D",
                "B_CR", "B_CR_D",
                "C_CR", "C_CR_D",
                "A_PR", "A_PR_D",
                "B_PR", "B_PR_D",
                "C_PR", "C_PR_D",
                "Finished", "A", "B", "C",
                "Avg waiting", "Avg queue", "A", "B", "C",
                "Max queue", "Load%" 
        })).append('\n');

        int number = 1;
        for (Result r : results) {
            Parameters p = r.scenario.parameters;
            csv.append(number++);
            for (int i = 0; i < 3; i++) {
                csv.append(';').append(number(p.arrivalMean[i]));
                csv.append(';').append(number(p.arrivalDev[i]));
            }
            for (int i = 0; i < 3; i++) {
                csv.append(';').append(number(p.serviceMean[i]));
                csv.append(';').append(number(p.serviceDev[i]));
            }
            int totalDone = r.completed[0] + r.completed[1] + r.completed[2];
            csv.append(';').append(totalDone);
            for (int count : r.completed) csv.append(';').append(count);
            csv.append(';').append(number(r.totalWaiting));
            csv.append(';').append(number(r.totalQueue));
            for (double q : r.meanQueue) csv.append(';').append(number(q));
            csv.append(';').append(r.maxQueue);
            csv.append(';').append(number(r.utilization)).append('\n');
        }
        return csv.toString();
    }

    private static String number(double value) {
        return String.format(Locale.GERMANY, "%.2f", value);
    }

    private static void saveCsv(Strategy strategy, String csv) {
        try {
            Files.writeString(Path.of(  "results/verification_" + strategy + ".csv"),
                                        "\uFEFF" + csv, StandardCharsets.UTF_8);
        } catch (IOException e) {}
    }
}
