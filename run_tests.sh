#!/bin/bash
# Round trip test: assemble, disassemble, assemble again, compare.

SRC="${1:-fizzbuzz.asm}"
NAME=$(basename "$SRC" .asm)
OUT=test_output
RUN="java -cp classes"
pass=0
fail=0

check() {
    if cmp -s "$2" "$3"; then
        echo "PASS: $1"
        pass=$((pass+1))
    else
        echo "FAIL: $1"
        fail=$((fail+1))
    fi
}

rm -rf $OUT
mkdir -p $OUT/from_bin $OUT/from_ascii

echo "== assemble the original"
$RUN Assembler "$SRC" $OUT/$NAME || exit 1

echo
echo "== disassemble both formats"
$RUN Disassembler $OUT/$NAME.mhc-bin $OUT/${NAME}_from_bin.asm || exit 1
$RUN Disassembler $OUT/$NAME.mhc-ascii $OUT/${NAME}_from_ascii.asm || exit 1

echo
echo "== assemble the rebuilt programs"
$RUN Assembler $OUT/${NAME}_from_bin.asm $OUT/from_bin/$NAME || exit 1
$RUN Assembler $OUT/${NAME}_from_ascii.asm $OUT/from_ascii/$NAME || exit 1

echo
echo "== comparing files"
check "both rebuilt .asm files are the same" $OUT/${NAME}_from_bin.asm $OUT/${NAME}_from_ascii.asm
check "mhc-bin: original vs rebuilt from mhc-bin" $OUT/$NAME.mhc-bin $OUT/from_bin/$NAME.mhc-bin
check "mhc-bin: original vs rebuilt from mhc-ascii" $OUT/$NAME.mhc-bin $OUT/from_ascii/$NAME.mhc-bin
check "mhc-ascii: original vs rebuilt from mhc-bin" $OUT/$NAME.mhc-ascii $OUT/from_bin/$NAME.mhc-ascii
check "mhc-ascii: original vs rebuilt from mhc-ascii" $OUT/$NAME.mhc-ascii $OUT/from_ascii/$NAME.mhc-ascii

echo
echo "$pass passed, $fail failed"
[ $fail -eq 0 ]
