package cn.bitloom.agentic.tool.command;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Shared encoding utilities for decoding process output.
 * Handles UTF-8/GBK fallback and BOM detection.
 */
final class EncodingHelper {

    static final Charset GBK;
    static {
        Charset gbk = null;
        try { gbk = Charset.forName("GBK"); } catch (Exception ignored) {}
        GBK = gbk;
    }

    private EncodingHelper() {}

    /**
     * Try UTF-8 first; if the result contains replacement characters (U+FFFD),
     * fall back to GBK on Windows. Use whichever produces fewer replacement chars.
     * Also handles UTF-8 BOM (EF BB BF) which PowerShell may emit.
     */
    static String decodeBest(byte[] bytes) {
        if (bytes.length == 0) return "";
        int offset = 0;
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            offset = 3;
        }
        byte[] data = offset > 0 ? Arrays.copyOfRange(bytes, offset, bytes.length) : bytes;
        String utf8 = new String(data, StandardCharsets.UTF_8);
        long utf8Repl = utf8.chars().filter(c -> c == '\ufffd').count();
        if (utf8Repl == 0 || GBK == null) return utf8;
        String gbk = new String(data, GBK);
        long gbkRepl = gbk.chars().filter(c -> c == '\ufffd').count();
        return gbkRepl < utf8Repl ? gbk : utf8;
    }
}
