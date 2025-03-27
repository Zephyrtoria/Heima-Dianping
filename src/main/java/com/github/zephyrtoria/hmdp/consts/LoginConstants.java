package com.github.zephyrtoria.hmdp.consts;

public class LoginConstants {
    // session
    public static final String LOGIN_CODE_KEY = "code";
    public static final String LOGIN_PHONE_KEY = "phone";
    public static final String LOGIN_USER_KEY = "user";

    // redis
    public static final String LOGIN_PHONE_PREFIX = "phone:";
    public static final String LOGIN_CODE_PREFIX = "login:code:";
    public static final Integer LOGIN_CODE_TTL = 2;
    public static final String LOGIN_USER_PREFIX = "login:token:";
    public static final Integer LOGIN_USER_TTL = 30;
}
