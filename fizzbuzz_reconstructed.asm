# Rebuilt by the disassembler.
# Original comments and label names are gone. Data is written as raw bytes.

.data
.byte 0x46, 0x69, 0x7a, 0x7a, 0x0a, 0x00, 0x42, 0x75
.byte 0x7a, 0x7a, 0x0a, 0x00, 0x46, 0x69, 0x7a, 0x7a
.byte 0x42, 0x75, 0x7a, 0x7a, 0x0a, 0x00

.text
.globl main
main:
    addiu $t1, $zero, 1
    addiu $t9, $zero, 3
    addiu $t8, $zero, 5
    addiu $t7, $zero, 15
    addiu $t6, $zero, 101
L1:
    beq $t1, $t6, L6
    div $t1, $t7
    mfhi $t0
    beq $t0, $zero, L2
    div $t1, $t9
    mfhi $t0
    beq $t0, $zero, L3
    div $t1, $t8
    mfhi $t0
    beq $t0, $zero, L4
    addiu $v0, $zero, 1
    addu $a0, $zero, $t1
    syscall
    addiu $v0, $zero, 11
    addiu $a0, $zero, 10
    syscall
    j L5
L2:
    addiu $v0, $zero, 4
    lui $a0, 0x1001
    ori $a0, $a0, 0x000c
    syscall
    j L5
L3:
    addiu $v0, $zero, 4
    lui $a0, 0x1001
    ori $a0, $a0, 0x0000
    syscall
    j L5
L4:
    addiu $v0, $zero, 4
    lui $a0, 0x1001
    ori $a0, $a0, 0x0006
    syscall
    j L5
L5:
    addi $t1, $t1, 1
    j L1
L6:
    addiu $v0, $zero, 10
    syscall
