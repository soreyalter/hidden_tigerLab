package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import javax.swing.event.TreeExpansionListener;
import java.util.HashMap;
import java.util.List;

// an interpreter for the SLP language.
public class Interpreter {
    // an abstract memory mapping each variable to its value
    HashMap<String, Integer> memory = new HashMap<>();

    // ///////////////////////////////////////////
    // interpret an expression
    private int interpExp(Exp.T exp) {
        // throw new Todo(exp);
        switch (exp) {
            case Exp.Id(String x) -> {
                return memory.get(x);
            }
            case Exp.Num(int n) -> {
                return n;
            }
            case Exp.Op(
                    Exp.T left,
                    String op,
                    Exp.T right
            ) -> {
                return switch (op) {
                    case "-" -> interpExp(left) - interpExp(right);
                    case "+" -> interpExp(left) + interpExp(right);
                    case "*" -> interpExp(left) * interpExp(right);
                    case "/" -> interpExp(left) / interpExp(right);
                    default -> throw new IllegalStateException(STR."Unexpected operation: \{op}");
                };
            }
            case Exp.Eseq(Stm.T stm, Exp.T e) -> {
                interpStm(stm);
                return interpExp(e);
            }
        }
    }

    // ///////////////////////////////////////////
    // interpret a statement
    public void interpStm(Stm.T stm) {
        // throw new Todo(stm);
        switch (stm) {
            // 组合语句 -> 递归
            case Stm.Compound(
                    Stm.T s1,
                    Stm.T s2
            ) -> {
                interpStm(s1);
                interpStm(s2);
            }
            // 赋值语句 -> 环境追踪
            case Stm.Assign(
                    String id,
                    Exp.T e
            ) -> {
                int value = interpExp(e);
                memory.put(id, value);
            }
            // 打印语句 -> 对 expList 的每个元素求值并打印
            case Stm.Print(List<Exp.T> exps) -> {
                for (Exp.T exp : exps) {
                    int value = interpExp(exp);
                    System.out.print(STR."\{value} ");
                }
                System.out.println();
            }
            default -> throw new IllegalStateException(STR."Unexpected value: \{stm}");
        }
    }
}
