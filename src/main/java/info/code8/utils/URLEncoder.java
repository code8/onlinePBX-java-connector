package info.code8.utils;

import org.apache.commons.codec.CharEncoding;

import java.io.UnsupportedEncodingException;

/**
 * Created by code8 on 12/11/15.
 */

public class URLEncoder {
    public static String encode(String s) {
        try {
            return s == null ? null : java.net.URLEncoder.encode(s, CharEncoding.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String decode(String s) {
        try {
            return s == null ? null : java.net.URLDecoder.decode(s, CharEncoding.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}