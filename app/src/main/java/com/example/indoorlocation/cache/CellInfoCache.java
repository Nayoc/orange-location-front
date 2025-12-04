package com.example.indoorlocation.cache;

import android.telephony.CellInfo;
import java.util.ArrayList;
import java.util.List;

public class CellInfoCache {

    private static volatile List<CellInfo> latestCellInfos = new ArrayList<>();

    public static synchronized void update(List<CellInfo> cellInfos) {
        if (cellInfos != null && !cellInfos.isEmpty()) {
            latestCellInfos = cellInfos;
        }
    }

    public static List<CellInfo> getLatest() {
        return latestCellInfos;
    }
}
