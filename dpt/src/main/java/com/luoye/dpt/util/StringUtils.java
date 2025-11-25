package com.luoye.dpt.util;

import java.security.SecureRandom;

public class StringUtils {

    public static String generateIdentifier(int minLength) {
        SecureRandom secureRandom = new SecureRandom();
        final int cnt = secureRandom.nextInt(minLength) + minLength;
        StringBuilder sb = new StringBuilder();
        for(int i = 0;i < cnt;i++) {
            char baseChar = secureRandom.nextBoolean() ? 'A' : 'a';

            int index = secureRandom.nextInt(26);
            char ch = (char) (baseChar + index);
            sb.append(ch);
        }

        return sb.toString();
    }

    public static String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
