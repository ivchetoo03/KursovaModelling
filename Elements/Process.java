package Elements;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;

import System.Task;
import Util.FunRand;
import Util.Strategy;

public class Process extends Element {
    private final Deque<Task> queue = new ArrayDeque<>();
    private final Task[] devices;
    private final Strategy strategy;
    private Process previous;
    private Task d0;
    private PrintWriter queueTrace;
    private PrintWriter taskTrace;
    private final double[] serviceMean = {20, 15, 15};
    private final double[] serviceDev = {1, 3, 5};
    private final int[] arrived = new int[3];
    private final int[] completed = new int[3];
    private final int[] waiting = new int[3];
    private final double[] queueArea = new double[3];
    private final double[] waitingAreaByClass = new double[3];
    private double totalQueueArea;
    private double waitingArea;
    private double blockedArea;
    private double busyArea;
    private double idleWithQueueArea;
    private int maxQueue;

    public Process(String name, int channels, Strategy strategy) {
        super(name);
        devices = new Task[channels];
        this.strategy = strategy;
    }

    @Override
    public void setNextElement(Element element) {
        super.setNextElement(element);
        ((Process) element).previous = this;
    }

    public void setService(char type, double mean, double dev) {
        int i = type - 'A';
        serviceMean[i] = mean;
        serviceDev[i] = dev;
    }

    public void setQueueTrace(PrintWriter writer) {
        queueTrace = writer;
        writer.println("time_min;duration_min;queue;blocked_D0");
    }

    public void setTaskTrace(PrintWriter writer) {
        taskTrace = writer;
        writer.println("type;arrival_min;start_min;finish_min;wait_min;service_min;system_min");
    }

    @Override
    public void inAct(Task task) {
        if (nextElement != null) {
            arrived[task.type - 'A']++;
            waiting[task.type - 'A']++;
            queue.addLast(task);
            maxQueue = Math.max(maxQueue, queue.size());
            return;
        }
        int i = task.type - 'A';
        task.startTime = tcurr;
        task.finishTime = tcurr + FunRand.Unif(
                serviceMean[i] - serviceDev[i], serviceMean[i] + serviceDev[i], 3 + i);
        if (task.type == 'C') {
            Task second = new Task('C', task.arrivalTime);
            second.startTime = task.startTime;
            second.finishTime = task.finishTime;
            second.secondPart = true;
            devices[0] = task;
            devices[1] = second;
        } else {
            for (int j = 0; j < devices.length; j++) {
                if (devices[j] == null) {
                    devices[j] = task;
                    break;
                }
            }
        }
        updateNextEvent();
    }

    private boolean blockedA(Process target) {
        return target.devices[0] != null && target.devices[1] != null;
    }

    private boolean blockedB(Process target) {
        return target.devices[0] != null && target.devices[1] != null;
    }

    private boolean blockedC(Process target) {
        return target.devices[0] != null || target.devices[1] != null;
    }

    private boolean routeBlocked(Task task, Process target) {
        switch (task.type) {
            case 'A': return blockedA(target);
            case 'B': return blockedB(target);
            case 'C': return blockedC(target);
            default: throw new IllegalArgumentException();
        }
    }

    private Task chooseNext(Process target) {
        if (queue.isEmpty()) return null;
        Task first = queue.peekFirst();
        if (strategy == Strategy.FIFO) return first;
        if (strategy == Strategy.FAIR_RESERVE && tcurr - first.arrivalTime >= 30) return first;
        if (strategy == Strategy.BOUNDED_BYPASS && first.bypasses >= 3) return first;
        for (Task task : queue) {
            if (!routeBlocked(task, target)) {
                if (task != first && strategy == Strategy.BOUNDED_BYPASS) first.bypasses++;
                return task;
            }
        }
        return null;
    }

    public void updateRoutes() {
        if (nextElement == null) return;
        Process target = (Process) nextElement;
        while (true) {
            if (d0 != null) {
                if (routeBlocked(d0, target)) return;
                Task task = d0;
                d0 = null;
                waiting[task.type - 'A']--;
                target.inAct(task);
            }
            Task next = chooseNext(target);
            if (next == null) return;
            queue.remove(next);
            d0 = next;
        }
    }

    private void updateNextEvent() {
        tnext = Double.MAX_VALUE;
        for (Task task : devices) {
            if (task != null) tnext = Math.min(tnext, task.finishTime);
        }
    }

