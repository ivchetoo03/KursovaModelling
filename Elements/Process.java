package Elements;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import System.Task;
import Util.Statistics;
import Util.Strategy;
import Util.FunRand;

public class Process extends Element {
    private final Queue<Task> queue = new ArrayDeque<>();
    private final Strategy strategy;
    private final List<Task> processing = new ArrayList<>();
    private final double[] serviceMean = {20.0, 15.0, 15.0};
    private final double[] serviceDev = {1.0, 3.0, 5.0};
    private final int[] arrived = new int[3];
    private final int[] started = new int[3];
    private final int[] completed = new int[3];
    private final int[] waiting = new int[3];
    private final int[] maxQueueByClass = new int[3];
    private final double[] queueArea = new double[3];
    private final double[] queueSquareArea = new double[3];
    private final double[] systemArea = new double[3];
    private double totalQueueArea;
    private double totalQueueSquareArea;
    private double totalSystemArea;
    private double busySlotArea;
    private double idleTime;
    private double fullTime;
    private int maxQueue;

    private final Statistics[] waitStats = new Statistics[3];
    private final Statistics[] serviceStats = new Statistics[3];
    private final Statistics[] systemStats = new Statistics[3];

    public Process() {
        this(Strategy.FIFO);
    }

    public Process(Strategy strategy) {
        super("PROCESSOR");
        this.strategy = strategy;
        for (int i = 0; i < 3; i++) {
            waitStats[i] = new Statistics();
            serviceStats[i] = new Statistics();
            systemStats[i] = new Statistics();
        }
    }

    public void setService(char type, double mean, double dev) {
        int i = type - 'A';
        serviceMean[i] = mean;
        serviceDev[i] = dev;
    }

    @Override
    public void inAct(Task task) {
        int i = task.type - 'A';
        arrived[i]++;
        queue.add(task);
        waiting[i]++;
        startAvailable();
        updateMaxQueue();
    }

    private int busySlots() {
        int result = 0;
        for (Task task : processing) result += task.slots();
        return result;
    }

    private Task chooseNext(int freeSlots) {
        if (queue.isEmpty() || freeSlots == 0) return null;

        if (strategy == Strategy.FIFO) {
            Task first = queue.peek();
            return first.slots() <= freeSlots ? first : null;
        }

        if (strategy == Strategy.PRIORITY_AB) {
            for (Task task : queue) {
                if (task.type != 'C') return task;
            }
            return freeSlots == 2 ? queue.peek() : null;
        }

        for (Task task : queue) {
            if (task.type == 'C') return freeSlots == 2 ? task : null;
        }
        return queue.peek();
    }

    private void startAvailable() {
        int freeSlots = 2 - busySlots();
        while (true) {
            Task task = chooseNext(freeSlots);
            if (task == null) break;
            queue.remove(task);
            int i = task.type - 'A';
            waiting[i]--;
            started[i]++;
            task.startTime = tcurr;
            waitStats[i].add(task.startTime - task.arrivalTime);
            task.finishTime = tcurr + FunRand.Unif(
                    serviceMean[i] - serviceDev[i],
                    serviceMean[i] + serviceDev[i], 3 + i);
            processing.add(task);
            freeSlots -= task.slots();
        }
        updateNextEvent();
    }

    private void updateNextEvent() {
        tnext = Double.MAX_VALUE;
        for (Task task : processing) tnext = Math.min(tnext, task.finishTime);
    }

    private void updateMaxQueue() {
        maxQueue = Math.max(maxQueue, queue.size());
        for (int i = 0; i < 3; i++) {
            maxQueueByClass[i] = Math.max(maxQueueByClass[i], waiting[i]);
        }
    }

