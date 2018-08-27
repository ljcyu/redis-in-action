package spring.xmljava;

import domain.Stu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("xmlJavaDemo")
public class SpringRedisXmlJavaDemo {
    public static final Logger logger= LoggerFactory.getLogger(SpringRedisXmlJavaDemo.class);
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static void main(String[] args) {
        ApplicationContext ctx=new ClassPathXmlApplicationContext("classpath:spring-redis-xmljava.xml");
        SpringRedisXmlJavaDemo demo=ctx.getBean("xmlJavaDemo", SpringRedisXmlJavaDemo.class);
        //demo.test();
        logger.debug("first getAllStus");
        logger.debug(""+demo.getAllStus());
        logger.debug("second getAllStus");
        logger.debug(""+demo.getAllStus());
        logger.debug("newStu 删除缓存");
        demo.newStu();
        logger.debug("third getAllStus");
        logger.debug(""+demo.getAllStus());

        ((ClassPathXmlApplicationContext) ctx).registerShutdownHook();
    }
    public void test(){
        stringRedisTemplate.opsForValue().set("hello:","test2");
        String res=stringRedisTemplate.opsForValue().get("hello:");
        System.out.println(res);
    }

    @Cacheable("getAllStus-xmljava")
    public List<Stu> getAllStus() {
        List<Stu> stus = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String username = "10000"+ i;
            Stu stu = new Stu(i, username, 80 + i, 80 + i, 80 + i);
            stus.add(stu);
        }
        logger.debug("自己组装");
        return stus;
    }
    @CacheEvict("getAllStus-xmljava")
    public void newStu(){}
}
