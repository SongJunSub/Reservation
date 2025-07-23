package com.example.reservation.domain.availability_java;

public enum RatePlan {
    STANDARD,           // 기본 요금
    EARLY_BIRD,         // 조기 예약 할인
    LAST_MINUTE,        // 막판 특가
    WEEKEND,            // 주말 요금
    HOLIDAY,            // 휴일 요금
    PEAK_SEASON,        // 성수기 요금
    LOW_SEASON,         // 비수기 요금
    CORPORATE,          // 기업 요금
    GROUP_DISCOUNT,     // 단체 할인
    LOYALTY_MEMBER,     // 멤버십 할인
    PROMOTIONAL        // 프로모션 요금
}