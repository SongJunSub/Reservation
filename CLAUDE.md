# Claude Code Memory - Reservation System Project

## 프로젝트 개요
- **목적**: 호스피탈리티 도메인의 예약 시스템 구현
- **기술 스택**: Spring WebFlux + Kotlin
- **Java 버전**: Java 21 (LTS)
- **학습 목표**: 실무 프로젝트 기반으로 Spring WebFlux와 Kotlin 학습
- **특이사항**: 각 비즈니스 로직을 Java 21과 Kotlin 두 버전으로 구현하여 비교 학습

## 프로젝트 설정
- GitHub Repository: https://github.com/SongJunSub/Reservation
- 기본 Spring Boot Kotlin 프로젝트 구조로 시작
- Git 초기화 및 GitHub 연동 완료

## 개발 방향
1. 도메인 모델 설계
2. API 엔드포인트 구현
3. 데이터 레이어 구현
4. WebFlux 리액티브 프로그래밍과 Kotlin 코루틴 비교
5. Java vs Kotlin 코드 비교를 통한 학습

## 아키텍처 설계
### 패턴
- **Hexagonal Architecture (Clean Architecture)** 채택
- 도메인 로직과 외부 의존성 분리, 리액티브 스트림과 적합

### 레이어 구조
```
├── domain/           # 도메인 엔티티, 비즈니스 로직
├── application/      # 유스케이스, 서비스 로직  
├── infrastructure/   # 데이터베이스, 외부 API 연동
└── presentation/     # REST API, WebFlux 핸들러
```

### 주요 도메인
- **Reservation**: 예약 관리
- **Guest**: 고객 관리
- **Room/Property**: 숙박 시설 관리
- **Payment**: 결제 관리
- **Availability**: 예약 가능성 관리

### 기술 스택 (하이브리드 접근)
- **Database**: 
  - JPA + QueryDSL (블로킹, 복잡한 쿼리용)
  - R2DBC (리액티브, 단순 조회용)
  - PostgreSQL
- **Cache**: Redis (비동기)
- **Message**: Kafka (이벤트 기반)
- **웹**: Spring WebFlux (논블로킹)

## 프로젝트 구조
```
src/main/
├── kotlin/com/example/reservation/
│   ├── domain/          # 도메인 엔티티 및 비즈니스 로직
│   │   ├── reservation/ # 예약 도메인
│   │   ├── guest/       # 고객 도메인  
│   │   ├── room/        # 객실 도메인
│   │   ├── payment/     # 결제 도메인
│   │   └── availability/# 가용성 도메인
│   ├── application/     # 유스케이스 및 서비스
│   │   ├── usecase/     # 비즈니스 유스케이스
│   │   └── service/     # 애플리케이션 서비스
│   ├── infrastructure/ # 외부 시스템 연동
│   │   ├── persistence/ # 데이터베이스 접근
│   │   ├── messaging/   # Kafka 메시징
│   │   ├── cache/       # Redis 캐싱
│   │   └── config/      # 설정
│   └── presentation/   # API 레이어
│       ├── rest/        # REST API
│       └── handler/     # WebFlux 핸들러
└── java/com/example/reservation/ # Java 비교 구현
```

## 기술 문서
`document/` 디렉토리에 각 기술별 학습 자료 및 참고 문서 구성:
- spring-webflux: 리액티브 프로그래밍 개념
- jpa-querydsl: 블로킹 데이터 접근 패턴  
- r2dbc: 리액티브 데이터 접근 패턴
- kafka: 메시징 및 이벤트 기반 아키텍처
- redis: 캐싱 전략
- kotlin-coroutines: 코루틴 vs Virtual Threads
- architecture: 헥사고날 아키텍처 설계

## 구현 진행 상황

### 완료된 도메인 모델 ✅
1. **Guest 도메인** (Java + Kotlin 완료)
   - 고객 정보, 로열티 프로그램, 선호도 관리
   - 다국어 지원 (12개 언어), 주소 정보, 계정 상태 관리
   - 비즈니스 메서드: 로열티 포인트 적립, 등급 계산, VIP 판정 등
   - Lombok 적용으로 코드 간소화

2. **Property/Room 도메인** (Java + Kotlin 완료)
   - 숙박시설 정보, 정책, 편의시설 관리
   - 객실 유형, 요금, 상태 관리, 편의시설 체크
   - 비즈니스 메서드: 가용성 확인, 요금 계산, 예약 허용 여부 등

3. **Reservation 도메인** (Java + Kotlin 완료)
   - 예약 생명주기 관리 (대기 → 확정 → 체크인 → 체크아웃 → 완료)
   - 결제 상태, 취소/환불 정책, No-Show 처리
   - 특별 요청, 게스트 정보, 선호도 관리
   - 환불 금액 계산, 조기/늦은 체크인/아웃 처리

4. **Payment 도메인** (Java + Kotlin 완료)
   - 다양한 결제 수단 지원 (카드, 계좌이체, 디지털지갑, 암호화폐 등)
   - 결제 생명주기 관리 (대기 → 처리중 → 완료/실패)
   - 부분/전체 환불 처리, 분쟁 관리
   - 보안을 위한 카드번호 마스킹, 게이트웨이 연동

5. **Availability 도메인** (Java + Kotlin 완료)
   - 객실별 일자별 가용성 관리
   - 요금 계획 (기본, 조기예약, 막판특가, 성수기 등)
   - 최소/최대 숙박일 제한, 도착/출발 제한
   - 점유율 계산, 동적 요금 조정

### 기업급 기능 포함사항
- 로열티 프로그램 (5단계 등급제: STANDARD → DIAMOND)
- 다국어 지원 (12개 언어)
- 세분화된 상태 관리 (게스트, 예약, 결제, 가용성)
- 감사 추적 (생성/수정 시간)
- 비즈니스 규칙 구현 (취소 정책, 환불 계산, 요금 규칙 등)
- Lombok 활용으로 Java 코드 간소화 및 유지보수성 향상
- 데이터베이스 인덱스 최적화
- 보안 고려사항 (개인정보 마스킹, 민감정보 제외)

## 🛠️ 프로젝트 안정화 완료
### 빌드 환경 개선
- 누락된 enum 클래스들 완전 생성
- 컴파일 오류 해결 및 프로젝트 안정화

### 최신 기술 스택 적용
- **보안**: Spring Security + JWT (jjwt 0.12.3)
- **API 문서**: SpringDoc OpenAPI 3 (Swagger UI)
- **매핑**: MapStruct 1.5.5 (컴파일타임 매핑)
- **모니터링**: Actuator + Prometheus + Tracing
- **테스트**: Testcontainers + MockK + Reactor Test
- **JSON**: Jackson Kotlin + Kotlinx Serialization

## 다음 단계
- Application Layer 구현 (UseCase, Service)
- Infrastructure Layer 구현 (Repository, Configuration)  
- Presentation Layer 구현 (WebFlux Handler, Router)
- Java vs Kotlin 코드 비교 분석 문서화

## 작업 방식
- **문서화**: 모든 작업 진행사항을 CLAUDE.md에 지속적으로 업데이트
- **버전 관리**: 작업 완료 시마다 커밋 & 푸시 진행
- **커밋 메시지**: 한글 코멘트로 작성하여 이해하기 쉽게 작성
- **기억 유지**: 모든 작업 내용을 CLAUDE.md에 기록하여 컨텍스트 유지

## 명령어
- 빌드: `./gradlew build`
- 테스트: `./gradlew test`
- 실행: `./gradlew bootRun`