    @Override
    public void outAct() {
        for (int i = 0; i < devices.length; i++) {
            Task task = devices[i];
            if (task != null && task.finishTime <= tcurr + 1e-9) {
                devices[i] = null;
                if (!task.secondPart) {
                    completed[task.type - 'A']++;
                    quantity++;
                    if (taskTrace != null) {
                        taskTrace.printf("%c;%.6f;%.6f;%.6f;%.6f;%.6f;%.6f%n",
                                task.type, task.arrivalTime, task.startTime, tcurr,
                                task.startTime - task.arrivalTime, tcurr - task.startTime,
                                tcurr - task.arrivalTime);
                    }
                }
            }
        }
        updateNextEvent();
    }

    @Override
    public void doStatistics(double delta) {
        if (delta <= 0) return;
        if (nextElement != null) {
            totalQueueArea += queue.size() * delta;
            waitingArea += (queue.size() + (d0 == null ? 0 : 1)) * delta;
            if (d0 != null) blockedArea += delta;
            for (int i = 0; i < 3; i++) {
                int blocked = d0 != null && d0.type - 'A' == i ? 1 : 0;
                queueArea[i] += (waiting[i] - blocked) * delta;
                waitingAreaByClass[i] += waiting[i] * delta;
            }
            if (queueTrace != null) {
                queueTrace.printf("%.6f;%.6f;%d;%d%n",
                        tcurr, delta, queue.size(), d0 == null ? 0 : 1);
            }
        } else {
            int busy = 0;
            for (Task task : devices) if (task != null) busy++;
            busyArea += busy * delta;
            if (busy == 1 && previous != null && previous.hasWaiting()) idleWithQueueArea += delta;
        }
    }

    private boolean hasWaiting() {
        return d0 != null || !queue.isEmpty();
    }

    public int getCompleted(char type) { return completed[type - 'A']; }
    public int getArrived(char type) { return arrived[type - 'A']; }
    public int getWaiting(char type) { return waiting[type - 'A']; }
    public int getInService(char type) {
        int result = 0;
        for (Task task : devices) if (task != null && !task.secondPart && task.type == type) result++;
        return result;
    }
    public double getMeanQueue(char type, double time) { return queueArea[type - 'A'] / time; }
    public double getMeanTotalQueue(double time) { return totalQueueArea / time; }
    public double getMeanWaiting(double time) { return waitingArea / time; }
    public double getBlockedArea() { return blockedArea; }
    public double getUtilization(double time) { return 100 * busyArea / (devices.length * time); }
    public int getMaxQueue() { return maxQueue; }
    public double getTotalQueueArea() { return totalQueueArea; }
    public double getWaitingArea() { return waitingArea; }
    public double getBusySlotArea() { return busyArea; }
    public double getIdleWithQueueArea() { return idleWithQueueArea; }
    public double getClassQueueArea(char type) { return queueArea[type - 'A']; }
    public double getClassWaitingArea(char type) { return waitingAreaByClass[type - 'A']; }
    public int getQueueSize() { return queue.size(); }

    @Override
    public void printInfo() {
        int busy = 0;
        for (Task task : devices) if (task != null) busy++;
        System.out.printf("%s: queue=%d, busy=%d, blocked=%b%n",
                name, queue.size(), busy, d0 != null);
    }

    public void printResult(double time) {
        Process source = previous == null ? this : previous;
        Process server = nextElement == null ? this : (Process) nextElement;
        int received = source.arrived[0] + source.arrived[1] + source.arrived[2];
        int pending = source.queue.size() + (source.d0 == null ? 0 : 1);
        int active = 0;
        for (Task task : server.devices) if (task != null && !task.secondPart) active++;
        System.out.println("Strategy - " + strategy);
        System.out.printf("\nArrived -\t%d\nCompleted -\t%d\nWaiting -\t%d\nProcessing -\t%d\n",
                received, server.quantity, pending, active);
        System.out.printf("\nMean Q - %.5f,\nMean waiting +D0 -  %.5f\n",
                source.totalQueueArea / time, source.waitingArea / time);
        System.out.printf("\nUtilization - %.2f%%\nBlocked D0 - %.2f%%\nIdle device with waiting - %.2f%%\n",
                server.getUtilization(time), 100 * source.blockedArea / time,
                100 * server.idleWithQueueArea / time);
        System.out.println("\nTask balance - " + (received == server.quantity + pending + active ? "OK" : "LOST"));
        for (int i = 0; i < 3; i++) {
            char type = (char) ('A' + i);
            System.out.printf("%c: arr - %d, com - %d, mean q - %.5f\n", type, source.arrived[i], server.completed[i], source.getMeanQueue(type, time));
        }
    }
}
