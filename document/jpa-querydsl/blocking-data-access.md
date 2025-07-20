# JPA + QueryDSL (블로킹 데이터 접근)

## 하이브리드 아키텍처에서의 역할
- **복잡한 쿼리**: 조인, 집계, 서브쿼리
- **관리 기능**: 어드민 대시보드, 리포트 생성
- **트랜잭션 처리**: 강한 일관성이 필요한 비즈니스 로직

## JPA의 장점
1. **객체-관계 매핑**: 엔티티 관계 표현이 자연스러움
2. **지연 로딩**: 필요한 시점에 데이터 로드
3. **1차 캐시**: 영속성 컨텍스트 기반 캐싱
4. **트랜잭션 관리**: @Transactional과 완벽 통합

## QueryDSL 활용
```java
// Java에서의 QueryDSL
QReservation reservation = QReservation.reservation;
QGuest guest = QGuest.guest;

List<ReservationDto> result = queryFactory
    .select(Projections.constructor(ReservationDto.class,
        reservation.id,
        guest.name,
        reservation.checkInDate))
    .from(reservation)
    .join(reservation.guest, guest)
    .where(reservation.status.eq(ReservationStatus.CONFIRMED)
        .and(reservation.checkInDate.between(startDate, endDate)))
    .orderBy(reservation.checkInDate.asc())
    .fetch();
```

```kotlin
// Kotlin에서의 QueryDSL
val reservation = QReservation.reservation
val guest = QGuest.guest

val result = queryFactory
    .select(constructor(ReservationDto::class.java,
        reservation.id,
        guest.name, 
        reservation.checkInDate))
    .from(reservation)
    .join(reservation.guest, guest)
    .where(reservation.status.eq(ReservationStatus.CONFIRMED)
        .and(reservation.checkInDate.between(startDate, endDate)))
    .orderBy(reservation.checkInDate.asc())
    .fetch()
```

## WebFlux와의 통합
```kotlin
@Service
class ReservationQueryService(
    private val queryFactory: JPAQueryFactory,
    private val asyncExecutor: Executor
) {
    fun findComplexReservations(): Mono<List<ReservationDto>> {
        return Mono.fromCallable {
            // 블로킹 JPA 쿼리를 별도 스레드풀에서 실행
            queryFactory.select(...)
                .from(reservation)
                .fetch()
        }.subscribeOn(Schedulers.fromExecutor(asyncExecutor))
    }
}
```

## 주의사항
1. **스레드풀 격리**: 메인 이벤트 루프 블로킹 방지
2. **커넥션 풀 관리**: 적절한 크기 설정
3. **N+1 문제**: 페치 조인 활용
4. **트랜잭션 범위**: 지나치게 긴 트랜잭션 방지