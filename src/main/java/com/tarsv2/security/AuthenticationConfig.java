package com.tarsv2.security;

public class AuthenticationConfig {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "1037";

    public static boolean authenticate(String username, String password) {
        return USERNAME.equals(username) && PASSWORD.equals(password);
    }

    public static String getConfiguredUsername() {
        return USERNAME;
    }
}
