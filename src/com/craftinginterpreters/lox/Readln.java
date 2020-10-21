package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Arrays;
// import java.io.Console;
import java.util.Scanner;
class Readln implements LoxCallable {
      
    @Override
      public int arity() { return 1; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments)  {
        Scanner scan = new Scanner(System.in); 
        String input;
        if (arguments.size() >= 1)
            System.out.print(arguments.get(0));
        input = scan.nextLine();
       
        return input;
      }

      @Override
      public String toString() { return "<native fn: readln>"; }
  
}


