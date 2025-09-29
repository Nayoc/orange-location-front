package com.example.indoorlocation.model;

import lombok.Data;

@Data
public class CellDto {
    private String cellId;
    private String protocol;
    private Integer rsrp;
}
