/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtils {
    private static final Pattern PCT_ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f][0-9A-Fa-f]");

    public static boolean isEncoded(String value, Charset charset) {
        for (byte b : value.getBytes(charset)) {
            if (UriUtils.isUnreserved((char)b) || b == 37) continue;
            return false;
        }
        return PCT_ENCODED_PATTERN.matcher(value).find();
    }

    public static String encode(String value) {
        return UriUtils.encodeChunk(value, Util.UTF_8, false);
    }

    public static String encode(String value, Charset charset) {
        return UriUtils.encodeChunk(value, charset, false);
    }

    public static String encode(String value, boolean allowReservedCharacters) {
        return UriUtils.encodeInternal(value, Util.UTF_8, allowReservedCharacters);
    }

    public static String encode(String value, Charset charset, boolean allowReservedCharacters) {
        return UriUtils.encodeInternal(value, charset, allowReservedCharacters);
    }

    public static String decode(String value, Charset charset) {
        try {
            return URLDecoder.decode(value, charset.name());
        }
        catch (UnsupportedEncodingException uee) {
            return value;
        }
    }

    public static boolean isAbsolute(String uri) {
        return uri != null && !uri.isEmpty() && uri.startsWith("http");
    }

    public static String encodeInternal(String value, Charset charset, boolean allowReservedCharacters) {
        Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);
        if (!matcher.find()) {
            return UriUtils.encodeChunk(value, charset, true);
        }
        int length = value.length();
        StringBuilder encoded = new StringBuilder(length + 8);
        int index = 0;
        do {
            String before = value.substring(index, matcher.start());
            encoded.append(UriUtils.encodeChunk(before, charset, allowReservedCharacters));
            encoded.append(matcher.group());
            index = matcher.end();
        } while (matcher.find());
        String tail = value.substring(index, length);
        encoded.append(UriUtils.encodeChunk(tail, charset, allowReservedCharacters));
        return encoded.toString();
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static String encodeChunk(String value, Charset charset, boolean allowReserved) {
        if (UriUtils.isEncoded(value, charset)) {
            return value;
        }
        byte[] data = value.getBytes(charset);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();){
            for (byte b : data) {
                if (UriUtils.isUnreserved((char)b)) {
                    bos.write(b);
                    continue;
                }
                if (UriUtils.isReserved((char)b) && allowReserved) {
                    bos.write(b);
                    continue;
                }
                UriUtils.pctEncode(b, bos);
            }
            String string = new String(bos.toByteArray(), charset);
            return string;
        }
        catch (IOException ioe) {
            throw new IllegalStateException("Error occurred during encoding of the uri: " + ioe.getMessage(), ioe);
        }
    }

    private static void pctEncode(byte data, ByteArrayOutputStream bos) {
        bos.write(37);
        char hex1 = Character.toUpperCase(Character.forDigit(data >> 4 & 0xF, 16));
        char hex2 = Character.toUpperCase(Character.forDigit(data & 0xF, 16));
        bos.write(hex1);
        bos.write(hex2);
    }

    private static boolean isAlpha(int c) {
        return c >= 97 && c <= 122 || c >= 65 && c <= 90;
    }

    private static boolean isDigit(int c) {
        return c >= 48 && c <= 57;
    }

    private static boolean isGenericDelimiter(int c) {
        return c == 58 || c == 47 || c == 63 || c == 35 || c == 91 || c == 93 || c == 64;
    }

    private static boolean isSubDelimiter(int c) {
        return c == 33 || c == 36 || c == 38 || c == 39 || c == 40 || c == 41 || c == 42 || c == 43 || c == 44 || c == 59 || c == 61;
    }

    private static boolean isUnreserved(int c) {
        return UriUtils.isAlpha(c) || UriUtils.isDigit(c) || c == 45 || c == 46 || c == 95 || c == 126;
    }

    private static boolean isReserved(int c) {
        return UriUtils.isGenericDelimiter(c) || UriUtils.isSubDelimiter(c);
    }

    private boolean isPchar(int c) {
        return UriUtils.isUnreserved(c) || UriUtils.isSubDelimiter(c) || c == 58 || c == 64;
    }
}

