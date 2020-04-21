package nl.omgwtfbbq.delver;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Author: wangyukun
 * Date: 2020/4/21 下午5:35
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatrueCalls {
    private String className;
    private String method;
    private int callCount = 0;
    /**
     * 统计平均耗时,最大耗时,总耗时
     */
    private long average = 0;
    private long max = 0;
    private long total = 0;
}
