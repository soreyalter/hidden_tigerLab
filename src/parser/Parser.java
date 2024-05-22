package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import ast.Ast.Exp.ExpId;
import util.Id;
import util.Trace;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.lang.classfile.ClassSignature;
import java.util.LinkedList;
import ast.Ast.Exp.T;
import ast.Ast.Stm;
import ast.Ast.Class;
import ast.Ast.Exp;
import util.Tuple;
import ast.Ast.Type;
import ast.Ast.Dec;

import static java.lang.System.*;

public class Parser {
    String inputFileName;
    BufferedInputStream inputStream;
    Lexer lexer;
    Token current;
    private boolean isSpecial = false;
    private boolean isField = true;
    private Token currentNext;
    private Ast.Type.T currentType = null;

    public Parser(String fileName) {
        this.inputFileName = fileName;
    }

    // /////////////////////////////////////////////
    // utility methods to connect the lexer and the parser.
    private void advance() {
        current = lexer.nextToken();
    }

    private void eatToken(Token.Kind kind) {
        if (kind.equals(current.kind)) {
            advance();
            return;
        }
        System.out.println(STR."Expects: \{kind}");
        System.out.println(STR."But got: \{current.kind} at row \{current.rowNum}, line \{current.colNum}");
        error("syntax error");
    }

    private void error(String errMsg) {
        System.out.println(STR."Error: \{errMsg}, compilation aborting...\n");
        exit(1);
    }
    // Function for debug
    private void printID(String msg) {
        System.out.println(STR."\{msg}\n");
    }

    // ////////////////////////////////////////////////////////////
    // The followings are methods for parsing.

    // A bunch of parsing methods to parse expressions.
    // The messy parts are to deal with precedence and associativity.

