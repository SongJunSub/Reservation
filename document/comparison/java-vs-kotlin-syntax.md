# Java vs Kotlin 문법 비교

## 1. 엔티티 클래스 비교

### Data Class vs POJO

| **Kotlin (data class)** | **Java (POJO)** |
|:---|:---|
| ```kotlin<br>@Entity<br>@Table(name = "guests")<br>data class Guest(<br>    @Id<br>    @GeneratedValue(strategy = GenerationType.IDENTITY)<br>    val id: Long = 0,<br>    <br>    @Column(nullable = false, length = 100)<br>    val firstName: String,<br>    <br>    @Column(nullable = false, length = 100)<br>    val lastName: String,<br>    <br>    @Column(unique = true, nullable = false)<br>    val email: String<br>) {<br>    fun getFullName(): String = "$firstName $lastName"<br>}<br>``` | ```java<br>@Entity<br>@Table(name = "guests")<br>public class Guest {<br>    @Id<br>    @GeneratedValue(strategy = GenerationType.IDENTITY)<br>    private Long id = 0L;<br>    <br>    @Column(nullable = false, length = 100)<br>    private String firstName;<br>    <br>    @Column(nullable = false, length = 100)<br>    private String lastName;<br>    <br>    @Column(unique = true, nullable = false)<br>    private String email;<br>    <br>    // 기본 생성자<br>    protected Guest() {}<br>    <br>    // 전체 생성자<br>    public Guest(String firstName, String lastName, String email) {<br>        this.firstName = firstName;<br>        this.lastName = lastName;<br>        this.email = email;<br>    }<br>    <br>    // Getters<br>    public Long getId() { return id; }<br>    public String getFirstName() { return firstName; }<br>    public String getLastName() { return lastName; }<br>    public String getEmail() { return email; }<br>    <br>    // Setters<br>    public void setFirstName(String firstName) { this.firstName = firstName; }<br>    public void setLastName(String lastName) { this.lastName = lastName; }<br>    public void setEmail(String email) { this.email = email; }<br>    <br>    public String getFullName() {<br>        return firstName + " " + lastName;<br>    }<br>}<br>``` |

**핵심 차이점:**
- **Kotlin**: `data class`로 자동 생성 (equals, hashCode, toString, copy)
- **Java**: 모든 보일러플레이트 코드를 수동 작성
- **Kotlin**: 생성자 파라미터에서 프로퍼티 선언
- **Java**: 필드 + 생성자 + Getter/Setter 분리

---

## 2. Null Safety 비교

| **Kotlin (Null Safety)** | **Java (Nullable)** |
|:---|:---|
| ```kotlin<br>class Guest(<br>    val firstName: String,          // Not null<br>    val lastName: String,           // Not null<br>    val phoneNumber: String? = null // Nullable<br>) {<br>    fun getDisplayName(): String {<br>        return phoneNumber?.let { phone -><br>            "$firstName $lastName ($phone)"<br>        } ?: "$firstName $lastName"<br>    }<br>    <br>    fun isPhoneValid(): Boolean {<br>        return phoneNumber != null && phoneNumber.isNotEmpty()<br>    }<br>}<br>``` | ```java<br>public class Guest {<br>    private String firstName;          // Potentially null<br>    private String lastName;           // Potentially null<br>    private String phoneNumber;        // Potentially null<br>    <br>    public String getDisplayName() {<br>        if (phoneNumber != null && !phoneNumber.isEmpty()) {<br>            return firstName + " " + lastName + " (" + phoneNumber + ")";<br>        } else {<br>            return firstName + " " + lastName;<br>        }<br>    }<br>    <br>    public boolean isPhoneValid() {<br>        return phoneNumber != null && !phoneNumber.isEmpty();<br>    }<br>}<br>``` |

**핵심 차이점:**
- **Kotlin**: 컴파일 타임에 null safety 보장
- **Java**: 런타임 NullPointerException 위험
- **Kotlin**: `?.`, `?:`, `!!` 연산자로 간결한 null 처리
- **Java**: 명시적 null 체크 필요

