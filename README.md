# Assignment-5-Assembler
Assignment-5-Assembler

This project includes a MIPS assembler and disassembler written in Java. The assembler converts a MIPS assembly file into ASCII and binary machine-code files. The disassembler converts either file back into assembly code.

How to Run

You need a JDK. This project was tested with Java 21. Bash is also required for the test scripts. Git Bash works on Windows.

javac -d classes *.java

java -cp classes Assembler fizzbuzz.asm

java -cp classes Disassembler fizzbuzz.mhc-bin fizzbuzz_reconstructed.asm

java -cp classes Disassembler fizzbuzz.mhc-ascii fizzbuzz_reconstructed.asm

./run_tests.sh

./error_tests.sh

The assembler accepts an input .asm file and optionally an output name. If no output name is provided, the input filename is used.

run_tests.sh assembles the program, disassembles both file formats, reassembles the results, and compares the machine-code files. A different assembly file can be provided as an argument.

error_tests.sh tests invalid input and displays error messages. Run run_tests.sh first.

The reconstructed program can also be opened in MARS to verify that it produces the same output.

Supported Features

Supported directives:

.data, .text, .globl, .asciiz, .ascii, .word, .byte

Supported features include labels, comments beginning with #, and registers written by name or number.

Supported instructions include:

add, addu, sub, and, or, slt, sll, jr, syscall, div, mfhi, mflo, addi, addiu, slti, andi, ori, lui, lw, sw, beq, bne, j, jal

Supported pseudoinstructions:

li, la, move, rem, nop

The assembler reports invalid instructions with their line number. The assembler and disassembler are designed to be consistent with each other, but their output may not exactly match MARS because MARS can expand pseudoinstructions differently.

Addresses and File Formats

The text segment begins at 0x00400000, and the data segment begins at 0x10010000, matching the default MARS addresses. The main label should be the first label in the .text section.

All files use big-endian byte order.

The .mhc-bin file stores the instruction count, data-byte count, instructions, and data.

The .mhc-ascii file contains TEXT and DATA sections with the binary representation of the instructions and data bytes.

Disassembler Output

The disassembler creates new labels for branch and jump targets. Data is written as .byte directives. Original comments, label names, and pseudoinstructions are not preserved.

Sources and AI Usage

MIPS instruction information came from the MIPS instruction-set reference, course notes, and MARS documentation.

AI was used to help debug and organize the code and format the PDF
