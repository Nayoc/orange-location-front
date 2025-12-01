package com.example.indoorlocation.api.req;

import com.example.indoorlocation.model.ApDto;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @author Monolog
 * @date 2025/8/25 16:11
 */
@Data
public class LocationReq {
    private Integer spaceId;
    private String navigationBatchId;

    private List<ApDto> apList;

    private String createTime;
}
