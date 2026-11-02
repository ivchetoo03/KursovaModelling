package Main;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import Util.*;

public class SimModel {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Strategy strategy = Strategy.FIFO;
        double modelingTime = 1000.0;
        long randomSeed = 42L;
        Verification.run(strategy, modelingTime, randomSeed);
    }
}
