# Spring MVC vs Spring WebFlux 비교

## 1. 컨트롤러 구조 비교

### 기본 CRUD 엔드포인트

| **Spring MVC** | **Spring WebFlux** |
|:---|:---|
| ```kotlin<br>@RestController<br>@RequestMapping("/api/reservations")<br>class ReservationController(<br>    private val reservationService: ReservationService<br>) {<br>    @GetMapping<br>    fun getAllReservations(): ResponseEntity<List<Reservation>> {<br>        val reservations = reservationService.findAll()<br>        return ResponseEntity.ok(reservations)<br>    }<br>    <br>    @GetMapping("/{id}")<br>    fun getReservation(@PathVariable id: UUID): ResponseEntity<Reservation> {<br>        val reservation = reservationService.findById(id)<br>        return if (reservation != null) {<br>            ResponseEntity.ok(reservation)<br>        } else {<br>            ResponseEntity.notFound().build()<br>        }<br>    }<br>    <br>    @PostMapping<br>    fun createReservation(@RequestBody request: CreateReservationRequest): ResponseEntity<Reservation> {<br>        val reservation = reservationService.create(request)<br>        return ResponseEntity.status(HttpStatus.CREATED).body(reservation)<br>    }<br>}<br>``` | ```kotlin<br>@RestController<br>@RequestMapping("/api/webflux/reservations")<br>class ReservationControllerWebFlux(<br>    private val reservationService: ReservationService<br>) {<br>    @GetMapping<br>    fun getAllReservations(): Flux<Reservation> {<br>        return Flux.fromIterable(reservationService.findAll())<br>    }<br>    <br>    @GetMapping("/{id}")<br>    fun getReservation(@PathVariable id: UUID): Mono<Reservation> {<br>        val reservation = reservationService.findById(id)<br>        return if (reservation != null) {<br>            Mono.just(reservation)<br>        } else {<br>            Mono.empty()<br>        }<br>    }<br>    <br>    @PostMapping<br>    @ResponseStatus(HttpStatus.CREATED)<br>    fun createReservation(@RequestBody request: CreateReservationRequestWebFlux): Mono<Reservation> {<br>        return Mono.fromCallable {<br>            reservationService.createFromWebFluxRequest(request)<br>        }<br>    }<br>}<br>``` |

**핵심 차이점:**
- **MVC**: `List<T>`, `ResponseEntity<T>` 반환
- **WebFlux**: `Flux<T>`, `Mono<T>` 반환
- **MVC**: 동기식 블로킹 처리
- **WebFlux**: 비동기 논블로킹 처리

---

## 2. 고급 리액티브 기능

| **Spring MVC (불가능)** | **Spring WebFlux (가능)** |
|:---|:---|
| ```kotlin<br>// MVC에서는 스트리밍이 제한적<br>@GetMapping("/stream")<br>fun streamReservations(): ResponseEntity<List<Reservation>> {<br>    // 모든 데이터를 한번에 로드<br>    val reservations = reservationService.findAll()<br>    return ResponseEntity.ok(reservations)<br>}<br><br>// 실시간 업데이트 어려움<br>@GetMapping("/count")<br>fun getCount(): ResponseEntity<Long> {<br>    val count = reservationService.findAll().size.toLong()<br>    return ResponseEntity.ok(count)<br>}<br>``` | ```kotlin<br>// WebFlux에서는 진정한 스트리밍<br>@GetMapping("/stream")<br>fun streamAllReservations(): Flux<Reservation> {<br>    return Flux.fromIterable(reservationService.findAll())<br>        .delayElements(Duration.ofSeconds(1)) // 1초마다 하나씩<br>}<br><br>// 비동기 처리와 변환<br>@GetMapping("/guest/{guestName}")<br>fun getReservationsByGuest(@PathVariable guestName: String): Flux<Reservation> {<br>    return Flux.fromIterable(reservationService.findAll())<br>        .filter { it.guestDetails.primaryGuestFirstName.contains(guestName, ignoreCase = true) }<br>}<br><br>// 리액티브 집계<br>@GetMapping("/count")<br>fun getReservationCount(): Mono<Long> {<br>    return Flux.fromIterable(reservationService.findAll())<br>        .count()<br>}<br>``` |

---

## 3. Java vs Kotlin 차이 (WebFlux 컨텍스트)

### Kotlin WebFlux

```kotlin
@RestController
@RequestMapping("/api/webflux/reservations")
class ReservationControllerWebFlux(
    private val reservationService: ReservationService
) {
    @GetMapping
    fun getAllReservations(): Flux<Reservation> {
        return Flux.fromIterable(reservationService.findAll())
    }

    @GetMapping("/{id}")
    fun getReservation(@PathVariable id: UUID): Mono<Reservation> {
        val reservation = reservationService.findById(id)
        return if (reservation != null) {
            Mono.just(reservation)
        } else {
            Mono.empty()
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createReservation(@RequestBody request: CreateReservationRequestWebFlux): Mono<Reservation> {
        return Mono.fromCallable {
            reservationService.createFromWebFluxRequest(request)
        }
    }
}

// 간결한 DTO
data class CreateReservationRequestWebFlux(
    val guestName: String,
    val roomNumber: String,
    val checkInDate: String,
    val checkOutDate: String,
    val totalAmount: Double
)
```

### Java WebFlux

