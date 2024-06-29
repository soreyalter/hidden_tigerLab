package slp;

import util.Dot;

public class Main {
    public static void main(String[] args) {
        Main obj = new Main();
        obj.doit(SamplePrograms.sample1);
        obj.doit(SamplePrograms.sample2);

    }

    public void doit(Slp.Stm.T prog) {
        PrettyPrint pp = new PrettyPrint();
        pp.ppStm(prog);

        // maximum argument:
        System.out.println();
        MaxArgument max = new MaxArgument();
        System.out.println(STR."maximum argument: \{max.maxStm(prog)}");

        // interpreter:
        Interpreter interp = new Interpreter();
        interp.interpStm(prog);

        // compiler to x64:
        System.out.println("================");
        Compiler compiler = new Compiler();
        try {
            compiler.compileStm(prog);
        } catch (Exception e) {
            throw new util.Error();
        }
    }
}
