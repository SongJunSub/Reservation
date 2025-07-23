# 🏨 예약 시스템 - Java vs Kotlin, MVC vs WebFlux 학습 프로젝트

> Spring Boot를 활용한 호스피탈리티 도메인 예약 시스템으로 Java/Kotlin 문법 비교와 Spring MVC/WebFlux 패러다임 비교 학습을 위한 프로젝트

## 📚 학습 목표

### 1. **Java vs Kotlin 문법 비교**
- Data Class vs POJO 패턴
- Null Safety vs Nullable 처리
- 함수형 프로그래밍 스타일 차이
- Extension Functions vs Utility Classes

### 2. **Spring MVC vs WebFlux 패러다임 비교**
- 동기 블로킹 vs 비동기 논블로킹
- 전통적 서블릿 vs 리액티브 스트림
- Thread-per-Request vs Event Loop
- ResponseEntity vs Mono/Flux

## 🗂️ 프로젝트 구조

```
src/
├── main/
│   ├── java/                           # Java 구현 (비교 학습용)
│   │   └── com/example/reservation/
│   │       ├── controller/
│   │       │   ├── ReservationControllerJava.java          # Java MVC
│   │       │   └── ReservationControllerWebFluxJava.java   # Java WebFlux
│   │       └── domain/guest/
│   │           ├── Guest.java                              # Java POJO
│   │           ├── Address.java
│   │           ├── GuestPreferences.java
│   │           └── *.java (enum들)
│   │
│   ├── kotlin/                         # Kotlin 구현 (주 구현)
│   │   └── com/example/reservation/
│   │       ├── controller/
│   │       │   ├── ReservationController.kt                # Kotlin MVC
│   │       │   └── ReservationControllerWebFlux.kt         # Kotlin WebFlux
│   │       ├── domain/
│   │       │   ├── guest/Guest.kt                          # Kotlin data class
│   │       │   ├── reservation/Reservation.kt
│   │       │   ├── room/Room.kt, Property.kt
│   │       │   ├── payment/Payment.kt
│   │       │   └── availability/RoomAvailability.kt
│   │       └── service/ReservationService.kt
│   │
│   └── resources/
│       └── application.yml
│
├── document/
│   ├── comparison/                     # 📊 비교 문서
│   │   ├── java-vs-kotlin-syntax.md   # 문법 비교 (시각적)
│   │   └── mvc-vs-webflux.md          # 패러다임 비교
│   │
│   ├── architecture/                   # 🏗️ 아키텍처 문서
│   ├── spring-webflux/                 # 🌊 WebFlux 가이드
│   └── project-setup/                  # ⚙️ 프로젝트 설정
│
└── test/                              # 🧪 테스트 코드
```

## 🔄 API 엔드포인트 비교

각 패러다임별로 동일한 기능을 다른 방식으로 구현하여 차이점을 학습할 수 있습니다:

| 구현 방식 | 엔드포인트 | 특징 |
|:---|:---|:---|
| **Kotlin MVC** | `/api/reservations` | 간결한 문법 + 동기 처리 |
| **Java MVC** | `/api/java/reservations` | 전통적 문법 + 동기 처리 |
| **Kotlin WebFlux** | `/api/webflux/reservations` | 간결한 문법 + 리액티브 |
| **Java WebFlux** | `/api/webflux-java/reservations` | 전통적 문법 + 리액티브 |

### 추가 WebFlux 전용 엔드포인트
- `GET /stream` - 실시간 스트리밍
- `GET /guest/{name}` - 필터링된 스트림
- `GET /count` - 리액티브 집계

## 📖 주요 학습 포인트

### 1. 문법 간결성 비교

**Kotlin Data Class (5줄)**
```kotlin
@Entity
data class Guest(
    @Id val id: Long = 0,
    val firstName: String,
    val email: String
)
```

**Java POJO (50+ 줄)**
```java
@Entity
public class Guest {
    @Id private Long id = 0L;
    private String firstName;
    private String email;
    
    // 생성자, Getter, Setter, equals, hashCode, toString...
}
```

### 2. 리액티브 처리 방식

**Spring MVC (동기)**
```kotlin
@GetMapping
fun getAllReservations(): ResponseEntity<List<Reservation>>
```

**Spring WebFlux (비동기)**
```kotlin
@GetMapping  
fun getAllReservations(): Flux<Reservation>
```

### 3. Null Safety 차이

**Kotlin (컴파일 타임 안전성)**
```kotlin
val phoneNumber: String? = null
phoneNumber?.let { /* safe call */ }
```

**Java (런타임 위험성)**
```java
String phoneNumber = null;
if (phoneNumber != null) { /* manual check */ }
```

## 🛠️ 기술 스택

- **언어**: Java 21, Kotlin 1.9.25
- **프레임워크**: Spring Boot 3.5.3
- **웹**: Spring MVC + Spring WebFlux (비교)
- **데이터**: JPA + R2DBC (하이브리드)
- **빌드**: Gradle

## 🚀 실행 방법

```bash
# 빌드
./gradlew build

# 테스트  
./gradlew test

# 실행 (포트 8080)
./gradlew bootRun
```

## 📊 학습 가이드

### 1단계: 문법 비교
1. `document/comparison/java-vs-kotlin-syntax.md` 읽기
2. `src/main/java/domain/guest/Guest.java` vs `src/main/kotlin/domain/guest/Guest.kt` 비교
3. 동일한 기능의 코드 라인 수 차이 확인

### 2단계: 패러다임 비교  
1. `document/comparison/mvc-vs-webflux.md` 읽기
2. 각 컨트롤러의 메서드 시그니처 비교
3. 동기 vs 비동기 처리 방식 이해

### 3단계: 실습
1. 각 엔드포인트로 동일한 요청 보내기
2. 응답 시간과 처리 방식 차이 관찰
3. WebFlux `/stream` 엔드포인트로 실시간 스트리밍 체험

## 📝 주요 학습 결과

| 측면 | Java | Kotlin | MVC | WebFlux |
|:---|:---:|:---:|:---:|:---:|
| **코드 간결성** | ❌ | ✅ | ➖ | ➖ |
| **학습 용이성** | ✅ | ❌ | ✅ | ❌ |
| **안전성** | ❌ | ✅ | ➖ | ➖ |
| **성능** | ➖ | ➖ | ❌ | ✅ |
| **동시성** | ➖ | ➖ | ❌ | ✅ |
| **디버깅** | ✅ | ➖ | ✅ | ❌ |

## 📚 추가 학습 자료

- [Java vs Kotlin 상세 비교 문서](document/comparison/java-vs-kotlin-syntax.md)
- [MVC vs WebFlux 패러다임 비교](document/comparison/mvc-vs-webflux.md)  
- [헥사고날 아키텍처 설계](document/architecture/hexagonal-architecture.md)
- [리액티브 프로그래밍 가이드](document/spring-webflux/reactive-programming.md)

---

**💡 학습 팁**: 이 프로젝트는 실행보다는 **코드 읽기와 비교**에 중점을 둔 학습용 프로젝트입니다. 각 파일을 열어보고 문법과 패러다임의 차이를 직접 확인해보세요!