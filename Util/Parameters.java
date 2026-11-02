package Util;
import java.util.Locale;

public class Parameters {
    final double[] arrivalMean = {20, 20, 30};
    final double[] arrivalDev  = {5, 10, 10};
    final double[] serviceMean = {20, 15, 15};
    final double[] serviceDev  = {1, 3, 5};

    public Parameters copy() {
        Parameters copy = new Parameters();
        System.arraycopy(arrivalMean, 0, copy.arrivalMean, 0, 3);
        System.arraycopy(arrivalDev, 0, copy.arrivalDev, 0, 3);
        System.arraycopy(serviceMean, 0, copy.serviceMean, 0, 3);
        System.arraycopy(serviceDev, 0, copy.serviceDev, 0, 3);
        return copy;
    }

    public String input(int i, boolean arrival) {
        double mean = arrival ? arrivalMean[i] : serviceMean[i];
        double halfWidth = arrival ? arrivalDev[i] : serviceDev[i];
        return String.format(Locale.US, "%.0f+/-%.0f", mean, halfWidth);
    }
}
