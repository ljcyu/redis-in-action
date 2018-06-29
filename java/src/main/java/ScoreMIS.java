import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**学生成绩管理系统*/
public class ScoreMIS {
  public static void main(String[] args) {
    //testJSonObj();
    Jedis redis = new Jedis("localhost");
    //initStus(redis);
    //怎么实现按姓名查找?觉得这种方式非常不好
    long num=Long.parseLong(redis.get("stu:"));
    for(int i=0;i<num;i++){
      String val=redis.hget("stu:"+(i+1),"java");
      if(val.equals("81.0")) {
        System.out.println("found");
        break;
      }
    }
  }

  private static void testJSonObj() {
    Stu stu=new Stu(1,"zhangsan",100,100,100);
    String strStu=new Gson().toJson(stu);
    System.out.println(strStu);
    Stu bStu=new Gson().fromJson(strStu,Stu.class);
    System.out.println(bStu.getJava());

  }

  private static void initStus(Jedis redis){
    for(int i=0;i<10;i++) {
      long id=redis.incr("stu:");
      Stu stu = new Stu(id, "zhangsan", 80+i, 80+i, 80+i);
      redis.hmset("stu:"+id,toMap(stu));
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
