package parser;

import ast.Ast;
import ast.PrettyPrinter;
import lexer.Lexer;
import lexer.Token;
import slp.Slp;
import util.Todo;
import util.Trace;

import javax.print.attribute.standard.PrinterLocation;
import java.io.BufferedInputStream;
import java.io.FileInputStream;

import static java.lang.System.*;

public class Parser {
    String inputFileName;
    BufferedInputStream inputStream;
    Lexer lexer;
    Token current;
    private boolean isSpecial = false;
    private Token currentNext;

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
    private void parseExpList() {
        if (current.kind.equals(Token.Kind.RPAREN))
            // Exp )
            return;
        parseExp();
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            parseExp();
        }
        return;
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
    private void parseAtomExp() {
        switch (current.kind) {
            case LPAREN:
                advance();
                parseExp();
                eatToken(Token.Kind.RPAREN);
                printID(STR."========  parseAtomExp after eatToken), current -> \{current.kind} =========");
                return;
            case ID, FALSE, TRUE, NUM, THIS:
                advance();
                return;
            case SUB:
                advance();
                if (current.kind == Token.Kind.NUM) {
                    advance();
                    return;
                } else {
                    error(STR."Error: got \{current.kind}");
                }
                return;
            case NEW:
                advance();
                switch (current.kind) {
                    case INT:
                        advance();
                        eatToken(Token.Kind.LBRACKET);
                        parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        return;
                    case ID:
                        advance();
                        eatToken(Token.Kind.LPAREN);
                        eatToken(Token.Kind.RPAREN);
                        return;
                    default:
                        // throw new Todo();
                        error("in parseAtomExp");
                }
            default:
                error("parseAtomExp failed");
                return;
        }
    }

