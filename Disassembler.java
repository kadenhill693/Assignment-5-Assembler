import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

// Disassembler: reads a .mhc-ascii or .mhc-bin file and writes an assembly file
public class Disassembler {

    static final int TEXT_START = 0x00400000;
    static final int MAX_SIZE = 1000000;

    static String[] regNames = {
        "$zero", "$at", "$v0", "$v1", "$a0", "$a1", "$a2", "$a3",
        "$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6", "$t7",
        "$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7",
        "$t8", "$t9", "$k0", "$k1", "$gp", "$sp", "$fp", "$ra"
    };

    static class BadInput extends Exception {
        BadInput(String msg) {
            super(msg);
        }
    }

    static int[] words;
    static byte[] data;

    // reading the files

    static void readAscii(byte[] file) throws BadInput {
        String[] lines = new String(file).split("\\r?\\n");
        int pos = 0;

        pos = skipBlank(lines, pos);
        int numWords = readHeader(lines, pos, "TEXT");
        pos++;
        words = new int[numWords];
        for (int i = 0; i < numWords; i++) {
            pos = skipBlank(lines, pos);
            String s = pos < lines.length ? lines[pos].trim() : "";
            if (s.length() != 32 || !s.matches("[01]+")) {
                throw new BadInput("line " + (pos + 1) + ": expected 32 characters of 0 and 1 for an instruction, found \"" + s + "\"");
            }
            words[i] = (int) Long.parseLong(s, 2);
            pos++;
        }

        pos = skipBlank(lines, pos);
        int numBytes = readHeader(lines, pos, "DATA");
        pos++;
        data = new byte[numBytes];
        for (int i = 0; i < numBytes; i++) {
            pos = skipBlank(lines, pos);
            String s = pos < lines.length ? lines[pos].trim() : "";
            if (s.length() != 8 || !s.matches("[01]+")) {
                throw new BadInput("line " + (pos + 1) + ": expected 8 characters of 0 and 1 for a data byte, found \"" + s + "\"");
            }
            data[i] = (byte) Integer.parseInt(s, 2);
            pos++;
        }
    }

    static int skipBlank(String[] lines, int pos) {
        while (pos < lines.length && lines[pos].trim().isEmpty()) pos++;
        return pos;
    }

    static int readHeader(String[] lines, int pos, String word) throws BadInput {
        String s = pos < lines.length ? lines[pos].trim() : "";
        String[] parts = s.split("\\s+");
        if (parts.length != 2 || !parts[0].equals(word) || !parts[1].matches("[0-9]+") || parts[1].length() > 7) {
            throw new BadInput("line " + (pos + 1) + ": expected \"" + word + " <count>\" but found \"" + s + "\"");
        }
        int n = Integer.parseInt(parts[1]);
        if (n > MAX_SIZE) throw new BadInput("line " + (pos + 1) + ": count is too large");
        return n;
    }

    static void readBin(byte[] file) throws BadInput {
        if (file.length < 8) throw new BadInput("file is too short, the header needs 8 bytes");
        ByteBuffer bb = ByteBuffer.wrap(file);
        int numWords = bb.getInt();
        int numBytes = bb.getInt();
        if (numWords < 0 || numBytes < 0 || numWords > MAX_SIZE || numBytes > MAX_SIZE) {
            throw new BadInput("header has an invalid size (" + numWords + " words, " + numBytes + " data bytes)");
        }
        long expected = 8L + 4L * numWords + numBytes;
        if (file.length != expected) {
            throw new BadInput("file is " + file.length + " bytes but the header says it should be " + expected);
        }
        words = new int[numWords];
        for (int i = 0; i < numWords; i++) words[i] = bb.getInt();
        data = new byte[numBytes];
        bb.get(data);
    }

    // decoding

    static String r(int n) {
        return regNames[n];
    }

    static BadInput bad(int word, int pc) {
        return new BadInput(String.format("can't decode instruction 0x%08x at address 0x%08x", word, pc));
    }

