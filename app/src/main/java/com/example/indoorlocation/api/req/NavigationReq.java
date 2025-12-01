package com.example.indoorlocation.api.req;

import java.util.Date;

import lombok.Data;

/**
 * @author Monolog
 * @date 2025/8/25 16:11
 */
@Data
public class NavigationReq {
    private Integer spaceId;
    private String navigationBatchId;

    private String createTime;
}
