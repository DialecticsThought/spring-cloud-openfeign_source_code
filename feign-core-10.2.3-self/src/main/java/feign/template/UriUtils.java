/*
 * Decompiled with CFR 0.152.
 */
package feign.template;

import feign.Util;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UriUtils {
    private static final Pattern PCT_ENCODED_PATTERN = Pattern.compile("%[0-9A-Fa-f][0-9A-Fa-f]");

    public static boolean isEncoded(String value) {
        return PCT_ENCODED_PATTERN.matcher(value).matches();
    }

    public static String encode(String value) {
        return UriUtils.encodeReserved(value, FragmentType.URI, Util.UTF_8);
    }

    public static String encode(String value, Charset charset) {
        return UriUtils.encodeReserved(value, FragmentType.URI, charset);
    }

    public static String decode(String value, Charset charset) {
        try {
            return URLDecoder.decode(value, charset.name());
        }
        catch (UnsupportedEncodingException uee) {
            return value;
        }
    }

    public static String pathEncode(String path, Charset charset) {
        return UriUtils.encodeReserved(path, FragmentType.PATH_SEGMENT, charset);
    }

    public static String queryEncode(String query, Charset charset) {
        return UriUtils.encodeReserved(query, FragmentType.QUERY, charset);
    }

    public static String queryParamEncode(String queryParam, Charset charset) {
        return UriUtils.encodeReserved(queryParam, FragmentType.QUERY_PARAM, charset);
    }

    public static boolean isAbsolute(String uri) {
        return uri != null && !uri.isEmpty() && uri.startsWith("http");
    }

    public static String encodeReserved(String value, FragmentType type, Charset charset) {
        Matcher matcher = PCT_ENCODED_PATTERN.matcher(value);
        if (!matcher.find()) {
            return UriUtils.encodeChunk(value, type, charset);
        }
        int length = value.length();
        StringBuilder encoded = new StringBuilder(length + 8);
        int index = 0;
        do {
            String before = value.substring(index, matcher.start());
            encoded.append(UriUtils.encodeChunk(before, type, charset));
            encoded.append(matcher.group());
            index = matcher.end();
        } while (matcher.find());
        String tail = value.substring(index, length);
        encoded.append(UriUtils.encodeChunk(tail, type, charset));
        return encoded.toString();
    }

    private static String encodeChunk(String value, FragmentType type, Charset charset) {
        byte[] data = value.getBytes(charset);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        for (byte b : data) {
            if (type.isAllowed(b)) {
                encoded.write(b);
                continue;
            }
            UriUtils.pctEncode(b, encoded);
        }
        return new String(encoded.toByteArray());
    }

    private static void pctEncode(byte data, ByteArrayOutputStream bos) {
        bos.write(37);
        char hex1 = Character.toUpperCase(Character.forDigit(data >> 4 & 0xF, 16));
        char hex2 = Character.toUpperCase(Character.forDigit(data & 0xF, 16));
        bos.write(hex1);
        bos.write(hex2);
    }

    static enum FragmentType {
        URI{

            @Override
            boolean isAllowed(int c) {
                return this.isUnreserved(c);
            }
        }
        ,
        RESERVED{

            @Override
            boolean isAllowed(int c) {
                return this.isUnreserved(c) || this.isReserved(c);
            }
        }
        ,
        PATH_SEGMENT{

            @Override
            boolean isAllowed(int c) {
                return this.isPchar(c) || c == 47;
            }
        }
        ,
        QUERY{

            @Override
            boolean isAllowed(int c) {
                if (c == 43) {
                    return false;
                }
                return this.isPchar(c) || c == 47 || c == 63;
            }
        }
        ,
        QUERY_PARAM{

            @Override
            boolean isAllowed(int c) {
                if (c == 61 || c == 38 || c == 63) {
                    return false;
                }
                return QUERY.isAllowed(c);
            }
        };


        abstract boolean isAllowed(int var1);

        protected boolean isAlpha(int c) {
            return c >= 97 && c <= 122 || c >= 65 && c <= 90;
        }

        protected boolean isDigit(int c) {
            return c >= 48 && c <= 57;
        }

        protected boolean isGenericDelimiter(int c) {
            return c == 58 || c == 47 || c == 63 || c == 35 || c == 91 || c == 93 || c == 64;
        }

        protected boolean isSubDelimiter(int c) {
            return c == 33 || c == 36 || c == 38 || c == 39 || c == 40 || c == 41 || c == 42 || c == 43 || c == 44 || c == 59 || c == 61;
        }

        protected boolean isUnreserved(int c) {
            return this.isAlpha(c) || this.isDigit(c) || c == 45 || c == 46 || c == 95 || c == 126;
        }

        protected boolean isReserved(int c) {
            return this.isGenericDelimiter(c) || this.isSubDelimiter(c);
        }

        protected boolean isPchar(int c) {
            return this.isUnreserved(c) || this.isSubDelimiter(c) || c == 58 || c == 64;
        }
    }
}

