package Main;

import Util.*;

public class SimModel {
    public static void main(String[] args) {
        Strategy strategy = Strategy.FIFO;
        double modelingTime = 1000;
        long randomSeed = 42;
        Verification.run(strategy, modelingTime, randomSeed);
    }
}
