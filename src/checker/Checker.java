package checker;

import ast.Ast;
import ast.Ast.Class;
import ast.Ast.*;
import ast.PrettyPrinter;
import control.Control;
import util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Checker {
    // symbol table for all classes
    private final ClassTable classTable;
    // symbol table for each method
    private MethodTable methodTable;
    // 类型检查错误信息保存表，上限是4，有四条错误信息就会直接打印并停止编译
    private final List<String> errorInfo;
    private final int errorLimit = 3;
    // 类中字段引用次数表，用于检查未引用的变量
    private final HashMap<Id, HashMap<Id, Integer>> usedClassField;
    // 方法中形参和本地变量引用次数表
    private HashMap<Id, Integer> usedMethodField;
    // the class name being checked
    private Id currentClass;

    private void putMethodField(List<Dec.T> formals, List<Dec.T> locals) {
        for (Dec.T dec : formals) {
            Dec.Singleton decc = (Dec.Singleton) dec;
            Ast.AstId aid = decc.aid();
            if (this.usedMethodField.get(aid.id) != null) {
                System.out.println(STR."duplicated parameter: \{aid.id}");
                System.exit(1);
            }
            this.usedMethodField.put(aid.id, 0);
        }

        for (Dec.T dec : locals) {
            Dec.Singleton decc = (Dec.Singleton) dec;
            Ast.AstId aid = decc.aid();
            if (this.usedMethodField.get(aid.id) != null) {
                System.out.println(STR."duplicated variable: \{aid.id}");
                System.exit(1);
            }
            this.usedMethodField.put(aid.id, 0);
        }
    }

    /**
     * 将一个类的 classId, fields 以及 field 的引用次数，记录到哈希表 usedClassField 中
     * @param c Class.T
     */
    private void putClassField(Class.Singleton c) {
        if (this.usedClassField.get(c.classId()) != null) {
            System.out.println(STR."duplicated parameter: \{c.classId()}");
            System.exit(1);
        }
        HashMap<Id, Integer> fields = new HashMap<>();
        for (Dec.T dec : c.decs()) {
            Dec.Singleton d = (Dec.Singleton) dec;
            Id fieldId = d.aid().id;
            if (fields.get(fieldId) != null) {
                System.out.println(STR."duplicated parameter: \{fieldId}");
                System.exit(1);
            }
            fields.put(fieldId, 0);
        }
        this.usedClassField.put(c.classId(), fields);

    }

    public Checker() {
        this.classTable = new ClassTable();
        this.methodTable = new MethodTable();
        this.errorInfo = new ArrayList<>();
        this.currentClass = null;
        this.usedClassField = new HashMap<Id, HashMap<Id, Integer>>();
        this.usedMethodField = new HashMap<>();
    }

    private void error(String s) {
        // System.out.println(STR."Error: type mismatch: \{s}");
        if (errorInfo.size() == errorLimit) {
            for (String msg : errorInfo) {
                System.out.println(msg);
            }
            System.exit(1);
        }
        errorInfo.add(STR."Error: type mismatch: \{s}");
    }

    private void error(String s, Type.T expected, Type.T got) {
        // System.out.println(STR."Error: type mismatch: \{s}");
        // Type.output(expected);
        // Type.output(got);
        if (errorInfo.size() == errorLimit) {
            errorInfo.add(STR."Error: type mismatch: \{s}, expect \{Type.convertString(expected)}, but got \{Type.convertString(got)}");
            for (String msg : errorInfo) {
                System.out.println(msg);
            }
            System.exit(1);
        }
        errorInfo.add(STR."Error: type mismatch: \{s}, expect \{Type.convertString(expected)}, but got \{Type.convertString(got)}");
    }

    private void undeclError(AstId s) {
        if (errorInfo.size() == errorLimit) {
            errorInfo.add(STR."Warning: Variable '\{s}' is never defined");
            for (String msg : errorInfo) {
                System.out.println(msg);
            }
            System.exit(1);
        }
        errorInfo.add(STR."Warning: Variable '\{s}' is never defined");
    }

    // /////////////////////////////////////////////////////
    // ast-id
    // 返回一个 astId 的 Type
    private Type.T checkAstId(AstId aid) {
        boolean isClassField = false;
        // first search in current method table
        // 在当前 method 的形参列表和本地变量中找
        Tuple.Two<Ast.Type.T, Id> resultId = this.methodTable.get(aid.id);
        // not a local or formal, -> but a class field, maybe.
        if (resultId == null) {
            isClassField = true;
            // 在类的字段里面找
            resultId = this.classTable.getField(this.currentClass, aid.id);
        }
        if (resultId == null) {
            // 也不是类的属性，报错
            undeclError(aid);
            return null;
        }
        assert resultId != null;
        // set up the fresh
        // resultId -> <Type.T, Id>
        if (isClassField) {
            HashMap<Id, Integer> fmap = usedClassField.get(currentClass);
            fmap.put(aid.id, fmap.get(aid.id)+1);
            // System.out.println("Todo");
        } else {
            usedMethodField.put(aid.id, usedMethodField.get(aid.id)+1);
        }
        aid.freshId = resultId.second();
        aid.isClassField = isClassField;
        aid.type = resultId.first();
        return resultId.first();
    }

    // /////////////////////////////////////////////////////
    // expressions
    // type check an expression will return its type.
    private Type.T checkExp(Exp.T e) {
        switch (e) {
            case Exp.Call(
                    // theObject.methodId(args)
                    Exp.T theObject,
                    AstId methodId,
                    List<Exp.T> args,
                    Tuple.One<Id> calleeTy,
                    Tuple.One<Type.T> retTy
            ) -> {
                // var typeOfTheObject = checkExp(theObject);
                // theObject 类的classType
                Type.T typeOfTheObject = checkExp(theObject);
                // theObject 类的Id
                Id calleeClassId = null;
                if (Objects.requireNonNull(typeOfTheObject) instanceof Type.ClassType(Id calleeClassId_)) {
                    calleeClassId = calleeClassId_;
                    // put the return type onto the AST
                    calleeTy.set(calleeClassId);
                }
                // 查找被调用方法的 MethodType {Type.T retType, List<Ast.Type.T> argsType} 和 freshId
                var resultMethodId = this.classTable.getMethod(calleeClassId, methodId.id);
                if (resultMethodId == null) {
                    error(STR."method not found: \{calleeClassId} . \{methodId}");
                    return null;
                }
                // 方法 实参列表的类型 的 列表
                var resultArgs = args.stream().map(this::checkExp).toList();
                assert resultMethodId != null;
                methodId.freshId = resultMethodId.second();
                Ast.Type.T retType = resultMethodId.first().retType();
                // put the return type onto the AST
                retTy.set(retType);
                return retType;
            }
            case Exp.NewObject(Id classId) -> {
                var classBinding = this.classTable.getClass_(classId);
                return Type.getClassType(classId);
            }
            case Exp.Num(int n) -> {
                return Type.getInt();
            }
            case Exp.Bop(
                    Exp.T left,
                    String bop,
                    Exp.T right
            ) -> {
                var resultLeft = checkExp(left);
                var resultRight = checkExp(right);

                switch (bop) {
                    case "+", "-" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error(bop);
                        }
                        return Type.getInt();
                    }
                    case "<" -> {
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error("<");
                        }
                        return Type.getBool();
                    }
                    case "&&" -> {
                        if (Type.nonEquals(resultLeft, Type.getBool()) ||
                                Type.nonEquals(resultRight, Type.getBool())) {
                            error("&&");
                        }
                        return Type.getBool();
                    }
                    case "*" -> {
                        // 乘法和 加减法 没有区别啊为啥要单独拿出来
                        if (Type.nonEquals(resultLeft, Type.getInt()) ||
                                Type.nonEquals(resultRight, Type.getInt())) {
                            error("*");
                        }
                        return Type.getInt();
                    }
                    default -> {
                        // throw new Todo();
                        error(bop);
                        return null;
                    }
                }
            }
            case Exp.ExpId(AstId aid) -> {
                return checkAstId(aid);
            }
            case Exp.This() -> {
                return Type.getClassType(this.currentClass);
            }
            // Call, NewObject, Num, This, ExpId, Bop
            case Exp.ArraySelect(Exp.T array, Exp.T index) -> {
                Type.T resultArray = checkExp(array);
                Type.T resultIndex = checkExp(index);
                if (Type.nonEquals(resultArray, Type.getIntArray())) {
                    error("intArray");
                }
                if (Type.nonEquals(resultIndex, Type.getInt())) {
                    error("int");
                }
                return Type.getInt();
            }
            case Exp.BopBool(Exp.T left, String op, Exp.T right) -> {
                Type.T resultLeft = checkExp(left);
                Type.T resultRight = checkExp(right);
                if (Type.nonEquals(resultLeft, Type.getBool())
                        || Type.nonEquals(resultRight, Type.getBool())){
                    error(op);
                }
                return Type.getBool();
            }
            case Exp.False(), Exp.True() -> {
                return Type.getBool();
            }
            case Exp.NewIntArray(Exp.T exp) -> {
                Type.T resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error(exp.toString());
                }
                return Type.getIntArray();
            }
            case Exp.Uop(String op, Exp.T exp) -> {
                Type.T resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getBool())) {
                    error(op);
                }
                return Type.getBool();
            }
            default -> {
                // Todo
                error("Not a expression");
                return null;
            }
        }
    }

    // type check a statement
    private void checkStm(Stm.T s) {
        switch (s) {
            case Stm.If(
                    Exp.T cond,
                    Stm.T then_,
                    Stm.T else_
            ) -> {
                var resultCond = checkExp(cond);
                if (Type.nonEquals(resultCond, Type.getBool())) {
                    error("if require a boolean type");
                }
                checkStm(then_);
                checkStm(else_);
            }
            case Stm.Print(Exp.T exp) -> {
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("print requires an integer type");
                }
            }
            case Stm.Assign(
                    AstId id,
                    Exp.T exp
            ) -> {
                // first lookup in the method table
                var resultAstId = checkAstId(id);
                var resultExp = checkExp(exp);
                if (Type.nonEquals(resultAstId, resultExp)) {
                    error("=");
                }
            }
            // if, print, assign
            case Stm.AssignArray(AstId id, Exp.T index, Exp.T exp) -> {
                Type.T resultAstId = checkAstId(id);
                Type.T resultExp = checkExp(exp);
                Type.T resultIndex = checkExp(index);
                if (Type.nonEquals(resultAstId, Type.getIntArray())) {
                    error("IntArray", Type.getIntArray(), resultAstId);
                }
                if (Type.nonEquals(resultExp, Type.getInt())) {
                    error("Int", Type.getInt(), resultExp);
                }
                if (Type.nonEquals(resultIndex, Type.getInt())) {
                    error("Int", Type.getInt(), resultIndex);
                }
            }
            case Stm.Block(List<Stm.T> stms) -> {
                for (Stm.T stm : stms) {
                    checkStm(stm);
                }
            }
            case Stm.While(Exp.T cond, Stm.T body) -> {
                var resultCond = checkExp(cond);
                if (Type.nonEquals(resultCond, Type.getBool())) {
                    error("if require a boolean type");
                }
                checkStm(body);
            }
            default -> {
                // Todo
                // System.out.println("Error: check statement failed.");
                error("Not a statement.");
                return;
            }
        }
    }

    // check type
    public void checkType(Type.T t) {
        // throw new Todo();
        // 对Type进行类型检查，传进来已经是Type了，没啥检查的
        return;
    }

    // dec
    public void checkDec(Dec.T d) {
        // throw new Todo();

        return;
    }

    // method type
    private List<Type.T> genMethodArgType(List<Dec.T> decs) {
        return decs.stream().map(Dec::getType).toList();
    }

    // method
    private void checkMethod(Method.T mtd) {
        Method.Singleton m = (Method.Singleton) mtd;
        // construct the method table
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(m.formals(), m.locals());
        this.usedMethodField = new HashMap<>();
        putMethodField(m.formals(), m.locals());
        m.stms().forEach(this::checkStm);

        // 检查未引用的 方法形参 和 方法本地变量
        for (Id key : this.usedMethodField.keySet()) {
            if (this.usedMethodField.get(key) == 0) {
                System.out.println(STR."Warning: Variable '\{key}' is never used");
            }
        }
        var resultExp = checkExp(m.retExp());
        if (Type.nonEquals(resultExp, m.retType())) {
            error("ret type mismatch", m.retType(), resultExp);
        }
    }
    //
    // class
    private void checkClass(Class.T c) {
        Class.Singleton cls = (Class.Singleton) c;
        this.currentClass = cls.classId();
        Id extends_ = cls.extends_();
        if (extends_ != null) {
            ClassTable.Binding binding = this.classTable.getClass_(extends_);
            // 设置 Ast.Class.singleton 的 parent 字段
            cls.parent().set(binding.self());
        }
        cls.methods().forEach(this::checkMethod);

        // 检查未引用的类字段
        HashMap<Id, Integer> fmap = this.usedClassField.get(currentClass);
        for (Id key : fmap.keySet()) {
            if (fmap.get(key) == 0) {
                System.out.println(STR."Warning: Variable '\{key}' is never used");
            }
        }
    }

    // main class
    private void checkMainClass(MainClass.T c) {
        MainClass.Singleton mainClass = (MainClass.Singleton) c;
        this.currentClass = mainClass.classId();
        // "main" method has an argument "arg" of type "String[]", but
        // MiniJava programs do not use it.
        // So we can safely create a fake one with integer type.
        this.methodTable = new MethodTable();
        this.methodTable.putFormalLocal(List.of(new Dec.Singleton(Type.getInt(), mainClass.arg())),
                List.of()); // no local variables
        checkStm(mainClass.stm());
    }

    // ////////////////////////////////////////////////////////
    // step 1: create class table for Main class
    private void buildMainClass(MainClass.T main) {
        // we do not put Main class into the class table.
        // so that no other class can inherit from it.
        // MainClass.Singleton mc = (MainClass.Singleton) main;
        //this.classTable.putClass(mc.classId(), null);
    }

    // create class table for each normal class
    private void buildClass(Class.T cls) {
        Class.Singleton c = (Class.Singleton) cls;
        // 在classTable中增加一个 key: classId -> classBinding
        // 初始化 key, value{ c.extends_ -> extends_, cls -> Ast.Class self}
        this.classTable.putClass(c.classId(), c.extends_(), cls);

        // add all instance variables into the class table
        // 设置 classTable 中的 fields 哈希表
        for (Dec.T dec : c.decs()) {
            Dec.Singleton d = (Dec.Singleton) dec;
            this.classTable.putField(c.classId(),
                    d.aid(),
                    d.type());
            // this.usedClassField.put()
        }
        this.putClassField(c);
        // add all methods into the class table
        for (Method.T method : c.methods()) {
            Method.Singleton m = (Method.Singleton) method;
            this.classTable.putMethod(c.classId(),
                    m.methodId(),
                    // for now, do not worry to check
                    // method formals, as we will check
                    // this during method table construction.
                    new ClassTable.MethodType(m.retType(),
                            genMethodArgType(m.formals())));
        }
    }

    private Program.T buildTable0(Program.T p) {
        Program.Singleton prog = (Program.Singleton) p;
        // ////////////////////////////////////////////////
        // a class table maps a class name to its class binding:
        // classTable: className -> Binding{extends_, fields, methods}
        buildMainClass(prog.mainClass());
        prog.classes().forEach(this::buildClass);
        return p;
    }

    private Program.T buildTable(Program.T p) {
        // -trace checker.Checker.buildTable
        Trace<Program.T, Program.T> trace =
                new Trace<>("checker.Checker.buildTable",   //flag
                        this::buildTable0,  //function
                        p,  //arg
                        (_) -> {
                            // before
                            System.out.println("build class table:");
                        },
                        (_) -> {
                            // after
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    private Program.T checkIt0(Program.T p) {
        Program.Singleton prog = (Program.Singleton) p;
        checkMainClass(prog.mainClass());
        prog.classes().forEach(this::checkClass);
        return p;
    }

    private Program.T checkIt(Program.T p) {
        Trace<Program.T, Program.T> trace =
                new Trace<>("checker.Checker.checkClass",
                        this::checkIt0,
                        p,
                        (_) -> {
                            System.out.println("check class:");
                        },
                        (_) -> {
                            this.classTable.dump();
                        });
        return trace.doit();
    }

    // to check a program
    private Program.T checkProgram(Program.T p) {
        // pass 1: build the class table
        Pass<Program.T, Program.T> buildTablePass =
                new Pass<>("build class table",
                        this::buildTable,
                        p,
                        Control.Verbose.L1);
        p = buildTablePass.apply();


        // ////////////////////////////////////////////////
        // pass 2: check each class in turn, under the class table
        // built above.
        Pass<Program.T, Program.T> checkPass =
                new Pass<>("check class",
                        this::checkIt,
                        p,
                        Control.Verbose.L1);
        p = checkPass.apply();
        return p;
    }

    public Ast.Program.T check(Program.T ast) {
        PrettyPrinter pp = new PrettyPrinter();

        var traceCheckProgram = new Trace<>(
                "checker.Checker.check",
                this::checkProgram,
                ast,
                pp::ppProgram,
                pp::ppProgram);
        return traceCheckProgram.doit();
    }
}


