import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.TimeUnit;

/**模仿第三章消息队列*/
public class MQ {
    static Logger logger= LoggerFactory.getLogger(MQ.class);
    public static void main(String[] args) {
        Jedis redis=new Jedis("localhost");
        Runnable publisher=()-> {
            try {
                TimeUnit.SECONDS.sleep(1);
                String channel="radio1";
                for (int i = 0; i < 10; i++) {
                    if(i%2==0) channel="radio";
                    else channel="fm";
                    redis.publish(channel, "msg:" + i);
                    TimeUnit.SECONDS.sleep(1);
                }
            }catch(InterruptedException ex){
                ex.printStackTrace();
            }
        };
        //订阅后会线程阻塞，所以需要一个新线程，订阅和发布的redis不能用同一个
        subscribe("sub1","radio");
        subscribe("sub2","fm");
        new Thread(publisher).start();
    }
    public static void subscribe(String name,String channel){
        Runnable r=()->{
            logger.info("{}",Thread.currentThread().getName());
            Jedis subscriberRedis=new Jedis("localhost");
            Subscriber subscriber=new Subscriber(name);
            subscriberRedis.subscribe(subscriber,channel);
        };
        new Thread(r).start();
    }
}

class Subscriber extends JedisPubSub{
    Logger logger= LoggerFactory.getLogger(Subscriber.class);
    String name;
    Subscriber(String name){ this.name=name; }
    @Override
    public void onMessage(String channel, String message) {
        logger.info("message {}:{}->{}",name,channel,message);
   }
    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        logger.info("subscribe {}:{}->{}",name,channel,subscribedChannels);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        logger.info("unsubscibe {}:{}->{}",name,channel,subscribedChannels);
    }
}
