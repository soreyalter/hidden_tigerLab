package util;

import control.Control;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

public class Dot {
    private static final int Bmp = 1, Pdf = 1, Ps = 1, Jpg = 1;

    public static record Entry<X, Y, Z>(X x, Y y, Z z) {

    }

    class DotElement<X, Y, Z> {
        Entry<X, Y, Z> e;

        public DotElement(X x, Y y, Z z) {
            this.e = new Entry<>(x, y, z);
        }

        public String toString() {
            String s = "";
            if (this.e.z != null) s = this.e.z.toString();

            return ("\"" + e.x.toString() + "\"" + "->" + "\"" + e.y.toString() + "\"" + s + ";\n");
        }
    }

    LinkedList<DotElement<String, String, String>> list;

    public Dot() {
        this.list = new LinkedList<DotElement<String, String, String>>();
    }

    public void insert(String from, String to) {
        this.list.addFirst(new DotElement<String, String, String>(from, to, null));
    }

    public void insert(String from, String to, String info) {

        String s = "[label=\"" + info + "\"]";
        // System.out.println(s);
        this.list.addFirst(new DotElement<String, String, String>(from, to, s));
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();

        for (DotElement<String, String, String> e : this.list) {
            sb.append(e.toString());
        }

        String result = sb.toString();

        return result;
    }

    public void toDot(String fname) {
        String fn = fname + ".dot";
        try {
            File f = new File(fn);
            FileWriter fw = new FileWriter(f);
            BufferedWriter w = new BufferedWriter(fw);

            StringBuffer sb = new StringBuffer();
            sb.append("digraph g{\n");
            sb.append("\tsize = \"10, 10\";\n");
            sb.append("\tnode [color=lightblue2, style=filled];\n");

            sb.append(this.toString());

            sb.append("}\n");

            w.write(sb.toString());
            w.close();
            fw.close();
        } catch (Throwable o) {
            new util.Bug();
        }
        return;
    }

    void visualize(String name) {
        toDot(name);
        String format = "";
        String postfix = "";
        switch (Control.visualize) {
            case 1:
                format = "-Tbmp";
                postfix = "bmp";
                break;
            default:
                new Bug();
                break;
        }
        String[] args = {"dot", format, name + ".dot", "-o", name + "." + postfix};
        try {
            // Read this article:
            // http://walsh.iteye.com/blog/449051
            final class StreamDrainer implements Runnable {
                private InputStream ins;

                public StreamDrainer(InputStream ins) {
                    this.ins = ins;
                }

                public void run() {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
                        String line = null;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
            Process process = Runtime.getRuntime().exec(args);
            new Thread(new StreamDrainer(process.getInputStream())).start();
            new Thread(new StreamDrainer(process.getErrorStream())).start();
            process.getOutputStream().close();
            int exitValue = process.waitFor();
            if (!Control.debug) {
                if (new File(name + ".dot").delete()) ;
                else throw new Throwable();
            }
        } catch (Throwable o) {
            o.printStackTrace();
        }
        return;
    }
}
