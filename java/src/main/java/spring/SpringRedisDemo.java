package spring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("demo")
public class SpringRedisDemo {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static void main(String[] args) {
        ApplicationContext ctx=new ClassPathXmlApplicationContext("classpath:spring-redis.xml");
        SpringRedisDemo demo=ctx.getBean("demo",SpringRedisDemo.class);
        demo.test();
        ((ClassPathXmlApplicationContext) ctx).registerShutdownHook();
    }
    public void test(){
        stringRedisTemplate.opsForValue().set("hello:","test2");
        String res=stringRedisTemplate.opsForValue().get("hello:");
        System.out.println(res);
    }
}
