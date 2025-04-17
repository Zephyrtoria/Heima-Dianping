package com.github.zephyrtoria.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// 暴露代理对象
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.github.zephyrtoria.hmdp.mapper")
@SpringBootApplication
public class HmdpApplication {

	public static void main(String[] args) {
		SpringApplication.run(HmdpApplication.class, args);
	}

}
