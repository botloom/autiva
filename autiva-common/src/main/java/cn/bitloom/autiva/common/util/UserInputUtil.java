package cn.bitloom.autiva.common.util;

import java.util.Scanner;

/**
 * The type User input util.
 *
 * @author bitloom
 */
public class UserInputUtil {
    /**
     * Gets user input from console.
     *
     * @return the user input from console
     */
    public static String getUserInputFromConsole() {
        Scanner sc = new Scanner(System.in);
        return sc.nextLine();
    }
}
