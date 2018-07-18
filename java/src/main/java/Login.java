import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.Tuple;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.out;
import static java.lang.Thread.sleep;

/**
 * 模仿chapter02 购物程序
 */
public class Login {
  public static final Logger logger=LoggerFactory.getLogger(Login.class);
  public static void main(String[] args) throws InterruptedException {
    Jedis redis = new Jedis("localhost");
    redis.select(12);
    /*Set<String> usernames = initUsers(redis);
    //testIsLogin(redis);
    for(String username:usernames)    {
      String token=login(redis,username);
      view(redis,token,"ssd 120G");
    }
    checkCache(redis, 10);*/
    //testShop(redis);
    readyCache(redis);
  }
  private static void testShop(Jedis redis){
    String token=login(redis,"wxj");
    addToCart(redis,token,"hdd 3T",3);
    addToCart(redis,token,"surface pro5",1);
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
  private static void addToCart(Jedis redis,String token,String item,int count){
     if(count<=0)
       redis.hdel("cart:"+token,item);
    else
      redis.hset("cart:"+token,item,""+count);
    logger.debug("--购物车--");
    Map<String,String> goods=redis.hgetAll("cart:"+token);
    logger.debug("{}",goods);
  }
  /**准备缓存*/
  private static void readyCache(Jedis redis) throws InterruptedException {
    long now=System.currentTimeMillis()/1000;
    for (int i = 0; i < 10; i++) {
      String rowId="test-"+i;
      redis.zadd("self-delay:",i,rowId);
      redis.zadd("self-schedule:",now,rowId);
    }
    Runnable runnable=new ReadyCacheRunnable();
    new Thread(runnable).start();
    sleep(2*1000);
    logger.debug("all cached items:");
    dispAllCached(redis);
    //随机找一个准备删除
    redis.smembers("self-cache-keys:").forEach(key->{
      if(key.endsWith("6")) redis.zadd("self-delay:",-1,key);
    });
    sleep(10*1000);
    logger.debug("all cached items:====>");
    dispAllCached(redis);
    ((ReadyCacheRunnable) runnable).quit();
  }
  static void dispAllCached(Jedis redis){
    Set<String> keys=redis.smembers("self-cache-keys:");
    for(String key:keys){
      String val=redis.get("self-cache-"+key);
      logger.debug(val);
    }
    logger.debug("<====");
  }
  static class ReadyCacheRunnable implements Runnable{
    Jedis redis;
    boolean quit;
    ReadyCacheRunnable(){
      redis=new Jedis("localhost");
      redis.select(12);
    }
    public void quit(){quit=true;}
    public void run(){
      Gson gson=new Gson();
      while(!quit){
        //logger.debug(""+redis.zcard("self-schedule:"));
        //self-schedule:中的第一个不满足delay删除条件项会被缓存、score增加，增加后zset重新排序，
        //排序后第一个是新的，这样不断重新排序，总会发现delay<0的项，也就会被删除。没有追求立即发现被删项
        // 删除缓存的依据只是delay，如果不为零，self-schedule:中的score会不断增加。
        // 开始以为会根据这个score来判断是否到期，不是这个score
        dispScheduleAndDelay(redis);

        Set<Tuple> datas=redis.zrangeWithScores("self-schedule:",0,0);
        Tuple data=datas.size()>0?datas.iterator().next():null;
        long now=System.currentTimeMillis()/1000;
        if(data!=null) logger.debug("Test if del:{}.score:{}>now:{}",data.getElement(),data.getScore(),now);
        if(data==null||data.getScore()>now){
          try {
            sleep(50);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          continue;
        }
        String rowId=data.getElement();
        double delay=redis.zscore("self-delay:",rowId);

        if(delay>0){
          redis.zincrby("self-schedule:",delay,rowId);
          show(redis,rowId);
          redis.set("self-cache-"+rowId,gson.toJson(newStu(rowId)));
          redis.sadd("self-cache-keys:",rowId);
        }else{
          redis.zrem("self-schedule:",rowId);
          redis.zrem("self-delay:",rowId);
          redis.del("self-cache-"+rowId);
          redis.srem("self-cache-keys:",rowId);
          logger.info("del {}",rowId);
        }
      }
    }
  }
  private static void dispScheduleAndDelay(Jedis redis){
    logger.debug("--->schedule&delay all in");
    Set<Tuple> datas=redis.zrangeWithScores("self-schedule:",0,-1);
    datas.forEach(it-> {
          String key=it.getElement();
          double delay=redis.zscore("self-delay:",key);
          logger.debug("{}.{}.{}", key, it.getScore(),delay);
        }
    );
    logger.debug("<----");
  }
  private static void dispZset(Jedis redis,String key){
    logger.debug("--->all in {}",key);
    Set<Tuple> datas=redis.zrangeWithScores(key,0,-1);
    datas.forEach(it->logger.debug("{},{}",it.getElement(),it.getScore()));
    logger.debug("<----");
  }
  private static Stu newStu(String rowId){
    long id=Long.parseLong(rowId.split("\\-")[1]);
    return new Stu(id,rowId,90+id,80+id,70+id);
  }
  private static void show(Jedis redis,String rowId){
    double score=redis.zscore("self-schedule:",rowId);
    logger.info("cache: {} is {}",rowId,score);
  }
}
