package util;

import control.Control;

import java.io.*;
import java.util.LinkedList;

public class Dot {
    private record Element(String x,
                           String y,
                           String z) {

        @Override
        public String toString() {
            return STR."""
"\{x}"->"\{y}"\{z};
""";
        }
    }
    // end of Element

    //
    private final String name;
    private final LinkedList<Element> list;

    public Dot(String name) {
        this.name = name;
        this.list = new LinkedList<>();
    }

    /**
     * 生成一条dot图语句 "from -> to" 并添加到dot图的语句列表中
     * @param from 源结点名
     * @param to 目的结点名
     */
    public void insert(String from, String to) {
        this.insert(from, to, "");
    }

    public void insert(String from, String to, String info) {
        String s = STR."[label=\"\{info}\"]";
        this.list.addFirst(new Element(from, to, s));
    }

    /**
     * 把Dot对象Element列表中的dot图语句写到写缓冲区bw
     * @param bw 写缓冲
     * @throws IOException IO异常
     */
    private void output(BufferedWriter bw) throws IOException {
        for (Element e : list) {
            bw.write(e.toString());
        }
    }

    /**
     * 生成一个 Dot.name.dot 文件
     */
    public void toDot() {
        String fileName = STR."\{this.name}.dot";
        try {
            FileWriter fw = new FileWriter(fileName);
            BufferedWriter bw = new BufferedWriter(fw);
            // dot 图的开头代码
            bw.write(STR."""
                    digraph g{
                    \tsize = "10, 10";
                    \tnode [color=lightblue2, style=filled];""");
            // 写 element 到写缓冲中
            this.output(bw);
            bw.write("\n}\n\n");
            bw.close();
            fw.close();
        } catch (Exception o) {
            throw new util.Error();
        }
    }

    public void visualize() {
        this.toDot();
        String format = Control.Dot.format;
        // dot -Tpng -O dotName.dot 读取dotName.dot，输出 dotName.dot.png
        String[] args = {"dot", "-T", format, "-O", STR."\{this.name}.dot"};
        try {
            final class StreamDrainer implements Runnable {
                private final InputStream ins;

                public StreamDrainer(InputStream ins) {
                    this.ins = ins;
                }

                @Override
                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    } catch (Exception e) {
                        throw new util.Error(e);
                    }
                }
            }
            Process process = Runtime.getRuntime().exec(args);
            new Thread(new StreamDrainer(process.getInputStream())).start();
            new Thread(new StreamDrainer(process.getErrorStream())).start();
            process.getOutputStream().close();
            int exitValue = process.waitFor();
            if (exitValue != 0) {
                throw new util.Error(exitValue);
            }
            if (!Control.Dot.keep) {
                if (!new File(STR."\{name}.dot").delete())
                    throw new util.Error("Cannot delete dot");
            }
        } catch (Exception o) {
            throw new util.Error(o);
        }
    }
}