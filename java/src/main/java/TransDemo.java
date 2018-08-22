import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**模拟事务*/
public class TransDemo {
    public static void main(String[] args) throws Exception{
        noTrans();
        /*Jedis redis=new Jedis("localhost");
        redis.incr("notrans:");
        System.out.println(redis.decr("notrans:"));*/
    }

    private static void noTrans() throws InterruptedException {
        Runnable r=()->{
            Jedis redis=new Jedis("localhost");
            System.out.println(redis.incr("notrans:"));
            try {
                TimeUnit.MILLISECONDS.sleep(5*1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(redis.decrBy("notrans:",-1));
        };
        for(int i=0;i<3;i++){
            new Thread(r).start();
        }
        TimeUnit.MILLISECONDS.sleep(20*1000);
    }
}
