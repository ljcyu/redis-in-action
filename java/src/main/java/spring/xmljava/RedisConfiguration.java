package spring.xmljava;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.lang.reflect.Method;
import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfiguration{
    @Bean
    public KeyGenerator simpleKeyGenerator() {
        return (Object obj, Method method, Object[] args) -> {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(obj.getClass().getSimpleName());
            stringBuilder.append(".");
            stringBuilder.append(method.getName());
            stringBuilder.append("[");
            for (Object arg : args) {
                stringBuilder.append(arg.toString());
            }
            stringBuilder.append("]");

            return stringBuilder.toString();
        };
    }
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
      Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
      //这几个必须同时设置，不然不会json存
      redisTemplate.setKeySerializer(new StringRedisSerializer());
      redisTemplate.setValueSerializer(serializer);
      redisTemplate.setHashKeySerializer(new StringRedisSerializer());
      redisTemplate.setHashValueSerializer(serializer);
      redisTemplate.afterPropertiesSet();
      return redisTemplate;
    }

    @Bean
    public RedisCacheManager cacheManager() {
        RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(jedisConnFactory());
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate().getValueSerializer()));
        redisCacheConfiguration.entryTtl(Duration.ofSeconds(120));
        RedisCacheManager redisCacheManager = new RedisCacheManager(redisCacheWriter, redisCacheConfiguration);

        return redisCacheManager;
    }
  @Bean
    public StringRedisTemplate stringRedisTemplate(){
        StringRedisTemplate stringRedisTemplate=new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(jedisConnFactory());
        return stringRedisTemplate;
  }

}
