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
public class CollectionReq {
    private Integer spaceId;
    private String collectionBatchId;

    private List<ApDto> apList;
    private double rpX;
    private double rpY;
    private String createdTime;
}
