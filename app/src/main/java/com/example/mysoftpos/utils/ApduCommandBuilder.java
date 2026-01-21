package com.example.mysoftpos.utils;

import java.util.Arrays;

public class ApduCommandBuilder {

    // PPSE Name: 2PAY.SYS.DDF01
    private static final byte[] PPSE_NAME = "2PAY.SYS.DDF01".getBytes();

    public static byte[] selectPpse() {
        return select(PPSE_NAME);
    }

    public static byte[] selectAid(byte[] aid) {
        return select(aid);
    }

    private static byte[] select(byte[] data) {
        byte[] command = new byte[5 + data.length + 1];
        command[0] = (byte) 0x00; // CLA
        command[1] = (byte) 0xA4; // INS (SELECT)
        command[2] = (byte) 0x04; // P1 (Select by DF Name)
        command[3] = (byte) 0x00; // P2 (First or only occurrence)
        command[4] = (byte) data.length; // Lc
        System.arraycopy(data, 0, command, 5, data.length);
        command[command.length - 1] = (byte) 0x00; // Le
        return command;
    }

    public static byte[] getProcessingOptions() {
        // GPO with empty PDOL (standard simplified)
        // CLA INS P1 P2 Lc Data Le
        // 80  A8  00 00 02 8300 00
        return new byte[]{
                (byte) 0x80, (byte) 0xA8, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x83, (byte) 0x00, (byte) 0x00
        };
    }

    public static byte[] readRecord(int sfi, int recordNumber) {
        // READ RECORD
        // CLA INS P1 (Record Number) P2 (SFI shifted left by 3 | 4) Le
        byte p2 = (byte) ((sfi << 3) | 4);
        return new byte[]{
                (byte) 0x00, (byte) 0xB2, (byte) recordNumber, p2, (byte) 0x00
        };
    }
}
