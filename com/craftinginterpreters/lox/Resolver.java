package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  private static class Variable {
    final Token name;
    VariableState state;

    private Variable(Token name, VariableState state) {
      this.name = name;
      this.state = state;
    }
  }

  private enum VariableState {
    DECLARED,
    DEFINED,
    READ
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
  }
  
  private enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
  }

  private final Interpreter interpreter;
  private final Stack<Map<String, Variable>> scopes = new Stack<>();
  Boolean isDebug = true;
  private FunctionType currentFunction = FunctionType.NONE;
  private ClassType currentClass = ClassType.NONE;

  Resolver(Interpreter interpreter) {
    debug("Resolver: ");
    this.interpreter = interpreter;
  }

  void debug(String msg) {
    if (isDebug == false) {
      Logger.debug(msg);
    }
    
  }

  void resolve(List<Stmt> statements) {
    debug("\nTopLevel Resolver");
    debug("Resolve statement list");
    for (Stmt statement : statements) {
      resolve(statement);
    printScopes();
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    debug("visitblock before scope");
    beginScope();
    resolve(stmt.statements);
    endScope();
    debug("visitblock after scope");
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    debug("visitClassStmt");
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    if (stmt.superclass != null &&
        stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      Lox.error(stmt.superclass.name,
          "A class cannot inherit from itself.");
    }

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;
      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      beginScope();
      // FIXE: see for variablestate 
      // Using READ as State to not generate "local variable inused" as error;.
      scopes.peek().put("super", new Variable(stmt.superclass.name, VariableState.READ));
    }

    beginScope();
    // FIXE: pass token of "this" by argument
    // Adding: VariableState 
    // Using State READ for "this" to not generate an error for variable inused
    scopes.peek().put("this", new Variable(stmt.name, VariableState.READ));

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method.function, declaration); 
    }

    endScope();

    if (stmt.superclass != null) endScope();
    currentClass = enclosingClass;

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    debug("visitExpressionStmt");
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    debug("visitFunctionStmt");
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt.function, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    debug("visitPrintStmt");
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Cannot return from top-level code.");
    }

    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Cannot return a value from an initializer.");
      }

      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    debug("visitVarStmt");
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    debug("visitWhileStmt");
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }
  
  // adding: visitBreakStmt
  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    
    debug("visitBreakStmt");
    
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    debug("visitAssignExpr");
    resolve(expr.value);
    resolveLocal(expr, expr.name, false);
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    debug("visitBinaryExpr");
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }
  // adding: visitTernaryExpr
  @Override
  public Void visitTernaryExpr(Expr.Ternary expr) {
    debug("visitTernaryExpr");
    resolve(expr.condition);
    resolve(expr.thenBranch);
    resolve(expr.elseBranch);

    return null;
  }


  @Override
  public Void visitCallExpr(Expr.Call expr) {
    debug("visitCallExpr");
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }
  
  @Override
  public Void visitGetExpr(Expr.Get expr) {
    debug("visitGetExpr");
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    debug("visitFunctionExpr");
    resolveFunction(expr, FunctionType.FUNCTION);
    return null;

  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    debug("visitGroupingExpr");
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    debug("visitLiteralExpr");
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    debug("visitLogicalalExpr");
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    debug("visitSetExpr");
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Cannot use 'super' outside of a class.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword,
          "Cannot use 'super' in a class with no superclass.");
    }
    // Adding: ...
    resolveLocal(expr, expr.keyword, true);
    
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
      debug("visitThisExpr");
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Cannot use 'this' outside of a class.");
      return null;
    }

    resolveLocal(expr, expr.keyword, true);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    debug("visitUnaryExpr");
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    debug("visitVariableExpr");
    if (!scopes.isEmpty() &&
            scopes.peek().containsKey(expr.name.lexeme) &&
            scopes.peek().get(expr.name.lexeme).state == VariableState.DECLARED) {
          Lox.error(expr.name,
              "Cannot read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name, true);
    return null;
  }

  private void resolve(Stmt stmt) {
    debug("Resolve Stmt");
    stmt.accept(this);
  }

  private void resolveFunction(
      Expr.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    debug("resolveFunction before beginscope");
    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();
    currentFunction = enclosingFunction;
    debug("resolveFunction after endscope");
  }

  private void resolve(Expr expr) {
    debug("resolve expr");
    expr.accept(this);
  }

 
  private void beginScope() {
    debug("beginScope");
    scopes.push(new HashMap<String, Variable>());
  }

  private void endScope() {
    debug("endScope");
    Map<String, Variable> scope = scopes.pop();

        // FIXE: variables inused
        for (Map.Entry<String, Variable> entry : scope.entrySet()) {
          if (entry.getValue().state == VariableState.DEFINED) {
            Lox.error(entry.getValue().name, "Local variable is not used.");
          }
        }
  }

  private void declare(Token name) {
    debug("declare name");
    if (scopes.isEmpty()) return;

    Map<String, Variable> scope = scopes.peek();
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Variable with this name already declared in this scope.");
    }

    scope.put(name.lexeme, new Variable(name, VariableState.DECLARED));
  }

  private void define(Token name) {
    debug("define name");
    if (scopes.isEmpty()) return;
    scopes.peek().get(name.lexeme).state = VariableState.DEFINED;
  }

  private void resolveLocal(Expr expr, Token name, boolean isRead) {
    debug("resolveLocal expr");
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - 1 - i);

        // Mark it used.
        if (isRead) {
          scopes.get(i).get(name.lexeme).state = VariableState.READ;
        }
        return;
      }
    }

    // Not found. Assume it is global.
  }

  void printScopes() {
    debug("\nPrint scopes");
    Map<String, Variable> scope;
    if (scopes.isEmpty()) {
      debug("Scopes are empty");
      return;
    }
    for (int i=0; i< scopes.size(); i++) {
      scope = scopes.get(i);
      for (String key: scope.keySet()) {
        Variable cVar = scope.get(key);
        debug(key + ": " + cVar.name + ", " + cVar.state);
      
      }
    }

  }

}

