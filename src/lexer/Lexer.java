package lexer;

import util.Todo;

import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Pattern;

import static control.Control.Lexer.dumpToken;

public record Lexer(String fileName,
                    InputStream fileStream) {
    static HashMap<Integer, Token> singals = new HashMap<Integer, Token>();
    static HashMap<String, Token> nsingals = new HashMap<String, Token>();
    private static final Pattern patternNum = Pattern.compile("0|[1-9][0-9]*");
    //标识符
    private static final Pattern patternAlpha = Pattern.compile("_|[A-Z]|[a-z]");
    private static final Pattern patternAlphas = Pattern.compile("_|[A-Z]|[a-z]|[0-9]");
    private static Integer lineNum = 1; // 初始化行号为1
    private static Integer colNum = 0; // 初始化列号为0

    public Lexer(String fileName, InputStream fileStream) {
        this.fileStream = fileStream;
        this.fileName = fileName;
        nsingals.put("boolean", new Token(Token.Kind.BOOLEAN, lineNum, colNum));
        nsingals.put("class",new Token(Token.Kind.CLASS, lineNum, colNum));
        nsingals.put("else",new Token(Token.Kind.ELSE, lineNum, colNum));
        nsingals.put("extends",new Token(Token.Kind.EXTENDS, lineNum, colNum));
        nsingals.put("false",new Token(Token.Kind.FALSE, lineNum, colNum));
        nsingals.put("if",new Token(Token.Kind.IF, lineNum, colNum));
        nsingals.put("int",new Token(Token.Kind.INT, lineNum, colNum));
        nsingals.put("length",new Token(Token.Kind.LENGTH, lineNum, colNum));
        nsingals.put("main",new Token(Token.Kind.MAIN, lineNum, colNum));
        nsingals.put("new",new Token(Token.Kind.NEW, lineNum, colNum));
        nsingals.put("out",new Token(Token.Kind.OUT, lineNum, colNum));
        nsingals.put("println",new Token(Token.Kind.PRINTLN, lineNum, colNum));
        nsingals.put("public",new Token(Token.Kind.PUBLIC, lineNum, colNum));
        nsingals.put("return",new Token(Token.Kind.RETURN, lineNum, colNum));
        nsingals.put("static",new Token(Token.Kind.STATIC, lineNum, colNum));
        nsingals.put("String",new Token(Token.Kind.STRING, lineNum, colNum));
        nsingals.put("System",new Token(Token.Kind.SYSTEM, lineNum, colNum));
        nsingals.put("this",new Token(Token.Kind.THIS, lineNum, colNum));
        nsingals.put("true",new Token(Token.Kind.TRUE, lineNum, colNum));
        nsingals.put("void",new Token(Token.Kind.VOID, lineNum, colNum));
        nsingals.put("while",new Token(Token.Kind.WHILE, lineNum, colNum));

        singals.put(33, new Token(Token.Kind.NOT, lineNum, colNum));		//'!'
        singals.put(40, new Token(Token.Kind.LPAREN, lineNum, colNum));	//'('
        singals.put(41, new Token(Token.Kind.RPAREN, lineNum, colNum));	//')'
        singals.put(42, new Token(Token.Kind.TIMES, lineNum, colNum));	//'*'
        singals.put(43, new Token(Token.Kind.ADD, lineNum, colNum));		//'+'
        singals.put(44, new Token(Token.Kind.COMMA, lineNum, colNum));	//','
        singals.put(46, new Token(Token.Kind.DOT, lineNum, colNum));		//'.'
        singals.put(59, new Token(Token.Kind.SEMI, lineNum, colNum));		//';'
        singals.put(61, new Token(Token.Kind.ASSIGN, lineNum, colNum));	//'='
        singals.put(45, new Token(Token.Kind.SUB, lineNum, colNum));		//'-'
        singals.put(60, new Token(Token.Kind.LT, lineNum, colNum));		//'<'
        singals.put(91, new Token(Token.Kind.LBRACKET, lineNum, colNum));	//'['
        singals.put(93, new Token(Token.Kind.RBRACKET, lineNum, colNum));		//']'
        singals.put(123, new Token(Token.Kind.LBRACE, lineNum, colNum));	//'{'
        singals.put(125, new Token(Token.Kind.RBRACE, lineNum, colNum));	//'}'
    }

    // When called, return the next token (refer to the code "Token.java")
    // from the input stream.
    // Return EOF when reaching the end of the input stream.
    private Token nextToken0() throws Exception {
        int c = this.fileStream.read();
        // skip all kinds of "blanks"
        // think carefully about how to set up "colNum" and "rowNum" correctly?
        while (' ' == c || '\t' == c || '\n' == c) {
            if (c == '\n') {
                // 如果读到换行符，更新行号并将列号重置为0
                lineNum++;
                colNum = 1;
            } else {
                // 否则，增加列号
                colNum++;
            }
            c = this.fileStream.read();
        }
        switch (c) {
            case -1:
                // The value for "lineNum" is now "null",
                // you should modify this to an appropriate
                // line number for the "EOF" token.
                return new Token(Token.Kind.EOF, lineNum, 0);
            case '+':
                return new Token(Token.Kind.ADD, lineNum, colNum);
            case ',':
                return new Token(Token.Kind.COMMA, lineNum, colNum);
            case '-':
                return new Token(Token.Kind.SUB, lineNum, colNum);
            case '*':
                return new Token(Token.Kind.TIMES, lineNum, colNum);
            case '<':
                return new Token(Token.Kind.LT, lineNum, colNum);
            case '{':
                return new Token(Token.Kind.LBRACE, lineNum, colNum);
            case '}':
                return new Token(Token.Kind.RBRACE, lineNum, colNum);
            case '[':
                return new Token(Token.Kind.LBRACKET, lineNum, colNum);
            case ']':
                return new Token(Token.Kind.RBRACKET, lineNum, colNum);
            case '(':
                return new Token(Token.Kind.LPAREN, lineNum, colNum);
            case ')':
                return new Token(Token.Kind.RPAREN, lineNum, colNum);
            case '=':
                return new Token(Token.Kind.ASSIGN, lineNum, colNum);
            case '.':
                return new Token(Token.Kind.DOT, lineNum, colNum);
            case '!':
                return new Token(Token.Kind.NOT, lineNum, colNum);
            case ';':
                return new Token(Token.Kind.SEMI, lineNum, colNum);
            case '&':
                this.fileStream.mark(1);
                c = this.fileStream.read();
                if(c == '&'){
                    return new Token(Token.Kind.AND, lineNum, colNum);
                }else{
                    this.fileStream.reset();
                    // Error.error("error", "lexer", "'&' not allowed", lineNum, colNum);
                    return new Token(Token.Kind.AND, lineNum, colNum);
                }
            default:
                // Lab 1, exercise 9: supply missing code to
                // recognize other kind of tokens.
                // Hint: think carefully about the basic
                // data structure and algorithms. The code
                // is not that much and may be less than 50 lines.
                // If you find you are writing a lot of code, you
                // are on the wrong way.
                // throw new Todo(c);
                String temp = "" + (char)c;
                this.fileStream.mark(1);
                c = this.fileStream.read();

                if(patternNum.matcher(temp).matches()){
                    //数字开头
                    while(patternNum.matcher(""+(char)c).matches()){
                        temp += (char)c;
                        this.fileStream.mark(1);
                        c = this.fileStream.read();
                    }
                    this.fileStream.reset();
                    if(temp.startsWith("0") && 1 != temp.length()){
                        // Error.error("error", "lexer", "'&' not allowed", lineNum);
                        System.exit(0);
                    }
                    return new Token(Token.Kind.NUM, temp, lineNum, colNum);
                }else if(patternAlpha.matcher(temp).matches()){
                    //字母或下滑线开头
                    while(patternAlphas.matcher(""+(char)c).matches()){
                        temp += (char)c;
                        this.fileStream.mark(1);
                        c = this.fileStream.read();
                    }
                    this.fileStream.reset();
                    Token tt = nsingals.get(temp);
                    if(null != tt) {
                        tt.rowNum = lineNum;
                        return tt;
                    }
                    return new Token(Token.Kind.ID, temp, lineNum, colNum);
                }else{
                    // Error.error("error", "lexer", "unrecognize char \"" + temp + "\"", lineNum);
                    return nextToken0();
                    //return null;
                }
        }
    }


    public Token nextToken() {
        Token t = null;

        try {
            t = this.nextToken0();
        } catch (Exception e) {
            //e.printStackTrace();
            System.exit(1);
        }
        if (dumpToken) {
            System.out.println(t);
        }
        return t;
    }
}
