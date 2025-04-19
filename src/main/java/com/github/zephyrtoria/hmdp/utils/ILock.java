package com.github.zephyrtoria.hmdp.utils;

public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec 锁允许持有的超时事件，过期后自动释放
     * @return true - 获取锁成功；false - 获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
