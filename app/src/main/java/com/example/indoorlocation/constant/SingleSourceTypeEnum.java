package com.example.indoorlocation.constant;

public enum SingleSourceTypeEnum {
    WIFI("wifi", "无线"),
    CELL("cell", "基站"),
    ;

    private String value;
    private String desc;

    SingleSourceTypeEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public String getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static SingleSourceTypeEnum getByValue(String value) {
        for (SingleSourceTypeEnum singleSourceTypeEnum : SingleSourceTypeEnum.values()) {
            if (singleSourceTypeEnum.getValue().equals(value)) {
                return singleSourceTypeEnum;
            }
        }
        return null;
    }
}
