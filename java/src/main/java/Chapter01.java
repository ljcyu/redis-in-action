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
    //�õ�article:id hash�д�����м�ֵ��
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
    //ȡһ��ֵ����һ�λ������ڣ��ᴴ��������1���൱��һ������
    String articleId = String.valueOf(conn.incr("article:"));

    //����һ��ͶƱ��set,ÿƪ����һ��set����߾�ͶƱ���û�
    String votedKey = "voted:" + articleId;
    conn.sadd(votedKey, user);
    conn.expire(votedKey, ONE_WEEK_IN_SECONDS);

    //���������Ϣ��article:id��Ӧ��hash��
    long now = System.currentTimeMillis() / 1000;
    String articleKey = "article:" + articleId;
    HashMap<String, String> articleData = new HashMap<String, String>();
    articleData.put("title", title);
    articleData.put("link", link);
    articleData.put("user", user);
    articleData.put("now", String.valueOf(now));
    articleData.put("votes", "1");
    conn.hmset(articleKey, articleData);
    //����zset
    conn.zadd("score:", now + VOTE_SCORE, articleKey);
    conn.zadd("time:", now, articleKey);

    return articleId;
  }
  /**�Լ�ʵ��*/
  private void articleVoteSelf(Jedis redis,String user,String articleKey){
    //�Ƿ�Ͷ��Ʊ voted:id
    String id=articleKey.split(":")[1];
    if(redis.sadd("voteds:"+id,user)==1) {
        //articleKey��Ӧ��hash��votes+1
        redis.hincrBy(articleKey, "votes", 1);
    }


  }
  public void articleVote(Jedis conn, String user, String articleKey) {
    long cutoff = (System.currentTimeMillis() / 1000) - ONE_WEEK_IN_SECONDS;
    if (conn.zscore("time:", articleKey) < cutoff) {
      return;
    }

    String articleId = articleKey.substring(articleKey.indexOf(':') + 1);
    //��ǰ���û�û��Ͷ��
    if (conn.sadd("voted:" + articleId, user) == 1) {//��¼ͶƱ�û�
      conn.zincrby("score:", VOTE_SCORE, articleKey);//��score:�м�¼�����·���
      conn.hincrBy(articleKey, "votes", 1);//��article:id��hash������votesֵ
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

    //�ȵõ�score:�������������article��key,Ҳ����article:id
    Set<String> articleKeys = conn.zrevrange(order, start, end);
    List<Map<String, String>> articles = new ArrayList<>();
    for (String articleKey : articleKeys) {
      //ȡarticleKey hash�е����м�ֵ��
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
    if (!conn.exists(key)) {//�Ƿ����key��Ӧ��zset������
      ZParams params = new ZParams().aggregate(ZParams.Aggregate.MAX);
      //�󽻼��� ��һ������set,û��score�����ѽ?redis��Ĭ��������1
      conn.zinterstore(key, params, "group:" + group, order);
      conn.expire(key, 10*60);//���ںܶ�ʱ�䣬�͹��ڣ�Ȼ��ͱ�ɾ��
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
