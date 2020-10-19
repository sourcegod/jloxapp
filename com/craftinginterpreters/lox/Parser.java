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
    /* Grammar
     *
    * program     → declaration* EOF ;
    *
    * declaration → funDecl
    *       | VarDecl
    *       | statement ;
    *
    * funDecl  → "fun" function ;
    *
    * function → IDENTIFIER "(" parameters? ")" block ;
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
    *                  expression? ";"
    *                  expression? ")" statement ;
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
    * expression → assignment ;
    *
    * assignment → identifier "=" assignment
    *       | logic_or 
    *       | compoundAssignment ;
    *       | ternaryExpr ; 
    *
    * logic_or   → logic_and ( "or" logic_and )* ;
    *
    * compoundAssignment → identifier ( "+=" | "-=" | "*=" | "/=" | "%=" ) addition ;
    *
    * ternaryExpr : expression "?" expression ":" expression ;
    *
    * logic_and  → equality ( "and" equality )* ;
    *
    * equality → comparison ( ( "!=" | "==" ) comparison )* ;
    * 
    * comparison → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
    * 
    * addition → multiplication ( ( "+" | "-" ) multiplication )* ;
    *
    * multiplication → unary ( ( "/" | "*" | "%" ) unary )* ;
    * 
    * unary → ( "!" | "-" | "+" ) unary | call ;
    *
    * call  → primary ( "(" arguments? ")" )* ;
    *
    * primary → "true" | "false" | "nil"
    *      | NUMBER | STRING
    *        | "(" expression ")"
    *        | IDENTIFIER ;
    *
    * arguments → expression ( "," expression )* ;
    *
    * */

    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Expr expression() {
    // expression : assignment ;
    
    return assignment();
  }

  private Expr assignment() {
    /* assignment → identifier "=" assignment
    *       | logic_or
    *       | compoundAssignment ;
    * */

    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      }

      error(equals, "Invalid assignment target."); 
    }
    
    // adding: compound assignment
    if (match(PLUS_EQUAL, MINUS_EQUAL, 
              STAR_EQUAL, SLASH_EQUAL, MODULO_EQUAL)) {
      Token operator = previous();
      return compoundAssignment(expr, operator);
    }

    // adding: ternaryExpr
    if (match(QUESTION)) {
      Token operator = previous();
      return ternaryExpression(expr, operator);
    }


    return expr;
  }

  private Expr compoundAssignment(Expr expr, Token operator) {
    /* compoundAssignment → identifier ( "+=" | "-=" | "*=" | "/=" | "%=" ) addition ;
     *
    * */

  Expr value = addition();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;

        Expr val = new Expr.Binary(expr, operator, value);
        return new Expr.Assign(name, val);
      }

      error(operator, "Invalid compound assignment target.");

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


  private Expr or() {
    // logic_or   → logic_and ( "or" logic_and )* ;

    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    // logic_and  → equality ( "and" equality )* ;
    
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Stmt declaration() {
    /* declaration → funDecl
     *      | varDecl
    *       | statement ;
    * */

    try {

      if (match(FUN)) return function("function");
      
      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
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
    consume(SEMICOLON, "Expect ';' after value.");
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

    consume(SEMICOLON, "Expect ';' after variable declaration.");
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
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    // function → IDENTIFIER "(" parameters? ")" block ;
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

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

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);

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
    // comparison → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
    Expr expr = addition();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
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

    // adding MODULO
    while (match(SLASH, STAR, MODULO)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    // unary → ( "!" | "-" | "+" ) unary | call ;
    // adding: plus operator
    if (match(BANG, MINUS, PLUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 32) {
          error(peek(), "Cannot have more than 32 arguments.");
        }

        arguments.add(expression());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr call() {
    // call  → primary ( "(" arguments? ")" )* ;
    
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr primary() {
    /* primary → "true" | "false" | "nil"
    *        | NUMBER | STRING
    *        | "(" expression ")"
    *        | IDENTIFIER ;
     * */

    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");

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

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
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

