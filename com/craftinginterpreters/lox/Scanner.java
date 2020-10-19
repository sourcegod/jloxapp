package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*; 

class Scanner {
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int start = 0;
  private int current = 0;
  private int line = 1;
  private int col =0;

  private static final Map<String, TokenType> keywords;

  static {
    keywords = new HashMap<>();
    keywords.put("and",    AND);
    keywords.put("break",    BREAK);
    keywords.put("class",  CLASS);
    keywords.put("continue",    CONTINUE);
    keywords.put("else",   ELSE);
    keywords.put("false",  FALSE);
    keywords.put("for",    FOR);
    keywords.put("fun",    FUN);
    keywords.put("if",     IF);
    keywords.put("nil",    NIL);
    keywords.put("or",     OR);
    keywords.put("print",  PRINT);
    keywords.put("return", RETURN);
    keywords.put("super",  SUPER);
    keywords.put("this",   THIS);
    keywords.put("true",   TRUE);
    keywords.put("var",    VAR);
    keywords.put("while",  WHILE);
  }

  Scanner(String source) {
    this.source = source;
  }

  List<Token> scanTokens() {
    while (!isAtEnd()) {
      // We are at the beginning of the next lexeme.
      start = current;
      scanToken();
    }

    tokens.add(new Token(EOF, "", null, line, col+1));
    return tokens;
  }

  private boolean isAtEnd() {
    return current >= source.length();
  }

  private char advance() {
    current++;
    col++;
    return source.charAt(current - 1);
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line, col));
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(': addToken(LEFT_PAREN); break;
      case ')': addToken(RIGHT_PAREN); break;
      case '{': addToken(LEFT_BRACE); break;
      case '}': addToken(RIGHT_BRACE); break;
      case ',': addToken(COMMA); break;
      case '.': addToken(DOT); break;
      case '-': addToken(match('=') ? MINUS_EQUAL : MINUS); break;
      case '+': addToken(match('=') ? PLUS_EQUAL : PLUS); break;
      case ';': addToken(SEMICOLON); break;
      case '*': addToken(match('=') ? STAR_EQUAL : STAR); break; 

      case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
      case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
      case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
      case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
      
      // adding: SLASH_EQUAL and MOD, MOD_EQUAL
      case '/':
        if (match('/')) {
          // A comment goes until the end of the line.                
          skipComments();
          // while (peek() != '\n' && !isAtEnd()) advance();
        } else if (match('*')) {
            skipMultilineComments();
        } else {
          addToken(match('=') ? SLASH_EQUAL : SLASH);
        }
        break;
      case '%': addToken(match('=') ? MOD_EQUAL : MOD); break;
      
      // adding: colon, question tokens
      case ':': addToken(COLON); break;
      case '?': addToken(QUESTION); break;
      
      // adding: bitwise operators
      case '&': addToken(match('=') ? BIT_AND_EQUAL : BIT_AND); break;
      case '~': addToken(BIT_NOT); break;
      case '|': addToken(match('=') ? BIT_OR_EQUAL : BIT_OR); break;
      case '^': addToken(match('=') ? BIT_XOR_EQUAL : BIT_XOR); break;

      case ' ':
      case '\r':
      case '\t':
        // Ignore whitespace.                      
        break;

      case '\n':
        line++;
        col =0;
        break;

      case '"': string(); break;
      default:
        if (isDigit(c)) {
          number();

        } else if (isAlpha(c)) {
          identifier();

        } else {
          Lox.error(line, col, "Unexpected character.");
        }

        break;

    }
  }

    private void identifier() {
    while (isAlphaNumeric(peek())) advance();

    // See if the identifier is a reserved word.   
    String text = source.substring(start, current);

    TokenType type = keywords.get(text);
    if (type == null) type = IDENTIFIER;
    addToken(type);

  }

  private void number() {
    while (isDigit(peek())) advance();

    // Look for a fractional part.                            
    if (peek() == '.' && isDigit(peekNext())) {
      // Consume the "."                                      
      advance();

      while (isDigit(peek())) advance();
    }

    addToken(NUMBER,
        Double.parseDouble(source.substring(start, current)));
  }

  private void string() {
    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') {
          line++;
          col =0;
      }
      advance();
    }

    // Unterminated string.                                 
    if (isAtEnd()) {
      Lox.error(line, col, "Unterminated string.");
      return;
    }

    // The closing ".                                       
    advance();

    // Trim the surrounding quotes.                         
    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (source.charAt(current) != expected) return false;

    current++;
    col++;

    return true;
  }

  private char peek() {
    if (isAtEnd()) return '\0';
    return source.charAt(current);
  }

  private char peekNext() {
    if (current + 1 >= source.length()) return '\0';
    return source.charAt(current + 1);
  } 

  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
            c == '_';
  }

  private boolean isAlphaNumeric(char c) {
    return isAlpha(c) || isDigit(c);
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  } 

  private void skipComments() {
    while (!isAtEnd()) {
        if (peek() != '\n') {
            advance();
        } else {
            line++;
            col =0;
            return;
        }
    }
  
  }

  private void skipMultilineComments() {
      while (!isAtEnd()) {
        advance();
        if (peek() == '\n') {
            line++;
            col =0;
            continue;
        }
        if (peek() == '/' && peekNext() == '*') skipMultilineComments();
        if (match('*') && match('/')) break;
      }

  }


}

