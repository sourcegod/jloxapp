package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {

  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {
    /* Grammar for compound assignment 2
     *
    * program     → declaration* EOF ;
    *
    * declaration → classDecl
    *       | funDecl
    *       | VarDecl
    *       | statement ;
    *
    * classDecl → "class" IDENTIFIER ( "<" IDENTIFIER )?
    *        "{" function* "}" ;
    *
    * funDecl  → "fun" function ;
    *
    * function → IDENTIFIER "(" functionBody ")" ;
    *
    * functionBody → "(" parameters? ")" block ;
    *
    * parameters → IDENTIFIER ( "," IDENTIFIER )* ;
    *
    * varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    *
    * statement → exprStmt
    *           | forStmt
    *           | ifStmt
    *           | printStmt
    *           | returnStmt
    *           | whileStmt
    *           | breakStmt
    *           | continueStmt
    *           | block ;
    *
    * exprStmt  → expression ";" ;
    *
    *  forStmt   → "for" "(" ( varDecl | exprStmt | ";" )
    *                  ( expression )? ";"
    *                  ( expression )? ")" statement ;
    *
    * ifStmt    → "if" "(" expression ")" statement ( "else" statement )? ;
    *
    * printStmt → "print" expression ";" ;
    *
    * returnStmt → "return" expression? ";" ;
    *
    * whileStmt → "while" "(" expression ")" statement ;
    *
    * breakStmt → "break" ";" 
    *             | "continue" ";" ;
    *
    * continueStmt → "continue" ";" ;
    *
    * block     → "{" declaration* "}" ;
    *
    * expression → comma ;
    *
    * comma → assignment ( "," assignment )* ;
    *
    * assignment → ( call "." )? identifier ( ( "=" 
    *               | "+=" | "-=" | "*=" | "/=" | "%=" 
    *               | "&=" | "|=" | "^="
    *               | "**=" | "<<=" | ">>=" ) assignment )? 
    *       | conditional ;
    *
    * conditional : logicOr "?" expression ":" conditional;
    *
    * logicOr   → logicAnd ( "or" logicAnd )* ;
    *
    * logicAnd  → bitwiseOr ( "and" bitwiseOr )* ;
    *
    * bitwiseOr → bitwiseXor ( ( "|" ) )* bitwiseXor ;
    *
    * bitwiseXor → bitwiseAnd ( ( "^" ) )* bitwiseAnd ;
    *
    * bitwiseAnd → equality ( ( "&" ) )* equality ;
    *
    * equality → comparison ( ( "!=" | "==" ) comparison )* ;
    * 
    * comparison → bitwiseShift ( ( ">" | ">=" | "<" | "<=" ) bitwiseShift )* ;
    *
    * bitwiseShift → addition ( ( "<<" | ">>" ) addition )* ;
    * 
    * addition → multiplication ( ( "+" | "-" ) multiplication )* ;
    *
    * multiplication → unary ( ( "/" | "*" | "%" ) unary )* ;
    *
    * unary → ( "!" | "-" | "+" | "++" | "--" | "~") unary 
    *           | exponentiation ;
    *
    * exponentiation → prefix ( ( "**" ) unary )* ;
    *
    * prefix → ( "--" | "++" ) primary 
    *           | postfix ;
    *
    * postfix → primary ( "--" | "++" ) 
    *           | call ;
    *
    * call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
    *
    * primary → "true" | "false" | "nil"
    *        | NUMBER | STRING
    *        | "(" expression ")"
    *        | this
    *        | IDENTIFIER
    *        | lambda
    *        | "super" "." IDENTIFIER ;
    *
    * arguments → assignment ( "," assignment )* ;
    *
    * */

    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Stmt declaration() {
    /* declaration → classDecl
     *      | funDecl
     *      | varDecl
    *       | statement ;
    * */

    try {
      
      if (match(CLASS)) return classDeclaration();

      // detect classic function
      if (check(FUN) && checkNext(IDENTIFIER) ) {
        // Logger.debug("je suis apres fun");
        consume(FUN, null);
        return function("function");
      }

      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    /* classDecl → "class" IDENTIFIER ( "<" IDENTIFIER )?
    *        "{" function* "}" ;
    */

    Token name = consume(IDENTIFIER, "Expect class name.");
    Expr.Variable superclass = null;
    if (match(LESSER)) {
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }

    consume(LEFT_BRACE, "Expect '{' before class body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }

    consume(RIGHT_BRACE, "Expect '}' after class body.");

    return new Stmt.Class(name, superclass, methods);

  }

  private Stmt statement() {
    /* statement → exprStmt
     *            | forStmt
     *            | ifStmt 
    *             | printStmt
    *             | returnStmt 
    *             | whileStmt 
    *             | breakStmt
    *             | continueStmt
    *             | block ;
    * */

    // manage semicolon with empty statement
    if (match(SEMICOLON)) return expressionStatement();
     
    if (match(FOR)) return forStatement();

    if (match(IF)) return ifStatement();
    
    if (match(PRINT)) return printStatement();

    if (match(RETURN)) return returnStatement();
    
    if (match(WHILE)) return whileStatement();

    if (match(BREAK, CONTINUE)) return breakStatement(previous());

    if (match(LEFT_BRACE)) return new Stmt.Block(block());
    
    return expressionStatement();
  }

  private Stmt forStatement() {
    /*  forStmt   → "for" "(" ( varDecl | exprStmt | ";" )
    *                  expression? ";"
    *                  expression? ")" statement ;
    * */
    
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body = statement();

    if (increment != null) {
      body = new Stmt.Block(Arrays.asList(
          body,
          new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;

  }
  
  private Stmt breakStatement(Token token) {
    /* breakStmt → "break" ";"
     *             | "continue" ";" ;
    *
    * */
    
    // Adding newline test
    consume(SEMICOLON, "Expected ';' after break or continue statement.");

    return new Stmt.Break(token);
    
  }

  private Stmt ifStatement() {
    // ifStmt    → "if" "(" expression ")" statement ( "else" statement )? ;
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition."); 

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    // printStmt → "print" expression ";" ;

    Expr value = expression();
    // Adding: no require semicolon
    // checking whether not end line for automatic semicolon insertion
    checkEndLine("Expect ';' after value.");


    return new Stmt.Print(value);
  }
  
  private Stmt returnStatement() {
    // returnStmt → "return" expression? ";" ;

    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");

    return new Stmt.Return(keyword, value);
  }

  private Stmt varDeclaration() {
    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }
    // consume(SEMICOLON, "Expect ';' after variable declaration.");
    // checking whether not end line for automatic semicolon insertion
    checkEndLine("Expect ';' after variable declaration.");


    return new Stmt.Var(name, initializer);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after condition.");
    Stmt body = statement();

    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    // exprStmt  → expression ";" ;

    Expr expr = expression();
    // checking whether not end line for automatic semicolon insertion
    checkEndLine("Expect ';' after expression.");

    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    // function → IDENTIFIER "(" parameters? ")" block ;
      Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
      return new Stmt.Function(name, functionBody(kind));
  }

  private Expr.Function functionBody(String kind) {
    // functionBody → "(" parameters? ")" block ;

    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 32) {
          error(peek(), "Cannot have more than 32 parameters.");
        }

        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    // manage Automatic semicolon insertion
    // match(SEMICOLON);
    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Expr.Function(parameters, body);
  }
  
  private List<Stmt> block() {
    // block     → "{" declaration* "}" ;
    
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");

    return statements;
  }

  private Expr expression() {
    // expression : comma ;
   // adding: comma 
    return comma();
  }

  private Expr comma() {
      // comma : assignment ( "," assignment )* ;
      Expr left = assignment();
      while (match(COMMA)) {
          Token operator = previous();
          Expr right = assignment();
          left = new Expr.Binary(left, operator, right);
      }

      return left;
  }

  private Expr assignment() {
    /* assignment → ( call "." )? identifier ( ( "=" 
    *               | "+=" | "-=" | "*=" | "/=" | "%=" 
    *               | "&=" | "|=" | "^="
    *               | "**=" | "<<=" | ">>=" ) assignment )? 
    *       | conditional ;
    * */

    Expr expr = conditional();

    if (match(EQUAL, PLUS_EQUAL, MINUS_EQUAL,
              STAR_EQUAL, SLASH_EQUAL, 
              MOD_EQUAL, EXP_EQUAL,
              BIT_AND_EQUAL, BIT_OR_EQUAL, BIT_XOR_EQUAL,
              BIT_LEFT_EQUAL, BIT_RIGHT_EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, equals, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get)expr;
        return new Expr.Set(get.object, get.name, value);
      }

      error(equals, "Invalid assignment target."); 
    }
    
    /*
    // adding: compound assignment
    if (match(PLUS_EQUAL, MINUS_EQUAL, 
              STAR_EQUAL, SLASH_EQUAL, MOD_EQUAL,
              BIT_AND_EQUAL, BIT_OR_EQUAL, BIT_XOR_EQUAL,
              BIT_LEFT_EQUAL, BIT_RIGHT_EQUAL,
              EXP_EQUAL)) {
      Token operator = previous();
      return compoundAssignment(expr, operator);
    }
    */


    return expr;
  }

  private Expr compoundAssignment(Expr expr, Token operator) {
    // Deprecated: not used
    /* compoundAssignment → identifier ( "+=" | "-=" | "*=" | "/=" | "%=" 
    *                               | "&=" | "|=" | "^=" 
    *                               | "**=" | "<<=" | ">>=" ) expression ;
    * */

  Expr value = expression();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;

        Expr val = new Expr.Binary(expr, operator, value);
        return new Expr.Assign(name, operator, val);
      }

      error(operator, "Invalid compound assignment target.");

      return expr;
  
  }
   
  private Expr conditional() {
      // conditional : logicOr "?" expression ":" conditional;
      // Adding: conditional
      Expr expr = logicOr();
      if (match(QUESTION)) {
          Expr thenBranch = expression();
          consume(COLON, 
                  "Expect ':' after then branch of conditional expression.");
          Expr elseBranch = conditional();
          return new Expr.Ternary(expr, thenBranch, elseBranch);
      }

      return expr;
  }
  
 
  private Expr ternaryExpression(Expr condition, Token operator) {
      // ternaryExpr : expression "?" expression ":" expression ;
      
      if (condition instanceof Expr) {
        Expr thenBranch = expression();
        consume(COLON, "expected ':' after expression");
        Expr elseBranch = expression();
        return new Expr.Ternary(condition, thenBranch, elseBranch);
      }

      error(operator, "Invalid Ternary Expression target.");

      return condition;
  }


  private Expr logicOr() {
    // logicOr   → logicAnd ( "or" logicAnd )* ;

    Expr expr = logicAnd();

    while (match(OR)) {
      Token operator = previous();
      Expr right = logicAnd();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr logicAnd() {
    // logicAnd  → bitwiseOr ( "and" bitwiseOr )* ;
    
    Expr expr = bitwiseOr();

    while (match(AND)) {
      Token operator = previous();
      Expr right = bitwiseOr();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr bitwiseOr() {
    // bitwiseOr → bitwiseXor ( ( "|" ) )* bitwiseXor ;
    
    Expr expr = bitwiseXor();

    while (match(BIT_OR)) {
      Token operator = previous();
      Expr right = bitwiseXor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr bitwiseXor() {
    // bitwiseXor → bitwiseAnd ( ( "^" ) )* bitwiseAnd ;
    
    Expr expr = bitwiseAnd();

    while (match(BIT_XOR)) {
      Token operator = previous();
      Expr right = bitwiseAnd();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr bitwiseAnd() {
    // bitwiseAnd → equality ( ( "&" ) )* equality ;
    
    Expr expr = equality();

    while (match(BIT_AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    // comparison → bitwiseShift ( ( ">" | ">=" | "<" | "<=" ) bitwiseShift )* ;
    
    // Adding: bitwiseShift operators
    Expr expr = bitwiseShift();

    while (match(GREATER, GREATER_EQUAL, LESSER, LESSER_EQUAL)) {
      Token operator = previous();
      Expr right = bitwiseShift();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr bitwiseShift() {
    // bitwiseShift → addition ( ( "<<" | ">>" ) addition )* ;
    Expr expr = addition();

    while (match(BIT_LEFT, BIT_RIGHT)) {
      Token operator = previous();
      Expr right = addition();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }


  private Expr addition() {
    // addition → multiplication ( ( "+" | "-" ) multiplication )* ;
    
    Expr expr = multiplication();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = multiplication();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr multiplication() {
    // multiplication → unary ( ( "/" | "*" | "%" ) unary )* ;
    
    Expr expr = unary();

    while (match(SLASH, STAR, MOD)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr modulo() {
    // Deprecated: not used
    // modulo → exponentiation ( ( "%" ) exponentiation )* ;
    
    Expr expr = exponentiation();

    // adding MOD
    while (match(MOD)) {
      Token operator = previous();
      Expr right = exponentiation();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    /* unary → ( "!" | "-" | "+" | "--" | "++" | "~" ) unary 
     *          | exponentiation ;
     * */

    // adding: plus, bit_not, operator
    if (match(BANG, MINUS, PLUS, BIT_NOT)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right, false);
    }

    return exponentiation();
  }

  private Expr exponentiation() {
    // exponentiation → prefix ( ( "**" ) unary )* ;
    
    Expr expr = prefix();

    // Adding: exponent
    while (match(EXP)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr prefix() {
    /* prefix → ( "--" | "++" ) primary 
     *          | postfix ;
     * */
    
    // Adding: prefix operators
    if (match(MINUS_MINUS, PLUS_PLUS)) {
      Token operator = previous();
      Expr right = primary();
      return new Expr.Unary(operator, right, false);
    }

    return postfix();

  }

  private Expr postfix() {
    /* postfix → primary ( "--" | "++" ) 
     *           | call ;
     * */
    
    // Adding: postfix operators
    // System.out.println("Avant postfix: " + peek().type + ", " + current);
    if (matchNext(MINUS_MINUS, PLUS_PLUS)) {
        Token operator = peek();
        current--;
        Expr left = primary();
        advance();
        return new Expr.Unary(operator, left, true);
    }

    return call();

  }

  private Expr call() {
    // call → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;
    
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(DOT)) {
        Token name = consume(IDENTIFIER,
            "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 8) {
          error(peek(), "Cannot have more than 8 arguments.");
        }

        // calling assignment function rather than expression to avoid the comma operator
        arguments.add(assignment());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private List<Expr> expressionList() {
    List<Expr> exprList = new ArrayList<>();
      do { 

        exprList.add(expression());
      } while (match(COMMA));

    return exprList;
    // return new Expr.ExprList(exprList)
  }


  private Expr primary() {
    /* primary → "true" | "false" | "nil"
    *        | NUMBER | STRING
    *        | "(" expression ")"
    *        | this
    *        | IDENTIFIER 
    *        | lambda
    *        | "super" "." IDENTIFIER ;
     * */

    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER,
          "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }

    if (match(THIS)) return new Expr.This(previous());

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }
    // lambda function
    if (match(FUN)) return functionBody("function");

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }
    
   
    throw error(peek(), "in primary function, Expect expression.");

  }

  private  boolean checkEndLine(String message) {
    if (isAtEnd()) return false;
    if (match(SEMICOLON)) return true;

    throw error(peek(), message);

  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private boolean matchNext(TokenType... types) {
    for (TokenType type : types) {
      if (checkNext(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private boolean checkNext(TokenType tokenType) {
    if (isAtEnd()) return false;
    if (tokens.get(current + 1).type == EOF) return false;
    return tokens.get(current + 1).type == tokenType;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }

}