    // NotExp -> AtomExp
    // -> AtomExp .id (expList)
    // -> AtomExp [exp]
    // -> AtomExp .length
    // 可以被取“非”的表达式
    private void parseNotExp() {
        parseAtomExp();
        if (current.kind == Token.Kind.DOT ||
                current.kind == Token.Kind.LBRACKET) {
            if (current.kind == Token.Kind.DOT) {
                advance();
                if (current.kind == Token.Kind.LENGTH) {
                    // .length
                    advance();
                    return;
                }
                // .id(expList)
                eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                parseExpList();
                eatToken(Token.Kind.RPAREN);
                return;
            } else {
                // [exp]
                eatToken(Token.Kind.LBRACKET);
                parseExp();
                eatToken(Token.Kind.RBRACKET);
                return;
            }
        }
        printID(STR."======== parseNotExp return, current -> \{current.kind} =========");
        return;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private void parseTimesExp() {
        // throw new Todo();
        if (current.kind != Token.Kind.NOT){
            parseNotExp();
            return;
        }
        while (current.kind == Token.Kind.NOT) {
            advance();  // -> (
            parseTimesExp();
        }
        return;
    }

    // AddSubExp -> TimesExp * TimesExp
    // -> TimesExp
    private void parseAddSubExp() {
        parseTimesExp();
        // throw new Todo();
        if (current.kind == Token.Kind.TIMES) {
            advance();
            parseTimesExp();
            printID(STR."======== parseAddSubExp return, current -> \{current.kind} =========");
            return;
        }
        printID(STR."======== parseAddSubExp return, current -> \{current.kind} =========");
        return;
    }

    // LtExp -> AddSubExp + AddSubExp
    // -> AddSubExp - AddSubExp
    // -> AddSubExp
    private void parseLtExp() {
        parseAddSubExp();
        // throw new Todo();
        if (current.kind == Token.Kind.ADD
                || current.kind == Token.Kind.SUB) {
            advance();
            parseAddSubExp();
            return;
        }
        printID(STR."======== parseLtExp return, current -> \{current.kind} =========");

        return;
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private void parseAndExp() {
        parseLtExp();
        // throw new Todo();
        if (current.kind == Token.Kind.LT) {
            advance();
            parseLtExp();
            printID(STR."======== parseAndExp return, current -> \{current.kind} =========");

            return;
        }
        return;
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private void parseExp() {
        parseAndExp();
        // throw new Todo();
        if (current.kind == Token.Kind.AND) {
            advance();
            parseAndExp();
        }
    }

    // Statement -> { Statement* }
    // -> if ( Exp ) Statement else Statement
    // -> while ( Exp ) Statement
    // -> System.out.println ( Exp ) ;
    // -> id = Exp ;
    // -> id [ Exp ]= Exp ;
    private void parseStatement() {
        // to parse a statement.
        // throw new Todo();
        switch (current.kind) {
            case LBRACE:
                advance();
                parseStatements();
                eatToken(Token.Kind.RBRACE);
                return;
            case IF:
                advance();
                eatToken(Token.Kind.LPAREN);
                // 问题就是这个 parseExp
                // current -> !
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                eatToken(Token.Kind.ELSE);
                parseStatement();
                return;
            case WHILE:
                advance();
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                return;
            case SYSTEM:
                advance();
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.OUT);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.PRINTLN);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                eatToken(Token.Kind.SEMI);
                return;
            case ID:
                if (isSpecial) {
                    // printID("========= special =============");
                    // 这是混进变量声明中的表达式statement走的支线，此时 current.kind = id，
                    // 但是nextToken 得到的是 = 或者 [ 后的那个 token
                    // currentNext 记录了 id 后面的 token是 = 还是 [
                    current = currentNext;
                    // 浅拷贝和深拷贝导致的
                    printID("+++++++++++++++++next: "+ currentNext.toString());
                    printID("+++++++++++++++++current: "+ current.toString());
                    switch (current.kind){
                        case ASSIGN:
                            advance();
                            parseExp();
                            // 处理完把标志位恢复
                            eatToken(Token.Kind.SEMI);
                            isSpecial = false;
                            return;
                        case LBRACKET:
                            advance();
                            parseExp();
                            eatToken(Token.Kind.RBRACKET);
                            eatToken(Token.Kind.ASSIGN);
                            parseExp();
                            isSpecial = false;
                            return;
                    }

                }
                else {
                    advance();
                    if (current.kind == Token.Kind.ASSIGN) {
                        // id = exp ;
                        advance();
                        parseExp();
                        eatToken(Token.Kind.SEMI);
                        return;
                    }
                    else if (current.kind == Token.Kind.LBRACKET) {
                        // id [Exp] = Exp ;
                        advance();
                        parseExp();
                        eatToken(Token.Kind.RBRACKET);
                        eatToken(Token.Kind.ASSIGN);
                        parseExp();
                        eatToken(Token.Kind.SEMI);
                        return;
                    }
                    else error(STR."parse statement failed in case ID, got \{current.kind}");
                }
            default: error("parse statement failed, no token matched");
        }
    }

    // Statements -> Statement Statements
    // ->
    private void parseStatements() {
        // throw new Todo();
        while (current.kind == Token.Kind.LBRACE
                || current.kind == Token.Kind.IF
                || current.kind == Token.Kind.WHILE
                || current.kind == Token.Kind.SYSTEM
                || current.kind == Token.Kind.ID) {
            // 这些开头的都是 statement，否则不是，停止继续解析
            parseStatement();
        }
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    // 这里id指的是类名
    private void parseType() {
        // to parse a type.
        // throw new Todo();
        switch (current.kind) {
            case INT:
                advance();
                if (current.kind == Token.Kind.LBRACKET) {
                    // int []
                    eatToken(Token.Kind.LBRACKET);
                    eatToken(Token.Kind.RBRACKET);
                    return;
                }
                else {
                    // int
                    return;
                }
            case BOOLEAN:
                advance();
                return;
            case ID:
                advance();
                return;
            default:
                error(STR."parseType failed, got \{current.kind}");
        }
    }

    // VarDecl -> Type id ;
    // id id ;
    private void parseVarDecl() throws Exception {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.SEMI);
        return;
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private void parseVarDecls() throws Exception {
        // throw new util.Todo();
        // 注意一种情况：int i; i = 3;
        // 循环到第二个语句时由于 i 是 ID，可以进入循环导致 赋值语句 被 parseVarDecl 解析
        while (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            // 这里 while 判断 type 的三种类型，但 id 必须是 class 才行
            if (current.kind != Token.Kind.ID) {
                // boolean or int
                parseVarDecl();
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
                advance();
                if (current.kind != Token.Kind.ID){
                    // 不是变量声明
                    currentNext = current;
                    // current.kind = Token.Kind.ID;
                    current = new Token(Token.Kind.ID, currentNext.rowNum, currentNext.colNum);
                    isSpecial = true;
                    // 此时已经不是声明语句，直接结束 parseVarDecls 的解析
                    return;
                }
                else {
                    // 若是还是id，说明还是声明语句，但此时 parser 已经到了 id id ; 中的第二个id处
                    // 省个调用栈直接在这解析剩下的，也可以去 parseVarDecl 里面处理，同样用 isSpecial 判断
                    eatToken(Token.Kind.ID);
                    eatToken(Token.Kind.SEMI);
                    // 继续循环
                }
            }
        }
        //        return;
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    // 唯一一个，可能在执行结束后，current不指向下一个token的函数
    // 因为eat 左括号后，当前可能是形参，也可能是右括号，是右括号的话直接返回给 methodDecl 中的eatToken
    private void parseFormalList() {
        // throw new Todo();
        if (current.kind == Token.Kind.RPAREN) {
            // advance();
            return;
        }
        while (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            parseType();
            eatToken(Token.Kind.ID);
            if (current.kind == Token.Kind.COMMA) {
                // eatToken(Token.Kind.COMMA);
                advance();
            }
            // 在这里 return 可以在 while 外面写报错信息
            else {
                // 正常来说这里 current = )
                return;
            }
        }
        error(STR."parseFormalList fail, got \{current.kind}");
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private void parseMethod() throws Exception {
        // to parse a method.
        // throw new Todo();
        eatToken(Token.Kind.PUBLIC);
        parseType();
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);    // (
        parseFormalList();
        eatToken(Token.Kind.RPAREN);    // )
        eatToken(Token.Kind.LBRACE);    // {
        parseVarDecls();
        parseStatements();
        eatToken(Token.Kind.RETURN);
        parseExp();
        eatToken(Token.Kind.SEMI);
        eatToken(Token.Kind.RBRACE);    // }
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private void parseMethodDecls() throws Exception {
        // throw new util.Todo();
        while (current.kind == Token.Kind.PUBLIC) {
            parseMethod();
        }
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    // -> class id extends id { VarDecl* MethodDecl* }
    private void parseClassDecl() throws Exception {
        eatToken(Token.Kind.CLASS);
        eatToken(Token.Kind.ID);
        // throw new util.Todo();
        if (current.kind == Token.Kind.LBRACE) {
            advance();
            parseVarDecls();
            // parseVarDecls 已完成
            parseMethodDecls();
            eatToken(Token.Kind.RBRACE);
        }
        else if (current.kind == Token.Kind.EXTENDS) {
            advance();
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.LBRACE);
            parseVarDecls();
            parseMethodDecls();
            eatToken(Token.Kind.RBRACE);
        }
        else error(STR."parseClassDecl fail, got \{current.kind}");
    }

    // ClassDecls -> ClassDecl ClassDecls
    // ->
    private void parseClassDecls() throws Exception {
        while (current.kind.equals(Token.Kind.CLASS)) {
            parseClassDecl();
        }
        return;
    }

    // MainClass -> class id {
    //   public static void main ( String [] id ) {
    //     Statement
    //   }
    // }
    private void parseMainClass() {
        // Lab 1. Exercise 11: Fill in the missing code
        // to parse a main class as described by the
        // grammar above.
        // throw new Todo();
        eatToken(Token.Kind.CLASS);
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
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.RPAREN);    // )
        eatToken(Token.Kind.LBRACE);    // {
        // error("eatToken LBRACE done");
        parseStatement();
        // error("parseStatement done");
        eatToken(Token.Kind.RBRACE);    // }
        eatToken(Token.Kind.RBRACE);    // }

    }

    // Program -> MainClass ClassDecl*
    private Ast.Program.T parseProgram(Object obj){
        try {
            parseMainClass();

            parseClassDecls();
            eatToken(Token.Kind.EOF);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
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