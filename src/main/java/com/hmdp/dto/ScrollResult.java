package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult<T> {
    /**
     * 返回的数据list
     **/
    private List<T> list;

    /**
     * 上一轮数据的最末尾元素，此次数据的开头
     **/
    private Long minTime;

    /**
     * 偏移量，上次数据中相同元素的个数，这次查询需要偏移的量
     **/
    private Integer offset;
}
