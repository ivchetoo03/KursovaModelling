package Util;
import java.util.Random;

public class FunRand {
    private static final Random[] randoms = new Random[6];

    public static void reset(long seed) {
        for (int i = 0; i < randoms.length; i++) {
            randoms[i] = new Random(seed + 1009L * i);
        }
    }

    public static double Unif(double min, double max, int stream) {
        if (randoms[stream] == null) reset(42L);
        return min + (max - min) * randoms[stream].nextDouble();
    }
}
