package Util;

public class Statistics {
    private int count;
    private double mean;
    private double m2;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public void add(double value) {
        count++;
        double delta = value - mean;
        mean += delta / count;
        m2 += delta * (value - mean);
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    public int getCount() { return count; }
    public double getMean() { return count == 0 ? Double.NaN : mean; }
    public double getStdDev() { return count < 2 ? Double.NaN : Math.sqrt(m2 / (count - 1)); }
    public double getMin() { return count == 0 ? Double.NaN : min; }
    public double getMax() { return count == 0 ? Double.NaN : max; }
}
