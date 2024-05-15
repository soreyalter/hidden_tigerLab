package slp;

import slp.Slp.Exp.Eseq;
import slp.Slp.Exp.Id;
import slp.Slp.Exp.Num;
import slp.Slp.Exp.Op;
import slp.Slp.Stm;
import slp.Slp.Stm.Assign;
import slp.Slp.Stm.Compound;
import slp.Slp.Stm.Print;

import java.util.List;

// two sample programs.
public class SamplePrograms {
    public static Stm.T sample1 = new Compound(
            new Assign("a", new Op(new Num(5), "+", new Num(3))),
            new Compound(
                    // b := (print(a, a-1), 10*a);
                    new Assign("b", new Eseq(new Print(List.of(
                            new Id("a"),
                            new Op(new Id("a"), "-", new Num(1)))),
                            new Op(new Num(10), "*", new Id("a")))),
                    new Print(List.of(new Id("b")))));

    // lab 1, exercise 3:
    // replace the "null" with your code:
    public static Stm.T sample2 = new Compound(
            // a := 1
            new Assign("a", new Num(1)),
            new Compound(
                    // b := 0
                    new Assign("b", new Num(3)),
                    // print(a/b)
                    new Print(List.of(new Op(new Id("a"),"/", new Id("b")
                    )))
            )
    );
 }
