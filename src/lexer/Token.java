package lexer;

// Lab 1, exercise 8: read the MiniJava specification carefully,
// and fill in other possible tokens.
public class Token {
    // alphabetically ordered
    public enum Kind {
        ADD,
        CLASS,
        COMMA,  // ,
        DOT,
        EOF,
        ID,
        INT,    // int
        LBRACKET,   // [
        LENGTH, // length
        LPAREN, // (
        NEW,    // new
        RBRACKET,   // ]
        RPAREN, // )
        // SEMICOLON,  // ;
        // NEW TOKEN ADDED
        AND, // "&&"
        ASSIGN, // "="
        BOOLEAN, // "boolean"
        ELSE, // "else"
        EXTENDS, // "extends"
        FALSE, // "false"
        IF, // "if"
        LBRACE, // "{"
        LT, // "<"
        MAIN, // "main"
        NOT, // "!"
        NUM, // IntegerLiteral
        // "out" is not a Java key word, but we treat it as
        // a MiniJava keyword, which will make the
        // compilation a little easier. Similar cases apply
        // for "println", "System" and "String".
        OUT, // "out"
        PRINTLN, // "println"
        PUBLIC, // "public"
        RBRACE, // "}"
        RETURN, // "return"
        SEMI, // ";"
        STATIC, // "static"
        STRING, // "String"
        SUB, // "-"
        SYSTEM, // "System"
        THIS, // "this"
        TIMES, // "*"
        TRUE, // "true"
        VOID, // "void"
        WHILE, // "while"
    }

    // kind of the token
    public Kind kind;
    // extra lexeme for this token, if any
    public String lexeme;
    // position of the token in the source file: (row, column)
    public Integer rowNum;
    public Integer colNum;


    public Token(Kind kind,
                 Integer rowNum,
                 Integer colNum) {
        this.kind = kind;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    public Token(Kind kind,
                 String lexeme,
                 Integer rowNum,
                 Integer colNum) {
        this.kind = kind;
        this.lexeme = lexeme;
        this.rowNum = rowNum;
        this.colNum = colNum;
    }

    @Override
    public String toString() {
        String s;

        s = STR."""
        : \{(this.lexeme == null) ? "<NONE>" : this.lexeme}
        : at row \{this.rowNum == null ? "<null>" : rowNum.toString()}
        : at column \{this.colNum == null ? "<null>" : colNum.toString()}
        """;
        // System.out.println(this.kind);
        return this.kind + s;
    }
}