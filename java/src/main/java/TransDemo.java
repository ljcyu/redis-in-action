import redis.clients.jedis.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**模拟事务*/
public class TransDemo {
    public static void main(String[] args) throws Exception{
        //noTrans();
        //trans();
        //pipelineTest();
        pipelineTransTest();
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
    public static void pipelineTest(){
        Jedis redis=new Jedis("localhost");
        Pipeline pipeline=redis.pipelined();
        for(int i=0;i<10;i++){
            String key=String.format("test-%d:",i);
            pipeline.set(key,""+i);
            pipeline.expire(key,5);
        }
        List<Object> resList=pipeline.syncAndReturnAll();
        resList.forEach(System.out::println);
        redis.disconnect();
    }
    public static void pipelineTransTest(){
        Jedis redis=new Jedis("localhost");
        Pipeline pipeline=redis.pipelined();
        pipeline.multi();
        for(int i=0;i<10;i++){
            String key=String.format("test-%d:",i);
            pipeline.set(key,""+i);
            pipeline.expire(key,5);
        }
        pipeline.exec();
        List<Object> resList=pipeline.syncAndReturnAll();
        resList.forEach(System.out::println);
        redis.disconnect();
    }
    //同时连接多个主机
    public void multiJedisShardTest() {
        List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("localhost", 6379),
                new JedisShardInfo("localhost", 6380));
        ShardedJedis sharding = new ShardedJedis(shards);
        for (int i = 0; i < 1000; i++) {
            sharding.set("multiJedis-key-" + i, "value-" + i);
        }
        sharding.disconnect();
    }
    //同时连接多个主机,pipeline方式执行
    public void multiJedisShardPipeLineTest() {
        List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("localhost", 6379),
                new JedisShardInfo("localhost", 6380));
        ShardedJedis sharding = new ShardedJedis(shards);
        ShardedJedisPipeline pipelined = sharding.pipelined();
        for (int i = 0; i < 1000; i++) {
            pipelined.set("multiJedis-key-" + i, "value-" + i);
        }
        List<Object> resList=pipelined.syncAndReturnAll();
        resList.forEach(System.out::println);
        sharding.disconnect();
    }
    //线程池方式同时连接多个主机
    public void multiJedisShardThreadTest() {
        List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("localhost", 6379),
                new JedisShardInfo("localhost", 6380));
        ShardedJedisPool pool = new ShardedJedisPool(new JedisPoolConfig(), shards);
        ShardedJedis sharding = pool.getResource();
        for (int i = 0; i < 1000; i++) {
            sharding.set("multiJedis-key-" + i, "value-" + i);
        }
        pool.destroy();
    }
    //同时连接多个主机,pipeline方式执行
    public void multiJedisShardPipeLineThreadTest() {
        List<JedisShardInfo> shards = Arrays.asList(new JedisShardInfo("localhost", 6379),
                new JedisShardInfo("localhost", 6380));
        ShardedJedisPool pool = new ShardedJedisPool(new JedisPoolConfig(), shards);
        ShardedJedis sharding = pool.getResource();
        ShardedJedisPipeline pipelined = sharding.pipelined();
        for (int i = 0; i < 1000; i++) {
            pipelined.set("multiJedis-key-" + i, "value-" + i);
        }
        List<Object> resList=pipelined.syncAndReturnAll();
        resList.forEach(System.out::println);
        pool.destroy();
    }
}
