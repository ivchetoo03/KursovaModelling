package System;

public class Task {
    public final char type;
    public final double arrivalTime;
    public double startTime;
    public double finishTime;
    public boolean secondPart;
    public int bypasses;

    public Task(char type, double arrivalTime) {
        this.type = type;
        this.arrivalTime = arrivalTime;
    }
}
