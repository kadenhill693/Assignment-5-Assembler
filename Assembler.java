import java.io.*;
import java.nio.file.*;
import java.util.*;

// Assembler: reads a .asm file and writes .mhc-ascii and .mhc-bin files
public class Assembler {

    static final int TEXT_START = 0x00400000;
    static final int DATA_START = 0x10010000;

    static String[] regNames = {
        "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
        "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
        "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
        "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"
    };

    static class AsmError extends RuntimeException {
        AsmError(int lineNo, String msg) {
            super("line " + lineNo + ": " + msg);
        }
    }

    static class Instr {
        String name;
        String[] ops;
        int lineNo;
        int address;
        String text;
    }

    static Map<String, Integer> labels = new HashMap<>();
    static List<Instr> instrs = new ArrayList<>();
    static List<Integer> words = new ArrayList<>();
    static ByteArrayOutputStream data = new ByteArrayOutputStream();
    static int textEnd = TEXT_START;


    static String removeComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) inString = !inString;
            if (c == '#' && !inString) return line.substring(0, i);
        }
        return line;
    }

    static long parseNum(String s, int lineNo) {
        try {
            boolean neg = s.startsWith("-");
            if (neg) s = s.substring(1);
            long v;
            if (s.startsWith("0x") || s.startsWith("0X")) v = Long.parseLong(s.substring(2), 16);
            else v = Long.parseLong(s);
            return neg ? -v : v;
        } catch (NumberFormatException e) {
            throw new AsmError(lineNo, "'" + s + "' is not a valid number");
        }
    }

    static int regNum(String s, int lineNo) {
        for (int i = 0; i < 32; i++) {
            if (regNames[i].equals(s) || s.equals("$" + i)) return i;
        }
        throw new AsmError(lineNo, "unknown register '" + s + "'");
    }

    static int rType(int rs, int rt, int rd, int shamt, int funct) {
        return (rs << 21) | (rt << 16) | (rd << 11) | (shamt << 6) | funct;
    }

    static int iType(int op, int rs, int rt, int imm) {
        return (op << 26) | (rs << 21) | (rt << 16) | (imm & 0xFFFF);
    }

    static void alignData() {
        while (data.size() % 4 != 0) data.write(0);
    }

    static boolean liIsSmall(long v) {
        return v >= -32768 && v <= 32767;
    }

    // first pass

    static void firstPass(List<String> lines) {
        boolean inText = true;
        int textAddr = TEXT_START;

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = removeComment(lines.get(i)).trim();

            // labels
            while (true) {
                int colon = line.indexOf(':');
                if (colon <= 0) break;
                String name = line.substring(0, colon).trim();
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) break;
                String rest = line.substring(colon + 1).trim();
                if (labels.containsKey(name)) {
                    throw new AsmError(lineNo, "label '" + name + "' is defined more than once");
                }
                if (!inText && rest.startsWith(".word")) alignData();
                labels.put(name, inText ? textAddr : DATA_START + data.size());
                line = rest;
            }
            if (line.isEmpty()) continue;

            if (line.startsWith(".")) {
                String dir = line.split("\\s+")[0];
                String args = line.substring(dir.length()).trim();
                switch (dir) {
                    case ".text": inText = true; break;
                    case ".data": inText = false; break;
                    case ".globl": case ".global": break;
                    case ".asciiz": case ".ascii":
                        if (inText) throw new AsmError(lineNo, dir + " must be in the .data section");
                        byte[] str = parseString(args, lineNo);
                        data.write(str, 0, str.length);
                        if (dir.equals(".asciiz")) data.write(0);
                        break;
                    case ".word":
                        if (inText) throw new AsmError(lineNo, ".word must be in the .data section");
                        alignData();
                        for (String n : args.split(",")) {
                            long v = parseNum(n.trim(), lineNo);
                            for (int b = 3; b >= 0; b--) data.write((int) (v >> (8 * b)) & 0xFF);
                        }
                        break;
                    case ".byte":
                        if (inText) throw new AsmError(lineNo, ".byte must be in the .data section");
                        for (String n : args.split(",")) data.write((int) parseNum(n.trim(), lineNo) & 0xFF);
                        break;
                    default:
                        throw new AsmError(lineNo, "unsupported directive '" + dir + "'");
                }
                continue;
            }

            // instructions
            if (!inText) throw new AsmError(lineNo, "instruction found in the .data section");
            String[] parts = line.replace(",", " ").trim().split("\\s+");
            Instr in = new Instr();
            in.name = parts[0].toLowerCase();
            in.ops = Arrays.copyOfRange(parts, 1, parts.length);
            in.lineNo = lineNo;
            in.address = textAddr;
            in.text = line;
            instrs.add(in);
            textAddr += 4 * sizeInWords(in);
        }
        textEnd = textAddr;
    }

    static byte[] parseString(String s, int lineNo) {
        int first = s.indexOf('"');
        int last = s.lastIndexOf('"');
        if (first < 0 || last <= first) throw new AsmError(lineNo, "expected a string in double quotes");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = first + 1; i < last; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                char e = s.charAt(i);
                if (e == 'n') out.write('\n');
                else if (e == 't') out.write('\t');
                else if (e == '0') out.write(0);
                else if (e == '\\') out.write('\\');
                else if (e == '"') out.write('"');
                else throw new AsmError(lineNo, "unknown escape \\" + e);
            } else {
                out.write(c);
            }
        }
        return out.toByteArray();
    }

    static int sizeInWords(Instr in) {
        switch (in.name) {
            case "la": case "rem":
                return 2;
            case "li":
                if (in.ops.length == 2 && !liIsSmall(parseNum(in.ops[1], in.lineNo))) return 2;
                return 1;
            default:
                return 1;
        }
    }

    // second pass

    static void need(Instr in, int n) {
        if (in.ops.length != n) {
            throw new AsmError(in.lineNo, "'" + in.name + "' needs " + n + " operands but has " + in.ops.length);
        }
    }

    static int reg(Instr in, int i) {
        return regNum(in.ops[i], in.lineNo);
    }

    static int imm(Instr in, int i, boolean signed) {
        long v = parseNum(in.ops[i], in.lineNo);
        long lo = signed ? -32768 : 0;
        long hi = signed ? 32767 : 65535;
        if (v < lo || v > hi) {
            throw new AsmError(in.lineNo, "immediate " + v + " is out of range (" + lo + " to " + hi + ")");
        }
        return (int) v;
    }

    static int labelAddr(Instr in, int i) {
        Integer a = labels.get(in.ops[i]);
        if (a == null) throw new AsmError(in.lineNo, "undefined label '" + in.ops[i] + "'");
        return a;
    }

    static int branchOffset(Instr in, int i) {
        int target = labelAddr(in, i);
        if (target < TEXT_START || target >= textEnd) {
            throw new AsmError(in.lineNo, "'" + in.ops[i] + "' is not a label in the .text section");
        }
        int off = (target - (in.address + 4)) / 4;
        if (off < -32768 || off > 32767) throw new AsmError(in.lineNo, "branch is too far away");
        return off;
    }

    static void encode(Instr in) {
        switch (in.name) {
            // register instructions
            case "add": case "addu": case "sub": case "and": case "or": case "slt": {
                need(in, 3);
                int funct;
                switch (in.name) {
                    case "add": funct = 0x20; break;
                    case "addu": funct = 0x21; break;
                    case "sub": funct = 0x22; break;
                    case "and": funct = 0x24; break;
                    case "or": funct = 0x25; break;
                    default: funct = 0x2A; break;
                }
                words.add(rType(reg(in, 1), reg(in, 2), reg(in, 0), 0, funct));
                break;
            }
            case "sll": {
                need(in, 3);
                long sh = parseNum(in.ops[2], in.lineNo);
                if (sh < 0 || sh > 31) throw new AsmError(in.lineNo, "shift amount must be 0 to 31");
                words.add(rType(0, reg(in, 1), reg(in, 0), (int) sh, 0));
                break;
            }
            case "jr":
                need(in, 1);
                words.add(rType(reg(in, 0), 0, 0, 0, 0x08));
                break;
            case "syscall":
                need(in, 0);
                words.add(0x0C);
                break;
            case "div":
                if (in.ops.length == 3) {
                    throw new AsmError(in.lineNo, "'div' takes 2 operands here; use mfhi/mflo or 'rem'");
                }
                need(in, 2);
                words.add(rType(reg(in, 0), reg(in, 1), 0, 0, 0x1A));
                break;
            case "mfhi":
                need(in, 1);
                words.add(rType(0, 0, reg(in, 0), 0, 0x10));
                break;
            case "mflo":
                need(in, 1);
                words.add(rType(0, 0, reg(in, 0), 0, 0x12));
                break;

            // immediate instructions
            case "addi": need(in, 3); words.add(iType(0x08, reg(in, 1), reg(in, 0), imm(in, 2, true))); break;
            case "addiu": need(in, 3); words.add(iType(0x09, reg(in, 1), reg(in, 0), imm(in, 2, true))); break;
            case "slti": need(in, 3); words.add(iType(0x0A, reg(in, 1), reg(in, 0), imm(in, 2, true))); break;
            case "andi": need(in, 3); words.add(iType(0x0C, reg(in, 1), reg(in, 0), imm(in, 2, false))); break;
            case "ori": need(in, 3); words.add(iType(0x0D, reg(in, 1), reg(in, 0), imm(in, 2, false))); break;
            case "lui": need(in, 2); words.add(iType(0x0F, 0, reg(in, 0), imm(in, 1, false))); break;

            case "lw": case "sw": {
                need(in, 2);
                String m = in.ops[1];
                int open = m.indexOf('(');
                if (open < 0 || !m.endsWith(")")) {
                    throw new AsmError(in.lineNo, "expected offset($reg) but found '" + m + "'");
                }
                String offText = m.substring(0, open);
                int off = offText.isEmpty() ? 0 : (int) parseNum(offText, in.lineNo);
                int base = regNum(m.substring(open + 1, m.length() - 1), in.lineNo);
                if (off < -32768 || off > 32767) throw new AsmError(in.lineNo, "offset out of range");
                words.add(iType(in.name.equals("lw") ? 0x23 : 0x2B, base, reg(in, 0), off));
                break;
            }
            case "beq": case "bne":
                need(in, 3);
                words.add(iType(in.name.equals("beq") ? 4 : 5, reg(in, 0), reg(in, 1), branchOffset(in, 2)));
                break;
            case "j": case "jal": {
                need(in, 1);
                int target = labelAddr(in, 0);
                if (target < TEXT_START || target >= textEnd) {
                    throw new AsmError(in.lineNo, "'" + in.ops[0] + "' is not a label in the .text section");
                }
                words.add(((in.name.equals("j") ? 2 : 3) << 26) | ((target >>> 2) & 0x03FFFFFF));
                break;
            }

            // pseudoinstructions
            case "nop":
                need(in, 0);
                words.add(0);
                break;
            case "move":
                need(in, 2);
                words.add(rType(0, reg(in, 1), reg(in, 0), 0, 0x21));
                break;
            case "li": {
                need(in, 2);
                long v = parseNum(in.ops[1], in.lineNo);
                if (v < -2147483648L || v > 4294967295L) {
                    throw new AsmError(in.lineNo, "value does not fit in 32 bits");
                }
                int rt = reg(in, 0);
                if (liIsSmall(v)) {
                    words.add(iType(0x09, 0, rt, (int) v));
                } else {
                    words.add(iType(0x0F, 0, rt, (int) (v >>> 16)));
                    words.add(iType(0x0D, rt, rt, (int) (v & 0xFFFF)));
                }
                break;
            }
            case "la": {
                need(in, 2);
                int rt = reg(in, 0);
                int addr = labelAddr(in, 1);
                words.add(iType(0x0F, 0, rt, addr >>> 16));
                words.add(iType(0x0D, rt, rt, addr & 0xFFFF));
                break;
            }
            case "rem":
                need(in, 3);
                words.add(rType(reg(in, 1), reg(in, 2), 0, 0, 0x1A));
                words.add(rType(0, 0, reg(in, 0), 0, 0x10));
                break;

            default:
                throw new AsmError(in.lineNo, "unsupported instruction '" + in.name + "'");
        }
    }

    // output files

    static String bits(int value, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = width - 1; i >= 0; i--) sb.append((value >>> i) & 1);
        return sb.toString();
    }

    static void writeAscii(String path, byte[] dataBytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("TEXT ").append(words.size()).append("\n");
        for (int w : words) sb.append(bits(w, 32)).append("\n");
        sb.append("DATA ").append(dataBytes.length).append("\n");
        for (byte b : dataBytes) sb.append(bits(b & 0xFF, 8)).append("\n");
        Files.write(Paths.get(path), sb.toString().getBytes("US-ASCII"));
    }

    static void writeBin(String path, byte[] dataBytes) throws IOException {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(path));
        out.writeInt(words.size());
        out.writeInt(dataBytes.length);
        for (int w : words) out.writeInt(w);
        out.write(dataBytes);
        out.close();
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java Assembler <input.asm> [output name]");
            System.exit(2);
        }
        String input = args[0];
        String base = args.length == 2 ? args[1]
                : (input.endsWith(".asm") ? input.substring(0, input.length() - 4) : input);
        try {
            List<String> lines = Files.readAllLines(Paths.get(input));
            firstPass(lines);
            for (Instr in : instrs) encode(in);
            byte[] dataBytes = data.toByteArray();
            writeAscii(base + ".mhc-ascii", dataBytes);
            writeBin(base + ".mhc-bin", dataBytes);
            System.out.println("Assembled " + input + ": " + words.size() + " instructions, "
                    + dataBytes.length + " data bytes");
            System.out.println("Wrote " + base + ".mhc-ascii and " + base + ".mhc-bin");
        } catch (AsmError e) {
            System.err.println(input + ": error on " + e.getMessage());
            System.exit(1);
        } catch (NoSuchFileException e) {
            System.err.println("Can't open input file: " + input);
            System.exit(2);
        } catch (IOException e) {
            System.err.println("I/O problem: " + e.getMessage());
            System.exit(2);
        }
    }
}
