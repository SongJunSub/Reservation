# R2DBC (리액티브 데이터베이스 접근)

## 개념
- **Reactive Relational Database Connectivity**
- 논블로킹 I/O 기반 데이터베이스 드라이버
- Reactive Streams 스펙 구현

## JPA vs R2DBC 비교

| 특성 | JPA | R2DBC |
|------|-----|-------|
| I/O 모델 | 블로킹 | 논블로킹 |
| 스레드 사용 | 요청당 스레드 | 이벤트 루프 |
| 연관관계 | 자동 매핑 | 수동 처리 |
| 쿼리 복잡도 | 높음 (QueryDSL) | 단순 권장 |
| 성능 | 높은 지연시간 | 높은 처리량 |

## R2DBC 활용 예시

### Kotlin 코루틴과 함께
```kotlin
@Repository
class ReservationR2dbcRepository(
    private val databaseClient: DatabaseClient
) {
    suspend fun findByGuestId(guestId: Long): Flow<Reservation> {
        return databaseClient
            .sql("SELECT * FROM reservations WHERE guest_id = :guestId")
            .bind("guestId", guestId)
            .map { row -> mapToReservation(row) }
            .all()
            .asFlow()
    }
    
    suspend fun findAvailableRooms(
        checkIn: LocalDate, 
        checkOut: LocalDate
    ): Flow<Room> {
        return databaseClient
            .sql("""
                SELECT r.* FROM rooms r 
                WHERE r.id NOT IN (
                    SELECT res.room_id FROM reservations res 
                    WHERE res.check_in_date < :checkOut 
                    AND res.check_out_date > :checkIn
                )
            """)
            .bind("checkIn", checkIn)
            .bind("checkOut", checkOut)
            .map { row -> mapToRoom(row) }
            .all()
            .asFlow()
    }
}
```

### Reactor와 함께
```kotlin
@Repository
class ReservationReactiveRepository(
    private val databaseClient: DatabaseClient
) {
    fun findByStatus(status: ReservationStatus): Flux<Reservation> {
        return databaseClient
            .sql("SELECT * FROM reservations WHERE status = :status")
            .bind("status", status.name)
            .map { row -> mapToReservation(row) }
            .all()
    }
    
    fun countByDateRange(start: LocalDate, end: LocalDate): Mono<Long> {
        return databaseClient
            .sql("""
                SELECT COUNT(*) FROM reservations 
                WHERE check_in_date >= :start 
                AND check_in_date <= :end
            """)
            .bind("start", start)
            .bind("end", end)
            .map { row -> row.get(0, Long::class.java)!! }
            .one()
    }
}
```

## 활용 시나리오
1. **실시간 조회**: 객실 가용성 확인
2. **대량 처리**: 배치 예약 처리  
3. **스트리밍**: 실시간 예약 현황
4. **간단한 CRUD**: 빠른 응답이 필요한 API

## 제약사항
1. **연관관계 수동 처리**: N+1 문제 해결 복잡
2. **트랜잭션 제한**: 선언적 트랜잭션 지원 부족
3. **쿼리 작성**: 네이티브 SQL 위주
4. **디버깅 어려움**: 비동기 스택 트레이스