package nl.omgwtfbbq.delver;

import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// TODO: do performance tests on the usage of ConcurrentHashmap.

/**
 * Singleton instance to collect usages of methods.
 */
@Slf4j
public final class PerformanceCollector {

    /**
     * Only instance.
     */
    private static PerformanceCollector performanceCollector = new PerformanceCollector();

    /**
     * The map with a key of signature, plus the amount of calls. The map is made
     * concurrent, because multiple threads can potentially modify the map. The add()
     * method is inserted in all transformed classes, therefore the add() can be
     * called from any number of threads.
     */
    private static Map<Signature, Metric> calls = new ConcurrentHashMap<>();

    private PerformanceCollector() {
    }

    public static PerformanceCollector instance() {
        return performanceCollector;
    }


    /**
     * 声明定时线程池，用于收集失败重新收集以及热修复等场景
     */
    private static final ScheduledExecutorService CLIENT_SCHEDULED_POOL = new ScheduledThreadPoolExecutor(
            1,
            new ThreadFactoryBuilder()
                    .setNameFormat("client-scheduled-pool-%d")
                    .setUncaughtExceptionHandler(((t, e) -> log.error("async task execute exception, thread: {}", t.getName(), e)))
                    .setDaemon(true)
                    .build(),
            new ThreadPoolExecutor.AbortPolicy()
    );

    //start scheduled job
    static {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        CLIENT_SCHEDULED_POOL.scheduleWithFixedDelay(()->{

            //count call times & filter callcount = 0's method
            List<SignatrueCalls> signatrueCallsLis=calls.keySet().stream()
                    .filter(t -> calls.get(t).getCallCount() >0 )
                    .map(key -> SignatrueCalls.builder()
                            .className(key.getClassName())
                            .method(key.getMethod())
                            .callCount(calls.get(key).getCallCount())
                            .average(calls.get(key).getAverage())
                            .max(calls.get(key).getMax())
                            .total(calls.get(key).getTotal())
                            .build())
                    .collect(Collectors.toList());

            //write result to local
            String fileName=String.format("delver-%s.json",formatter.format(new Date()));
            storeResultToLocal(new JSONObject()
                    .fluentPut("timestamp",System.currentTimeMillis())
                    .fluentPut("res",signatrueCallsLis)
                    .toJSONString(),fileName);

        //collect every 5 min
        },1,5, TimeUnit.MINUTES);
    }

    /**
     * 将结果写入到本地文件中，用于后续信息的获取
     * @param result
     * @param fileName
     * @return
     */
    public static Boolean storeResultToLocal(String result,String fileName){
        try{
            String folder=System.getProperty("user.home") + "/.rattler/delver";
            if(! new File(folder).exists()){
                //一次可以创建单级或者多级目录
                FileUtils.forceMkdir(new File(folder));
            }
            File file = new File(folder + "/"+fileName);
            //创建一个文件夹，如果由于某些原因导致不能创建，则抛出异常
            //将结果写入文件
            FileUtils.writeStringToFile(file,result+"\n","utf-8",true);
            return true;
        }catch(IOException e){
            e.printStackTrace();
            throw new RuntimeException("收集结果写入文件失败");
        }
    }

    /**
     * Adds a signature, or ups the counter by one for that signature.
     *
     * @param signature The signature to add.
     */
    public void add(final Signature signature, long start, long end) {
        if (calls.containsKey(signature)) {
            Metric m = calls.get(signature);
            m.update(start, end);
            calls.put(signature, m);
        } else {
            Metric m = new Metric();
            m.setSignature(signature);
            calls.put(signature, m);
        }
    }

    /**
     * Gets the call map as an unmodifiable map.
     *
     * @return The map.
     */
    public Map<Signature, Metric> getCallMap() {
        return Collections.unmodifiableMap(calls);
    }

    /**
     * Gets the total amount of calls.
     *
     * @return The tiotal amount of calls.
     */
    public long totalCallCount() {
        return calls.values().stream().mapToInt(Metric::getCallCount).sum();
    }

    /**
     * Writes the contents of the map to the specified outputstream.
     *
     * @param os The outputstream to write to.
     * @throws IOException When something fails.
     */
    public void write(final OutputStream os) throws IOException {
        for (Signature signature : calls.keySet()) {
            Metric m = calls.get(signature);
            os.write((m.getCallCount() + ";").getBytes());
            os.write((m.getAverage() + ";").getBytes());
            os.write(SignatureFormatter.format(signature).getBytes());
        }
        os.flush();
    }

    /**
     * Writes the contents of the map to the specified writer.
     *
     * @param w The writer to write to.
     * @throws IOException When something fails.
     */
    public void write(final Writer w) throws IOException {
        w.write("Call count;Max (ms);Average (ms);Total (ms);Modifiers;Returntype;Classname;Methodname\n");

        List<Metric> metricList = new ArrayList<>(calls.values());
        Collections.sort(metricList);
        for (Metric m : metricList) {
            Signature signature = m.getSignature();;
            w.write(String.valueOf(m.getCallCount()));
            w.write(";");
            w.write(String.valueOf(m.getMax()));
            w.write(";");
            w.write(String.valueOf(m.getAverage()));
            w.write(";");
            w.write(String.valueOf(m.getTotal()));
            w.write(";");
            w.write(SignatureFormatter.format(signature));
            w.write("\n");
        }
//        int sum = calls.values().stream().mapToInt(Metric::getCallCount).sum();
//        System.out.println("sum of all calls: " + sum);
        w.flush();
    }
}
