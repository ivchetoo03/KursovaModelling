package Main;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import Elements.Create;
import Elements.Process;
import System.Model;
import Util.FunRand;
import Util.Strategy;

public class Main {
    public static void main(String[] args) throws Exception {
        double time = 1000;
        Strategy strategy = Strategy.FIFO;
        FunRand.reset(42);

        Create a = new Create('A', 20, 5);
        Create b = new Create('B', 20, 10);
        Create c = new Create('C', 30, 10);
        Process d0 = new Process("D0", 1, strategy);
        Process processor = new Process("PROCESSOR", 2, strategy);
        d0.setNextElement(processor);
        a.setNextElement(d0);
        b.setNextElement(d0);
        c.setNextElement(d0);

        Files.createDirectories(Path.of("results"));
        try (PrintWriter queue = new PrintWriter(Files.newBufferedWriter(Path.of("results/queue_states.csv")));
             PrintWriter tasks = new PrintWriter(Files.newBufferedWriter(Path.of("results/tasks.csv")))) {
            d0.setQueueTrace(queue);
            processor.setTaskTrace(tasks);
            Model model = new Model(Arrays.asList(a, b, c, d0, processor));
            model.simulate(time, true);
        }

        System.out.printf("t = %.2f\n", time);
        processor.printResult(time);
    }
}
