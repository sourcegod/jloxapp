package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

  final Environment globals = new Environment();
  private final Map<Expr, Integer> locals = new HashMap<>();
  private Environment environment = globals;


  Object outputResult;
  Boolean isPrint = false;

  Interpreter() {
    globals.define("clock", new LoxCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });

    globals.define("println", new Println());


  }

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
      // whether no print statement 
      if (!isPrint) printResult();
      isPrint = false;

    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  public void printResult() {
    // print global outputResult whether no print statement in the code
    // System.out.println(outputResult.getClass().getName());
    // if (outputResult instanceof String || outputResult instanceof Double)
    System.out.println(stringify(outputResult));
  
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);

      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
   
      case PLUS:
        checkNumberOperand(expr.operator, right);
        return (double)right;
    
 
    }


    // Unreachable.                              
    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    return lookUpVariable(expr.name, expr);
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      return environment.getAt(distance, name.lexeme);
    } else {
      return globals.get(name);
    }
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    // nil is only equal to nil.               
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    // Hack. Work around Java adding ".0" to integer-valued doubles.
    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  private Object evaluate(Expr expr) {
    outputResult =  expr.accept(this);
    
    return outputResult;
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);

  }

  void resolve(Expr expr, int depth) {
    locals.put(expr, depth);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        // System.out.println(statement.getClass().getName());
        if ( statement instanceof Stmt.Break) { 
          // System.out.println("un break statement");
          // this.environment = previous;
          // loopStack.peek().isBreak = true;
          // break;
        }
        
        // System.out.println("je passe ici");
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null; 
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function = new LoxFunction(stmt, environment);
    environment.define(stmt.name.lexeme, function);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    isPrint = true;

    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition))) {
      try {
        execute(stmt.body);
      } catch (RuntimeError err) {
        // System.out.println("Caught in visitWhile with message: " + err.getMessage());
        if (err.token.lexeme.equals("continue")) continue;
        if (err.token.lexeme.equals("break")) break;
      }

    }

    return null;
  }
  
  public Void visitBreakStmt(Stmt.Break stmt) {
    // System.out.println("Je suis dans break\n");
    // loopStack.peek().isBreak = true;
    // throw new RuntimeException("No while statement"); // RuntimeError(token, "No while statement");
    String msg = "";
    if (stmt.token.lexeme.equals("break")) {
      msg = "Error: Break must with while loop";
      throw new RuntimeError(stmt.token, msg);
    } else if (stmt.token.lexeme.equals("continue")) {
      msg = "Error: Continue must with while loop";
      throw new RuntimeError(stmt.token, msg);
    }

    return null;
  }


  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Integer distance = locals.get(expr);
    if (distance != null) {
      environment.assignAt(distance, expr.name, value);
    } else {
      globals.assign(expr.name, value);
    }

    return value;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right); 

    switch (expr.operator.type) {
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double)left > (double)right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left >= (double)right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left < (double)right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left <= (double)right;

      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);

      case MINUS:
      case MINUS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;

      case PLUS:
      case PLUS_EQUAL:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        } 

        if (left instanceof String && right instanceof String) {
          return (String)left + (String)right;
        }
        
        if (left instanceof Double && right instanceof String) {
          return stringify(left) + (String)right;
        }

        if (left instanceof String && right instanceof Double) {
          return (String)left + stringify(right);
        }

        throw new RuntimeError(expr.operator,
        "Operands must be numbers or strings.");

      // adding: SLASH_EQUAL, STAR_EQUAL, MODULO, MODULO_EQUAL
      case SLASH:
      case SLASH_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        if ((double)right != 0) return (double)left / (double)right;
        // adding: error: Division by zero
        throw new RuntimeError(expr.operator, 
            "Error: Division by zero.");

      case STAR:
      case STAR_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;

    // adding: MODULO, MODULO_EQUAL
    case MODULO:
      case MODULO_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left % (double)right;
    
    }

    // Unreachable.                                
    return null;
  }
  
  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);
    // System.out.println(expr.callee.getClass().getName());

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) { 
      arguments.add(evaluate(argument));
    }

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable)callee;

    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }
    if (callee instanceof Println) isPrint = true;

    return function.call(this, arguments);
  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

}

