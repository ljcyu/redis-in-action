import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

import java.util.*;

public class Chapter01 {
  private static final int ONE_WEEK_IN_SECONDS = 7 * 86400;
  private static final int VOTE_SCORE = 432;
  private static final int ARTICLES_PER_PAGE = 25;

  public static final void main(String[] args) {
    new Chapter01().run();
  }

  public void run() {
    Jedis conn = new Jedis("localhost");
    conn.select(15);

    String articleId = postArticle(conn, "username", "A title", "http://www.google.com");
    System.out.println("We posted a new article with id: " + articleId);
    System.out.println("Its HASH looks like:");
    //得到article:id hash中存的所有键值对
    Map<String, String> articleData = conn.hgetAll("article:" + articleId);
    for (Map.Entry<String, String> entry : articleData.entrySet()) {
      System.out.println("  " + entry.getKey() + ": " + entry.getValue());
    }

    System.out.println();

    articleVote(conn, "other_user", "article:" + articleId);
    String votes = conn.hget("article:" + articleId, "votes");
    System.out.println("We voted for the article, it now has votes: " + votes);
    assert Integer.parseInt(votes) > 1;

    System.out.println("The currently highest-scoring articles are:");
    List<Map<String, String>> articles = getArticles(conn, 1);
    printArticles(articles);
    assert articles.size() >= 1;

    addGroups(conn, articleId, new String[]{"new-group"});
    System.out.println("We added the article to a new group, other articles include:");
    articles = getGroupArticles(conn, "new-group", 1);
    printArticles(articles);
    assert articles.size() >= 1;
  }


  public String postArticle(Jedis conn, String user, String title, String link) {
    //取一个值，第一次还不存在，会创建并返回1，相当于一个序列
    String articleId = String.valueOf(conn.incr("article:"));

    //创建一个投票的set,每篇文章一个set，里边就投票的用户
    String votedKey = "voted:" + articleId;
    conn.sadd(votedKey, user);
    conn.expire(votedKey, ONE_WEEK_IN_SECONDS);

    //保存具体信息在article:id对应的hash中
    long now = System.currentTimeMillis() / 1000;
    String articleKey = "article:" + articleId;
    HashMap<String, String> articleData = new HashMap<String, String>();
    articleData.put("title", title);
    articleData.put("link", link);
    articleData.put("user", user);
    articleData.put("now", String.valueOf(now));
    articleData.put("votes", "1");
    conn.hmset(articleKey, articleData);
    //两个zset
    conn.zadd("score:", now + VOTE_SCORE, articleKey);
    conn.zadd("time:", now, articleKey);

    return articleId;
  }
  /**自己实现*/
  private void articleVoteSelf(Jedis redis,String user,String articleKey){
    //是否投过票 voted:id
    String id=articleKey.split(":")[1];
    if(redis.sadd("voteds:"+id,user)==1) {
        //articleKey对应的hash总votes+1
        redis.hincrBy(articleKey, "votes", 1);
    }


  }
  public void articleVote(Jedis conn, String user, String articleKey) {
    long cutoff = (System.currentTimeMillis() / 1000) - ONE_WEEK_IN_SECONDS;
    if (conn.zscore("time:", articleKey) < cutoff) {
      return;
    }

    String articleId = articleKey.substring(articleKey.indexOf(':') + 1);
    //以前该用户没有投过
    if (conn.sadd("voted:" + articleId, user) == 1) {//记录投票用户
      conn.zincrby("score:", VOTE_SCORE, articleKey);//在score:中记录改文章分数
      conn.hincrBy(articleKey, "votes", 1);//在article:id中hash中增加votes值
    }
  }


  public List<Map<String, String>> getArticles(Jedis conn, int page) {
    return getArticles(conn, page, "score:");
  }
  public List<Map<String,String>> getArticlesBySelf(Jedis redis,int page,String order){
    int start=(page-1)*ARTICLES_PER_PAGE;
    int end=page*ARTICLES_PER_PAGE-1;
    Set<String> articleKeys=redis.zrevrange(order,start,end);
    List<Map<String,String>> articleDatas=new ArrayList<>();
    for(String articleKey:articleKeys){
      Map<String,String> articleData=redis.hgetAll(articleKey);
      articleData.put("id",articleKey);
      articleDatas.add(articleData);
    }
    return articleDatas;
  }

  public List<Map<String, String>> getArticles(Jedis conn, int page, String order) {
    int start = (page - 1) * ARTICLES_PER_PAGE;
    int end = start + ARTICLES_PER_PAGE - 1;

    //先得到score:表中逆序的所有article的key,也就是article:id
    Set<String> articleKeys = conn.zrevrange(order, start, end);
    List<Map<String, String>> articles = new ArrayList<>();
    for (String articleKey : articleKeys) {
      //取articleKey hash中的所有键值对
      Map<String, String> articleData = conn.hgetAll(articleKey);
      articleData.put("id", articleKey);
      articles.add(articleData);
    }
    return articles;
  }

  public void addGroups(Jedis conn, String articleId, String[] toAdd) {
    String article = "article:" + articleId;
    for (String group : toAdd) {
      conn.sadd("group:" + group, article);
    }
  }

  public List<Map<String, String>> getGroupArticles(Jedis conn, String group, int page) {
    return getGroupArticles(conn, group, page, "score:");
  }

  public List<Map<String, String>> getGroupArticles(Jedis conn, String group, int page, String order) {
    String key = order + group;
    if (!conn.exists(key)) {//是否存在key对应的zset，有序集
      ZParams params = new ZParams().aggregate(ZParams.Aggregate.MAX);
      //求交集， 是一个无序set,没有score这个项呀?redis就默认它等于1
      conn.zinterstore(key, params, "group:" + group, order);
      conn.expire(key, 10*60);//存在很短时间，就过期，然后就被删除
    }
    return getArticles(conn, page, key);
  }

  private void printArticles(List<Map<String, String>> articles) {
    for (Map<String, String> article : articles) {
      System.out.println("  id: " + article.get("id"));
      for (Map.Entry<String, String> entry : article.entrySet()) {
        if (entry.getKey().equals("id")) {
          continue;
        }
        System.out.println("    " + entry.getKey() + ": " + entry.getValue());
      }
    }
  }
}