```java
@RestController
@RequestMapping("/api/webflux-java/reservations")
public class ReservationControllerWebFluxJava {

    private final ReservationService reservationService;

    public ReservationControllerWebFluxJava(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public Flux<Reservation> getAllReservations() {
        return Flux.fromIterable(reservationService.findAll());
    }

    @GetMapping("/{id}")
    public Mono<Reservation> getReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.findById(id);
        if (reservation != null) {
            return Mono.just(reservation);
        } else {
            return Mono.empty();
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Reservation> createReservation(@RequestBody CreateReservationRequestWebFluxJava request) {
        return Mono.fromCallable(() -> 
            reservationService.createFromWebFluxJavaRequest(request)
        );
    }
}

// 장황한 DTO
class CreateReservationRequestWebFluxJava {
    private String guestName;
    private String roomNumber;
    private String checkInDate;
    private String checkOutDate;
    private Double totalAmount;

    // 기본 생성자
    public CreateReservationRequestWebFluxJava() {}

    // 전체 생성자
    public CreateReservationRequestWebFluxJava(String guestName, String roomNumber, 
                                              String checkInDate, String checkOutDate, 
                                              Double totalAmount) {
        this.guestName = guestName;
        this.roomNumber = roomNumber;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalAmount = totalAmount;
    }

    // Getters
    public String getGuestName() { return guestName; }
    public String getRoomNumber() { return roomNumber; }
    public String getCheckInDate() { return checkInDate; }
    public String getCheckOutDate() { return checkOutDate; }
    public Double getTotalAmount() { return totalAmount; }

    // Setters
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}
```

---

## 4. 에러 처리 비교

| **Spring MVC** | **Spring WebFlux** |
|:---|:---|
| ```kotlin<br>@GetMapping("/{id}")<br>fun getReservation(@PathVariable id: UUID): ResponseEntity<Reservation> {<br>    return try {<br>        val reservation = reservationService.findById(id)<br>        if (reservation != null) {<br>            ResponseEntity.ok(reservation)<br>        } else {<br>            ResponseEntity.notFound().build()<br>        }<br>    } catch (e: Exception) {<br>        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()<br>    }<br>}<br>``` | ```kotlin<br>@GetMapping("/{id}")<br>fun getReservation(@PathVariable id: UUID): Mono<Reservation> {<br>    return Mono.fromCallable { reservationService.findById(id) }<br>        .filter { it != null }<br>        .switchIfEmpty(Mono.error(NotFoundException("Reservation not found")))<br>        .onErrorResume { error -><br>            when (error) {<br>                is NotFoundException -> Mono.empty()<br>                else -> Mono.error(error)<br>            }<br>        }<br>}<br>``` |

---

## 5. 성능 특성 비교

| 측면 | Spring MVC | Spring WebFlux |
|:---|:---|:---|
| **스레드 모델** | Thread-per-Request | Event Loop |
| **메모리 사용** | 높음 (스레드 스택) | 낮음 (이벤트 기반) |
| **동시성** | 제한적 (스레드 풀 크기) | 높음 (이벤트 루프) |
| **지연시간** | 높음 (블로킹 I/O) | 낮음 (논블로킹 I/O) |
| **처리량** | 중간 | 높음 (고부하에서) |
| **백프레셰** | 제한적 | 자연스러운 지원 |

---

## 6. 실제 사용 사례

### Spring MVC가 적합한 경우:
```kotlin
// 1. 간단한 CRUD 애플리케이션
@GetMapping("/customers")
fun getCustomers(): List<Customer> {
    return customerService.findAll() // 동기 DB 호출
}

// 2. 기존 블로킹 라이브러리 사용
@PostMapping("/process")
fun processData(@RequestBody data: ProcessRequest): ProcessResult {
    return legacyService.processSync(data) // 블로킹 처리
}
```

### Spring WebFlux가 적합한 경우:
```kotlin
// 1. 높은 동시성이 필요한 경우
@GetMapping("/notifications/stream")
fun streamNotifications(): Flux<Notification> {
    return notificationService.getNotificationStream()
        .delayElements(Duration.ofMillis(100))
}

// 2. 마이크로서비스 간 비동기 통신
@GetMapping("/aggregated-data")
fun getAggregatedData(): Mono<AggregatedResponse> {
    return Mono.zip(
        userService.getUserAsync(),
        orderService.getOrdersAsync(),
        inventoryService.getInventoryAsync()
    ).map { tuple ->
        AggregatedResponse(tuple.t1, tuple.t2, tuple.t3)
    }
}
```

---

## 요약

| 측면 | Spring MVC | Spring WebFlux |
|:---|:---|:---|
| **학습 곡선** | 쉬움 (전통적 모델) | 어려움 (리액티브 패러다임) |
| **생산성** | 높음 (익숙한 패턴) | 낮음 (초기 학습 비용) |
| **디버깅** | 쉬움 (스택 트레이스) | 어려움 (비동기 스택) |
| **테스팅** | 간단함 | 복잡함 (StepVerifier 필요) |
| **확장성** | 제한적 | 뛰어남 |
| **반응성** | 낮음 | 높음 |

**결론**: 
- **Spring MVC**: 전통적인 웹 애플리케이션, 빠른 개발
- **Spring WebFlux**: 고성능, 실시간, 마이크로서비스 아키텍처