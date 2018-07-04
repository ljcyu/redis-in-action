import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.lang.System.out;
public class Login {
  public static void main(String[] args) {
    Jedis redis = new Jedis("localhost");
    addUser(redis);
    boolean isSuc=isLogin(redis,"ljc","123");
    if(isSuc){
      String token=login(redis,"ljc");
      view(redis,token,"ssd 120G");
    }
    out.println(isSuc);
  }
  private static boolean isLogin(Jedis redis,String username,String pass){
    String val=redis.hget("self-user:",username);
    return val.equals(pass);
  }
  private static String login(Jedis redis,String username){
    String token= UUID.randomUUID().toString();
    long time=System.currentTimeMillis();
    redis.hset("self-login:",token,username);//
    redis.zadd("self-recent:",time,token);
    return token;
  }
  /**浏览商品*/
  private static void view(Jedis redis,String token,String item){
    long time=System.currentTimeMillis();
    redis.zadd("self-viewd:"+token,time,item);
    redis.zremrangeByRank("self-viewd:",0,-26);
  }

  private static void addUser(Jedis redis){
    Map<String,String> users=new HashMap<>();
    users.put("ljc","123");
    users.put("zhangsan","123");
    users.put("lisi","123");
    redis.hmset("self-user:",users);
  }
}
