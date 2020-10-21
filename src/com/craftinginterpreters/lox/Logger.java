package com.craftinginterpreters.lox;

class Logger {
  static boolean isDebug = true;

  public static void debug(Object msg){
    if (isDebug) {
        System.out.println(msg.toString());
    }
  }

}
