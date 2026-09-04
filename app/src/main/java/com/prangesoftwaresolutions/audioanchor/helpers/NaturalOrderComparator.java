package com.prangesoftwaresolutions.audioanchor.helpers;

import java.util.Comparator;

/*
 * Compares strings the way a human would order file names containing numbers, e.g.
 * "episode 2" before "episode 10" -- plain lexicographic comparison (as SQLite's LOWER()/ASC
 * does) sorts those the other way round because it compares the digits '1' and '2' one
 * character at a time instead of comparing the numbers 10 and 2. Case-insensitive, and not
 * limited to numbers at the start of the string (unlike the SQL CAST(title AS SIGNED) trick
 * used elsewhere, which only helps when the title starts with a digit).
 */
public class NaturalOrderComparator implements Comparator<String> {

    public static final NaturalOrderComparator INSTANCE = new NaturalOrderComparator();

    @Override
    public int compare(String a, String b) {
        int i = 0, j = 0;
        int aLen = a.length(), bLen = b.length();
        while (i < aLen && j < bLen) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);

            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int aStart = i;
                while (i < aLen && Character.isDigit(a.charAt(i))) i++;
                int bStart = j;
                while (j < bLen && Character.isDigit(b.charAt(j))) j++;

                // Strip leading zeros before comparing so magnitude, not digit count, decides --
                // e.g. "007" and "7" should compare equal here.
                int aNumStart = aStart;
                while (aNumStart < i - 1 && a.charAt(aNumStart) == '0') aNumStart++;
                int bNumStart = bStart;
                while (bNumStart < j - 1 && b.charAt(bNumStart) == '0') bNumStart++;

                int aDigits = i - aNumStart;
                int bDigits = j - bNumStart;
                int cmp;
                if (aDigits != bDigits) {
                    cmp = Integer.compare(aDigits, bDigits);
                } else {
                    cmp = a.substring(aNumStart, i).compareTo(b.substring(bNumStart, j));
                }
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (cmp != 0) return cmp;
                i++;
                j++;
            }
        }
        return Integer.compare(aLen - i, bLen - j);
    }
}
