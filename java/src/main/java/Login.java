import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.out;

/**
 * 模仿chapter02 购物程序
 */
public class Login {
  public static final Logger logger=LoggerFactory.getLogger(Login.class);
  public static void main(String[] args) {
    Jedis redis = new Jedis("localhost");
    redis.select(12);
    /*Set<String> usernames = initUsers(redis);
    //testIsLogin(redis);
    for(String username:usernames)    {
      String token=login(redis,username);
      view(redis,token,"ssd 120G");
    }*/
    checkCache(redis, 10);
  }
  private static void testIsLogin(Jedis redis){
    boolean isSuc = isLogin(redis, "ljc", "123");
    out.println(isSuc);
  }
  private static boolean isLogin(Jedis redis, String username, String pass) {
    String val = redis.hget("self-user:", username);
    return pass.equals(val);
  }

  private static String login(Jedis redis, String username) {
    String token = UUID.randomUUID().toString();
    long time = System.currentTimeMillis();
    redis.hset("self-login:", token, username);//
    redis.zadd("self-recent:", time, token);
    return token;
  }

  /**
   * 浏览商品
   */
  private static void view(Jedis redis, String token, String item) {
    long time = System.currentTimeMillis();
    redis.zadd("self-viewd:" + token, time, item);
    redis.zremrangeByRank("self-viewd:" + token, 0, -26);
  }

  private static Set<String> initUsers(Jedis redis) {
    Map<String, String> users = new HashMap<>();
    Set<String> usernames = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      users.put("ljc-" + i, "123");
      usernames.add("ljc-" + i);
    }
    redis.hmset("self-user:", users);
    return usernames;
  }

  /**
  * 缓存的用户太多，删除一部分
   */
  private static void checkCache(Jedis redis, int limit) {
    long count = redis.zcard("self-recent:");
    if (count <= limit) return;
    long endIndex = count - limit;//超过几个
    //zset结构self-recent:删除,hash结构login:中删除,hash结构self-viewd:token中删除
    Set<String> tokens = redis.zrange("self-recent:", 0, endIndex - 1);
    logger.debug("tokens:{}",tokens);
    String[] strTokens = tokens.toArray(new String[0]);

    redis.hdel("self-login:", strTokens);
    redis.zrem("self-recent:", strTokens);
    //从self-viewd:token中删除
    String[] keys = tokens.stream().map(it -> "self-viewd:" + it).collect(Collectors.toSet()).toArray(new String[0]);
    logger.debug("浏览历史:{}",Arrays.toString(keys));
    redis.del(keys);
  }
}