    // ExpList -> Exp ExpRest*
    // ->
    // ExpRest -> , Exp
    // Exp, Exp, ...
    private LinkedList<T> parseExpList() {
        LinkedList<T> args = new LinkedList<T>();
        if (current.kind.equals(Token.Kind.RPAREN))
            // Exp )
            return args;
        args.add(parseExp());
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            args.add(parseExp());
        }
        return args;
    }

    // AtomExp -> (exp)
    // -> INTEGER_LITERAL = NUM
    // -> true
    // -> false
    // -> this
    // -> id
    // -> new int [exp]
    // -> new id ()
    // 只有 new，(exp)两种情况要讨论，其他全部直接返回
    private Exp.T parseAtomExp() {
        Exp.T exp = null;
        String s;
        int i;

        switch (current.kind) {
            case LPAREN:
                advance();
                exp = parseExp();
                eatToken(Token.Kind.RPAREN);
                return exp;
            case ID:
                s = current.lexeme;
                advance();
                return new ExpId(new Ast.AstId(Id.newName(s)));
            case FALSE:
                advance();
                return new Exp.False();
            case TRUE:
                advance();
                return new Exp.True();
            case NUM:
                i = Integer.parseInt(current.lexeme);
                advance();
                return new Exp.Num(i);
            case THIS:
                advance();
                return new Exp.This();
            case SUB:
                advance();
                if (current.kind == Token.Kind.NUM) {
                    int j = Integer.parseInt(current.lexeme);
                    i = -j;
                    advance();
                    return new Exp.Num(i);
                } else {
                    error(STR."Error: got \{current.kind}");
                }
                // return;
            case NEW:
                advance();
                switch (current.kind) {
                    case INT:
                        // new int [exp]
                        advance();
                        eatToken(Token.Kind.LBRACKET);
                        exp = parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        return new Exp.NewIntArray(exp);
                    case ID:
                        // new A()
                        s = current.lexeme;
                        advance();
                        eatToken(Token.Kind.LPAREN);
                        eatToken(Token.Kind.RPAREN);
                        return new Exp.NewObject(Id.newName(s));
                    default:
                        // throw new Todo();
                        error("in parseAtomExp");
                }
            default:
                error("parseAtomExp failed");
                return exp;
        }
    }

    // NotExp -> AtomExp
    // -> AtomExp .id (expList)
    // -> AtomExp [exp]
    // -> AtomExp .length
    // 可以被取“非”的表达式
    private Exp.T parseNotExp() {
        Exp.T exp;
        exp = parseAtomExp();
        if (current.kind == Token.Kind.DOT ||
                current.kind == Token.Kind.LBRACKET) {
            if (current.kind == Token.Kind.DOT) {
                advance();
                if (current.kind == Token.Kind.LENGTH) {
                    // .length
                    advance();
                    return new Exp.Length(exp);
                }
                // .id(expList)
                String s = current.lexeme;
                eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                LinkedList<T> args = parseExpList();
                eatToken(Token.Kind.RPAREN);
                return new Exp.Call(exp,
                        new Ast.AstId(Id.newName(s)),
                        args,
                        new Tuple.One<>(), // 声明类型
                        new Tuple.One<>());             // 实际返回类型
            } else {
                // [exp]
                Exp.T t = (ExpId) exp;
                eatToken(Token.Kind.LBRACKET);
                exp = parseExp();
                eatToken(Token.Kind.RBRACKET);
                return new Exp.ArraySelect(t, exp);
            }
        }
        return exp;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private Exp.T parseTimesExp() {
        // throw new Todo();
        Exp.T exp = null;
        while (current.kind == Token.Kind.NOT) {
            advance();
            exp = parseTimesExp();
        }
        if (exp != null){
            return new Exp.Uop("!", exp);
        } else {
            exp = parseNotExp();
        }
        return exp;
    }

    // AddSubExp -> TimesExp * TimesExp
    // -> TimesExp
    private Exp.T parseAddSubExp() {
        Exp.T left, right = null;
        left = parseTimesExp();
        // throw new Todo();
        if (current.kind == Token.Kind.TIMES) {
            advance();
            right = parseTimesExp();
            return new Exp.Bop(left, "*", right);
        }
        return left;
    }

    // LtExp -> AddSubExp + AddSubExp
    // -> AddSubExp - AddSubExp
    // -> AddSubExp
    private Exp.T parseLtExp() {
        Exp.T left, right = null;
        left = parseAddSubExp();
        // throw new Todo();
        if (current.kind == Token.Kind.ADD
                || current.kind == Token.Kind.SUB) {
            if (current.kind == Token.Kind.ADD) {
                advance();
                right = parseAddSubExp();
                return new Exp.Bop(left, "+", right);
            }
            else {
                advance();
                right = parseAddSubExp();
                return new Exp.Bop(left, "-", right);
            }

        }
        return left;
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private Exp.T parseAndExp() {
        Exp.T left, right = null;
        left = parseLtExp();
        // throw new Todo();
        if (current.kind == Token.Kind.LT) {
            advance();
            right = parseLtExp();
            return new Exp.Bop(left, "<", right);
        }
        return left;
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private Exp.T parseExp() {
        Exp.T left, right = null;

        left = parseAndExp();
        // throw new Todo();
        if (current.kind == Token.Kind.AND) {
            advance();
            right = parseAndExp();
            return new Exp.BopBool(left, "&&", right);
        }
        return left;
    }

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private Stm.T parseStatement() {
        Exp.T exp;
        Exp.T condition;
        LinkedList<Stm.T> stms = new LinkedList<Stm.T>();

        // to parse a statement.
        // throw new Todo();
        switch (current.kind) {
            case LBRACE:
                LinkedList<Stm.T> block = new LinkedList<Stm.T>();
                advance();
                block = (LinkedList<ast.Ast.Stm.T>) parseStatements();
                eatToken(Token.Kind.RBRACE);
                return new Stm.Block(block);
            case IF:
                advance();
                eatToken(Token.Kind.LPAREN);
                condition = parseExp();
                eatToken(Token.Kind.RPAREN);
                Stm.T thenn = parseStatement();
                eatToken(Token.Kind.ELSE);
                Stm.T elsee =  parseStatement();
                return new Stm.If(condition, thenn, elsee);
            case WHILE:
                advance();
                eatToken(Token.Kind.LPAREN);
                exp = parseExp();
                eatToken(Token.Kind.RPAREN);
                Stm.T body = parseStatement();
                return new Stm.While(exp, body);
            case SYSTEM:
                advance();
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.OUT);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.PRINTLN);
                eatToken(Token.Kind.LPAREN);
                exp = parseExp();
                eatToken(Token.Kind.RPAREN);
                eatToken(Token.Kind.SEMI);
                return new Stm.Print(exp);
            case ID:
                String id = current.lexeme;
                if (isSpecial) {
                    // 这是混进变量声明中的表达式statement走的支线，此时 current.kind = id，
                    // 但是nextToken 得到的是 = 或者 [ 后的那个 token
                    // currentNext 记录了 id 后面的 token是 = 还是 [
                    current = currentNext;
                    // 浅拷贝和深拷贝导致的
                    switch (current.kind){
                        case ASSIGN:
                            // id = exp;
                            advance();
                            exp = parseExp();
                            // 处理完把标志位恢复
                            eatToken(Token.Kind.SEMI);
                            isSpecial = false;
                            return new Stm.Assign(new Ast.AstId(Id.newName(id)), exp);
                        case LBRACKET:
                            // id [index] = exp;
                            advance();
                            Exp.T index = parseExp();
                            eatToken(Token.Kind.RBRACKET);
                            eatToken(Token.Kind.ASSIGN);
                            exp = parseExp();
                            eatToken(Token.Kind.SEMI);
                            isSpecial = false;
                            return new Stm.AssignArray(new Ast.AstId(Id.newName(id)), index, exp);
                    }

                }
                else {
                    advance();
                    if (current.kind == Token.Kind.ASSIGN) {
                        // id = exp ;
                        advance();
                        exp = parseExp();
                        eatToken(Token.Kind.SEMI);
                        return new Stm.Assign(new Ast.AstId(Id.newName(id)), exp);
                    }
                    else if (current.kind == Token.Kind.LBRACKET) {
                        // id [Exp] = Exp ;
                        advance();
                        Exp.T index = parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        eatToken(Token.Kind.ASSIGN);
                        exp = parseExp();
                        eatToken(Token.Kind.SEMI);
                        return new Stm.AssignArray(new Ast.AstId(Id.newName(id)), index, exp);
                    }
                    else {
                        error(STR."parse statement failed in case ID, got \{current.kind}");
                        return null;
                    }
                }
            default:
                error("parse statement failed, no token matched");
                return null;
        }
    }

    // Statements -> Statement Statements
    // ->
    private LinkedList<Stm.T> parseStatements() {
        LinkedList<Stm.T> stms = new LinkedList<Stm.T>();
        // throw new Todo();
        while (current.kind == Token.Kind.LBRACE
                || current.kind == Token.Kind.IF
                || current.kind == Token.Kind.WHILE
                || current.kind == Token.Kind.SYSTEM
                || current.kind == Token.Kind.ID) {
            // 这些开头的都是 statement，否则不是，停止继续解析
            stms.add(parseStatement());
        }
        return stms;
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    // 这里id指的是类名
    private Type.T parseType() {
        // to parse a type.
        // throw new Todo();
        switch (current.kind) {
            case INT:
                advance();
                if (current.kind == Token.Kind.LBRACKET) {
                    // int []
                    eatToken(Token.Kind.LBRACKET);
                    eatToken(Token.Kind.RBRACKET);
                    // currentType = Type.getIntArray();
                    return Type.getIntArray();
                }
                else {
                    // int
                    // currentType = Type.getInt();
                    // printID("*********"+current.toString()); -> doit
                    // printID(Type.convertString(Type.getInt())); -> int
                    return Type.getInt();
                }
            case BOOLEAN:
                advance();
                // currentType = Type.getBool();
                return Type.getBool();
            case ID:
                String s = current.lexeme;
                advance();
                // currentType = new Type.ClassType(Id.newName(s));
                // currentType = Type.getClassType(Id.newName(s));
                return Type.getClassType(Id.newName(s));
            default:
                error(STR."parseType failed, got \{current.kind}");
                return null;
        }
    }

    // VarDecl -> Type id ;
    // id id ;
    private Dec.Singleton parseVarDecl() throws Exception {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        Type.T type =  parseType();
        String id = current.lexeme;
        Ast.Dec.Singleton dec = new Ast.Dec.Singleton(type, new Ast.AstId(Id.newName(id)));
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.SEMI);
        return dec;
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private LinkedList<Dec.T> parseVarDecls() throws Exception {
        LinkedList<Dec.T> decs = new LinkedList<Dec.T>();
        // throw new util.Todo();
        // 注意一种情况：int i; i = 3;
        // 循环到第二个语句时由于 i 是 ID，可以进入循环导致 赋值语句 被 parseVarDecl 解析
        while (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            // 这里 while 判断 type 的三种类型，但 id 必须是 class 才行
            if (current.kind != Token.Kind.ID) {
                // boolean or int
                decs.add(parseVarDecl());
            }
            else {
                // current.kind = ID
                // 要判断是 VarDecl 声明语句： id id ;
                // 还是 statement 语句： id = exp ; || id [exp] = exp ;
                // 若是 statement，不做额外的处理，因为在程序中，声明语句后应该就是 statements 语句块
                // 此时解析到的真实位置是 current 对应的位置，而current现在是 = 或 [ 进不了 parseStatement
                // 为了让解析过程能进入 parseStatement 语句中，把 current.kind 设置为 id
                // 用 currentNext 记录一下现在是 = 还是 [

                // 试探一下下面是 id, 还是别的东西
                String id = current.lexeme; // classType
                advance();
                if (current.kind != Token.Kind.ID){
                    // 不是变量声明
                    currentNext = current;
                    // current.kind = Token.Kind.ID;
                    current = new Token(Token.Kind.ID, currentNext.rowNum, currentNext.colNum);
                    isSpecial = true;
                    // 此时已经不是声明语句，直接结束 parseVarDecls 的解析
                    return decs;
                }
                else {
                    // 若是还是id，说明还是声明语句，但此时 parser 已经到了 id id ; 中的第二个id处
                    // 省个调用栈直接在这解析剩下的，也可以去 parseVarDecl 里面处理，同样用 isSpecial 判断
                    String s = current.lexeme;
                    eatToken(Token.Kind.ID);
                    eatToken(Token.Kind.SEMI);
                    Type.T type = new Type.ClassType(Id.newName(id));
                    decs.add(new Dec.Singleton(type, new Ast.AstId(Id.newName(s))));
                    // 继续循环
                }
            }
        }
        return decs;
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    // 唯一一个，可能在执行结束后，current不指向下一个token的函数
    // 因为 eat '(' 后，当前可能是形参，也可能是右括号，是右括号的话直接返回给 methodDecl 中的eatToken ')'
    private LinkedList<Dec.T> parseFormalList() {
        LinkedList<Dec.T> formals = new LinkedList<Dec.T>();
        Type.T type;
        String id;
        // throw new Todo();
        // if (current.kind == Token.Kind.RPAREN) {
        //     // advance();
        //     return formals;
        // }
        // while (current.kind == Token.Kind.INT
        //         || current.kind == Token.Kind.BOOLEAN
        //         || current.kind == Token.Kind.ID) {
        //     type = parseType();
        //     id = current.lexeme;
        //     eatToken(Token.Kind.ID);
        //     formals.add(new Dec.Singleton(type, new Ast.AstId(Id.newName(id))));
        //     if (current.kind == Token.Kind.COMMA) {
        //         // eatToken(Token.Kind.COMMA);
        //         advance();
        //     }
        //     // 在这里 return 可以在 while 外面写报错信息
        //     else {
        //         // 正常来说这里 current = )
        //         return formals;
        //     }
        // }
        // error(STR."parseFormalList fail, got \{current.kind}");

        // 另一种写法
        if (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            type = parseType();
            id = current.lexeme;
            eatToken(Token.Kind.ID);
            formals.add(new Dec.Singleton(type, new Ast.AstId(Id.newName(id))));
            while (current.kind == Token.Kind.COMMA) {
                advance();
                type = parseType();
                id = current.lexeme;
                eatToken(Token.Kind.ID);
                formals.add(new Dec.Singleton(type, new Ast.AstId(Id.newName(id))));
            }
        }
        // 如果是没有形参的情况，此时 formals 是一个空列表
        return formals;
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private Ast.Method.T parseMethod() throws Exception {
        // to parse a method.
        // throw new Todo();
        Type.T reType;
        String id;
        LinkedList<Dec.T> formals;
        LinkedList<Dec.T> locals;
        LinkedList<Stm.T> stms;
        Exp.T retExp;

        eatToken(Token.Kind.PUBLIC);
        reType = parseType();
        // printID(Type.convertString(reType)); -> int
        // out.println(reType); -> int[]
        id = current.lexeme;    // 函数名
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);    // (
        formals = parseFormalList();
        eatToken(Token.Kind.RPAREN);    // )
        eatToken(Token.Kind.LBRACE);    // {
        locals = parseVarDecls();
        stms =  parseStatements();
        eatToken(Token.Kind.RETURN);
        retExp = parseExp();
        eatToken(Token.Kind.SEMI);
        eatToken(Token.Kind.RBRACE);    // }
        return new Ast.Method.Singleton(reType,
                new Ast.AstId(Id.newName(id)),
                formals,
                locals,
                stms,
                retExp);
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private LinkedList<Ast.Method.T> parseMethodDecls() throws Exception {
        // throw new util.Todo();
        LinkedList<Ast.Method.T> methods = new LinkedList<Ast.Method.T>();
        while (current.kind == Token.Kind.PUBLIC) {
            isField = false;
            Ast.Method.T ms = parseMethod();
            methods.add(ms);
        }
        isField = true;
        return methods;
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    // -> class id extends id { VarDecl* MethodDecl* }
    private Class.T parseClassDecl() throws Exception {
        String id;
        String extendss = null;
        LinkedList<Dec.T> decs = new LinkedList<>();
        LinkedList<Ast.Method.T> methods = new LinkedList<>();
        // Tuple.One<Class.T> parentClass =  new Tuple.One<Class.T>();

        eatToken(Token.Kind.CLASS);
        id = current.lexeme;
        eatToken(Token.Kind.ID);
        // throw new util.Todo();
        if (current.kind == Token.Kind.LBRACE) {
            advance();

            decs = parseVarDecls();
            methods = parseMethodDecls();
            eatToken(Token.Kind.RBRACE);
            return new Class.Singleton(Id.newName(id),
                    null,
                    decs,
                    methods,
                    new Tuple.One<>(null));
        }
        else if (current.kind == Token.Kind.EXTENDS) {
            advance();
            extendss = current.lexeme;
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.LBRACE);

            decs = parseVarDecls();
            methods = parseMethodDecls();
            eatToken(Token.Kind.RBRACE);
            return new Class.Singleton(Id.newName(id),
                    Id.newName(extendss),
                    decs,
                    methods,
                    new Tuple.One<>());
        }
        else error(STR."parseClassDecl fail, got \{current.kind}");
        return null;
    }

    // ClassDecls -> ClassDecl ClassDecls
    // ->
    private LinkedList<Class.T> parseClassDecls() throws Exception {
        LinkedList<Class.T> classes = new LinkedList<>();
        while (current.kind.equals(Token.Kind.CLASS)) {
            Class.T sc = parseClassDecl();
            classes.add(sc);
        }
        return classes;
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private Ast.MainClass.Singleton parseMainClass() {
        // Lab 1. Exercise 11: Fill in the missing code
        // to parse a main class as described by the
        // grammar above.
        // throw new Todo();

        String id;
        String arg;
        Stm.T stm;

        eatToken(Token.Kind.CLASS);
        id = current.lexeme;
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LBRACE);    // {
        eatToken(Token.Kind.PUBLIC);
        eatToken(Token.Kind.STATIC);
        eatToken(Token.Kind.VOID);
        eatToken(Token.Kind.MAIN);
        eatToken(Token.Kind.LPAREN);    // (
        eatToken(Token.Kind.STRING);
        eatToken(Token.Kind.LBRACKET);
        eatToken(Token.Kind.RBRACKET);
        arg = current.lexeme;
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.RPAREN);    // )
        eatToken(Token.Kind.LBRACE);    // {
        // error("eatToken LBRACE done");
        stm = parseStatement();
        // error("parseStatement done");
        eatToken(Token.Kind.RBRACE);    // }
        eatToken(Token.Kind.RBRACE);    // }
        return new Ast.MainClass.Singleton(Id.newName(id), new Ast.AstId(Id.newName(arg)), stm);
    }

    // Program -> MainClass ClassDecl*
    private Ast.Program.T parseProgram(Object obj){
        Ast.MainClass.Singleton mainClass;
        LinkedList<Class.T> classes;

        try {
            mainClass = parseMainClass();

            classes = parseClassDecls();
            eatToken(Token.Kind.EOF);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return new Ast.Program.Singleton(mainClass, classes);
    }

    private void initParser() {
        try {
            this.inputStream = new BufferedInputStream(new FileInputStream(this.inputFileName));
        } catch (Exception e) {
            error(STR."unable to open file \{this.inputFileName}");
        }

        this.lexer = new Lexer(this.inputFileName, this.inputStream);
        this.current = lexer.nextToken();
    }

    private void finalizeParser() {
        try {
            this.inputStream.close();
        } catch (Exception e) {
            error("unable to close file");
        }
    }

    public Ast.Program.T parse() {

        initParser();
        Ast.Program.T ast = null;

        Trace<Object, Ast.Program.T> trace =
                new Trace<>("parser.Parser.parse",
                        this::parseProgram,
                        this.inputFileName,
                        (s) -> System.out.println(STR."parsing: \{s}"),
                        new PrettyPrinter()::ppProgram);
        ast = trace.doit();

        finalizeParser();
        return ast;
    }
}