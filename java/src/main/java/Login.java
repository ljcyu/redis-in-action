import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import static java.lang.System.out;
public class Login {
  public static void main(String[] args) {
    Jedis redis = new Jedis("localhost");
    addUser(redis);
    boolean isSuc=login(redis,"ljc","123");
    out.println(isSuc);
  }
  private static boolean login(Jedis redis,String username,String pass){
    String val=redis.hget("user:",username);
    return val.equals(pass);
  }
  private static void addUser(Jedis redis){
    Map<String,String> users=new HashMap<>();
    users.put("ljc","123");
    users.put("zhangsan","123");
    users.put("lisi","123");
    redis.hmset("user:",users);
  }
  private static void addStu(Jedis redis){

  }
}