    // returns the text of one instruction
    static String decode(int w, int pc, int[] target) throws BadInput {
        int op = w >>> 26;
        int rs = (w >>> 21) & 31;
        int rt = (w >>> 16) & 31;
        int rd = (w >>> 11) & 31;
        int shamt = (w >>> 6) & 31;
        int funct = w & 63;
        int simm = (short) (w & 0xFFFF);
        int uimm = w & 0xFFFF;
        target[0] = -1;

        if (op == 0) {
            if (w == 0) return "nop";
            if (funct == 0x00) {
                if (rs != 0) throw bad(w, pc);
                return "sll " + r(rd) + ", " + r(rt) + ", " + shamt;
            }
            if (funct == 0x0C) {
                if (w != 0x0C) throw bad(w, pc);
                return "syscall";
            }
            if (shamt != 0) throw bad(w, pc);
            switch (funct) {
                case 0x08: return "jr " + r(rs);
                case 0x10: return "mfhi " + r(rd);
                case 0x12: return "mflo " + r(rd);
                case 0x1A: return "div " + r(rs) + ", " + r(rt);
                case 0x20: return "add " + r(rd) + ", " + r(rs) + ", " + r(rt);
                case 0x21: return "addu " + r(rd) + ", " + r(rs) + ", " + r(rt);
                case 0x22: return "sub " + r(rd) + ", " + r(rs) + ", " + r(rt);
                case 0x24: return "and " + r(rd) + ", " + r(rs) + ", " + r(rt);
                case 0x25: return "or " + r(rd) + ", " + r(rs) + ", " + r(rt);
                case 0x2A: return "slt " + r(rd) + ", " + r(rs) + ", " + r(rt);
                default: throw bad(w, pc);
            }
        }

        switch (op) {
            case 0x02: case 0x03:
                target[0] = ((pc + 4) & 0xF0000000) | ((w & 0x03FFFFFF) << 2);
                return (op == 2 ? "j" : "jal") + " %L";
            case 0x04: case 0x05:
                target[0] = pc + 4 + (simm << 2);
                return (op == 4 ? "beq " : "bne ") + r(rs) + ", " + r(rt) + ", %L";
            case 0x08: return "addi " + r(rt) + ", " + r(rs) + ", " + simm;
            case 0x09: return "addiu " + r(rt) + ", " + r(rs) + ", " + simm;
            case 0x0A: return "slti " + r(rt) + ", " + r(rs) + ", " + simm;
            case 0x0C: return "andi " + r(rt) + ", " + r(rs) + ", " + String.format("0x%04x", uimm);
            case 0x0D: return "ori " + r(rt) + ", " + r(rs) + ", " + String.format("0x%04x", uimm);
            case 0x0F:
                if (rs != 0) throw bad(w, pc);
                return "lui " + r(rt) + ", " + String.format("0x%04x", uimm);
            case 0x23: return "lw " + r(rt) + ", " + simm + "(" + r(rs) + ")";
            case 0x2B: return "sw " + r(rt) + ", " + simm + "(" + r(rs) + ")";
            default: throw bad(w, pc);
        }
    }

    static String buildAssembly() throws BadInput {
        int textEnd = TEXT_START + 4 * words.length;
        String[] text = new String[words.length];
        int[] targets = new int[words.length];
        TreeSet<Integer> targetSet = new TreeSet<>();
        int[] t = new int[1];

        for (int i = 0; i < words.length; i++) {
            int pc = TEXT_START + 4 * i;
            text[i] = decode(words[i], pc, t);
            targets[i] = t[0];
            if (t[0] != -1) {
                if (t[0] < TEXT_START || t[0] >= textEnd || t[0] % 4 != 0) {
                    throw new BadInput(String.format("instruction at 0x%08x jumps to 0x%08x, which is outside the program", pc, t[0]));
                }
                targetSet.add(t[0]);
            }
        }

        // make labels for branch targets
        Map<Integer, String> names = new HashMap<>();
        int count = 1;
        for (int addr : targetSet) names.put(addr, "L" + count++);

        StringBuilder sb = new StringBuilder();
        sb.append("# Rebuilt by the disassembler.\n");
        sb.append("# Original comments and label names are gone. Data is written as raw bytes.\n\n");
        if (data.length > 0) {
            sb.append(".data\n");
            for (int i = 0; i < data.length; i += 8) {
                sb.append(".byte ");
                for (int j = i; j < Math.min(i + 8, data.length); j++) {
                    if (j > i) sb.append(", ");
                    sb.append(String.format("0x%02x", data[j] & 0xFF));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        sb.append(".text\n.globl main\n");
        for (int i = 0; i < words.length; i++) {
            int pc = TEXT_START + 4 * i;
            if (i == 0) sb.append("main:\n");
            if (names.containsKey(pc)) sb.append(names.get(pc)).append(":\n");
            String line = text[i];
            if (targets[i] != -1) line = line.replace("%L", names.get(targets[i]));
            sb.append("    ").append(line).append("\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Disassembler <input.mhc-ascii or input.mhc-bin> <output.asm>");
            System.exit(2);
        }
        try {
            byte[] file = Files.readAllBytes(Paths.get(args[0]));
            if (file.length == 0) throw new BadInput("file is empty");
            boolean ascii = file[0] == 'T';
            if (ascii) readAscii(file);
            else readBin(file);
            String asm = buildAssembly();
            Files.write(Paths.get(args[1]), asm.getBytes("US-ASCII"));
            System.out.println("Read " + args[0] + " (" + (ascii ? "mhc-ascii" : "mhc-bin") + "): "
                    + words.length + " instructions, " + data.length + " data bytes");
            System.out.println("Wrote " + args[1]);
        } catch (BadInput e) {
            System.err.println(args[0] + ": bad input: " + e.getMessage());
            System.exit(1);
        } catch (NoSuchFileException e) {
            System.err.println("Can't open input file: " + args[0]);
            System.exit(2);
        } catch (IOException e) {
            System.err.println("I/O problem: " + e.getMessage());
            System.exit(2);
        }
    }
}
