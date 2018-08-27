import com.google.gson.Gson;
import domain.Stu;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Field;
import java.util.*;

import static java.lang.System.out;

/**学生成绩管理系统*/
public class ScoreMIS {
  public static void main(String[] args) {
    //testJSonObj();
    Jedis redis = new Jedis("localhost");
    initStus(redis);
    //dispAvgDesc(redis);
    findByName2(redis);
  }
  /**准备一个stu:username,存username=stu:id*/
  private static void findByName2(Jedis redis){
    out.println("查找zhangsan 5");
    String stuKeys=redis.hget("stu:username","zhangsan 5");
    for(String stuKey:stuKeys.split(",")) {
      Map<String, String> stuData = redis.hgetAll(stuKey);
      out.println(stuData);
    }
  }
  /**怎么实现按姓名查找?觉得这种方式非常不好，遍历*/
  private static void findByName(Jedis redis){
    long num=Long.parseLong(redis.get("stu:"));
    for(int i=0;i<num;i++){
      String val=redis.hget("stu:"+(i+1),"java");
      if(val.equals("81.0")) {
        out.println("found");
        break;
      }
    }
  }
  /**平均成绩逆序显示*/
  private static void dispAvgDesc(Jedis redis){
    Set<String> stuKeys=redis.zrevrange("score:avg",0,-1);
    List<Map<String,String>> stuDatas=new ArrayList<>();
    for(String stuKey:stuKeys){
      stuDatas.add(redis.hgetAll(stuKey));
    }
    printStus(stuDatas);
  }

  private static void printStus(List<Map<String,String>> stuDatas) {
    for(Map<String,String> row:stuDatas){
      out.println(row);
    }
  }

  private static void testJSonObj() {
    Stu stu=new Stu(1,"zhangsan",100,100,100);
    String strStu=new Gson().toJson(stu);
    out.println(strStu);
    Stu bStu=new Gson().fromJson(strStu,Stu.class);
    out.println(bStu.getJava());
  }

  private static void initStus(Jedis redis){
    for(int i=0;i<10;i++) {
      long id=redis.incr("stu:");
      String username="zhangsan "+i;
      Stu stu = new Stu(id, username, 80+i, 80+i, 80+i);
      String stuKey="stu:"+id;
      redis.hmset(stuKey,toMap(stu));
      //平均成绩单独的表
      redis.zadd("score:avg",80+i,"stu:"+id);
      //有重名时
      if(redis.hexists("stu:username",username)){
        String oldKeys=redis.hget("stu:username",username);
        redis.hset("stu:username",username,oldKeys+","+stuKey);
      }else{//假设没有重名
        redis.hset("stu:username",username,stuKey);
      }
    }
  }
  private static Map<String,String> toMap(Stu stu){
    Map<String,String> res=new HashMap<>();
    Field[] fields=stu.getClass().getDeclaredFields();
    for(Field tmp:fields){
      String name=tmp.getName();
      if(name=="id") continue;
      try{
        String val=""+tmp.get(stu);
        res.put(name,val);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }
    return res;
  }
}
