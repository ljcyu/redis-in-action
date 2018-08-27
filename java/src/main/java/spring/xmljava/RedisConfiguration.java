package spring.xmljava;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
@Configuration
@EnableCaching
public class RedisConfiguration{
    @Bean
    public JedisConnectionFactory jedisConnFactory(){
        RedisStandaloneConfiguration redisStandaloneConfiguration=new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName("localhost");
        redisStandaloneConfiguration.setPort(6379);
        JedisConnectionFactory factory=new JedisConnectionFactory();
        factory.setUsePool(true);
        return factory;
    }
  @Bean
    public RedisTemplate redisTemplate(){
      RedisTemplate redisTemplate=new RedisTemplate();
      redisTemplate.setConnectionFactory(jedisConnFactory());
      return redisTemplate;
  }
  @Bean
    public StringRedisTemplate stringRedisTemplate(){
        StringRedisTemplate stringRedisTemplate=new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(jedisConnFactory());
        return stringRedisTemplate;
  }

}
