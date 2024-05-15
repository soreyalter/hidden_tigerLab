package slp;

import slp.Slp.Exp;
import slp.Slp.Stm;
import util.Todo;

import java.util.List;

public class MaxArgument {
    // ///////////////////////////////////////////
    // expression
    private int maxExp(Exp.T exp) {
        // 两种 exp 能产生新的 exp 对象
        //  - 运算表达式 Exp.Op
        //  - 逗号表达式 Exp.Eseq
        switch (exp) {
            case Exp.Op(
                Exp.T left,
                String op,
                Exp.T right
            ) -> {
                return Math.max(maxExp(left), maxExp(right));
            }
            case Exp.Eseq(
                Stm.T s1,
                Exp.T e
            ) -> {
                return Math.max(maxStm(s1), maxExp(e));
            }
            default -> {
                return 0;
            }
        }
    }

    // ///////////////////////////////////////////
    // statement

    /**
     * 传入语句对象，返回其中最大参数值（Print语句接受的最多参数数量）
     * @param stm Stm语句对象（或者说多个语句组成的程序）
     * @return 最大参数值
     */
    public int maxStm(Stm.T stm) {
        // throw new Todo(stm);
        // 要产生 Print 语句，必须是 stm 对象，而 exp 可以通过逗号表达式产生 stm 对象
        // 只有 Print 语句需要计算其参数的个数，而 List 中全是 exp 对象，其中可能存在 逗号表达式
        // 如果是逗号表达式要递归从其中提取 Print 语句
        // 如果没有逗号表达式，List 的长度就是 Print 的参数数量
        switch (stm) {
            // s1; s2
            case Stm.Compound(
                    Stm.T s1,
                    Stm.T s2
            ) -> {
                return Math.max(maxStm(s1), maxStm(s2));
            }
            // x := e
            case Stm.Assign(
                    String x,
                    Exp.T e
            ) -> {
                if (e instanceof Exp.Eseq)
                    return maxExp(e);
            }
            case Stm.Print(List<Exp.T> exps) -> {
                int maxVal = exps.size();
                for (Exp.T exp : exps) {
                    maxVal = Math.max(maxVal, maxExp(exp));
                }
                return maxVal;
            }
        }
        // 表示错误
        return -1;
    }
}
