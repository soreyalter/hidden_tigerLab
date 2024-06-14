package cfg;

import ast.Ast;
import util.Id;
import util.Label;
import util.Todo;
import util.Tuple;

import java.lang.classfile.CodeBuilder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Liveness {

    // record information within a single "map"
    // the first is useSet, the second is defSet
    private final HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
            useDefMap;

    // for "block", "transfer", and "statement".
    private final HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
            liveInOutMap;

    public Liveness() {
        useDefMap = new HashMap<>();
        liveInOutMap = new HashMap<>();
    }

    // out[s] = And_p∈s.successor(in[p])
    // in[s] = use[s] ∪ (out[s] - def[s])

    // Rule 1. L(p, x, out) = ∨{L(s, x, in) | s a successor of p}
    // Rule 2. L(s, x, in) = true if s refers to x on the rhs
    // Rule 3. L(x := e, x, in) = false if e does not refer to x
    // Rule 4. L(s, x, in) = L(s, x, out) if s does not refer to x

    // /////////////////////////////////////////////////////////
    // statement
    private void doitStm(Cfg.Stm.T t) {
        // throw new Todo();
        // 仅仅是计算 stm 的 use 和 def 集合，不计算 live
        // 如果已经构建过这条 stm 的 use 和 def ，直接返回
        if (this.useDefMap.containsKey(t)) return;
        // 被消费的变量集合
        Set<Id> genSet = new Set<>();
        // 被定义的变量集合
        Set<Id> killSet = new Set<>();
        // 需要进一步解析才能得到变量Id的 cfg.exp.T 表达式
        LinkedList<Cfg.Exp.T> expList = new LinkedList<>();
        switch (t) {
            case Cfg.Stm.Assign(Id id, Cfg.Exp.T exp) ->{
                killSet.add(id);
                expList.add(exp);
            }
            case Cfg.Stm.AssignArray(Id array, Cfg.Exp.T index, Cfg.Exp.T value) -> {
                killSet.add(array);
                expList.add(index);
                expList.add(value);
            }
            default -> throw new IllegalStateException(STR."Unexpected value: \{t}");
        }
        for (Cfg.Exp.T exp : expList) {
            switch (exp) {
                case Cfg.Exp.Bop(String op, List<Id> operands, Cfg.Type.T type) -> {
                    operands.forEach(genSet::add);
                }
                case Cfg.Exp.NewIntArray(Id size) -> {
                    genSet.add(size);
                }
                case Cfg.Exp.Length(Id array) -> {
                    genSet.add(array);
                }
                case Cfg.Exp.Eid(Id id, Cfg.Type.T type) -> {
                    genSet.add(id);
                }
                case Cfg.Exp.Int(int num) -> {}
                case Cfg.Exp.ArraySelect(Id array, Id index) -> {
                    genSet.add(array);
                    genSet.add(index);
                }
                case Cfg.Exp.GetMethod(Id objId,   // 实例
                                       Id classId, // 类名
                                       Id methodId) -> {
                    genSet.add(objId);
                    // genSet.add(methodId);
                }
                case Cfg.Exp.Call(Id func,
                                  List<Id> operands,
                                  Cfg.Type.T retType) -> {
                    genSet.add(func);
                    // 把 this 排除出去，只有在类的方法里面自己调自己才会出现 this，此时 this 只是一种形式上的写法，不是真正的变量
                    for (Id operand : operands) {
                        if (!Id.originalEquals(Id.newName("this"), operand)) {
                            genSet.add(operand);
                        }
                    }
                }
                case Cfg.Exp.Print(Id x) -> {
                    genSet.add(x);
                }
                case Cfg.Exp.New(Id classId) -> {
                }
                default -> throw new IllegalStateException(STR."Unexpected value: \{exp}");
            }
        }
        Tuple.Two<Set<Id>, Set<Id>> stmUseAndDef = new Tuple.Two<>(genSet, killSet);
        this.useDefMap.put(t, stmUseAndDef);


    }
    // end of statement

    // /////////////////////////////////////////////////////////
    // transfer
    private void doitTransfer(Cfg.Transfer.T t) {
        // throw new Todo();
        if (this.useDefMap.containsKey(t)) return;
        // 被消费的变量集合
        Set<Id> genSet = new Set<>();
        // 被定义的变量集合
        Set<Id> killSet = new Set<>();
        // transfer 的 liveIn 和 liveOut 集合可以在这里初始化
        // Tuple.Two<Set<Id>, Set<Id>> inAndOut = new Tuple.Two<>(new Set<>(), new Set<>());

        switch (t) {
            case Cfg.Transfer.If(Id x,
                                 Cfg.Block.T trueBlock,
                                 Cfg.Block.T falseBlock) -> {
                // 消费 if 的条件
                genSet.add(x);
                // 处理两个基本块
                // doitBlock(trueBlock);
                // doitBlock(falseBlock);
            }
            case Cfg.Transfer.Ret(Id x) -> genSet.add(x);
            case Cfg.Transfer.Jmp(Cfg.Block.T _) -> {}
            default -> throw new IllegalStateException(STR."Unexpected value: \{t}");
        }
        Tuple.Two<Set<Id>, Set<Id>> tfUseAndDef = new Tuple.Two<>(genSet, killSet);
        this.useDefMap.put(t, tfUseAndDef);

    }

    // /////////////////////////////////////////////////////////
    // block
    private void doitBlock(Cfg.Block.T b) {
        switch (b) {
            case Cfg.Block.Singleton(
                    Label label,
                    List<Cfg.Stm.T> stms,
                    List<Cfg.Transfer.T> transfer
            ) -> {
                // throw new Todo();
                // 本块的 livein 和 liveout
                Set<Id> liveOut = this.liveInOutMap.get(b).second();
                Set<Id> liveIn = this.liveInOutMap.get(b).first();

                assert !stms.isEmpty();
                // 标记 stms 的入口和出口
                Cfg.Stm.T BlockIn = stms.getFirst();
                Cfg.Stm.T BlockOut = stms.getLast();

                // 计算每一条 statement 的 use 和 def 集合
                for (Cfg.Stm.T stm : stms) {
                    doitStm(stm);
                }

                // 处理 transfer
                for (Cfg.Transfer.T t : transfer) {
                    // 计算 transfer 的 use 和 def 集合
                    doitTransfer(t);

                    // transfer 的 in 和 out 集合 每一次迭代都是要重新从空集计算的，不继承上一次的结果
                    Tuple.Two<Set<Id>, Set<Id>> currentTransferInAndOut = new Tuple.Two<>(new Set<>(), new Set<>());
                    // this.liveInOutMap.put(t, currentTransferInAndOut);
                    // 计算 transfer 的 out集合
                    switch (t) {
                        case Cfg.Transfer.If(Id x,
                                             Cfg.Block.T trueBlock,
                                             Cfg.Block.T falseBlock) -> {
                            // 处理两个后继块
                            // out[s] = And_p∈s.successor(in[p])
                            // in[s] = use[s] ∪ (out[s] - def[s])

                            // out[s] ∪ in[trueBlock]
                            currentTransferInAndOut.second().union(this.liveInOutMap.get(trueBlock).first());
                            // out[s] ∪ in[falseBlock]
                            currentTransferInAndOut.second().union(this.liveInOutMap.get(falseBlock).first());

                            Tuple.Two<Set<Id>, Set<Id>> UseAndDef = this.useDefMap.get(t);
                            Set<Id> newUse = UseAndDef.first().clone();
                            Set<Id> newOut = currentTransferInAndOut.second().clone();
                            Set<Id> newDef = UseAndDef.second().clone();
                            // out[s] - def[s]
                            newOut.sub(newDef);
                            // use[s] ∪ (out[s] - def[s])
                            newUse.union(newOut);
                            // in[s] = use[s] ∪ (out[s] - def[s])
                            currentTransferInAndOut.first().union(newUse);
                        }
                        case Cfg.Transfer.Ret(Id x) -> {
                            // ret 语句的 out 必然是空集，所以 in 就是 use 集合
                            Tuple.Two<Set<Id>, Set<Id>> UseAndDef = this.useDefMap.get(t);
                            Set<Id> newUse = UseAndDef.first().clone();
                            // in[s] = use[s] ∪ (out[s] - def[s])
                            currentTransferInAndOut.first().union(newUse);
                        }
                        case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                            // out[s] = And_p∈s.successor(in[p])
                            currentTransferInAndOut.second().union(this.liveInOutMap.get(target).first());
                            Tuple.Two<Set<Id>, Set<Id>> UseAndDef = this.useDefMap.get(t);
                            Set<Id> newUse = UseAndDef.first().clone();
                            Set<Id> newOut = currentTransferInAndOut.second().clone();
                            Set<Id> newDef = UseAndDef.second().clone();
                            // out[s] - def[s]
                            newOut.sub(newDef);
                            // use[s] ∪ (out[s] - def[s])
                            newUse.union(newOut);
                            // in[s] = use[s] ∪ (out[s] - def[s])
                            currentTransferInAndOut.first().union(newUse);
                        }
                        default -> throw new IllegalStateException(STR."Unexpected value: \{t}");
                    }
                    // 更新本次迭代中 transfer 的 in 和 out
                    this.liveInOutMap.put(t, currentTransferInAndOut);
                    liveOut.union(currentTransferInAndOut.second());
                }

                // 处理最后一条 stm，他是特殊的，因为他后面是 transfer 语句而不是 stm
                // 初始化语句的 livein 和 liveout 为空集
                Tuple.Two<Set<Id>, Set<Id>> LastInOut = this.liveInOutMap.get(BlockOut);
                Tuple.Two<Set<Id>, Set<Id>> LastUseDef = this.useDefMap.get(BlockOut);
                // out = transfer.in
                LastInOut.second().union(this.liveInOutMap.get(transfer.getFirst()).first());
                Set<Id> newLastOut = LastInOut.second().clone();
                Set<Id> newLastDef = LastUseDef.second().clone();
                // out[s] = out[s] - def[s]
                newLastOut.sub(newLastDef);
                // in[s] = use[s]
                LastInOut.first().union(LastUseDef.first());
                // in[s] = in[s] ∪ out[s]
                LastInOut.first().union(newLastOut);

                // 前向计算每条 stm 的 livein 和 liveout
                for (int i = stms.size()-2; i >= 0; i--) {
                    // 初始化语句的 livein 和 liveout 为上一次迭代的结果
                    Tuple.Two<Set<Id>, Set<Id>> stmInOut = this.liveInOutMap.get(stms.get(i));
                    Tuple.Two<Set<Id>, Set<Id>> stmUseDef = this.useDefMap.get(stms.get(i));

                    // out[s] = And_p∈s.successor(in[p])
                    // in[s] = use[s] ∪ (out[s] - def[s])
                    // 上一条 stm 的 use 和 def
                    stmInOut.second().union(this.liveInOutMap.get(stms.get(i+1)).first());

                    Set<Id> newOut = stmInOut.second().clone();
                    Set<Id> newDef = stmUseDef.second().clone();
                    // out[s] = out[s] - def[s]
                    newOut.sub(newDef);
                    // in[s] = in[s] ∪ use[s]
                    stmInOut.first().union(stmUseDef.first());
                    // in[s] = in[s] ∪ out[s]
                    stmInOut.first().union(newOut);
                    // 更新当前 stm 的 in 和 out 集合
                    // this.liveInOutMap.put(stms.get(i), stmInOut);
                }
                liveIn.union(this.liveInOutMap.get(BlockIn).first());
                // this.liveInOutMap.put(b, new Tuple.Two<>(liveIn, liveOut));
            } // case single

        }// end of switch
    }

    // /////////////////////////////////////////////////////////
    // function
    // TODO: lab3, exercise 9.
    private boolean stillChanging = true;

    private void doitFunction(Cfg.Function.T func) {
        // 每个 method 是独立的
        this.stillChanging = true;
        switch (func) {
            case Cfg.Function.Singleton(
                    Cfg.Type.T retType,
                    Id classId,
                    Id functionId,
                    List<Cfg.Dec.T> formals,
                    List<Cfg.Dec.T> locals,
                    List<Cfg.Block.T> blocks
            ) -> {
                // throw new Todo();
                // 初始化 每个块，及其内语句 的 in 和 out 为空集
                for (Cfg.Block.T block : blocks) {
                    this.liveInOutMap.put(block, new Tuple.Two<>(new Set<>(), new Set<>()));
                    switch (block) {
                        case Cfg.Block.Singleton(Label label,
                                                 List<Cfg.Stm.T> stms,
                                                 // this is a special hack
                                                 // the transfer field is final, so that
                                                 // we use a list instead of a singleton field
                                                 List<Cfg.Transfer.T> transfer) ->{
                            for (Cfg.Stm.T stm : stms) {
                                this.liveInOutMap.put(stm, new Tuple.Two<>(new Set<>(), new Set<>()));
                            }
                            this.liveInOutMap.put(transfer.getFirst(), new Tuple.Two<>(new Set<>(), new Set<>()));
                        }
                    }
                }

                while (stillChanging) {
                    boolean changing = false;
                    for (int i = blocks.size()-1; i >= 0; i--) {
                        // 保存函数中每一个块的状态
                        Set<Id> oldIn = new Set<>();
                        Set<Id> oldOut = new Set<>();

                        Tuple.Two<Set<Id>, Set<Id>> currentBlockInOut = this.liveInOutMap.get(blocks.get(i));
                        oldIn.union(currentBlockInOut.first());
                        oldOut.union(currentBlockInOut.second());

                        // 计算 block 的 in 和 out
                        Cfg.Block.Singleton b = (Cfg.Block.Singleton) blocks.get(i);
                        doitBlock(b);

                        // 比较新旧是否相同，只要有一个不同就把 changing 置为 true
                        Tuple.Two<Set<Id>, Set<Id>> afterBlockInOut = this.liveInOutMap.get(blocks.get(i));
                        if (!oldIn.isSame(afterBlockInOut.first())
                                || !oldOut.isSame(afterBlockInOut.second())) {
                            changing = true;
                        }
                        this.stillChanging = changing;

                        // // 类似 树状结构 后序遍历 基本块，但其中由于 ast.while 会生成循环的图，所以不是树
                        // switch (b.transfer().getFirst()) {
                        //     case Cfg.Transfer.If(Id x,
                        //                          Cfg.Block.T trueBlock,
                        //                          Cfg.Block.T falseBlock) -> {
                        //         doitBlock(trueBlock);
                        //         doitBlock(falseBlock);
                        //     }
                        //     case Cfg.Transfer.Jmp(Cfg.Block.T target) -> {
                        //         doitBlock(target);
                        //     }
                        //     case Cfg.Transfer.Ret(Id x) -> {
                        //     }
                        //     default -> throw new IllegalStateException(STR."Unexpected value: \{b.transfer().getFirst()}");
                        // }
                        // // 处理完 子块，回来处理自己
                        // doitBlock(b);
                    }
                } // end of while

            }
        }
    }

    public HashMap<Object, Tuple.Two<Set<Id>, Set<Id>>>
    doitProgram(Cfg.Program.T prog) {
        switch (prog) {
            case Cfg.Program.Singleton(
                    Id mainClassId,
                    Id mainFuncId,
                    List<Cfg.Vtable.T> vtables,
                    List<Cfg.Struct.T> structs,
                    List<Cfg.Function.T> functions
            ) -> {
                functions.forEach(this::doitFunction);
                return liveInOutMap;
            }
        }
    }
}
