package com.example.reservation.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class PropertyPolicies {
    
    @Column(nullable = false)
    @Builder.Default
    private String cancellationPolicy = "24시간 전 무료 취소";
    
    @Column(nullable = false)
    @Builder.Default
    private String petPolicy = "반려동물 동반 불가";
    
    @Column(nullable = false)
    @Builder.Default
    private String smokingPolicy = "전 객실 금연";
    
    @Column(nullable = false)
    @Builder.Default
    private String childPolicy = "만 18세 미만 투숙 불가";
    
    @Column(nullable = false)
    @Builder.Default
    private String extraBedPolicy = "추가 침대 요청 가능";
    
    @Column(nullable = false)
    @Builder.Default
    private Integer ageRestriction = 18;
}