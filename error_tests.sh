#!/bin/bash
# Shows the error messages for bad input.

T=test_output/error_cases
RUN="java -cp classes"
rm -rf $T
mkdir -p $T

bad_asm() {   # name, file contents, description
    printf "$2" > $T/$1.asm
    printf "%-28s -> " "$3"
    $RUN Assembler $T/$1.asm $T/out 2>&1 | head -1
}

echo "== assembler"
bad_asm e1 'main:\n  addi $t0, $zero\n'         "missing operand"
bad_asm e2 'main:\n  frob $t0, $t1\n'           "unknown instruction"
bad_asm e3 'main:\n  add $t0, $t1, $t99\n'      "unknown register"
bad_asm e4 'main:\n  j nowhere\n'               "undefined label"
bad_asm e5 'main:\n  addi $t0, $zero, 70000\n'  "immediate too big"
bad_asm e6 'a:\n  nop\na:\n  nop\n'             "label defined twice"
bad_asm e7 '.data\n.float 1.0\n'                "unsupported directive"
bad_asm e8 '.data\n  nop\n'                     "instruction in .data"
bad_asm e9 'main:\n  lw $t0, label\n'           "bad lw operand"
printf "%-28s -> " "input file missing"
$RUN Assembler $T/not_here.asm 2>&1 | head -1

echo
echo "== disassembler"
printf 'this is not a machine code file' > $T/bad1.bin
head -c 30 test_output/fizzbuzz.mhc-bin > $T/bad2.mhc-bin
sed '5s/.$//' test_output/fizzbuzz.mhc-ascii > $T/bad3.mhc-ascii
sed '1s/41/42/' test_output/fizzbuzz.mhc-ascii > $T/bad4.mhc-ascii
printf '\000\000\000\001\000\000\000\000\377\377\377\377' > $T/bad5.mhc-bin

bad_dis() {   # file, description
    printf "%-28s -> " "$2"
    $RUN Disassembler $T/$1 $T/out.asm 2>&1 | head -1
}
bad_dis bad1.bin "not a machine code file"
bad_dis bad2.mhc-bin "truncated binary file"
bad_dis bad3.mhc-ascii "31 bit instruction line"
bad_dis bad4.mhc-ascii "wrong instruction count"
bad_dis bad5.mhc-bin "unknown instruction word"
printf "%-28s -> " "input file missing"
$RUN Disassembler $T/not_here.mhc-bin $T/out.asm 2>&1 | head -1
