package com.treeeducation.ioas.applicationflow;

import java.util.Arrays;
import java.util.List;

/** Fixed ordered application workflow after a lead becomes a customer profile. */
public enum ApplicationStepCode {
    PREPARE_MATERIALS(1, "准备申请材料"),
    SCHOOL_OFFER(2, "申请获批学校发Offer"),
    VISA_PROCESSING(3, "签证办理"),
    VISA_PROCEDURES(4, "完成签证相关手续"),
    VISA_APPROVED_TICKET(5, "签证获批购买机票");

    private final int orderNo;
    private final String label;

    ApplicationStepCode(int orderNo, String label) {
        this.orderNo = orderNo;
        this.label = label;
    }

    public int orderNo() { return orderNo; }
    public String label() { return label; }

    public static List<ApplicationStepCode> ordered() {
        return Arrays.stream(values()).sorted((a, b) -> Integer.compare(a.orderNo, b.orderNo)).toList();
    }
}
