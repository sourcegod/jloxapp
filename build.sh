#! /bin/bash
# Script for compile and running Lox language
# Date: samedi, 27/04/19
# Author: Coolbrother
srcDir="src"
help="Usage:\n
cgen : Compile GenerateAst script\n
rgen : Run GenerateAst script\n
clox : Compile Lox files\n
rlox [FILE] : Run Lox with or without file"

if [ $# -eq 0 ]; then
    echo "build.sh"
    echo -e $help
elif [ "$1" = "cgen" ]; then
    # Compile GenerateAst.java
    echo "Compile GenerateAst.java"
    javac -d build/java $srcDir/com/craftinginterpreters/tool/GenerateAst.java
elif [ "$1" = "rgen" ]; then
    echo "Run GenerateAst"
    # Run GenerateAst
    java -cp build/java $srcDir/com.craftinginterpreters.tool.GenerateAst java/com/craftinginterpreters/lox
elif [ "$1" = "clox" ]; then
    if [ -z "$2" ]; then
        # Compile Lox files
        echo "Compile all Lox files"
        javac -d build/java $srcDir/com/craftinginterpreters/lox/*.java
    else
        echo "Compile one Lox file"
        javac -d build/java "$2"
    fi
elif [ "$1" = "rlox" ]; then
    # Run Lox
    if [ -z "$2" ]; then
        echo "Run Lox without file"
        java -cp build/java com.craftinginterpreters.lox.Lox
    else
        echo "Run Lox with file: $2"
        java -cp build/java com.craftinginterpreters.lox.Lox "$2"
    fi

fi

exit 0
