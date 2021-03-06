import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class Chapter02 {
  public static final Logger logger= LoggerFactory.getLogger(Chapter02.class);
  public static final void main(String[] args) throws InterruptedException {
    new Chapter02().run();
  }

  public void run() throws InterruptedException {
    Jedis conn = new Jedis("localhost");
    conn.select(15);

    testLoginCookies(conn);
    testShopppingCartCookies(conn);
    testCacheRows(conn);
    testCacheRequest(conn);
  }

  public void testLoginCookies(Jedis conn) throws InterruptedException {
    System.out.println("\n----- testLoginCookies -----");
    String token = UUID.randomUUID().toString();

    updateToken(conn, token, "username", "itemX");
    System.out.println("We just logged-in/updated token: " + token);
    System.out.println("For user: 'username'");
    System.out.println();

    System.out.println("What username do we get when we look-up that token?");
    String r = checkToken(conn, token);
    System.out.println(r);
    System.out.println();
    assert r != null;

    System.out.println("Let's drop the maximum number of cookies to 0 to clean them out");
    System.out.println("We will start a thread to do the cleaning, while we stop it later");
    //为什么是0?
    CleanSessionsThread thread = new CleanSessionsThread(0);
    thread.start();
    Thread.sleep(1000);
    thread.quit();
    Thread.sleep(2000);
    if (thread.isAlive()) {
      throw new RuntimeException("The clean sessions thread is still alive?!?");
    }

    long s = conn.hlen("login:");
    System.out.println("The current number of sessions still available is: " + s);
    assert s == 0;
  }

  public void testShopppingCartCookies(Jedis conn) throws InterruptedException {
    System.out.println("\n----- testShopppingCartCookies -----");
    String token = UUID.randomUUID().toString();

    System.out.println("We'll refresh our session...");
    updateToken(conn, token, "username", "itemX");
    System.out.println("And add an item to the shopping cart");
    addToCart(conn, token, "itemY", 3);
    Map<String, String> r = conn.hgetAll("cart:" + token);
    System.out.println("Our shopping cart currently has:");
    for (Map.Entry<String, String> entry : r.entrySet()) {
      System.out.println("  " + entry.getKey() + ": " + entry.getValue());
    }
    System.out.println();

    assert r.size() >= 1;

    System.out.println("Let's clean out our sessions and carts");
    CleanFullSessionsThread thread = new CleanFullSessionsThread(0);
    thread.start();
    Thread.sleep(1000);
    thread.quit();
    Thread.sleep(2000);
    if (thread.isAlive()) {
      throw new RuntimeException("The clean sessions thread is still alive?!?");
    }

    r = conn.hgetAll("cart:" + token);
    System.out.println("Our shopping cart now contains:");
    for (Map.Entry<String, String> entry : r.entrySet()) {
      System.out.println("  " + entry.getKey() + ": " + entry.getValue());
    }
    assert r.size() == 0;
  }

  public void testCacheRows(Jedis conn) throws InterruptedException {
    System.out.println("\n----- testCacheRows -----");
    System.out.println("First, let's schedule caching of itemX every 5 seconds");
    scheduleRowCache(conn, "itemX", 5);
    System.out.println("Our schedule looks like:");
    Set<Tuple> s = conn.zrangeWithScores("schedule:", 0, -1);
    for (Tuple tuple : s) {
      System.out.println("  " + tuple.getElement() + ", " + tuple.getScore());
    }
    assert s.size() != 0;

    System.out.println("We'll start a caching thread that will cache the data...");

    CacheRowsThread thread = new CacheRowsThread();
    thread.start();

    Thread.sleep(1000);
    System.out.println("Our cached data looks like:");
    String r = conn.get("inv:itemX");
    System.out.println(r);
    assert r != null;
    System.out.println();

    System.out.println("We'll check again in 5 seconds...");
    Thread.sleep(5000);
    System.out.println("Notice that the data has changed...");
    String r2 = conn.get("inv:itemX");
    System.out.println(r2);
    System.out.println();
    assert r2 != null;
    assert !r.equals(r2);

    System.out.println("Let's force un-caching");
    scheduleRowCache(conn, "itemX", -1);
    Thread.sleep(1000);
    r = conn.get("inv:itemX");
    System.out.println("The cache was cleared? " + (r == null));
    assert r == null;

    thread.quit();
    Thread.sleep(2000);
    if (thread.isAlive()) {
      throw new RuntimeException("The database caching thread is still alive?!?");
    }
  }

  public void testCacheRequest(Jedis conn) {
    System.out.println("\n----- testCacheRequest -----");
    String token = UUID.randomUUID().toString();

    //用来取request对应的内容，这里进行了简化
    Callback callback = new Callback() {
      public String call(String request) {
        return "content for " + request;
      }
    };

    //保存登录信息(hash login:)、最后登录时间(zset recent: timestamp)
    //浏览历史记录(zset viewed:token timestamp)、(zset,viewed:中减1)
    updateToken(conn, token, "username", "itemX");
    String url = "http://test.com/?item=itemX";
    System.out.println("We are going to cache a simple request against " + url);
    String result = cacheRequest(conn, url, callback);
    System.out.println("We got initial content:\n" + result);
    System.out.println();

    assert result != null;

    System.out.println("To test that we've cached the request, we'll pass a bad callback");
    String result2 = cacheRequest(conn, url, null);
    System.out.println("We ended up getting the same response!\n" + result2);

    assert result.equals(result2);

    assert !canCache(conn, "http://test.com/");
    assert !canCache(conn, "http://test.com/?item=itemX&_=1234536");
  }

  public String checkToken(Jedis conn, String token) {
    return conn.hget("login:", token);
  }

  public void updateToken(Jedis conn, String token, String user, String item) {
    long timestamp = System.currentTimeMillis() / 1000;
    //登录的用户，hash
    conn.hset("login:", token, user);
    //最后登录时间,zset
    conn.zadd("recent:", timestamp, token);
    if (item != null) {
      //浏览的商品信息，浏览历史记录
      conn.zadd("viewed:" + token, timestamp, item);
      //前边浏览历史删掉，只留最新的25个
      conn.zremrangeByRank("viewed:" + token, 0, -26);
      //这个表什么用？
      conn.zincrby("viewed:", -1, item);
    }
  }
  /**购物车*/
  public void addToCart(Jedis conn, String token, String item, int count) {
    if (count <= 0) { //从购物车删除
      conn.hdel("cart:" + token, item);
    } else {//增加到购物车
      conn.hset("cart:" + token, item, String.valueOf(count));
    }
  }

  public void scheduleRowCache(Jedis conn, String rowId, int delay) {
    conn.zadd("delay:", delay, rowId);
    conn.zadd("schedule:", System.currentTimeMillis() / 1000, rowId);
  }

  public String cacheRequest(Jedis conn, String request, Callback callback) {
    if (!canCache(conn, request)) { //不能缓存，callback中提供的内容。
      return callback != null ? callback.call(request) : null;
    }

    //判断缓存中有没有cache:+request.hashCode()，以此判断是否缓存过
    String pageKey = "cache:" + toKey(request);
    String content = conn.get(pageKey);//从缓存中取下试试
    //没有缓存过
    if (content == null && callback != null) {
      content = callback.call(request);//取内容
      conn.setex(pageKey, 300, content);//缓存
      return content;
    }
    return content;
  }
  /**判断是否能缓存，这种逻辑不用深究，简单看看就行*/
  public boolean canCache(Jedis conn, String request) {
    try {
      URL url = new URL(request);
      //得到查询参数
      HashMap<String, String> params = new HashMap<String, String>();
      if (url.getQuery() != null) {
        for (String param : url.getQuery().split("&")) {
          String[] pair = param.split("=", 2);
          params.put(pair[0], pair.length == 2 ? pair[1] : null);
        }
      }

      String itemId = extractItemId(params);
      if (itemId == null || isDynamic(params)) {
        return false;
      }
      //zrank，在zset中的排名，从0开始，按照score从低到高
      Long rank = conn.zrank("viewed:", itemId);
      logger.debug("itemId {} rank:{}",itemId,rank);
      return rank != null && rank < 10000;
    } catch (MalformedURLException mue) {
      return false;
    }
  }

  public boolean isDynamic(Map<String, String> params) {
    return params.containsKey("_");
  }

  public String extractItemId(Map<String, String> params) {
    return params.get("item");
  }

  public String toKey(String request) {
    return String.valueOf(request.hashCode());
  }

  public interface Callback {
    public String call(String request);
  }

  public class CleanSessionsThread extends Thread {
    private Jedis conn;
    private int limit;
    private boolean quit;

    public CleanSessionsThread(int limit) {
      this.conn = new Jedis("localhost");
      this.conn.select(15);
      this.limit = limit;
    }

    public void quit() {
      quit = true;
    }

    public void run() {
      while (!quit) {//zset中有几个成员？ 有几个登录用户？
        long size = conn.zcard("recent:");
        if (size <= limit) {//登录过用户很少
          try {
            sleep(1000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
          continue;
        }

        long endIndex = Math.min(size - limit, 100);
        //超过限制的登录用户删掉，最前边的几个删掉
        //login:中只记录了token=useranm
        // recent:记录了登录时间=token，而且越早的越在前边，这样按照登录时间删除
        Set<String> tokenSet = conn.zrange("recent:", 0, endIndex - 1);
        String[] tokens = tokenSet.toArray(new String[tokenSet.size()]);

        ArrayList<String> sessionKeys = new ArrayList<>();
        for (String token : tokens) {
          sessionKeys.add("viewed:" + token);
        }
        //浏览历史删掉
        conn.del(sessionKeys.toArray(new String[sessionKeys.size()]));
        conn.hdel("login:", tokens);//从已登录中删掉 token=username
        conn.zrem("recent:", tokens);//已登录中删掉，time=token
      }
    }
  }

  public class CleanFullSessionsThread extends Thread {
    private Jedis conn;
    private int limit;
    private boolean quit;

    public CleanFullSessionsThread(int limit) {
      this.conn = new Jedis("localhost");
      this.conn.select(15);
      this.limit = limit;
    }

    public void quit() {
      quit = true;
    }

    public void run() {
      while (!quit) {
        long size = conn.zcard("recent:");
        if (size <= limit) {
          try {
            sleep(1000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
          continue;
        }

        long endIndex = Math.min(size - limit, 100);
        Set<String> sessionSet = conn.zrange("recent:", 0, endIndex - 1);
        String[] sessions = sessionSet.toArray(new String[sessionSet.size()]);

        ArrayList<String> sessionKeys = new ArrayList<>();
        for (String sess : sessions) {
          sessionKeys.add("viewed:" + sess);
          sessionKeys.add("cart:" + sess);
        }

        conn.del(sessionKeys.toArray(new String[sessionKeys.size()]));
        conn.hdel("login:", sessions);
        conn.zrem("recent:", sessions);
      }
    }
  }

  public class CacheRowsThread  extends Thread {
    private Jedis conn;
    private boolean quit;

    public CacheRowsThread() {
      this.conn = new Jedis("localhost");
      this.conn.select(15);
    }

    public void quit() {
      quit = true;
    }

    public void run() {
      Gson gson = new Gson();
      while (!quit) {
        //加入的时间
        Set<Tuple> range = conn.zrangeWithScores("schedule:", 0, 0);
        Tuple next = range.size() > 0 ? range.iterator().next() : null;
        long now = System.currentTimeMillis() / 1000;
        //没有数据，或第一个时间未到，后边的时间更不会到
        if (next == null || next.getScore() > now) {
          try {
            sleep(50);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
          continue;
        }

        //经过上边的判断，第一个满足条件，加入时间<=现在时间
        String rowId = next.getElement();
        double delay = conn.zscore("delay:", rowId);
        if (delay <= 0) {//是否要再次延迟？
          conn.zrem("delay:", rowId);
          conn.zrem("schedule:", rowId);
          conn.del("inv:" + rowId);
          continue;
        }

        Inventory row = Inventory.get(rowId);
        conn.zadd("schedule:", now + delay, rowId);
        System.out.println("cache "+rowId+" "+(now+delay));
        conn.set("inv:" + rowId, gson.toJson(row));
      }
    }
  }

  public static class Inventory {
    private String id;
    private String data;
    private long time;

    private Inventory(String id) {
      this.id = id;
      this.data = "data to cache...";
      this.time = System.currentTimeMillis() / 1000;
    }

    public static Inventory get(String id) {
      return new Inventory(id);
    }
  }
}
