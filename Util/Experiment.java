package Util;

import Elements.Create;
import Elements.Element;
import Elements.Process;
import System.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Experiment {
    private static final int N = 80;
    private static final long FIRST_SEED = 42;
    private static final long SEED_STEP = 10007;

    private static class Run {
        final Model model;
        final Process d0;
        final Process processor;

        Run(Parameters parameters, Strategy strategy, long seed) {
            FunRand.reset(seed);
            d0 = new Process("D0", 1, strategy);
            processor = new Process("PROCESSOR", 2, strategy);
            d0.setNextElement(processor);
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                char type = (char) ('A' + i);
                Create create = new Create(type, parameters.arrivalMean[i], parameters.arrivalDev[i]);
                create.setNextElement(d0);
                processor.setService(type, parameters.serviceMean[i], parameters.serviceDev[i]);
                elements.add(create);
            }
            elements.add(d0);
            elements.add(processor);
            model = new Model(elements);
        }

        void advance(double time) {
            model.simulate(time, false);
        }

        double[] snapshot() {
            return new double[] {
                d0.getTotalQueueArea(), d0.getWaitingArea(),
                d0.getClassQueueArea('A'), d0.getClassQueueArea('B'), d0.getClassQueueArea('C'),
                d0.getBlockedArea(), processor.getBusySlotArea(), processor.getIdleWithQueueArea(),
                processor.getCompleted('A'), processor.getCompleted('B'), processor.getCompleted('C'),
                d0.getClassWaitingArea('A'), d0.getClassWaitingArea('B'), d0.getClassWaitingArea('C')
            };
        }
    }

    private static Parameters inputs(String scenario) {
        Parameters parameters = new Parameters();
        if (scenario.equals("REDUCED")) {
            parameters.arrivalMean[0] = 40;
            parameters.arrivalMean[1] = 40;
            parameters.arrivalMean[2] = 60;
        }
        return parameters;
    }

    private static void write(Path file, StringBuilder contents) throws IOException {
        Files.writeString(file, "\uFEFF" + contents);
        System.out.println("Saved " + file);
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("load")) {
            Files.createDirectories(Path.of("results"));
            StringBuilder data = new StringBuilder("offered_load_percent;strategy;rep;actual_load_percent;idle_with_queue_percent;avg_waiting_tasks\n");
            for (double factor : new double[]{2.0, 1.75, 1.5, 1.25, 1.0}) {
                Parameters parameters = new Parameters();
                for (int i = 0; i < 3; i++) parameters.arrivalMean[i] *= factor;
                double offered = 50 * (parameters.serviceMean[0] / parameters.arrivalMean[0]
                        + parameters.serviceMean[1] / parameters.arrivalMean[1]
                        + 2 * parameters.serviceMean[2] / parameters.arrivalMean[2]);
                for (Strategy strategy : Strategy.values()) {
                    for (int rep = 0; rep < 20; rep++) {
                        Run run = new Run(parameters, strategy, FIRST_SEED + SEED_STEP * rep);
                        run.advance(5000);
                        double[] before = run.snapshot();
                        run.advance(30000);
                        double[] after = run.snapshot();
                        data.append(offered).append(';').append(strategy).append(';').append(rep + 1)
                                .append(';').append(100 * (after[6] - before[6]) / 50000)
                                .append(';').append(100 * (after[7] - before[7]) / 25000)
                                .append(';').append((after[1] - before[1]) / 25000).append('\n');
                    }
                }
            }
            write(Path.of("results/load_study.csv"), data);
            return;
        }
        Path dir = Path.of("results");
        Files.createDirectories(dir);
        double[] times = {1000, 3000, 5000, 10000, 20000, 30000};
        StringBuilder pilot = new StringBuilder("scenario;strategy;rep;time_min;cum_avg_queue_tasks;cum_avg_waiting_tasks;current_queue_tasks;cum_load_percent\n");
        StringBuilder data = new StringBuilder("scenario;strategy;rep;seed;warmup_min;end_min;observed_min;avg_queue_tasks;avg_waiting_tasks;avg_A_tasks;avg_B_tasks;avg_C_tasks;avg_wait_A;avg_wait_B;avg_wait_C;blocked_D0_pct;load_percent;idle_with_queue_pct;throughput_tasks_per_min;completed_tasks;completed_A;completed_B;completed_C\n");

        for (String scenario : new String[]{"BASE", "REDUCED"}) {
            Parameters parameters = inputs(scenario);
            double warmup = scenario.equals("REDUCED") ? 5000 : 0;
            double end = scenario.equals("REDUCED") ? 30000 : 5000;
            for (Strategy strategy : Strategy.values()) {
                for (int rep = 0; rep < 4; rep++) {
                    Run run = new Run(parameters, strategy, FIRST_SEED + SEED_STEP * rep);
                    for (double time : times) {
                        run.advance(time);
                        double[] s = run.snapshot();
                        pilot.append(String.format("%s;%s;%d;%.0f;%.6f;%.6f;%d;%.6f%n",
                                scenario, strategy, rep + 1, time, s[0] / time, s[1] / time,
                                run.d0.getQueueSize(), 100 * s[6] / (2 * time)));
                    }
                }
                for (int rep = 0; rep < N; rep++) {
                    long seed = FIRST_SEED + SEED_STEP * rep;
                    Run run = new Run(parameters, strategy, seed);
                    if (warmup > 0) run.advance(warmup);
                    double[] before = run.snapshot();
                    run.advance(end);
                    double[] after = run.snapshot();
                    double duration = end - warmup;
                    double[] difference = new double[after.length];
                    for (int i = 0; i < difference.length; i++) difference[i] = after[i] - before[i];
                    double completed = difference[8] + difference[9] + difference[10];
                    data.append(String.format(
                            "%s;%s;%d;%d;%.0f;%.0f;%.0f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f;%.0f;%.0f;%.0f;%.0f%n",
                            scenario, strategy, rep + 1, seed, warmup, end, duration,
                            difference[0] / duration, difference[1] / duration,
                            difference[2] / duration, difference[3] / duration, difference[4] / duration,
                            difference[11] / duration, difference[12] / duration, difference[13] / duration,
                            100 * difference[5] / duration, 100 * difference[6] / (2 * duration),
                            100 * difference[7] / duration, completed / duration, completed,
                            difference[8], difference[9], difference[10]));
                }
            }
        }
        write(dir.resolve("pilot.csv"), pilot);
        write(dir.resolve("replications.csv"), data);
    }
}