---

## 3. Enum with Methods 비교

| **Kotlin (Companion Object)** | **Java (Static Methods)** |
|:---|:---|
| ```kotlin<br>enum class LoyaltyTier {<br>    STANDARD, SILVER, GOLD, PLATINUM, DIAMOND;<br>    <br>    companion object {<br>        fun calculateTier(points: Int): LoyaltyTier = when {<br>            points >= 50000 -> DIAMOND<br>            points >= 25000 -> PLATINUM<br>            points >= 10000 -> GOLD<br>            points >= 5000 -> SILVER<br>            else -> STANDARD<br>        }<br>    }<br>    <br>    fun getDiscountPercentage(): Double = when (this) {<br>        STANDARD -> 0.0<br>        SILVER -> 5.0<br>        GOLD -> 10.0<br>        PLATINUM -> 15.0<br>        DIAMOND -> 20.0<br>    }<br>}<br>``` | ```java<br>public enum LoyaltyTier {<br>    STANDARD, SILVER, GOLD, PLATINUM, DIAMOND;<br>    <br>    public static LoyaltyTier calculateTier(int points) {<br>        if (points >= 50000) return DIAMOND;<br>        if (points >= 25000) return PLATINUM;<br>        if (points >= 10000) return GOLD;<br>        if (points >= 5000) return SILVER;<br>        return STANDARD;<br>    }<br>    <br>    public double getDiscountPercentage() {<br>        switch (this) {<br>            case STANDARD: return 0.0;<br>            case SILVER: return 5.0;<br>            case GOLD: return 10.0;<br>            case PLATINUM: return 15.0;<br>            case DIAMOND: return 20.0;<br>            default: return 0.0;<br>        }<br>    }<br>}<br>``` |

**핵심 차이점:**
- **Kotlin**: `when` 표현식으로 더 간결하고 표현력 있음
- **Java**: `switch` 문으로 더 장황함
- **Kotlin**: `companion object`로 static 멤버 정의
- **Java**: `static` 키워드 사용

---

## 4. 함수형 프로그래밍 비교

| **Kotlin (함수형)** | **Java (Stream API)** |
|:---|:---|
| ```kotlin<br>class ReservationService {<br>    fun findActiveReservations(): List<Reservation> {<br>        return reservations.values<br>            .filter { it.status == ReservationStatus.CONFIRMED }<br>            .sortedBy { it.checkInDate }<br>            .take(10)<br>    }<br>    <br>    fun getTotalRevenue(): BigDecimal {<br>        return reservations.values<br>            .filter { it.paymentStatus == PaymentStatus.PAID }<br>            .map { it.totalAmount }<br>            .fold(BigDecimal.ZERO) { acc, amount -> acc + amount }<br>    }<br>}<br>``` | ```java<br>public class ReservationService {<br>    public List<Reservation> findActiveReservations() {<br>        return reservations.values().stream()<br>            .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)<br>            .sorted(Comparator.comparing(Reservation::getCheckInDate))<br>            .limit(10)<br>            .collect(Collectors.toList());<br>    }<br>    <br>    public BigDecimal getTotalRevenue() {<br>        return reservations.values().stream()<br>            .filter(r -> r.getPaymentStatus() == PaymentStatus.PAID)<br>            .map(Reservation::getTotalAmount)<br>            .reduce(BigDecimal.ZERO, BigDecimal::add);<br>    }<br>}<br>``` |

**핵심 차이점:**
- **Kotlin**: 컬렉션에 직접 함수형 메서드 제공
- **Java**: Stream API를 통한 함수형 프로그래밍
- **Kotlin**: `fold`, `take` 등 더 간결한 메서드명
- **Java**: `reduce`, `limit` 등 더 명시적인 메서드명

---

## 5. String Interpolation 비교

