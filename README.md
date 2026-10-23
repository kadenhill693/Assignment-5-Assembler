# Assignment-5-Assembler
Assignment-5-Assembler
This project includes a MIPS assembler and disassembler written in Java. The assembler reads a MIPS FizzBuzz program called fizzbuzz.asm and creates two machine code files: fizzbuzz.mhc-ascii and fizzbuzz.mhc-bin.

The disassembler can read either file and convert it back into an assembly file. Assembling the reconstructed file produces the same machine code as the original.

How to Run

You need a JDK. I used Java 21. You also need Bash to run the test scripts. Git Bash works on Windows.

javac -d classes *.java

java -cp classes Assembler fizzbuzz.asm

java -cp classes Disassembler fizzbuzz.mhc-bin fizzbuzz_reconstructed.asm

java -cp classes Disassembler fizzbuzz.mhc-ascii fizzbuzz_reconstructed.asm

./run_tests.sh

./error_tests.sh

Assembler <input.asm> [output name] creates .mhc-ascii and .mhc-bin. If no output name is provided, it uses the input filename without .asm.

Disassembler <output.asm> automatically detects whether the input file is ASCII or binary.

run_tests.sh assembles the program, disassembles both file formats, assembles the results again, and compares the files using cmp. You can pass a different .asm file as an argument instead of using fizzbuzz.asm.

error_tests.sh tests invalid input and displays the related error messages. Run run_tests.sh first.

The reconstructed program can also be opened in MARS to make sure it produces the same FizzBuzz output.

What the Assembler Supports

The assembler supports the following directives:

.data .text .globl .asciiz .ascii .word .byte

It supports labels, # comments, and registers written either by name, such as $t0, or by number, such as $8.

Supported instructions include add, addu, sub, and, or, slt, sll, jr, syscall, div, mfhi, mflo, addi, addiu, slti, andi, ori, lui, lw, sw, beq, bne, j, and jal.

The assembler also supports the pseudoinstructions li, la, move, rem, and nop.

li rt, v uses addiu rt, $zero, v when the value fits in 16 bits. Larger values are converted using lui and ori.

la rt, label uses lui and ori to load the address of a label.

move rd, rs becomes addu rd, $zero, rs.

rem rd, rs, rt becomes div rs, rt followed by mfhi rd.

Invalid instructions produce an error with the line number. For example, unsupported instruction 'frob'.

The rem pseudoinstruction does not check for division by zero. MARS may also expand some pseudoinstructions differently, so the machine code from this project may not match MARS exactly. However, the assembler and disassembler are consistent with each other.

Addresses and Byte Order

The text segment starts at 0x00400000, and the data segment starts at 0x10010000. These are the default MARS addresses.

The first instruction is the entry point, so main should be the first label in the .text section.

All files use big-endian byte order. For example, the instruction 0x20080003 is stored as the bytes 20 08 00 03.

File Formats

The .mhc-bin file contains the number of instructions in bytes 0–3, the number of data bytes in bytes 4–7, all instructions after that, and then the data bytes.

The .mhc-ascii file contains a TEXT line followed by the binary representation of each instruction. It then contains a DATA line followed by the binary representation of each data byte.

The TEXT and DATA lines separate instructions from data so the disassembler knows which parts to decode as instructions.

Disassembler Output

The disassembler creates new labels for branch and jump targets, such as L1 and L2.

Data is written back as .byte lines. Original comments, label names, and pseudoinstruction names are not preserved.

For example, li $t1, 1 may be reconstructed as addiu $t1, $zero, 1.

Sources and Collaboration

MIPS instruction formats, opcodes, and function codes came from the MIPS instruction set reference, also known as the green sheet, and the course notes.

MARS syscall numbers, including 1, 4, 10, and 11, came from the MARS help pages.

AI Usage

Used AI to help debug and organize my code and help with pdf formatting
