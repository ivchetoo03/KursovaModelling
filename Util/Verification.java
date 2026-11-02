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
        double totalQueue, utilization;
        int maxQueue;

        Result(Scenario scenario, Process process, double time) {
            this.scenario = scenario;
            for (int i = 0; i < 3; i++) {
                char type = (char)('A' + i);
                completed[i] = process.getCompleted(type);
                meanQueue[i] = process.getMeanQueue(type, time);
            }
            totalQueue = process.getMeanTotalQueue(time);
            utilization = process.getUtilization(time);
            maxQueue = process.getMaxQueue();
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
        Process process = new Process(strategy);
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            char type = (char)('A' + i);
            Create create = new Create(type, s.parameters.arrivalMean[i], s.parameters.arrivalDev[i]);
            create.setNextElement(process);
            process.setService(type, s.parameters.serviceMean[i], s.parameters.serviceDev[i]);
            elements.add(create);
        }
        elements.add(process);
        new Model(elements).simulate(time, false);
        return new Result(s, process, time);
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
                "Avg queue", "A", "B", "C",
                "Load%", "Max queue"
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
            csv.append(';').append(number(r.totalQueue));
            for (double q : r.meanQueue) csv.append(';').append(number(q));
            csv.append(';').append(number(r.utilization));
            csv.append(';').append(r.maxQueue).append('\n');
        }
        return csv.toString();
    }

    private static String number(double value) {
        return String.format(Locale.GERMANY, "%.2f", value);
    }

    private static void saveCsv(Strategy strategy, String csv) {
        try {
            Files.writeString(Path.of(  "verification_" + strategy + ".csv"),
                                        "\uFEFF" + csv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save", e);
        }
    }
}
