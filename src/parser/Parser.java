package parser;

import lexer.Lexer;
import lexer.Token;
import slp.Slp;
import util.Todo;

import javax.crypto.AEADBadTagException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.lang.reflect.Type;

import static java.lang.System.err;
import static java.lang.System.exit;

public class Parser {
    String inputFileName;
    BufferedInputStream inputStream;
    Lexer lexer;
    Token current;
    private boolean isSpecial = false;
    private Token currentNext;
    private boolean isField = true;

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
        System.out.println(STR."But got: \{current.kind} in row: \{current.rowNum} column: \{current.colNum}");
        error("syntax error");
    }

    private void error(String errMsg) {
        System.out.println(STR."Error: \{errMsg}, compilation aborting...\n");
        exit(1);
    }

    // ////////////////////////////////////////////////////////////
    // The followings are methods for parsing.

    // A bunch of parsing methods to parse expressions.
    // The messy parts are to deal with precedence and associativity.

    // ExpList -> Exp ExpRest*
    // ->
    // ExpRest -> , Exp
    private void parseExpList() {
        if (current.kind.equals(Token.Kind.RPAREN))
            return;
        parseExp();
        while (current.kind.equals(Token.Kind.COMMA)) {
            advance();
            parseExp();
        }
        return;
    }

    // AtomExp -> (exp)
    // -> INTEGER_LITERAL
    // -> true
    // -> false
    // -> this
    // -> id
    // -> new int [exp]
    // -> new id ()
    private void parseAtomExp() {
        switch (current.kind) {
            case SUB:
                advance();
                if (current.kind == Token.Kind.NUM) {
                    advance();
                } else {
                    error(STR."parseAtomExp Error at line \{current.colNum}");
                }
            case LPAREN:
                advance();
                parseExp();
                eatToken(Token.Kind.RPAREN);
                return;
            case ID:
                advance();
                return;
            case NEW: {
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
                        error("parseAtomExp exception");
                }
            }
            default:
                advance();
                return;
        }
    }

    // NotExp -> AtomExp
    // -> AtomExp .id (expList)
    // -> AtomExp [exp]
    // -> AtomExp .length
    private void parseNotExp() {
        parseAtomExp();
        while (current.kind.equals(Token.Kind.DOT) ||
                current.kind.equals(Token.Kind.LBRACKET)) {
            if (current.kind.equals(Token.Kind.DOT)) {
                advance();
                if (current.kind.equals(Token.Kind.LENGTH)) {
                    advance();
                    return;
                }
                System.out.println("parseNotExp");
                eatToken(Token.Kind.ID);
                eatToken(Token.Kind.LPAREN);
                parseExpList();
                eatToken(Token.Kind.RPAREN);
            } else {
                advance();
                parseExp();
                eatToken(Token.Kind.RBRACKET);
            }
        }
        return;
    }

    // TimesExp -> ! TimesExp
    // -> NotExp
    private void parseTimesExp() {
        // throw new Todo();
        while (current.kind == Token.Kind.NOT) {
            advance();
            parseTimesExp();
        }
        // ??
        parseNotExp();
    }

    // AddSubExp -> TimesExp * TimesExp
    // -> TimesExp
    private void parseAddSubExp() {
        parseTimesExp();
        // throw new Todo();
        while (current.kind == Token.Kind.TIMES) {
            advance();
            parseTimesExp();
        }
    }

    // LtExp -> AddSubExp + AddSubExp
    // -> AddSubExp - AddSubExp
    // -> AddSubExp
    private void parseLtExp() {
        parseAddSubExp();
        // throw new Todo();
        while (current.kind == Token.Kind.ADD || current.kind == Token.Kind.SUB) {
            advance();
            parseAddSubExp();
        }
    }

    // AndExp -> LtExp < LtExp
    // -> LtExp
    private void parseAndExp() {
        parseLtExp();
        // throw new Todo();
        while (current.kind == Token.Kind.LT) {
            advance();
            parseLtExp();
        }
    }

    // Exp -> AndExp && AndExp
    // -> AndExp
    private void parseExp() {
        parseAndExp();
        // throw new Todo();
        while (current.kind == Token.Kind.AND) {
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
                // advance(); 修改
                // eatToken(Token.Kind.RBRACE);
                eatToken(Token.Kind.LBRACE);
                parseStatements();
                eatToken(Token.Kind.RBRACE);
            case IF:
                // advance(); 修改
                // eatToken(Token.Kind.LPAREN);
                // parseExp();
                // eatToken(Token.Kind.RPAREN);
                // parseStatement();
                // eatToken(Token.Kind.ELSE);
                // parseStatement();
                eatToken(Token.Kind.IF);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
                eatToken(Token.Kind.ELSE);
                parseStatement();
            case WHILE:
                // advance();
                eatToken(Token.Kind.WHILE);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                parseStatement();
            case SYSTEM:
                // advance();
                eatToken(Token.Kind.SYSTEM);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.OUT);
                eatToken(Token.Kind.DOT);
                eatToken(Token.Kind.PRINTLN);
                eatToken(Token.Kind.LPAREN);
                parseExp();
                eatToken(Token.Kind.RPAREN);
                eatToken(Token.Kind.SEMI);
            case ID:
                if (isSpecial) // it means this is returned from VarDecls
                {
                    current = currentNext;
                    switch (current.kind) {
                        case ASSIGN:
                            eatToken(Token.Kind.ASSIGN);
                            parseExp();
                            eatToken(Token.Kind.SEMI);

                            isSpecial = false;
                        case LBRACKET:
                            eatToken(Token.Kind.LBRACKET);
                            parseExp();
                            eatToken(Token.Kind.RBRACKET);
                            eatToken(Token.Kind.ASSIGN);
                            parseExp();
                            eatToken(Token.Kind.SEMI);
                            isSpecial = false;
                        default:
                            error("expect ASSIGN or LBRACK");

                    }

                } else {
                    System.out.println("parseStatement id");
                    eatToken(Token.Kind.ID);
                    switch (current.kind) {
                        case ASSIGN:
                            eatToken(Token.Kind.ASSIGN);
                            parseExp();
                            eatToken(Token.Kind.SEMI);
                        case LBRACKET:
                            eatToken(Token.Kind.LBRACKET);
                            parseExp();
                            eatToken(Token.Kind.RBRACKET);
                            eatToken(Token.Kind.ASSIGN);
                            parseExp();
                            eatToken(Token.Kind.SEMI);
                        default:
                            error("expect ASSIGN or LBRACK");
                            return;
                    }
                }
            case ASSIGN:
                parseExp();
                eatToken(Token.Kind.SEMI);
                break;
            default:
                System.out.println("Func: parseStatement");
                error("ParseStatement Exception");
                return;

        }
        return;
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
            parseStatement();
        }
    }

    // Type -> int []
    // -> boolean
    // -> int
    // -> id
    private void parseType() {
        // to parse a type.
        // throw new Todo();
        switch (current.kind) {
            case INT:
                eatToken(Token.Kind.INT);
                if (current.kind == Token.Kind.LBRACKET) {
                    eatToken(Token.Kind.LBRACKET);
                    eatToken(Token.Kind.RBRACKET);
                }
            case BOOLEAN:
                eatToken(Token.Kind.BOOLEAN);
            default:
                System.out.println("parseType id");
                eatToken(Token.Kind.ID);
        }
    }

    // VarDecl -> Type id ;
    private void parseVarDecl() throws Exception {
        // to parse the "Type" non-terminal in this method,
        // instead of writing a fresh one.
        if(!isSpecial) {
            parseType();
            System.out.println("parseVarDecl id");
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.SEMI);
        } else {
            current = currentNext;
            System.out.println("parseVarDecl id");
            eatToken(Token.Kind.ID);
            eatToken(Token.Kind.SEMI);
            isSpecial = false;
        }
        return;
    }

    // VarDecls -> VarDecl VarDecls
    // ->
    private void parseVarDecls() throws Exception {
        // throw new util.Todo();
        //        return;
        while (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            if (current.kind != Token.Kind.ID) {
                parseVarDecl();
            } else {
                int linenum = current.rowNum;
                int colnum = current.colNum;
                // ID
                System.out.println("parseVarDecls id");
                eatToken(Token.Kind.ID);
                if (current.kind == Token.Kind.ASSIGN) {
                    // x = 1;
                    currentNext = current;
                    current = new Token(Token.Kind.ID, linenum, colnum);
                    isSpecial = true;
                    return;
                }
                else if (current.kind == Token.Kind.LBRACKET) {
                    // x[
                    currentNext = current;
                    current = new Token(Token.Kind.ID, linenum, colnum);
                    isSpecial = true;
                    return;
                }
                else {
                    currentNext = current;
                    current = new Token(Token.Kind.ID, linenum, colnum);
                    isSpecial = true;
                    parseVarDecl();
                }
            }
        }
    }

    // FormalList -> Type id FormalRest*
    // ->
    // FormalRest -> , Type id
    private void parseFormalList() {
        // throw new Todo();
        if (current.kind == Token.Kind.INT
                || current.kind == Token.Kind.BOOLEAN
                || current.kind == Token.Kind.ID) {
            parseType();
            System.out.println("parseFormalList id");
            eatToken(Token.Kind.ID);
            while (current.kind == Token.Kind.COMMA) {
                advance();
                parseType();
                System.out.println("parseFormalList id");
                eatToken(Token.Kind.ID);
            }
        }
    }

    // Method -> public Type id ( FormalList )
    // { VarDecl* Statement* return Exp ;}
    private void parseMethod() throws Exception {
        // to parse a method.
        // throw new Todo();
        eatToken(Token.Kind.PUBLIC);
        parseType();
        System.out.println("parseMethod id");
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LPAREN);
        parseFormalList();
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        parseVarDecls();
        parseStatements();
        eatToken(Token.Kind.RETURN);
        parseExp();
        eatToken(Token.Kind.SEMI);
        System.out.println("parseMethod rbrace");

        eatToken(Token.Kind.RBRACE);
    }

    // MethodDecls -> MethodDecl MethodDecls
    // ->
    private void parseMethodDecls() throws Exception {
        // throw new util.Todo();
        while (current.kind == Token.Kind.PUBLIC) {
            isField = false;
            parseMethod();
        }
        isField = true;
    }

    // ClassDecl -> class id { VarDecl* MethodDecl* }
    // -> class id extends id { VarDecl* MethodDecl* }
    private void parseClassDecl() throws Exception {
        eatToken(Token.Kind.CLASS);
        System.out.println("parseClassDecl id");

        eatToken(Token.Kind.ID);
        // throw new util.Todo();
        if (current.kind == Token.Kind.EXTENDS) {
            eatToken(Token.Kind.EXTENDS);
            System.out.println("parseClassDecl id");
            eatToken(Token.Kind.ID);
        }
        eatToken(Token.Kind.LBRACE);
        parseVarDecls();
        parseMethodDecls();
        System.out.println("parseClassDecl rbrace");
        eatToken(Token.Kind.RBRACE);
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
        System.out.println("parseMainClass id");

        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.LBRACE);
        eatToken(Token.Kind.PUBLIC);
        eatToken(Token.Kind.STATIC);
        eatToken(Token.Kind.VOID);
        eatToken(Token.Kind.MAIN);
        eatToken(Token.Kind.LPAREN);
        eatToken(Token.Kind.STRING);
        eatToken(Token.Kind.LBRACKET);
        eatToken(Token.Kind.RBRACKET);
        System.out.println("parseMainClass id");
        eatToken(Token.Kind.ID);
        eatToken(Token.Kind.RPAREN);
        eatToken(Token.Kind.LBRACE);
        parseStatement();
        // System.out.println("parseMainClass");
        eatToken(Token.Kind.RBRACE);
    }

    // Program -> MainClass ClassDecl*
    private void parseProgram() throws Exception {
        parseMainClass();
        // 自己添加的行
        eatToken(Token.Kind.RBRACE);
        parseClassDecls();
        eatToken(Token.Kind.EOF);
        return;
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

    public Object parse() throws Exception {
        initParser();
        parseProgram();
        finalizeParser();
        return null;
    }
}
