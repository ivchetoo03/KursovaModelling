package Elements;

import System.Task;

public abstract class Element {
    protected final String name;
    protected double tcurr = 0.0;
    protected double tnext = Double.MAX_VALUE;
    protected int quantity = 0;
    protected Element nextElement;

    public Element(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public double getTnext() { return tnext; }
    public int getQuantity() { return quantity; }
    
    public void setTcurr(double time) { tcurr = time; }
    public void setNextElement(Element element) { nextElement = element; }

    public void inAct(Task task) { }
    public abstract void outAct();
    public void doStatistics(double delta) { }

    public void printInfo() {
        System.out.printf(java.util.Locale.US,
                "%s quantity = %d, tnext = %.5f%n", name, quantity, tnext);
    }
}
