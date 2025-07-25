# Hexagonal Architecture (헥사고날 아키텍처)

## 개념
- **포트와 어댑터 패턴**으로도 불림
- 비즈니스 로직을 외부 의존성으로부터 격리
- 테스트 용이성과 유지보수성 향상

## 구조

```
┌─────────────────────────────────────────┐
│              Infrastructure             │
│  ┌─────────────────────────────────────┐│
│  │            Application              ││
│  │  ┌─────────────────────────────────┐││
│  │  │            Domain               │││
│  │  │   (Business Logic)              │││
│  │  └─────────────────────────────────┘││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

## 레이어별 역할

### Domain (내부)
- 비즈니스 엔티티
- 도메인 서비스
- 비즈니스 규칙

### Application (중간)
- 유스케이스 구현
- 포트 정의 (인터페이스)
- 도메인 서비스 조합

### Infrastructure (외부)
- 어댑터 구현
- 데이터베이스 접근
- 외부 API 호출
- 메시징 시스템

### Presentation (외부)
- REST API
- WebFlux 핸들러
- 사용자 인터페이스

## 장점
1. **의존성 역전**: 외부가 내부에 의존
2. **테스트 용이성**: 모킹이 쉬움  
3. **기술 독립성**: 프레임워크 변경 용이
4. **명확한 책임 분리**: 각 레이어의 역할이 명확

## 프로젝트 적용
- 예약 시스템의 복잡한 비즈니스 로직을 안전하게 격리
- JPA와 R2DBC 등 여러 기술을 동시에 사용할 때 유용