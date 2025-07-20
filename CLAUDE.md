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

## 다음 단계
- 프로젝트 구조 생성
- 도메인 모델 구현 (Java 21 vs Kotlin 비교)
- WebFlux 핸들러 및 라우터 구현

## 작업 방식
- **문서화**: 모든 작업 진행사항을 CLAUDE.md에 지속적으로 업데이트
- **버전 관리**: 작업 완료 시마다 커밋 & 푸시 진행

## 명령어
- 빌드: `./gradlew build`
- 테스트: `./gradlew test`
- 실행: `./gradlew bootRun`