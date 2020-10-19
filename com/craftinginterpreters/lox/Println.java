package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Arrays;

class Println implements LoxCallable {
      
    @Override
      public int arity() { return 1; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        
        if (arguments.size() >= 1)
          System.out.println(arguments.get(0));
        else
          System.out.println("");
        
        
        return null;
      }

      @Override
      public String toString() { return "<native fn>"; }
  
}


