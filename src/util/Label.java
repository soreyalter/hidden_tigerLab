package util;

import java.io.Serializable;

public class Label implements Serializable {
    private final int i;
    private static int count = 0;

    // 每次初始化都会得到一个唯一的i作为标识
    public Label() {
        this.i = count++;
    }

    /**
     * 生成一个唯一的基本块标识符 L_i
     * @return ‘L_i’
     */
    @Override
    public String toString() {
        // L_1, L_2, ...
        return STR."L_\{this.i}";
    }
}