| **Kotlin (String Templates)** | **Java (String Concatenation)** |
|:---|:---|
| ```kotlin<br>class Guest {<br>    fun createWelcomeMessage(): String {<br>        return "안녕하세요, $firstName $lastName님! " +<br>               "귀하의 로열티 등급은 ${loyaltyTier.name}이며, " +<br>               "현재 ${loyaltyPoints}포인트를 보유하고 계십니다."<br>    }<br>    <br>    fun getReservationSummary(reservation: Reservation): String {<br>        return """<br>            예약 확인<br>            고객명: $firstName $lastName<br>            체크인: ${reservation.checkInDate}<br>            체크아웃: ${reservation.checkOutDate}<br>            총 금액: ${reservation.totalAmount}원<br>        """.trimIndent()<br>    }<br>}<br>``` | ```java<br>public class Guest {<br>    public String createWelcomeMessage() {<br>        return "안녕하세요, " + firstName + " " + lastName + "님! " +<br>               "귀하의 로열티 등급은 " + loyaltyTier.name() + "이며, " +<br>               "현재 " + loyaltyPoints + "포인트를 보유하고 계십니다.";<br>    }<br>    <br>    public String getReservationSummary(Reservation reservation) {<br>        return String.format(<br>            "예약 확인%n" +<br>            "고객명: %s %s%n" +<br>            "체크인: %s%n" +<br>            "체크아웃: %s%n" +<br>            "총 금액: %s원",<br>            firstName, lastName,<br>            reservation.getCheckInDate(),<br>            reservation.getCheckOutDate(),<br>            reservation.getTotalAmount()<br>        );<br>    }<br>}<br>``` |

**핵심 차이점:**
- **Kotlin**: `$변수`, `${표현식}` 템플릿 사용
- **Java**: `+` 연산자나 `String.format()` 사용
- **Kotlin**: 멀티라인 문자열 `"""` 지원
- **Java**: 개행 문자 `%n` 명시적 사용

---

## 6. Extension Functions vs Utility Methods

| **Kotlin (Extension Functions)** | **Java (Utility Methods)** |
|:---|:---|
| ```kotlin<br>// Extension function<br>fun LocalDate.toDisplayString(): String {<br>    return this.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"))<br>}<br><br>fun String.toLocalDate(): LocalDate? {<br>    return try {<br>        LocalDate.parse(this)<br>    } catch (e: DateTimeParseException) {<br>        null<br>    }<br>}<br><br>// 사용<br>val checkInDate = "2024-12-25".toLocalDate()<br>val displayDate = checkInDate?.toDisplayString()<br>``` | ```java<br>// Utility class<br>public class DateUtils {<br>    public static String toDisplayString(LocalDate date) {<br>        return date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));<br>    }<br>    <br>    public static LocalDate parseDate(String dateString) {<br>        try {<br>            return LocalDate.parse(dateString);<br>        } catch (DateTimeParseException e) {<br>            return null;<br>        }<br>    }<br>}<br><br>// 사용<br>LocalDate checkInDate = DateUtils.parseDate("2024-12-25");<br>String displayDate = DateUtils.toDisplayString(checkInDate);<br>``` |

**핵심 차이점:**
- **Kotlin**: 기존 클래스에 메서드를 추가하는 것처럼 사용
- **Java**: 별도의 유틸리티 클래스 필요
- **Kotlin**: 더 자연스러운 메서드 체이닝
- **Java**: 정적 메서드 호출 방식

---

## 요약

| 측면 | Kotlin 장점 | Java 장점 |
|:---|:---|:---|
| **간결성** | 보일러플레이트 코드 최소화 | 명시적이고 예측 가능한 코드 |
| **안전성** | Null Safety, 스마트 캐스트 | 성숙한 에코시스템과 툴링 |
| **표현력** | 함수형 프로그래밍, DSL 지원 | 널리 알려진 문법 |
| **학습곡선** | 간결하지만 새로운 개념들 | 친숙하지만 장황함 |