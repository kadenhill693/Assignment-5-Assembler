.data
msg: .asciiz "hi # there: ok"
.byte 1, 2, 3
nums: .word 10, -20, 0x7fffffff
.text
main:
  la $t0, nums
  lw $t1, 4($t0)
  sw $t1, 0($sp)
  li $t2, 100000
  li $t3, -5
  add $t4, $t1, $t2
  sub $t4, $t4, $t3
  and $t5, $t4, $t2
  or $t5, $t5, $t3
  slt $t6, $t1, $t2
  sll $t6, $t6, 3
  slti $t7, $t6, 9
  andi $t7, $t7, 0xff
  ori $t7, $t7, 0x100
  lui $s0, 0x1234
  addu $s1, $s0, $t7
  rem $s2, $s1, $t2
  div $s1, $t2
  mflo $s3
  jal helper
  bne $s3, $zero, end
  nop
end: li $v0, 10
  syscall
helper: move $v1, $s3
  jr $ra
