package util;

import control.Control;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 追踪函数执行过程
 * @param name 追踪名，对应命令行指令
 * @param f 执行函数
 * @param x 执行函数的参数
 * @param consumeX 副效应函数，用 x 作为参数
 * @param consumeY 结果追踪函数，用 f 函数产生的结果作为输入参数
 * @param <X> x 的类型
 * @param <Y> f 返回值的类型
 */
public record Trace<X, Y>(String name,
                          Function<X, Y> f,
                          X x,
                          Consumer<X> consumeX,
                          Consumer<Y> consumeY) {
    public Y doit() {
        boolean flag = Control.beingTraced(name);
        // System.out.println(STR."======= name: \{name}, flag: \{flag} ++++++++");

        if (flag) {
            System.out.println(STR."before \{this.name}:");
            consumeX.accept(x);
        }
        Y y = f.apply(x);

        if (flag) {
            System.out.println(STR."after \{this.name}:");
            consumeY.accept(y);
        }
        return y;
    }
}