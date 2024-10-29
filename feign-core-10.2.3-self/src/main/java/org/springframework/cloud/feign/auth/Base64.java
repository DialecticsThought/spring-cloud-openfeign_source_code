/*
 * Decompiled with CFR 0.152.
 */
package org.springframework.cloud.feign.auth;

import java.io.UnsupportedEncodingException;

final class Base64 {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final byte[] MAP = new byte[]{65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 43, 47};

    private Base64() {
    }

    public static byte[] decode(byte[] in) {
        return Base64.decode(in, in.length);
    }

    public static byte[] decode(byte[] in, int len) {
        byte chr;
        int length = len / 4 * 3;
        if (length == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        byte[] out = new byte[length];
        int pad = 0;
        while (true) {
            if ((chr = in[len - 1]) != 10 && chr != 13 && chr != 32 && chr != 9) {
                if (chr != 61) break;
                ++pad;
            }
            --len;
        }
        int outIndex = 0;
        int inIndex = 0;
        int bits = 0;
        int quantum = 0;
        for (int i = 0; i < len; ++i) {
            chr = in[i];
            if (chr == 10 || chr == 13 || chr == 32 || chr == 9) continue;
            if (chr >= 65 && chr <= 90) {
                bits = chr - 65;
            } else if (chr >= 97 && chr <= 122) {
                bits = chr - 71;
            } else if (chr >= 48 && chr <= 57) {
                bits = chr + 4;
            } else if (chr == 43) {
                bits = 62;
            } else if (chr == 47) {
                bits = 63;
            } else {
                return null;
            }
            quantum = quantum << 6 | (byte)bits;
            if (inIndex % 4 == 3) {
                out[outIndex++] = (byte)(quantum >> 16);
                out[outIndex++] = (byte)(quantum >> 8);
                out[outIndex++] = (byte)quantum;
            }
            ++inIndex;
        }
        if (pad > 0) {
            out[outIndex++] = (byte)((quantum <<= 6 * pad) >> 16);
            if (pad == 1) {
                out[outIndex++] = (byte)(quantum >> 8);
            }
        }
        byte[] result = new byte[outIndex];
        System.arraycopy(out, 0, result, 0, outIndex);
        return result;
    }

    public static String encode(byte[] in) {
        int length = (in.length + 2) * 4 / 3;
        byte[] out = new byte[length];
        int index = 0;
        int end = in.length - in.length % 3;
        for (int i = 0; i < end; i += 3) {
            out[index++] = MAP[(in[i] & 0xFF) >> 2];
            out[index++] = MAP[(in[i] & 3) << 4 | (in[i + 1] & 0xFF) >> 4];
            out[index++] = MAP[(in[i + 1] & 0xF) << 2 | (in[i + 2] & 0xFF) >> 6];
            out[index++] = MAP[in[i + 2] & 0x3F];
        }
        switch (in.length % 3) {
            case 1: {
                out[index++] = MAP[(in[end] & 0xFF) >> 2];
                out[index++] = MAP[(in[end] & 3) << 4];
                out[index++] = 61;
                out[index++] = 61;
                break;
            }
            case 2: {
                out[index++] = MAP[(in[end] & 0xFF) >> 2];
                out[index++] = MAP[(in[end] & 3) << 4 | (in[end + 1] & 0xFF) >> 4];
                out[index++] = MAP[(in[end + 1] & 0xF) << 2];
                out[index++] = 61;
            }
        }
        try {
            return new String(out, 0, index, "US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            throw new AssertionError((Object)e);
        }
    }
}

