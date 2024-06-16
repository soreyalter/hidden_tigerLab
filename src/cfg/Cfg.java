package cfg;

import util.*;

import java.io.Serializable;
import java.util.List;

public class Cfg {

    // the pretty printer
    private static int indentLevel = 0;

    private static void indent() {
        indentLevel += 4;
    }

    private static void unIndent() {
        indentLevel -= 4;
    }

    private static void printSpaces() {
        int i = indentLevel;
        while (i-- != 0) {
            say(" ");
        }
    }

    private static <T> void sayln(T s) {
        System.out.println(s);
    }

    private static <T> void say(T s) {
        System.out.print(s);
    }

    //  ///////////////////////////////////////////////////////////
    //  type
    public static class Type {
        public sealed interface T extends Serializable
                permits ClassType, CodePtr, Int, IntArray {
        }

        public record ClassType(Id id) implements T {
        }

        public record CodePtr() implements T {
        }

        public record Int() implements T {
        }

        public record IntArray() implements T {
        }

        public static void pp(T ty) {
            switch (ty) {
                case ClassType(Id id) -> say(id.toString());
                case CodePtr() -> say("CodePtr");
                case Int() -> say("int");
                case IntArray() -> say("int[]");
            }
        }
    }

    // ///////////////////////////////////////////////////
    // declaration
    public static class Dec {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        public record Singleton(Type.T type,
                                Id id) implements T {
            // only compare "id"
            @Override
            public boolean equals(Object o) {
                if (o == null)
                    return false;
                if (!(o instanceof Singleton))
                    return false;
                return this.id().equals(((Singleton) o).id());
            }

            @Override
            public int hashCode() {
                return this.id().hashCode();
            }
        }

