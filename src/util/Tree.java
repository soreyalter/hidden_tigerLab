package util;

import ast.Ast;

import java.util.List;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;


// a tree is parameterized by its containing data "X"
public class Tree<X> {

    // tree node
    public class Node {
        public X data;
        public List<Node> children;

        public Node(X data) {
            this.data = data;
            this.children = new Vector<>();
        }

        @Override
        public String toString() {
            return data.toString();
        }
    }
    // end of tree node

    // the tree name, for debugging
    public final String name;
    public Node root;
    private final Vector<Node> allNodes;

    public Tree(String name) {
        this.name = name;
        this.root = null;
        this.allNodes = new Vector<>();
    }

    public void addRoot(X data) {
        Node n = new Node(data);
        this.allNodes.add(n);
        this.root = n;
    }

    public void addNode(X data) {
        // data must not already be in the tree
        // we should check that for correctness
        this.allNodes.add(new Node(data));
    }

    public Node lookupNode(X data) {
        // 从头到尾线性查找
        // for (Node node : this.allNodes) {
        //     if (node.data.equals(data)) {
        //         return node;
        //     }
        // }

        // 反向查找，因为使用add()添加结点会添加到末尾，直接从后面往前找很快
        for (int i = this.allNodes.size()-1; i >= 0; i--) {
            Node node = allNodes.get(i);
            if (node.data.equals(data)) {
                return node;
            }
        }
        return null;
    }

    public void addEdge(Node from, Node to) {
        // 父类 -> 子类
        from.children.add(to);
    }

    public void addEdge(X from, X to) {
        // Ast.Class.Singleton fromm = (Ast.Class.Singleton) from;
        // Ast.Class.Singleton too = (Ast.Class.Singleton) to;
        // System.out.println(STR."from: \{from.getClassId()}, to: \{to.getClassId()}");
        Node f = this.lookupNode(from);
        Node t = this.lookupNode(to);

        if (f == null || t == null)
            throw new Error();

        this.addEdge(f, t);
    }

    // perform a level-order traversal of the tree.

    /**
     * 继承树层序遍历 ? 这不对吧这怎么好像是先序遍历
     * @param node 树的根节点，其内的数据 node.data 是doit 函数的第一个参数
     * @param doit 二元函数，形参类型分别为 X，Y，返回类型为 Y
     * @param value doit 的第二个参数
     * @param <Y> doit 函数的泛型
     */
    public <Y> void levelOrder(Node node,
                               BiFunction<X, Y, Y> doit,    // 传入函数：prefixOneClass
                               Y value) {
        Y result = doit.apply(node.data, value);
        for (Node child : node.children) {
            levelOrder(child, doit, result);
        }
    }

    public void output(Node n) {
        for (Node child : n.children)
            System.out.println(STR."\{n} -> \{child}");
        for (Node child : n.children)
            output(child);
    }

    public void dot(Function<X, String> converter) {
        Dot dot = new Dot(this.name);
        for (Node node : this.allNodes) {
            for (Node child : node.children)
                dot.insert(converter.apply(node.data),
                        converter.apply(child.data));
        }
        dot.visualize();
    }
}