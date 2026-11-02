package Util;

import Elements.Create;
import Elements.Element;
import Elements.Process;
import System.Model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Experiment {
    private static final int N = 80;
    private static final long FIRST_SEED = 42L;
    private static final long SEED_STEP = 10007L;
    private static final Strategy[] STRATEGIES = Strategy.values();
    private static final String[] HEADERS = {"scenario", "strategy", "rep", "seed",
            "warmup_min", "end_min", "observed_min", "avg_queue_tasks", "avg_A_tasks",
            "avg_B_tasks", "avg_C_tasks", "load_percent", "throughput_tasks_per_min", "completed_tasks", "completed_A", "completed_B", "completed_C"};

    private record Result(String scenario, Strategy strategy, int rep, long seed,
                          double warmup, double end, double avgQueue, double qA, double qB,
                          double qC, double load, double throughput, int completed, int completedA, int completedB, int completedC) { }

    private static Parameters inputs(String scenario) {
        Parameters p = new Parameters();
        if (scenario.equals("REDUCED")) {
            p.arrivalMean[0] = 40;
            p.arrivalMean[1] = 40;
            p.arrivalMean[2] = 60;
        }
        return p;
    }

    private static final class Run {
        final Model model;
        final Process processor;
        double lastTime = 0;
        Run(Parameters p, Strategy strategy, long seed) {
            FunRand.reset(seed);
            processor = new Process(strategy);
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                char type = (char)('A' + i);
                Create generator = new Create(type, p.arrivalMean[i], p.arrivalDev[i]);
                generator.setNextElement(processor);
                processor.setService(type, p.serviceMean[i], p.serviceDev[i]);
                elements.add(generator);
            }
            elements.add(processor);
            model = new Model(elements);
        }
        void advance(double time) {
            if (time < lastTime) throw new IllegalArgumentException("Non-monotonic model time");
            model.simulate(time, false);
            lastTime = time;
        }
        Snapshot snapshot() {
            double[] areas = new double[3];
            int completed = 0;
            int[] classCompleted = new int[3];
            for (int i = 0; i < 3; i++) {
                char type = (char)('A' + i);
                areas[i] = processor.getClassQueueArea(type);
                classCompleted[i] = processor.getCompleted(type);
                completed += classCompleted[i];
            }
            return new Snapshot(processor.getTotalQueueArea(), processor.getBusySlotArea(), areas,
                    completed, classCompleted, processor.getQueueSize());
        }
    }

    private record Snapshot(double queueArea, double busyArea, double[] classAreas,
                            int completed, int[] classCompleted, int currentQueue) { }

    private static Result replicate(String scenario, Strategy strategy, int index,
                                    double warmup, double horizon) {
        long seed = FIRST_SEED + SEED_STEP * index;
        Run run = new Run(inputs(scenario), strategy, seed);
        if (warmup > 0) run.advance(warmup);
        Snapshot before = run.snapshot();
        run.advance(horizon);
        Snapshot after = run.snapshot();
        double duration = horizon - warmup;
        double[] meanClass = new double[3];
        for (int i = 0; i < 3; i++)
            meanClass[i] = (after.classAreas[i] - before.classAreas[i]) / duration;
        return new Result(scenario, strategy, index + 1, seed, warmup, horizon,
                (after.queueArea - before.queueArea) / duration,
                meanClass[0], meanClass[1], meanClass[2],
                100 * (after.busyArea - before.busyArea) / (2.0 * duration),
                (after.completed - before.completed) / duration,
                after.completed - before.completed,
                after.classCompleted[0] - before.classCompleted[0],
                after.classCompleted[1] - before.classCompleted[1],
                after.classCompleted[2] - before.classCompleted[2]);
    }

    private static String f(double v) { return String.format(Locale.US, "%.6f", v); }
    private static String join(String... fields) { return String.join(";", fields) + "\n"; }

    private static void write(Path file, StringBuilder contents) throws IOException {
        Files.writeString(file, "\uFEFF" + contents, StandardCharsets.UTF_8);
        System.out.println("Saved " + file);
    }

    private static double average(double[] x) {
        return Arrays.stream(x).average().orElse(Double.NaN);
    }
    private static double sd(double[] x) {
        double mean = average(x), ss = 0;
        for (double v : x) ss += (v - mean) * (v - mean);
        return Math.sqrt(ss / (x.length - 1));
    }
    private static double t95(int n) {
        double z = 1.95996398454005, df = n - 1.0;
        return z + (Math.pow(z, 3) + z)/(4*df)
                 + (5*Math.pow(z, 5) + 16*Math.pow(z, 3) + 3*z)/(96*df*df);
    }

    private static void pilot(Path dir) throws IOException {
        double[] times = {1000, 3000, 5000, 10000, 20000, 30000};
        StringBuilder csv = new StringBuilder("scenario;strategy;rep;time_min;cum_avg_queue_tasks;current_queue_tasks;cum_load_percent\n");
        for (String scenario : new String[]{"BASE", "REDUCED"}) {
            for (Strategy strategy : STRATEGIES) {
                for (int i = 0; i < 4; i++) {
                    Run run = new Run(inputs(scenario), strategy, FIRST_SEED + SEED_STEP * i);
                    for (double time : times) {
                        run.advance(time);
                        Snapshot s = run.snapshot();
                        csv.append(join(scenario, strategy.name(), Integer.toString(i+1), f(time),
                                f(s.queueArea/time), Integer.toString(s.currentQueue),
                                f(100*s.busyArea/(2*time))));
                    }
                }
            }
        }
        write(dir.resolve("pilot.csv"), csv);
    }

    private static void mainExperiment(Path dir) throws IOException {
        StringBuilder data = new StringBuilder(String.join(";", HEADERS) + "\n");
        StringBuilder summary = new StringBuilder("scenario;strategy;warmup_min;end_min;n;queue_mean_tasks;queue_sd_tasks;queue_CI95_halfwidth_tasks;load_mean_pct;throughput_mean_tasks_per_min;queue_A_mean;queue_B_mean;queue_C_mean;completed_A_mean;completed_B_mean;completed_C_mean\n");
        StringBuilder pairs = new StringBuilder("scenario;comparison;warmup_min;end_min;n;mean_queue_difference_vs_FIFO_tasks;difference_sd_tasks;difference_CI95_lower_tasks;difference_CI95_upper_tasks\n");

        for (String scenario : new String[]{"BASE", "REDUCED"}) {
            double warmup = scenario.equals("REDUCED") ? 5000 : 0;
            double end = scenario.equals("REDUCED") ? 30000 : 5000;
            double[][] byStrategy = new double[STRATEGIES.length][N];
            for (int s = 0; s < STRATEGIES.length; s++) {
                Result[] results = new Result[N];
                double[] q = new double[N], u = new double[N], throughput = new double[N];
                double[] qA = new double[N], qB = new double[N], qC = new double[N];
                double[] doneA = new double[N], doneB = new double[N], doneC = new double[N];
                for (int j = 0; j < N; j++) {
                    Result r = replicate(scenario, STRATEGIES[s], j, warmup, end);
                    results[j] = r;
                    q[j] = r.avgQueue;
                    u[j] = r.load;
                    throughput[j] = r.throughput;
                    qA[j] = r.qA; qB[j] = r.qB; qC[j] = r.qC;
                    doneA[j] = r.completedA; doneB[j] = r.completedB; doneC[j] = r.completedC;
                    data.append(join(r.scenario, r.strategy.name(), Integer.toString(r.rep),
                            Long.toString(r.seed), f(r.warmup), f(r.end), f(r.end-r.warmup),
                            f(r.avgQueue), f(r.qA), f(r.qB), f(r.qC), f(r.load),
                            f(r.throughput), Integer.toString(r.completed),
                            Integer.toString(r.completedA), Integer.toString(r.completedB),
                            Integer.toString(r.completedC)));
                }
                byStrategy[s] = q;
                summary.append(join(scenario, STRATEGIES[s].name(), f(warmup), f(end),
                        Integer.toString(N), f(average(q)), f(sd(q)),
                        f(t95(N)*sd(q)/Math.sqrt(N)), f(average(u)), f(average(throughput)),
                        f(average(qA)), f(average(qB)), f(average(qC)),
                        f(average(doneA)), f(average(doneB)), f(average(doneC))));
            }
            for (int s = 1; s < STRATEGIES.length; s++) {
                double[] diff = new double[N];
                for (int i = 0; i < N; i++) diff[i] = byStrategy[s][i] - byStrategy[0][i];
                double m = average(diff), halfWidth = t95(N)*sd(diff)/Math.sqrt(N);
                pairs.append(join(scenario, STRATEGIES[s].name() + " - FIFO", f(warmup), f(end),
                        Integer.toString(N), f(m), f(sd(diff)), f(m-halfWidth), f(m+halfWidth)));
            }
        }
        write(dir.resolve("replications.csv"), data);
        write(dir.resolve("summary.csv"), summary);
        write(dir.resolve("paired_comparisons.csv"), pairs);
    }

    public static void main(String[] args) throws IOException {
        Path destination = args.length == 0 ? Path.of("results") : Path.of(args[0]);
        Files.createDirectories(destination);
        pilot(destination);
        mainExperiment(destination);
    }
}