        public static void pp(T dec) {
            switch (dec) {
                case Singleton(
                        Type.T type,
                        Id id
                ) -> {
                    Type.pp(type);
                    say(STR." \{id}");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // virtual function table
    // global table ?
    public static class Vtable {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        // 虚拟表项
        public record Entry(Type.T retType,
                            Id classId,
                            Id functionId,
                            List<Dec.T> argTypes) implements Serializable {
        }

        public record Singleton(Id name,    // current class id
                                List<Entry> funcTypes) implements T {
        }
        // name,
        // Type.T retType,
        // Id classId,
        // Id functionId,
        // List<Dec.T> argTypes
        public static void pp(T vtable) {
            switch (vtable) {
                /*
                * struct V_vtable {
                *     int func1(int a, int b,);
                *     int[] func2(A x, B y,);
                * } V_vtable_ = {
                *     .func1 = class_func1,
                *     .func2 = class_func2,
                * }
                *
                * */
                case Singleton(Id name, List<Entry> funcTypes) -> {
                    printSpaces();
                    sayln(STR."struct V_\{name} {");
                    // all entries
                    indent();
                    for (Entry e : funcTypes) {
                        printSpaces();
                        Type.pp(e.retType);
                        say(STR." \{e.functionId}(");
                        for (Dec.T dec : e.argTypes) {
                            Dec.pp(dec);
                            say(", ");
                        }
                        sayln(");");
                    }
                    unIndent();
                    printSpaces();
                    sayln(STR."} V_\{name}_ = {");
                    indent();
                    for (Entry e : funcTypes) {
                        printSpaces();
                        say(STR.".\{e.functionId} = \{e.classId}_\{e.functionId}");
                        say(",\n");
                    }
                    unIndent();
                    printSpaces();
                    say("};\n\n");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // structures
    // translate Java.class to Cpp.struct ?
    public static class Struct {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        public record Singleton(Id className,
                                List<Cfg.Dec.T> fields) implements T {
        }

        public static void pp(T s) {
            switch (s) {
                case Singleton(
                        Id clsName,
                        List<Cfg.Dec.T> fields
                ) -> {
                     // struct S_ClassName {
                     //     struct V_ClassName *vptr;
                     //     int a
                     //     int b
                     // } S_ClassName_ = {
                     //     这句是C语言语法中的初始化，将上面那个vptr指针的值设置为虚函数表的地址
                     //     .vptr = &V_ClassName_;
                     // }
                    printSpaces();
                    // Id.toString 重写了，这里类肯定是有 origName 的，所以打印出来就是原类名
                    sayln(STR."struct S_\{clsName.toString()} {");
                    indent();
                    // the first field is special
                    printSpaces();
                    sayln(STR."struct V_\{clsName} *vptr;");
                    fields.forEach((dec) -> {
                        printSpaces();
                        Dec.pp(dec);
                        sayln(";");
                    });
                    unIndent();
                    printSpaces();
                    sayln(STR."} S_\{clsName}_ = {");
                    indent();
                    printSpaces();
                    sayln(STR.".vptr = &V_\{clsName}_;");
                    unIndent();
                    printSpaces();
                    say("};\n\n");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // expression
    public static class Exp {
        public sealed interface T extends Serializable
                permits Bop, Call, Eid, GetMethod, Int, New, Print, ArraySelect, Length, NewIntArray {
        }

        public record NewIntArray(Id size) implements T {
        }
        public record Length(Id array) implements T {
        }

        // + [a, b, c] -> int, +, -, *, <, &&
        public record Bop(String op,
                          List<Id> operands,
                          Type.T type) implements T {
        }

        // funcName(x, y) -> int
        public record Call(Id func,
                           List<Id> operands,
                           Type.T retType) implements T {
        }

        // int x
        public record Eid(Id x,
                          Type.T type) implements T {
        }

        // get virtual method:
        // getMethod(objId, classId, methodId)
        public record GetMethod(Id objId,   // 实例
                                Id classId, // 类名，从类的虚函数表找函数？
                                Id methodId) implements T {
        }

        // integer constant
        public record Int(int n) implements T {
        }

        public record New(Id classId) implements T {
        }

        public record Print(Id x) implements T {
        }

        public record ArraySelect(Id array, Id index) implements T{}

        public static void pp(Exp.T t) {
            switch (t) {
                case Length(Id array) -> say(STR."\{array.toString()}.length");
                // +(1, 2, 3, ) @ty: int
                case Bop(String op, List<Id> operands, Type.T type) -> {
                    say(STR."\{op}(");
                    operands.forEach((e) -> {
                        say(STR."\{e.toString()}, ");
                    });
                    say(")  @ty:");
                    Type.pp(type);
                }
                case Call(Id func, List<Id> args, Type.T retType) -> {
                    // func(x, y, ) @retType: int
                    say(STR."\{func.toString()}(");
                    args.forEach((e) -> {
                        say(STR."\{e.toString()}, ");
                    });
                    say(")  @retType:");
                    Type.pp(retType);
                }
                case Eid(Id id, Type.T type) -> say(STR."\{id}");
                case GetMethod(Id objId, Id classId, Id methodId) ->
                        // getMethod(obj, Class, func)
                        say(STR."getMethod(\{objId.toString()}, \{classId.toString()}, \{methodId.toString()})");
                case Int(int n) -> say(STR."\{n}");
                case New(Id classId) -> say(STR."new \{classId.toString()}()");
                case Print(Id x) -> say(STR."print(\{x.toString()})");
                // array[index]
                case ArraySelect(Id array, Id index) -> say(STR."\{array.toString()}[\{index.toString()}]");
                // new int[size]
                case NewIntArray(Id size) -> say(STR."new int[\{size.toString()}]");
                default -> {
                    // throw new Todo(t);
                    say("Error: Exp prettyPrint failed.");
                }
            }
        }
    }
    // end of expression

    // /////////////////////////////////////////////////////////
    // statement
    public static class Stm {
        public sealed interface T extends Serializable
                permits Assign, AssignArray {
        }

        public record AssignArray(Id array,
                                  Id index,
                                  Id value) implements T{
        }


        // assign
        // "x" should not be "null", even if the exp is not used.
        public record Assign(Id x,
                             Exp.T exp) implements T {
        }

        static void pp(Stm.T t) {
            switch (t) {
                case Assign(Id x, Exp.T exp) -> {
                    printSpaces();
                    say(STR."\{x.toString()} = ");
                    Exp.pp(exp);
                    sayln(";");
                }
                case AssignArray(Id array, Id index, Id value) -> {
                    printSpaces();
                    sayln(STR."\{array.toString()}[\{index.toString()}] = \{value.toString()};");
                    // Exp.pp(index);
                    // say(STR."] = ");
                    // Exp.pp(value);
                    // sayln(";");
                }

            }
        }
    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    public static class Transfer {
        public sealed interface T extends Serializable
                permits If, Jmp, Ret {
        }

        public record If(Id x,  // 为什么这个是id而不是exp
                         Block.T trueBlock,
                         Block.T falseBlock) implements T {
        }

        public record Jmp(Block.T target) implements T {
        }

        // 这个id是什么东西
        public record Ret(Id x) implements T {
        }

        public static void dot(Dot d, String from, T t) {
            switch (t) {
                case If(
                        _,
                        Block.T thenn,
                        Block.T elsee
                ) -> {
                    // from = 源基本块名
                    d.insert(from, Block.getLabel(thenn).toString());
                    d.insert(from, Block.getLabel(elsee).toString());
                }
                case Jmp(Block.T target) -> d.insert(from, Block.getLabel(target).toString());
                case Ret(_) -> {
                }
            }
        }

        public static void pp(T t) {
            switch (t) {
                case If(
                        Id x,
                        Block.T thenn,
                        Block.T elsee
                ) -> {
                    // if(x, thenBlock, elseBlock);
                    printSpaces();
                    say(STR."if(\{x.toString()}");
                    say(STR.", \{Block.getLabel(thenn).toString()}, \{Block.getLabel(elsee).toString()});");
                    say(STR.", \{Block.getLabel(thenn)}, \{Block.getLabel(elsee)});");
                }
                case Jmp(Block.T target) -> {
                    // jmp targetBlock;
                    printSpaces();
                    say(STR."jmp \{Block.getLabel(target).toString()};");
                }
                case Ret(Id x) -> {
                    // ret x;
                    printSpaces();
                    say(STR."ret \{x.toString()};");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // block
    public static class Block {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        // 注意这个是record类
        public record Singleton(Label label,
                                List<Stm.T> stms,
                                // this is a special hack
                                // the transfer field is final, so that
                                // we use a list instead of a singleton field
                                List<Transfer.T> transfer) implements T {
        }
        public static int size(T t){
            switch (t) {
                case Singleton(_, List<Stm.T> stms, _) -> {
                    return stms.size();
                }
            }
        }

        /**
         * 将语句s添加到基本块b中
         * @param b Block 基本块
         * @param s Stm 语句
         */
        public static void add(T b, Stm.T s) {
            switch (b) {
                case Singleton(
                        _,
                        List<Stm.T> stms,
                        _
                ) -> stms.add(s);
            }
        }

        public static void addTransfer(T b, Transfer.T s) {
            switch (b) {
                case Singleton(
                        Label _,
                        List<Stm.T> _,
                        List<Transfer.T> transfer
                ) -> transfer.add(s);
            }
        }

        public static void dot(Block.T t, Dot d) {
            switch (t) {
                case Singleton(
                        Label label,
                        List<Stm.T> _,
                        List<Transfer.T> trans
                ) -> trans.forEach((tr) -> Transfer.dot(d, label.toString(), tr));
            }
        }

        public static Label getLabel(Block.T t) {
            switch (t) {
                case Singleton(
                        Label label,
                        List<Stm.T> _,
                        List<Transfer.T> _
                ) -> {
                    return label;
                }
            }
        }

        public static void pp(T b) {
            switch (b) {
                case Singleton(
                        Label label,
                        List<Stm.T> stms,
                        List<Transfer.T> transfer
                ) -> {
                    printSpaces();
                    sayln(STR."\{label.toString()}:");
                    indent();
                    stms.forEach(Stm::pp);
                    Transfer.pp(transfer.getFirst());
                    unIndent();
                    sayln("");
                }
            }
        }
    }

    // /////////////////////////////////////////////////////////
    // function
    public static class Function {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        public record Singleton(Type.T retType,
                                Id classId,
                                Id functionId,
                                List<Dec.T> formals,
                                List<Dec.T> locals,
                                List<Block.T> blocks) implements T {
        }

        /**
         * 计算一个函数的 size，即其中的基本块数和语句数量
         * @param t 函数对象
         * @return <函数Id，基本块数，语句数>三元组
         */
        public static Tuple.Three<Id, Integer, Integer> size(Cfg.Function.T t) {
            switch (t) {
                case Singleton(_, _, Id functionId, _, _, List<Block.T> blocks) -> {
                    int blockNum = blocks.size();
                    int statementNum = blocks.stream().mapToInt(Block::size).sum();
                    return new Tuple.Three<>(functionId, blockNum, statementNum);
                }
            }
        }

        public static void addBlock(T func, Block.T block) {
            switch (func) {
                case Singleton(
                        Type.T _,
                        Id _,
                        Id _,
                        List<Dec.T> _,
                        List<Dec.T> _,
                        List<Block.T> blocks
                ) -> blocks.add(block);
            }
        }

        public static void addFirstFormal(T func, Dec.T formal) {
            switch (func) {
                case Singleton(
                        _,
                        _,
                        _,
                        List<Dec.T> formals,
                        _,
                        _
                ) -> formals.addFirst(formal);
            }
        }

        public static void addDecs(T func, List<Dec.T> decs) {
            switch (func) {
                case Singleton(
                        _,
                        _,
                        _,
                        _,
                        List<Dec.T> locals,
                        _
                ) -> locals.addAll(decs);
            }
        }

        // TODO: lab3, exercise 4.
        public static void dot(T func) {
            switch (func) {
                case Singleton(
                        Type.T retType,
                        Id classId,
                        Id functionId,
                        List<Dec.T> formals,
                        List<Dec.T> locals,
                        List<Block.T> blocks
                ) -> {
                    // dot 图名：classId-functionId
                    Dot d = new util.Dot(STR."\{classId.toString()}-\{functionId.toString()}");
                    blocks.forEach((b) -> Block.dot(b, d));
                    d.visualize();
                    // d.toDot();
                }
            }
        }

        public static void pp(T f) {
            switch (f) {
                case Singleton(
                        Type.T retType,
                        Id classId,
                        Id id,
                        List<Dec.T> formals,
                        List<Dec.T> locals,
                        // 函数的 blocks 【不包含】函数签名 和 locals部分
                        List<Block.T> blocks
                ) -> {
                    // functionId(x, y, ){ @classId: classId.toString()
                    //    int a;
                    //    int b;
                    //    pp(Blocks)
                    // }
                    printSpaces();
                    Type.pp(retType);
                    say(STR." \{id}(");
                    formals.forEach(x -> {
                        Dec.pp(x);
                        say(", ");
                    });
                    say(STR."){ @classId: \{classId.toString()}\n");
                    indent();
                    locals.forEach(x -> {
                        printSpaces();
                        Dec.pp(x);
                        sayln(";");
                    });
                    blocks.forEach(Block::pp);
                    unIndent();
                    printSpaces();
                    say("}\n\n");
                }

            }
        }
    }

    // whole program
    public static class Program {
        public sealed interface T extends Serializable
                permits Singleton {
        }

        public record Singleton(Id mainClassId,
                                Id mainFuncId, // name of the entry function
                                List<Vtable.T> vtables,
                                List<Struct.T> structs,
                                List<Function.T> functions) implements T {
        }

        public static void size(T prog) {
            switch (prog) {
                case Singleton(Id mainClassId,
                               Id mainFuncId,
                               List<Vtable.T> vtables,
                               List<Struct.T> structs,
                               List<Function.T> functions) -> {
                    int totalBlocks = 0;
                    int totalStmts = 0;
                    for (Function.T func : functions) {
                        Tuple.Three<Id, Integer, Integer> result = Function.size(func);
                        sayln(STR."<\"\{result.first()}\", \{result.second()}, \{result.third()}>");
                        totalBlocks += result.second();
                        totalStmts += result.third();
                    }
                    sayln(STR."---------------------------------");
                    sayln(STR."subtotal \{totalBlocks}, \{totalStmts}");
                }
            }
        }

        public static void dot(T prog) {
            switch (prog) {
                case Singleton(
                        Id mainClassId,
                        Id mainFuncId,
                        List<Vtable.T> vtables,
                        List<Struct.T> structs,
                        List<Function.T> functions
                ) -> functions.forEach(Function::dot);
            }
        }

        public static void pp(T prog) {
            switch (prog) {
                case Singleton(
                        Id mainClassId,
                        Id mainFuncId,
                        List<Vtable.T> vtables,
                        List<Struct.T> structs,
                        List<Function.T> functions
                ) -> {
                    printSpaces();
                    sayln(STR."// the entry function: \{mainClassId}: \{mainFuncId}");
                    // vtables
                    vtables.forEach(Vtable::pp);
                    // structs
                    structs.forEach(Struct::pp);
                    // functions:
                    functions.forEach(Function::pp);
                }
            }
        }
    }
}
