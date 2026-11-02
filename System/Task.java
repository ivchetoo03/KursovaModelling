package System;

public class Task {
    public final char type;
    public final double arrivalTime;
    public double startTime;
    public double finishTime;

    public Task(char type, double arrivalTime) {
        this.type = type;
        this.arrivalTime = arrivalTime;
    }

    public int slots() {
        return type == 'C' ? 2 : 1;
    }
}
