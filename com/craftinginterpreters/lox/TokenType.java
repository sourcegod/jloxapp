package com.craftinginterpreters.lox;

enum TokenType {
  // Single-character tokens.                      
  LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
  COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

  // One or two character tokens.                  
  BANG, BANG_EQUAL,
  EQUAL, EQUAL_EQUAL,
  GREATER, GREATER_EQUAL,
  LESSER, LESSER_EQUAL,
  PLUS_EQUAL, MINUS_EQUAL,
  PLUS_PLUS, MINUS_MINUS,
  STAR_EQUAL, SLASH_EQUAL, 
  MOD, MOD_EQUAL,
  EXP, EXP_EQUAL,
  QUESTION, COLON, 
  
  // bitwise operators
  BIT_AND, BIT_AND_EQUAL, BIT_NOT,
  BIT_OR, BIT_OR_EQUAL, 
  BIT_XOR, BIT_XOR_EQUAL,
  BIT_LEFT, BIT_LEFT_EQUAL,
  BIT_RIGHT, BIT_RIGHT_EQUAL,

  // Literals.                                     
  IDENTIFIER, STRING, NUMBER,

  // Keywords.                                     
  AND, BREAK, CONTINUE,
  CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
  PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

  EOF
}