    @Override
    public void outAct() {
        Iterator<Task> iterator = processing.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.finishTime <= tcurr + 1e-9) {
                int i = task.type - 'A';
                completed[i]++;
                quantity++;
                serviceStats[i].add(tcurr - task.startTime);
                systemStats[i].add(tcurr - task.arrivalTime);
                iterator.remove();
            }
        }
        startAvailable();
    }

    @Override
    public void doStatistics(double delta) {
        if (delta <= 0) return;

        int[] inService = new int[3];
        for (Task task : processing) inService[task.type - 'A']++;

        for (int i = 0; i < 3; i++) {
            queueArea[i] += waiting[i] * delta;
            queueSquareArea[i] += waiting[i] * (double) waiting[i] * delta;
            systemArea[i] += (waiting[i] + inService[i]) * delta;
        }
        int q = queue.size();
        int slots = busySlots();
        totalQueueArea += q * delta;
        totalQueueSquareArea += q * (double) q * delta;
        totalSystemArea += (q + processing.size()) * delta;
        busySlotArea += slots * delta;
        if (slots == 0) idleTime += delta;
        if (slots == 2) fullTime += delta;
    }

    private static double timeStd(double area, double squareArea, double time) {
        double average = area / time;
        return Math.sqrt(Math.max(0, squareArea / time - average * average));
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.US, "%.5f", v);
    }

    private static void printSamples(String title, Statistics stats) {
        System.out.printf(Locale.US,
                "  %s (n=%d): mean=%s std=%s min=%s max=%s min%n",
                title, stats.getCount(), fmt(stats.getMean()),
                fmt(stats.getStdDev()), fmt(stats.getMin()), fmt(stats.getMax()));
    }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf(Locale.US,
                "state = %d busySlots = %d queue = %d (A=%d, B=%d, C=%d)",
                processing.isEmpty() ? 0 : 1, busySlots(), queue.size(),
                waiting[0], waiting[1], waiting[2]);
    }

    public int getCompleted(char type) {
        return completed[type - 'A'];
    }

    public double getMeanQueue(char type, double time) {
        return queueArea[type - 'A'] / time;
    }

    public double getMeanTotalQueue(double time) {
        return totalQueueArea / time;
    }

    public double getUtilization(double time) {
        return 100.0 * busySlotArea / (2.0 * time);
    }

    public int getMaxQueue() {
        return maxQueue;
    }

    public int getArrived(char type) {
        return arrived[type - 'A'];
    }

    public int getWaiting(char type) {
        return waiting[type - 'A'];
    }

    public int getInService(char type) {
        return started[type - 'A'] - completed[type - 'A'];
    }

    public double getTotalQueueArea() { return totalQueueArea; }
    public double getBusySlotArea() { return busySlotArea; }
    public double getTotalSystemArea() { return totalSystemArea; }
    public double getClassQueueArea(char type) { return queueArea[type - 'A']; }
    public int getQueueSize() { return queue.size(); }

    public void printResult(double time) {

        int received = arrived[0] + arrived[1] + arrived[2];
        System.out.println("queue strategy = " + strategy);
        System.out.printf(Locale.US, "PROCESSOR quantity = %d%n", quantity);
        System.out.printf(Locale.US, "state = %d queue = %d",
                processing.isEmpty() ? 0 : 1, queue.size());
        System.out.printf(Locale.US, "arrived = %d completed = %d in service = %d waiting = %d%n",
                received, quantity, processing.size(), queue.size());

        System.out.printf(Locale.US, "mean length of queue = %.5f%n", totalQueueArea / time);
        System.out.printf(Locale.US, "std length of queue = %.5f%n",
                timeStd(totalQueueArea, totalQueueSquareArea, time));
        System.out.printf(Locale.US, "max length of queue = %d%n", maxQueue);
        System.out.printf(Locale.US, "mean number in system = %.5f%n", totalSystemArea / time);
        System.out.printf(Locale.US, "mean busy slots = %.5f of 2%n", busySlotArea / time);
        System.out.printf(Locale.US, "utilization = %.2f%%%n", 100 * busySlotArea / (2 * time));
        System.out.printf(Locale.US, "busy probability = %.2f%%%n", 100 * (time - idleTime) / time);
        System.out.printf(Locale.US, "idle time = %.2f%% full capacity time = %.2f%%%n",
                100 * idleTime / time, 100 * fullTime / time);
        System.out.printf(Locale.US, "throughput = %.5f tasks/min%n", quantity / time);
        System.out.println("task balance = " + (received == quantity + queue.size() + processing.size() ? "OK" : "ERROR"));

        for (int i = 0; i < 3; i++) {
            System.out.printf(Locale.US, "%nClass %c: arrived = %d started = %d completed = %d waiting = %d in service = %d%n",
                    (char)('A' + i), arrived[i], started[i], completed[i], waiting[i],
                    started[i] - completed[i]);
            System.out.printf(Locale.US, "  mean queue = %.5f std queue = %.5f max queue = %d%n",
                    queueArea[i] / time, timeStd(queueArea[i], queueSquareArea[i], time),
                    maxQueueByClass[i]);
            System.out.printf(Locale.US, "  mean number in system = %.5f%n", systemArea[i] / time);
            printSamples("wait time (started)", waitStats[i]);
            printSamples("service time (completed)", serviceStats[i]);
            printSamples("time in system (completed)", systemStats[i]);
        }
    }
}
