import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**模拟事务*/
public class TransDemo {
    public static void main(String[] args) throws Exception{
        //noTrans();
        trans();
        /*Jedis redis=new Jedis("localhost");
        redis.incr("notrans:");
        System.out.println(redis.decr("notrans:"));*/
    }
    private static void trans() throws InterruptedException {
        Runnable r=()->{
            Jedis redis=new Jedis("localhost");
            Transaction tx=redis.multi();
            tx.incr("trans:");
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            tx.decrBy("trans:",1);
            List<Object> resList=tx.exec();
            for(Object obj:resList){
                System.out.println(Thread.currentThread().getName()+" "+obj);
            }
            redis.disconnect();
        };
        ExecutorService executorService=Executors.newFixedThreadPool(3);
        for(int i=0;i<3;i++){
            executorService.submit(r);
        }
        executorService.shutdown();
        while (!executorService.awaitTermination(100, TimeUnit.MILLISECONDS)) {
            System.out.println("线程池没有关闭");
        }
        System.out.println("线程池已经关闭");
    }

    private static void noTrans() throws InterruptedException {
        Runnable r=()->{
            Jedis redis=new Jedis("localhost");
            System.out.println(Thread.currentThread().getName()+" incr:"+redis.incr("notrans:"));
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName()+" decr:"+redis.decrBy("notrans:",1));
            redis.disconnect();
        };
        for(int i=0;i<3;i++){
            new Thread(r).start();
        }
        TimeUnit.MILLISECONDS.sleep(500);
    }
}
