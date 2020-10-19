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
  boolean isDebug = false;
  String classTitle = "Interpreter: ";

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

  void debug(String msg) {
    if (isDebug == true) {
      Logger.debug(msg);
    }
    
  }

  String getClassName(Object obj) {
    String name = obj.getClass().getName();
    name = name.substring(name.lastIndexOf(".") +1);
    return name;
  }
  void interpret(List<Stmt> statements) {
    String name = "";
    /*
   // debug("\n" + classTitle);
   // debug("List of Statement class name");
    for (int i=0; i < statements.size(); i++) {
      Stmt item = statements.get(i);
      // name = item.getClass().getName();
      name = getClassName(item);
     // debug("Statement " + i + ": " + name);
    }
    */


    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
      
      // whether no print statement 
      if (!isPrint) printResult();
      isPrint = false;
      // printState();

    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  public void printResult() {
    // print global outputResult whether no print statement in the code
    // System.out.println(outputResult.getClass().getName());
    // System.out.println("Finalement:"); 
    if (outputResult instanceof String || outputResult instanceof Double ||
            outputResult instanceof Boolean) {
        System.out.println(stringify(outputResult));
        outputResult = null;
    }
  
  }

  public void printState() {
     // debug("\nEnvironment state");
     // debug("Globals state");
      Map<String, Object> values = globals.values;
      if (values.isEmpty()) {
         // debug("Globals is empty");
      } else {
          for (Map.Entry<String, Object> entry : values.entrySet())  {
             // debug(entry.getKey() + ": " + entry.getValue());
          }
      }
     // debug("\nLocals state");
      if (locals.isEmpty()) {
         // debug("Locals is empty");
      } else {
          for (Map.Entry<Expr, Integer> entry : locals.entrySet()) {
             // debug(entry.getKey() + ": " + entry.getValue());
          }
      }
 
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
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof LoxInstance)) { 
      throw new RuntimeError(expr.name, "Only instances have fields.");
    }

    Object value = evaluate(expr.value);
    ((LoxInstance)object).set(expr.name, value);
    return value;
  }

  @Override
  public Object visitSuperExpr(Expr.Super expr) {
    int distance = locals.get(expr);
    LoxClass superclass = (LoxClass)environment.getAt(
        distance, "super");

    // "this" is always one level nearer than "super"'s environment.
    LoxInstance object = (LoxInstance)environment.getAt(
        distance - 1, "this");

    LoxFunction method = superclass.findMethod(expr.method.lexeme);
    if (method == null) {
      throw new RuntimeError(expr.method,
          "Undefined property '" + expr.method.lexeme + "'.");
    }

    return method.bind(object);
  }

  @Override
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
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
    
      case BIT_NOT:
        if (isInteger(right)) {
            Double dRight = (Double)right;
            return (double)(~dRight.intValue());
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operand must be integer.");
  
    }


    // Unreachable.                              
    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
   // debug("visitVariableExpr: name: " + expr.name.lexeme);
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
    if (object instanceof Double) {
      // System.out.println("Je passe ici");
      Double dbl = (Double)object;
      if (dbl == 0) return false; 
    }
    if (object instanceof Boolean) return (boolean)object;
    
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    // nil is only equal to nil.               
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }
  //
  // adding: isInteger
  private  boolean isInteger(Object object) {
      if (object instanceof Double) {
          double val = (double)object;
          return !Double.isInfinite(val) && (Math.floor(val) == val);
      }

      return false;

  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    
    throw new RuntimeError(operator, "Operands must be numbers.");
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
    // debug("evaluate: expr : " + getClassName(expr));
    outputResult =  expr.accept(this);
    // debug("evaluate: result : " + outputResult);
    
    return outputResult;
  }

  private void execute(Stmt stmt) {
   // debug("\nexecute top level: stmt: " + getClassName(stmt));
    stmt.accept(this);

  }

  void resolve(Expr expr, int depth) {
   // debug("\nResolve expr in Interpreter");
    locals.put(expr, depth);
  }

  void executeBlock(List<Stmt> statements, Environment environment) {
   // debug("executeBlock: ");
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        // System.out.println(statement.getClass().getName());
        // System.out.println("je passe ici");
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
   // debug("visitBlock: " + getClassName(stmt.statements));
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
   // debug("visitClassStmt");
    Object superclass = null;
    if (stmt.superclass != null) {
      superclass = evaluate(stmt.superclass);
      if (!(superclass instanceof LoxClass)) {
        throw new RuntimeError(stmt.superclass.name,
            "Superclass must be a class.");
      }
    }

    environment.define(stmt.name.lexeme, null);

    if (stmt.superclass != null) {
      environment = new Environment(environment);
      environment.define("super", superclass);
    }

    Map<String, LoxFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      // Adding: params for lambda function in LoxFunction object
      LoxFunction function = new LoxFunction(method.name.lexeme, method.function, environment, 
              method.name.lexeme.equals("init"));
      methods.put(method.name.lexeme, function);
    }

    LoxClass klass = new LoxClass(stmt.name.lexeme,
        (LoxClass)superclass, methods);

    if (superclass != null) {
      environment = environment.enclosing;
    }

    environment.assign(stmt.name, klass);

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
   // debug("visitExpressionStmt: expression : " + getClassName(stmt.expression));
    evaluate(stmt.expression);
    return null; 
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
   // debug("visitFunctionStmt : name: " + stmt.name.lexeme);
    // Adding: params for lambda function
    LoxFunction function = new LoxFunction(stmt.name.lexeme, stmt.function, 
        environment, false);

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
   // debug("visitPrint: expression: " + getClassName(stmt.expression));
    Object value = evaluate(stmt.expression);
    value = stringify(value);
    System.out.println(value);
    isPrint = true;
   // debug("visitPrint apres stringify value: " + value);

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
   // debug("VisitVarStmt: ");
   // debug("var name: " + stmt.name.lexeme);
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }
   // debug("Var value: " + value);

    environment.define(stmt.name.lexeme, value);
    printState();
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
   // debug("visitWhileStmt: " );
    while (isTruthy(evaluate(stmt.condition))) {
      try {
        execute(stmt.body);
      } catch (RuntimeError err) {
        // System.out.println("Caught in visitWhile with message: " + err.getMessage());
        if (err.token.lexeme.equals("continue")) continue;
        if (err.token.lexeme.equals("break")) break;
      }

    }
    
   // debug("End visitWhileStmt");

    return null;
  }
  
  public Void visitBreakStmt(Stmt.Break stmt) {
    // System.out.println("Je suis dans break\n");
    // loopStack.peek().isBreak = true;
    // throw new RuntimeException("No while statement"); // RuntimeError(token, "No while statement");
    String msg = "";
    if (stmt.keyword.lexeme.equals("break")) {
      msg = "Error: Break must with while loop";
      throw new RuntimeError(stmt.keyword, msg);
    } else if (stmt.keyword.lexeme.equals("continue")) {
      msg = "Error: Continue must with while loop";
      throw new RuntimeError(stmt.keyword, msg);
    }

    return null;
  }


  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
   // debug("VisitAssignexpr : expr : " + getClassName(expr));
    Object value = evaluate(expr.value);
   // debug("Assign name: " + expr.name.lexeme);
   // debug("Assign value: " + value);

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
   // debug("VisitBinaryExpr: ");
   // debug("left: " + left + ", operator: " + expr.operator.lexeme + ", right: " + right);

    switch (expr.operator.type) {
      // adding: string comparison
      case GREATER:
        if (left instanceof Double && right instanceof Double) {
            return (double)left > (double)right;
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) > 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");
      
      case GREATER_EQUAL:
        if (left instanceof Double && right instanceof Double) {
            return (double)left >= (double)right;
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) >= 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");

      case LESSER:
        if (left instanceof Double && right instanceof Double) {
            return (double)left < (double)right;
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) < 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");

      case LESSER_EQUAL:
        if (left instanceof Double && right instanceof Double) {
            return (double)left <= (double)right;
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) <= 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");

      case BANG_EQUAL: 
        if (left instanceof Double && right instanceof Double) {
            return !isEqual(left, right);
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) != 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");

      case EQUAL_EQUAL: 
        if (left instanceof Double && right instanceof Double) {
            return isEqual(left, right);
        }
        if (left instanceof String && right instanceof String) {
            return left.toString().compareTo((String)right) == 0;
        }
        throw new RuntimeError(expr.operator, "Comparison not supported for operands.");

      case MINUS:
      case MINUS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;

        // adding: concat string to number
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
        throw new RuntimeError(expr.operator, "Operands must be numbers or strings.");

      // adding: SLASH_EQUAL, STAR_EQUAL, MOD, MOD_EQUAL
      case SLASH:
      case SLASH_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        if ((double)right != 0) return (double)left / (double)right;
        // adding: error: Division by zero
        throw new RuntimeError(expr.operator, "Error: Division by zero.");

      case STAR:
      case STAR_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;

      // Adding: EXP
      case EXP:
      case EXP_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return Math.pow((double)left, (double)right);


    // adding: MOD, MOD_EQUAL
    case MOD:
    case MOD_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        if ((double)right != 0) return (double)left % (double)right;
        // adding: error: Division by zero

        throw new RuntimeError(expr.operator,  "Error: Division by zero.");

    // Adding: bitwise operators
    case BIT_OR:
    case BIT_OR_EQUAL:
        if (isInteger(left) && isInteger(right)) {
            Double dLeft = (Double)left;
            Double dRight = (Double)right;
            int val = dLeft.intValue() | dRight.intValue();
            return (double)val;
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operands must be integers.");
 
    case BIT_AND:
    case BIT_AND_EQUAL:
        if (isInteger(left) && isInteger(right)) {
            Double dLeft = (Double)left;
            Double dRight = (Double)right;
            int val = dLeft.intValue() & dRight.intValue();
            return (double)val;
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operands must be integers.");

    case BIT_XOR:
    case BIT_XOR_EQUAL:
        if (isInteger(left) && isInteger(right)) {
            Double dLeft = (Double)left;
            Double dRight = (Double)right;
            int val = dLeft.intValue() ^ dRight.intValue();
            return (double)val;
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operands must be integers.");
    
    case BIT_LEFT:
    case BIT_LEFT_EQUAL:
        if (isInteger(left) && isInteger(right)) {
            Double dLeft = (Double)left;
            Double dRight = (Double)right;
            int val = dLeft.intValue() << dRight.intValue();
            return (double)val;
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operands must be integers.");

    case BIT_RIGHT:
    case BIT_RIGHT_EQUAL:
        if (isInteger(left) && isInteger(right)) {
            Double dLeft = (Double)left;
            Double dRight = (Double)right;
            int val = dLeft.intValue() >> dRight.intValue();
            return (double)val;
        }
        throw new RuntimeError(expr.operator, "RuntimeError: operands must be integers.");

    // Adding: comma operator
    case COMMA: 
        return right;
     
    }

    // Unreachable.                                
    return null;
  }
  // adding: visitTernaryExpr
  @Override
  public Object visitTernaryExpr(Expr.Ternary expr) {
    if (isTruthy(evaluate(expr.condition))) {
      return evaluate(expr.thenBranch);
    }
    
    return evaluate(expr.elseBranch);

  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
   // debug("visitCallExpr : callee: " + getClassName(expr.callee));
    Object callee = evaluate(expr.callee);
   // debug("voici callee : " + callee);

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
  
  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);
    if (object instanceof LoxInstance) {
      return ((LoxInstance) object).get(expr.name);
    }

    throw new RuntimeError(expr.name,
        "Only instances have properties.");
  }

  public Object visitFunctionExpr(Expr.Function expr) {
    // Adding: for lambda function
   // debug("visitFunctionExpr : " + getClassName(expr));
   // debug("visitFunctionExpr : body " + getClassName(expr.body));
    // executeBlock(expr.body, new Environment(environment));

 return new LoxFunction(null, expr, environment, false);

  }

}
