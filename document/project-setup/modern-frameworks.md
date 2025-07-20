# 현대적 프레임워크 및 라이브러리 적용

## 추가된 최신 기술 스택

### 1. 보안 및 인증
- **Spring Security**: WebFlux와 통합된 리액티브 보안
- **JWT (jjwt)**: 최신 버전의 JWT 라이브러리 (0.12.3)

### 2. API 문서화
- **SpringDoc OpenAPI 3**: Swagger UI 통합 (2.3.0)
- WebFlux 환경에 최적화된 API 문서 자동 생성

### 3. 객체 매핑
- **MapStruct**: 컴파일 타임 매핑 라이브러리 (1.5.5)
- DTO ↔ Entity 변환을 안전하고 효율적으로 처리

### 4. 모니터링 및 관찰성
- **Spring Boot Actuator**: 운영 메트릭 및 헬스체크
- **Micrometer Tracing**: 분산 추적 (Brave 통합)
- **Prometheus**: 메트릭 수집 및 모니터링
- **Logstash Encoder**: 구조화된 로그 출력

### 5. 테스트 프레임워크
- **Testcontainers**: 통합 테스트용 컨테이너 (PostgreSQL, Kafka, R2DBC)
- **MockK**: Kotlin 친화적인 모킹 프레임워크
- **Reactor Test**: 리액티브 스트림 테스트 유틸리티

### 6. 데이터베이스
- **H2 Database**: 개발 및 테스트용 인메모리 DB
- **R2DBC H2**: 리액티브 H2 드라이버

### 7. JSON 처리
- **Jackson Kotlin Module**: Kotlin 데이터 클래스 직렬화 지원
- **Jackson JSR310**: Java 8 시간 API 지원
- **Kotlinx Serialization**: Kotlin 네이티브 직렬화

### 8. 유틸리티
- **Apache Commons Lang3**: 유용한 유틸리티 메서드
- **Commons Codec**: 인코딩/디코딩 유틸리티

## 기업급 개발 환경의 장점

### 1. 개발 생산성
- **MapStruct**: 보일러플레이트 코드 제거
- **Lombok**: Java 코드 간소화
- **OpenAPI**: 자동 API 문서화

### 2. 운영 및 모니터링
- **Actuator**: 애플리케이션 상태 모니터링
- **Prometheus**: 메트릭 기반 알림 시스템
- **Tracing**: 분산 시스템 디버깅

### 3. 테스트 품질
- **Testcontainers**: 실제 데이터베이스와 동일한 환경 테스트
- **MockK**: 타입 안전한 모킹
- **Reactor Test**: 비동기 코드 테스트

### 4. 보안
- **Spring Security**: 엔터프라이즈급 보안 기능
- **JWT**: 무상태 인증 시스템

## 프로젝트 적용 예정 기능

1. **API 문서 자동화**: `/swagger-ui.html` 엔드포인트
2. **메트릭 수집**: `/actuator/prometheus` 엔드포인트
3. **헬스체크**: `/actuator/health` 엔드포인트
4. **통합 테스트**: 실제 PostgreSQL 컨테이너 사용
5. **분산 추적**: 마이크로서비스 간 요청 추적