package cfg;

import ast.Ast;
import ast.PrettyPrinter;
import control.Control;
import util.*;
import util.Error;


import java.io.FileInputStream;
import java.io.ObjectInputStream;

import javax.swing.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.lang.invoke.TypeDescriptor;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class Translate {
    // the generated results:
    private final Vector<Cfg.Vtable.T> vtables;
    private final Vector<Cfg.Struct.T> structs;
    private final Vector<Cfg.Function.T> functions;
    // for bookkeeping purpose:
    private Id currentClassId = null;
    private Id currentThis = null;
    private Cfg.Function.T currentFunction = null;
    private Cfg.Block.T currentBlock = null;
    private LinkedList<Cfg.Dec.T> newDecs = new LinkedList<>();
    // for main function
    private Id mainClassId = null;
    private Id mainFunctionId = null;
    private Ast.Dec.T dec;

    public Translate() {
        this.vtables = new Vector<>();
        this.structs = new Vector<>();
        this.functions = new Vector<>();
    }

    /////////////////////////////
    // translate a type
    private Cfg.Type.T doitType(Ast.Type.T ty) {
        switch (ty) {
            case Ast.Type.ClassType(Id id) -> {
                return new Cfg.Type.ClassType(id);
            }
            case Ast.Type.Boolean() -> {
                return new Cfg.Type.Int();
            }
            case Ast.Type.IntArray() -> {
                return new Cfg.Type.IntArray();
            }
            case Ast.Type.Int() -> {
                return new Cfg.Type.Int();
            }
        }
    }

    private Cfg.Dec.T doitDec(Ast.Dec.T dec) {
        // 变量声明不用 emit 因为这部分不算在 block 里面，而 emit 是把语句放进当前 block 中
        switch (dec) {
            case Ast.Dec.Singleton(Ast.Type.T type, Ast.AstId aid) -> {
                Cfg.Dec.Singleton res = new Cfg.Dec.Singleton(doitType(type), aid.freshId);
                // emitDec(res);
                return res;
            }
        }
    }

    private List<Cfg.Dec.T> doitDecList(List<Ast.Dec.T> decs) {
        return decs.stream().map(this::doitDec).collect(Collectors.toList());
    }

    /**
     * 将 s 放入 currentBlock 中
     * @param s 控制流图语句
     */
    private void emit(Cfg.Stm.T s) {
        Cfg.Block.add(this.currentBlock, s);
    }

    private void emitTransfer(Cfg.Transfer.T s) {
        Cfg.Block.addTransfer(this.currentBlock, s);
    }

    private void emitDec(Cfg.Dec.T dec) {
        this.newDecs.add(dec);
    }

    /////////////////////////////
    // translate an expression
    // TODO: lab3, exercise 8.
    private Id doitExp(Ast.Exp.T exp) {
        // throw new Todo();
        switch (exp) {
            case Ast.Exp.ExpId(Ast.AstId id) -> {
                return id.freshId;
            }
            case Ast.Exp.Num(int num) -> {
                Id x = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), x));
                emit(new Cfg.Stm.Assign(x, new Cfg.Exp.Int(num)));
                return x;
            }
            case Ast.Exp.True() -> {
                Id x = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), x));
                emit(new Cfg.Stm.Assign(x, new Cfg.Exp.Int(0)));
                return x;
            }
            case Ast.Exp.False() -> {
                Id x = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), x));
                emit(new Cfg.Stm.Assign(x, new Cfg.Exp.Int(1)));
                return x;
            }
            case Ast.Exp.ArraySelect(Ast.Exp.T array, Ast.Exp.T index) -> {
                Id arrayId = doitExp(array);
                Id indexId = doitExp(index);
                Id tempId = Id.newNoname();
                // 会出现不太符合直觉的语句形式
                // A[y] = z ->
                // x1 = A
                // x2 = y
                // x3 = x1[x2]
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), tempId));
                emit(new Cfg.Stm.Assign(tempId, new Cfg.Exp.ArraySelect(arrayId, indexId)));
                return tempId;
            }
            case Ast.Exp.Bop(Ast.Exp.T left, String op, Ast.Exp.T right) -> {
                Id leftId = doitExp(left);
                Id rightId = doitExp(right);
                Id tempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), tempId));
                emit(new Cfg.Stm.Assign(tempId,
                                        new Cfg.Exp.Bop(op, List.of(leftId, rightId), new Cfg.Type.Int())));
                return tempId;
            }
            case Ast.Exp.BopBool(Ast.Exp.T left, String op, Ast.Exp.T right) -> {
                Id leftId = doitExp(left);
                Id rightId = doitExp(right);
                Id tempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), tempId));
                emit(new Cfg.Stm.Assign(tempId,
                        new Cfg.Exp.Bop(op, List.of(leftId, rightId), new Cfg.Type.Int())));
                return tempId;
            }
            case Ast.Exp.Call(Ast.Exp.T exp_,
                              Ast.AstId methodId,
                              List<Ast.Exp.T> args,
                              Tuple.One<Id> theObjectType,
                              Tuple.One<Ast.Type.T> retType) -> {
                // TODO: 这个实现好怪，总感觉有问题，晚点看看
                // exp_.methodId(args)
                Id x = Id.newNoname();
                // CodePtr x;
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.CodePtr(), x));

                Id expId = doitExp(exp_);
                // x = getMethod(this, ClassName, MethodName)
                emit(new Cfg.Stm.Assign(x, new Cfg.Exp.GetMethod(expId, theObjectType.get(), methodId.id)));

                LinkedList<Id> argIds = new LinkedList<>();
                for (Ast.Exp.T arg : args) {
                    argIds.add(doitExp(arg));
                }
                // argIds.addFirst(Id.newName("this"));
                argIds.addFirst(expId);

                // 接收函数调用返回值的id
                Id retTempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(doitType(retType.get()), retTempId));

                // x_1 = x(this, argIds, ) @retType: int
                emit(new Cfg.Stm.Assign(retTempId, new Cfg.Exp.Call(x, argIds, doitType(retType.get()))));
                return retTempId;
            }
            case Ast.Exp.Length(Ast.Exp.T array) -> {
                Id arrayId = doitExp(array);
                Id tempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), tempId));
                emit(new Cfg.Stm.Assign(tempId, new Cfg.Exp.Length(arrayId)));
                return tempId;
            }
            case Ast.Exp.NewIntArray(Ast.Exp.T expp) -> {
                // x = new int[exp]
                Id expId = doitExp(expp);
                Id tempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), tempId));
                emit(new Cfg.Stm.Assign(tempId, new Cfg.Exp.NewIntArray(expId)));
                return tempId;
            }
            case Ast.Exp.NewObject(Id id) -> {
                Id tempId = Id.newNoname();
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.ClassType(id), tempId));
                emit(new Cfg.Stm.Assign(tempId, new Cfg.Exp.New(id)));
                return tempId;
            }
            case Ast.Exp.This() -> {
                Id temId = Id.newName("this");
                return temId;
            }
            case Ast.Exp.Uop(String op, Ast.Exp.T expp) -> {
                Id expId = doitExp(expp);
                Id tempId = Id.newNoname();
                emit(new Cfg.Stm.Assign(tempId, new Cfg.Exp.Bop(op, List.of(expId), new Cfg.Type.Int())));
                return tempId;
            }
            default -> throw new IllegalStateException(STR."Unexpected value: \{exp}");
        }
    }

    /////////////////////////////
    // translate a statement
    // this function does not return its result,
    // but saved the result into "currentBlock"
    // TODO: lab3, exercise 8.
    private void doitStm(Ast.Stm.T stm) {
        // throw new Todo();
        switch (stm) {
            case Ast.Stm.Assign(Ast.AstId aid, Ast.Exp.T exp) -> {
                // 不能直接赋值，必须转一层 aid = exp
                // -> x = exp; aid = x
                Id expId = doitExp(exp);
                emit(new Cfg.Stm.Assign(aid.freshId, new Cfg.Exp.Eid(expId, doitType(aid.type))));
            }
            case Ast.Stm.AssignArray(Ast.AstId id, Ast.Exp.T index, Ast.Exp.T exp) -> {
                // ArrayId[index] = exp ->
                // ArrayId[x_1] = x_2
                Id indexId = doitExp(index);
                Id expId = doitExp(exp);
                emit(new Cfg.Stm.AssignArray(id.freshId,
                        new Cfg.Exp.Eid(indexId, new Cfg.Type.Int()),
                        new Cfg.Exp.Eid(expId, new Cfg.Type.Int())));
            }
            case Ast.Stm.Block(List<Ast.Stm.T> stms) -> {
                for (Ast.Stm.T s : stms) {
                    doitStm(s);
                }
            }
            case Ast.Stm.If(Ast.Exp.T cond, Ast.Stm.T thenn, Ast.Stm.T elsee) -> {
                Id condId = doitExp(cond);
                Cfg.Block.T thenBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                Cfg.Block.T elseBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                Cfg.Block.T joinBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());

                // 把新产生的block全部放到对应的函数中
                Cfg.Function.Singleton currentF = (Cfg.Function.Singleton) this.currentFunction;
                currentF.blocks().add(thenBlock);
                currentF.blocks().add(elseBlock);
                currentF.blocks().add(joinBlock);

                emitTransfer(new Cfg.Transfer.If(condId, thenBlock, elseBlock));

                this.currentBlock = thenBlock;
                doitStm(thenn);
                emitTransfer(new Cfg.Transfer.Jmp(joinBlock));

                this.currentBlock = elseBlock;
                doitStm(elsee);
                emitTransfer(new Cfg.Transfer.Jmp(joinBlock));

                this.currentBlock = joinBlock;
            }
            case Ast.Stm.Print(Ast.Exp.T exp) -> {
                Id temId = Id.newNoname();
                // int temId
                emitDec(new Cfg.Dec.Singleton(new Cfg.Type.Int(), temId));
                Id expId = doitExp(exp);
                // temId = print(exp)
                emit(new Cfg.Stm.Assign(temId, new Cfg.Exp.Print(expId)));
            }
            case Ast.Stm.While(Ast.Exp.T cond, Ast.Stm.T body) -> {
                Cfg.Block.T condBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                Cfg.Block.T bodyBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                Cfg.Block.T joinBlock = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());

                // 把新产生的block全部放到对应的函数中
                Cfg.Function.Singleton currentF = (Cfg.Function.Singleton) this.currentFunction;
                currentF.blocks().add(condBlock);
                currentF.blocks().add(bodyBlock);
                currentF.blocks().add(joinBlock);

                // 条件block可能会被多次进入，所以不能和 while 前的 block 放在一起
                emitTransfer(new Cfg.Transfer.Jmp(condBlock));

                this.currentBlock = condBlock;
                Id condId = doitExp(cond);
                emitTransfer(new Cfg.Transfer.If(condId, bodyBlock, joinBlock));

                this.currentBlock = bodyBlock;
                doitStm(body);
                emitTransfer(new Cfg.Transfer.Jmp(condBlock));

                this.currentBlock = joinBlock;
            }
        }
    }

    // translate a method
    // TODO: lab3, exercise 8.
    private Cfg.Function.T doitMethod(Ast.Method.T method) {
        // throw new Todo();
        switch (method) {
            case Ast.Method.Singleton(Ast.Type.T retType,
                                      Ast.AstId methodId,
                                      List<Ast.Dec.T> formals,
                                      List<Ast.Dec.T> locals,
                                      List<Ast.Stm.T> stms,
                                      Ast.Exp.T retExp) -> {
                // 函数块列表
                Vector<Cfg.Block.T> cfgBlocks = new Vector<>();

                // 处理形参和变量声明
                List<Cfg.Dec.T> cfgFormals = doitDecList(formals);
                List<Cfg.Dec.T> cfgLocals = doitDecList(locals);

                // 创建 CFG 函数
                Cfg.Function.Singleton cfgFunction = new Cfg.Function.Singleton(
                        doitType(retType),
                        this.currentClassId,
                        methodId.freshId,
                        cfgFormals,
                        cfgLocals,
                        cfgBlocks
                );
                this.currentFunction = cfgFunction;

                // TODO: 添加 this 参数 在 prefixOneClass 中完成
                // cfgFormals.add(new Cfg.Dec.Singleton(new Cfg.Type.ClassType(this.currentClassId), this.currentThis));

                // 创建一个新的块
                Cfg.Block.T block = new Cfg.Block.Singleton(new Label(), new LinkedList<>(), new LinkedList<>());
                this.currentBlock = block;
                // 将块添加到函数中
                cfgBlocks.add(block);

                // 处理方法体
                for (Ast.Stm.T stm : stms) {
                    doitStm(stm);
                }

                // 处理返回语句
                Id retId = doitExp(retExp);
                emitTransfer(new Cfg.Transfer.Ret(retId));
                // 把新生成的声明语句加进函数中，然后清空收集新声明的集合
                cfgLocals.addAll(this.newDecs);
                this.newDecs.clear();

                return cfgFunction;
            }
        }
    }

    // the prefixing algorithm
    // TODO: lab3, exercise 6.
    // called by: prefixOneClass(tree.node.data,
    //                           Tuple.Two<>(new Vector<>(),new Vector<>()))
    // 这个方法相当于doitClass了；
    // 在tree中会被递归调用，结果会被传入第二层调用的第二个参数

    /**
     * 利用前缀法将一个 Ast.Class.T 类的继承关系映射到控制流图的结构中，并生成 cfg 类的构成要素
     * @param cls Ast.Class.T 类
     * @param decsAndFunctions cls 的父类的属性和方法
     * @return cls 类的属性和方法
     */
    private Tuple.Two<Vector<Cfg.Dec.T>, Vector<Cfg.Vtable.Entry>> prefixOneClass(
            Ast.Class.T cls,
            Tuple.Two<Vector<Cfg.Dec.T>,
                    Vector<Cfg.Vtable.Entry>> decsAndFunctions) {
        // throw new Todo();

        // 复制一个二元组，不知道直接引用会不会有浅拷贝的问题，复制最保险
        Tuple.Two<Vector<Cfg.Dec.T>, Vector<Cfg.Vtable.Entry>> res = new Tuple.Two<>(new Vector<>(), new Vector<>());
        decsAndFunctions.first().forEach((dec) -> {
            res.first().add(dec);
        });
        decsAndFunctions.second().forEach((function) -> {
            res.second().add(function);
        });

        switch (cls) {
            case Ast.Class.Singleton(Id classId,
                                     Id extends_,
                                     List<Ast.Dec.T> decs,
                                     List<Ast.Method.T> methods,
                                     Tuple.One<Ast.Class.T> parent) -> {
                this.currentClassId = classId;

                // 处理当前类的 属性, mainClass 无
                res.first().addAll(doitDecList(decs));

                // 处理当前类的 方法，添加 this 参数
                for (Ast.Method.T method : methods) {
                    Ast.Method.Singleton methodSingle = (Ast.Method.Singleton) method;
                    // 添加 this 参数，Ast.Method.Singleton.formals 在 Parser 中是用 LinkedList 构造的，可以直接add
                    methodSingle.formals().addFirst(new Ast.Dec.Singleton(new Ast.Type.ClassType(classId), new Ast.AstId(Id.newName("this"))));

                    // 转换成 cfg 的函数对象
                    Cfg.Function.Singleton func = (Cfg.Function.Singleton)doitMethod(methodSingle);
                    // 添加到全局函数列表
                    this.functions.add(func);
                    // 添加到嗷当前类的 虚函数表 中
                    res.second().add(new Cfg.Vtable.Entry(func.retType(), func.classId(), func.functionId(), func.formals()));
                }
                // 现在 res.first 中包含当前类的所有属性，res.second 中包含当前类的所有方法（虚函数表）

                // 创建虚函数表
                Cfg.Vtable.T vtable = new Cfg.Vtable.Singleton(this.currentClassId, res.second());
                this.vtables.add(vtable);

                // 创建结构体
                Cfg.Struct.T struct = new Cfg.Struct.Singleton(this.currentClassId, res.first());
                this.structs.add(struct);
            }
        }
        return res;
    }

    // build an inherit tree
    // TODO: lab3, exercise 5.
    private Tree<Ast.Class.T> buildInheritTree0(Ast.Program.T ast) {
        // public record Singleton(Id classId,
        //                         Id extends_, // "null" for non-existing "extends"
        //                         List<Ast.Dec.T> decs,
        //                         List<ast.Ast.Method.T> methods,
        //                         // contain null element for non-existing parent
        //                         Tuple.One<Ast.Class.T> parent) implements Ast.Class.T {
        // }
        // throw new Todo();
        // 如果没通过 Check 设置 Class 的 parent 字段，这个函数用不了
        // 顶级父节点 objectClass
        Tree<Ast.Class.T> tree = new Tree<>(STR."InheritTree_\{ast.toString()}");

        Ast.Class.Singleton objectClass = new Ast.Class.Singleton(
                Id.newName("Object"),
                null,
                List.of(),
                List.of(),
                new Tuple.One<>());
        // 把 objectClass 设置为继承树的根节点
        tree.addRoot(objectClass);

        switch (ast) {
            case Ast.Program.Singleton(Ast.MainClass.T mainClass,
                                       List<Ast.Class.T> classes) -> {
                // 构建新的 mainClass: mainClass -> Class
                // 其中没有 属性，只有一个叫做 main 的方法
                switch (mainClass){
                    case Ast.MainClass.Singleton(Id classId,
                                                 Ast.AstId arg,
                                                 Ast.Stm.T stm) -> {
                        Ast.AstId newMainFunction = new Ast.AstId(Id.newName("main"));
                        LinkedList<Ast.Stm.T> stms = new LinkedList<>();
                        stms.add(stm);
                        Ast.Class.Singleton newMainClass = new Ast.Class.Singleton(
                                classId,
                                null,
                                // decs
                                new LinkedList<>(),
                                // methods: int main() { locals{}; stms; return 0;}
                                List.of(new Ast.Method.Singleton(Ast.Type.getInt(),
                                        newMainFunction,
                                        new LinkedList<>(),
                                        new LinkedList<>(),
                                        stms,
                                        // return 0
                                        new Ast.Exp.Num(0))),
                                new Tuple.One<>());
                        this.mainClassId = newMainClass.classId();
                        this.mainFunctionId = newMainFunction.id;

                        // 把 mainClass 添加到树中
                        tree.addNode(newMainClass);
                        tree.addEdge(objectClass, newMainClass);
                    }
                }

                // 遍历所有的类如果有父节点则添加结点和父类指向子类的边
                // parent 为 null，则直接将其挂在根节点下
                classes.forEach((aclass) -> {
                    Ast.Class.Singleton cls = (Ast.Class.Singleton) aclass;
                    if (cls.extends_() != null) {
                        Ast.Class.Singleton parentClass = (Ast.Class.Singleton)cls.parent().get();
                        tree.addNode(cls);
                        tree.addNode(parentClass);
                        tree.addEdge(parentClass, cls);
                        System.out.println("=========== add parentClass edge done ============");
                    } else {
                        tree.addNode(cls);
                        tree.addEdge(objectClass, cls);
                        System.out.println("=========== add nonparentClass edge done ============");
                    }
                });
                return tree;
            }
        }
    }


    private Tree<Ast.Class.T> buildInheritTree(Ast.Program.T ast) {
        Trace<Ast.Program.T, Tree<Ast.Class.T>> trace =
                new Trace<>("cfg.Translate.buildInheritTree",
                        this::buildInheritTree0,
                        ast,
                        new PrettyPrinter()::ppProgram,
                        (tree) -> {
                            // TODO: lab3, exercise 5.
                            // visualize the tree
                            if (Control.Dot.beingDotted("inheritTree"))
                                // throw new Todo();
                                tree.output(tree.root);
                        });
        return trace.doit();
    }

    private Cfg.Program.T doitProgram0(Ast.Program.T ast) {
        // if we are using the builtin AST, then do not generate
        // the CFG, but load the CFG directly from disk
        // and return it.
        if (Control.bultinAst != null) {
            Cfg.Program.T result;
            String serialFileName;
            try {
                File dir = new File("");
                serialFileName = dir.getCanonicalPath() + "/src/cfg/SumRec.java.cfg.ser";

                FileInputStream fileIn = new FileInputStream(serialFileName);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                result = (Cfg.Program.T) in.readObject();
                in.close();
                fileIn.close();
            } catch (Exception e) {
                throw new Error(e);
            }
            // 将原本的cfg图反序列化得到 Cfg.Program.T
            return result;
        }
        
        // Step #1: build the inheritance tree
        Tree<Ast.Class.T> tree = buildInheritTree(ast);
        // Step #2: perform prefixing via a level-order traversal
        // we also translate each method during this traversal.
        // 如果 tree.node 里面有 mainClass 的话会发生什么
        tree.levelOrder(tree.root,
                this::prefixOneClass,
                new Tuple.Two<>(new Vector<>(),
                        new Vector<>()));

        return new Cfg.Program.Singleton(this.mainClassId,
                this.mainFunctionId,
                this.vtables,
                this.structs,
                this.functions);
    }

    // given an abstract syntax tree, lower it down
    // to a corresponding control-flow graph.
    public Cfg.Program.T doitProgram(Ast.Program.T ast) {
        Trace<Ast.Program.T, Cfg.Program.T> trace =
                new Trace<>("cfg.Translate.doitProgram",
                        this::doitProgram0,
                        ast,
                        new PrettyPrinter()::ppProgram,
                        (x) -> {
                            Cfg.Program.pp(x);
                            if (Control.Dot.beingDotted("cfg")) {
                                Cfg.Program.dot(x);
                            }
                        });
        return trace.doit();
    }
}