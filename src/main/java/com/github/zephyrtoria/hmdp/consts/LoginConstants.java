package com.github.zephyrtoria.hmdp.consts;

public class LoginConstants {
    // mapper
    public static final String LOGIN_MAPPER_PHONE = "phone";

    // session
    public static final String LOGIN_CODE_SESSION = "code";
    public static final String LOGIN_PHONE_SESSION = "phone";
    public static final String LOGIN_USER_SESSION = "user";

    // redis
    public static final String LOGIN_CODE_REDIS_PREFIX = "login:code:";
    public static final String LOGIN_USER_REDIS_PREFIX = "login:token:";
    public static final Long LOGIN_CODE_REDIS_TTL = 2L;
    public static final Long LOGIN_USER_REDIS_TTL = 30L;
}
