package com.example.reservation.domain.room;

public enum RoomAmenity {
    AIR_CONDITIONING("에어컨"),
    HEATING("난방"),
    WIFI("무선 인터넷"),
    TV("TV"),
    CABLE_TV("케이블 TV"),
    SATELLITE_TV("위성 TV"),
    MINIBAR("미니바"),
    COFFEE_MAKER("커피머신"),
    TEA_MAKER("차 우리는 도구"),
    REFRIGERATOR("냉장고"),
    MICROWAVE("전자레인지"),
    SAFE("금고"),
    TELEPHONE("전화"),
    HAIR_DRYER("헤어드라이어"),
    BATHROBE("목욕가운"),
    SLIPPERS("슬리퍼"),
    IRON("다리미"),
    IRONING_BOARD("다리미판"),
    WAKE_UP_SERVICE("모닝콜"),
    ROOM_SERVICE("룸서비스"),
    HOUSEKEEPING("하우스키핑"),
    BALCONY("발코니"),
    TERRACE("테라스"),
    GARDEN_VIEW("정원뷰"),
    SEA_VIEW("바다뷰"),
    MOUNTAIN_VIEW("산뷰"),
    CITY_VIEW("시티뷰"),
    POOL_VIEW("풀뷰"),
    WHEELCHAIR_ACCESS("휠체어 접근"),
    NON_SMOKING("금연"),
    SOUNDPROOF("방음"),
    BLACKOUT_CURTAINS("암막커튼"),
    EXTRA_BED_AVAILABLE("추가 침대 가능");
    
    private final String displayName;
    
    RoomAmenity(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}