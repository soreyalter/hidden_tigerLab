package ast;

import ast.Ast.*;
import util.Id;
import util.Todo;
import util.Tuple;

import java.util.List;

public class PrettyPrinter {
    private int indentLevel = 4;

    public PrettyPrinter() {
        this.indentLevel = 0;
    }

    private void indent() {
        this.indentLevel += 4;
    }

    private void unIndent() {
        this.indentLevel -= 4;
    }

    private void printSpaces() {
        int i = this.indentLevel;
        while (i-- > 0)
            System.out.print(" ");
    }

    private <T> void say(T s) {
        printSpaces();
        System.out.print(s);
    }

    private <T> void sayln(T s) {
        printSpaces();
        System.out.println(s);
    }

    private <T> void sayLocal(T s) {
        System.out.print(s);
    }


    // /////////////////////////////////////////////////////
    // ast id
    public void ppAstId(AstId aid) {
        sayLocal(aid.freshId);
    }

    // /////////////////////////////////////////////////////
    // expressions
    public void ppExp(Exp.T e) {
        switch (e) {
            case Exp.ExpId(AstId aid) -> ppAstId(aid);
            case Exp.Call(
                    Exp.T callee,
                    AstId methodId,
                    List<Exp.T> args,
                    Tuple.One<Id> theObjectType,
                    Tuple.One<Type.T> retType
            ) -> {
                ppExp(callee);
                sayLocal(STR.".");
                ppAstId(methodId);
                sayLocal("(");
                for (Exp.T arg : args) {
                    ppExp(arg);
                    sayLocal(", ");
                }
                sayLocal(")");
            }
            case Exp.NewObject(Id id) -> {
                sayLocal(STR."new \{id.toString()}()");
            }
            case Exp.Num(int n) -> sayLocal(n);
            case Exp.Bop(
                    Exp.T left,
                    String bop,
                    Exp.T right
            ) -> {
                ppExp(left);
                sayLocal(STR." \{bop} ");
                ppExp(right);
            }
            case Exp.This() -> sayLocal("this");
            // ExpId, Call, NewObject, Num, Bop, This
            case Exp.True() -> sayLocal("true");
            case Exp.False() -> sayLocal("false");
            case Exp.ArraySelect(Exp.T array, Exp.T index) -> {
                ppExp(array);
                sayLocal(STR."[\{index}]");
            }
            case Exp.BopBool(Exp.T left, String op, Exp.T right) -> {
                ppExp(left);
                sayLocal(STR." \{op} ");
                ppExp(right);
            }
            case Exp.Length(Exp.T array) ->{
                ppExp(array);
                sayLocal(".length");
            }
            case Exp.NewIntArray(Exp.T num) -> {
                sayLocal("new int [");
                ppExp(num);
                sayLocal("]");
            }

            default -> {
                // throw new Todo();
                System.out.println("Error: print Exp failed.");
                // exit(-1);
            }
        }
    }

    // statement
    public void ppStm(Stm.T s) {
        switch (s) {
            case Stm.If(
                    Exp.T cond,
                    Stm.T then_,
                    Stm.T else_
            ) -> {
                say("if(");
                ppExp(cond);
                sayLocal("){\n");
                indent();
                ppStm(then_);
                unIndent();
                sayln("}else{");
                indent();
                ppStm(else_);
                unIndent();
                sayln("}");
            }
            case Stm.Print(Exp.T exp) -> {
                say("System.out.println(");
                ppExp(exp);
                sayLocal(");\n");
            }
            case Stm.Assign(
                    AstId aid,
                    Exp.T exp
            ) -> {
                say("");
                ppAstId(aid);
                sayLocal(STR." = ");
                ppExp(exp);
                sayLocal(";\n");
            }
            case Stm.AssignArray(AstId id, Exp.T index, Exp.T exp) -> {
                // id[exp] = exp;
                say("");
                ppAstId(id);
                sayLocal("[");
                ppExp(index);
                sayLocal("] = ");
                ppExp(exp);
                sayLocal(";\n");
            }
            case Stm.Block(List<Stm.T> stms) -> {
                for (Stm.T stm : stms) {
                    ppStm(stm);
                }
            }
            case Stm.While(Exp.T cond, Stm.T body) -> {
                // while (cond) {
                //     body
                // }
                say("while (");
                ppExp(cond);
                sayLocal(") {\n");
                indent();
                ppStm(body);
                unIndent();
                sayln("}");
            }
            default -> {
                // throw new Todo();
                System.out.println("Error: print Stm failed.");
            }
        }
    }

    // type
    public void ppType(Type.T t) {
        switch (t) {
            case Type.Int() -> sayLocal("int");
            case Type.Boolean() -> sayLocal("boolean");
            case Type.IntArray() -> sayLocal("int[]");
            case Type.ClassType(Id id) -> sayLocal(STR."class \{id}");
            default -> {
                // throw new Todo();
                System.out.println("Error: print Type failed.");
            }
        }
    }

    // dec
    public void ppDec(Dec.T dec) {
        // 只有这一种类型，所以直接用类型转换
        // 而不是 switch case 语句
        Dec.Singleton d = (Dec.Singleton) dec;
        ppType(d.type());
        sayLocal(" ");
        ppAstId(d.aid());
    }

    // method
    public void ppMethod(Method.T mtd) {
        // 签名
        Method.Singleton m = (Method.Singleton) mtd;
        this.say("public ");
        ppType(m.retType());
        this.sayLocal(" ");
        ppAstId(m.methodId());
        this.sayLocal(STR."(");
        // 这样写会有额外的一个逗号
        m.formals().forEach(x -> {
            ppDec(x);
            sayLocal(", ");
        });

        this.sayLocal("){\n");

        // 方法体
        indent();
        m.locals().forEach(x -> {
            this.say("");
            ppDec(x);
            // 声明语句块后会有个空行
            this.sayLocal(";\n");
        });
        this.sayln("");
        m.stms().forEach(this::ppStm);
        this.say("return ");
        ppExp(m.retExp());
        this.sayLocal(";\n");
        unIndent();
        this.sayln("}");
    }

    // class
    public void ppOneClass(Ast.Class.T cls) {
        Ast.Class.Singleton c = (Ast.Class.Singleton) cls;
        this.say(STR."class \{c.classId()}");
        if (c.extends_() != null) {
            this.sayLocal(STR." extends \{c.extends_()}");
        } else {
            this.sayLocal("");
        }
        this.sayLocal("{\n");
        indent();
        c.decs().forEach(this::ppDec);
        c.methods().forEach(this::ppMethod);
        unIndent();
        this.sayln("}");
    }

    // main class
    public void ppMainClass(MainClass.T m) {
        MainClass.Singleton mc = (MainClass.Singleton) m;
        this.sayln(STR."class \{mc.classId()}{");
        indent();
        this.say(STR."public static void main(String[] ");
        ppAstId(mc.arg());
        sayLocal("){\n");
        indent();
        ppStm(mc.stm());
        unIndent();
        this.sayln("}");
        unIndent();
        this.sayln("}");
    }

    // program
    public void ppProgram(Program.T prog) {
        Program.Singleton p = (Program.Singleton) prog;
        ppMainClass(p.mainClass());
        this.sayln("");
        p.classes().forEach(this::ppOneClass);
        this.sayln("\n");
    }

}

