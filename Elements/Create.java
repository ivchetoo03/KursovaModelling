package Elements;
import System.Task;
import Util.FunRand;

public class Create extends Element {
    private final char type;
    private final double delay, deviation;

    public Create(char type, double delay, double deviation) {
        super("CREATE_" + type);
        this.type = type;
        this.delay = delay;
        this.deviation = deviation;
        tnext = 0.0;
    }

    @Override
    public void outAct() {
        quantity++;
        nextElement.inAct(new Task(type, tcurr));
        tnext = tcurr + FunRand.Unif(delay - deviation, delay + deviation, type - 'A');
    }
}
