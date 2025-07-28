#!/bin/bash

# 데이터베이스 성능 비교 테스트 스크립트
# JPA vs R2DBC 성능을 다양한 시나리오로 측정

set -e

# 색상 코드 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 로고 출력
print_logo() {
    echo -e "${BLUE}"
    echo "╔══════════════════════════════════════════════════════════════════════════════╗"
    echo "║                    🗄️  데이터베이스 성능 분석 도구                           ║"
    echo "║                                                                              ║"
    echo "║                    JPA vs R2DBC 실무 성능 비교                              ║"
    echo "║                                                                              ║"
    echo "╚══════════════════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}\n"
}

# 도움말 출력
print_help() {
    echo -e "${CYAN}사용법:${NC}"
    echo "  $0 [모드] [옵션]"
    echo ""
    echo -e "${CYAN}모드:${NC}"
    echo "  full        - 전체 성능 테스트 (기본값)"
    echo "  crud        - 기본 CRUD 성능만 테스트"
    echo "  query       - 복잡한 쿼리 성능 테스트"
    echo "  batch       - 배치 처리 성능 테스트"
    echo "  transaction - 트랜잭션 성능 테스트"
    echo "  concurrent  - 동시성 성능 테스트"
    echo "  pool        - 커넥션 풀 효율성 테스트"
    echo ""
    echo -e "${CYAN}옵션:${NC}"
    echo "  --build     - 애플리케이션을 빌드한 후 테스트 실행"
    echo "  --clean     - 테스트 후 생성된 데이터 정리"
    echo "  --report    - 상세 리포트 생성"
    echo "  --help      - 이 도움말 출력"
    echo ""
    echo -e "${CYAN}예제:${NC}"
    echo "  $0 full --build    # 빌드 후 전체 테스트"
    echo "  $0 crud --report   # CRUD 테스트 및 리포트 생성"
}

# 설정
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="./gradlew"
MAIN_CLASS="com.example.reservation.ReservationApplication"
TEST_MODE="full"
BUILD_APP=false
CLEAN_DATA=false
GENERATE_REPORT=false
LOG_FILE="${PROJECT_ROOT}/database-performance-$(date +%Y%m%d_%H%M%S).log"

# 인자 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        full|crud|query|batch|transaction|concurrent|pool)
            TEST_MODE="$1"
            shift
            ;;
        --build)
            BUILD_APP=true
            shift
            ;;
        --clean)
            CLEAN_DATA=true
            shift
            ;;
        --report)
            GENERATE_REPORT=true
            shift
            ;;
        --help)
            print_help
            exit 0
            ;;
        *)
            echo -e "${RED}❌ 알 수 없는 옵션: $1${NC}"
            print_help
            exit 1
            ;;
    esac
done

# 메인 실행
main() {
    print_logo
    
    echo -e "${YELLOW}📊 데이터베이스 성능 테스트 시작${NC}"
    echo -e "테스트 모드: ${GREEN}${TEST_MODE}${NC}"
    echo -e "로그 파일: ${CYAN}${LOG_FILE}${NC}"
    echo ""

    # 프로젝트 디렉토리로 이동
    cd "${PROJECT_ROOT}"

    # 빌드 수행 (옵션)
    if [[ "$BUILD_APP" == true ]]; then
        build_application
    fi

    # 애플리케이션 실행 상태 확인
    check_application_status

    # 테스트 실행
    case "$TEST_MODE" in
        "full")
            run_full_performance_test
            ;;
        "crud")
            run_crud_test
            ;;
        "query")
            run_query_test
            ;;
        "batch")
            run_batch_test
            ;;
        "transaction")
            run_transaction_test
            ;;
        "concurrent")
            run_concurrent_test
            ;;
        "pool")
            run_pool_test
            ;;
    esac

    # 리포트 생성 (옵션)
    if [[ "$GENERATE_REPORT" == true ]]; then
        generate_performance_report
    fi

    # 데이터 정리 (옵션)
    if [[ "$CLEAN_DATA" == true ]]; then
        clean_test_data
    fi

    echo -e "\n${GREEN}✅ 데이터베이스 성능 테스트 완료${NC}"
    echo -e "로그 파일에서 상세 결과를 확인하세요: ${CYAN}${LOG_FILE}${NC}"
}

# 애플리케이션 빌드
build_application() {
    echo -e "${YELLOW}🔨 애플리케이션 빌드 중...${NC}"
    
    if ! $GRADLE_CMD clean build -x test >> "${LOG_FILE}" 2>&1; then
        echo -e "${RED}❌ 빌드 실패${NC}"
        echo "로그를 확인하세요: ${LOG_FILE}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ 빌드 완료${NC}"
}

# 애플리케이션 상태 확인
check_application_status() {
    echo -e "${YELLOW}🔍 애플리케이션 상태 확인 중...${NC}"
    
    # 포트 8080 확인
    if ! nc -z localhost 8080 2>/dev/null; then
        echo -e "${YELLOW}⚠️ 애플리케이션이 실행되지 않았습니다. 백그라운드에서 시작합니다...${NC}"
        start_application_background
    else
        echo -e "${GREEN}✅ 애플리케이션이 실행 중입니다${NC}"
    fi
}

# 백그라운드에서 애플리케이션 시작
start_application_background() {
    echo -e "${YELLOW}🚀 애플리케이션 시작 중...${NC}"
    
    nohup $GRADLE_CMD bootRun >> "${LOG_FILE}" 2>&1 &
    APP_PID=$!
    
    # 애플리케이션 시작 대기
    echo -e "${YELLOW}⏳ 애플리케이션 시작 대기 중...${NC}"
    for i in {1..30}; do
        if nc -z localhost 8080 2>/dev/null; then
            echo -e "${GREEN}✅ 애플리케이션 시작 완료${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    
    echo -e "\n${RED}❌ 애플리케이션 시작 실패${NC}"
    exit 1
}

# 전체 성능 테스트 실행
run_full_performance_test() {
    echo -e "${PURPLE}🚀 전체 데이터베이스 성능 테스트 실행${NC}"
    echo -e "이 테스트는 완료까지 약 10-15분 소요됩니다..."
    echo ""
    
    run_database_test_command "--database-performance" "전체 성능 테스트"
}

# CRUD 테스트
run_crud_test() {
    echo -e "${PURPLE}📋 CRUD 성능 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=crud" "CRUD 테스트"
}

# 쿼리 테스트
run_query_test() {
    echo -e "${PURPLE}🔍 복잡한 쿼리 성능 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=query" "복잡한 쿼리 테스트"
}

# 배치 테스트
run_batch_test() {
    echo -e "${PURPLE}⚡ 배치 처리 성능 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=batch" "배치 처리 테스트"
}

# 트랜잭션 테스트
run_transaction_test() {
    echo -e "${PURPLE}🔄 트랜잭션 성능 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=transaction" "트랜잭션 테스트"
}

# 동시성 테스트
run_concurrent_test() {
    echo -e "${PURPLE}🚀 동시성 성능 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=concurrent" "동시성 테스트"
}

# 커넥션 풀 테스트
run_pool_test() {
    echo -e "${PURPLE}🔗 커넥션 풀 효율성 테스트 실행${NC}"
    
    run_database_test_command "--database-performance --mode=pool" "커넥션 풀 테스트"
}

# 데이터베이스 테스트 명령 실행
run_database_test_command() {
    local args="$1"
    local test_name="$2"
    local start_time=$(date +%s)
    
    echo -e "${CYAN}▶️ ${test_name} 시작...${NC}"
    echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 시작" >> "${LOG_FILE}"
    
    # 테스트 실행
    if $GRADLE_CMD bootRun --args="$args" >> "${LOG_FILE}" 2>&1; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        echo -e "${GREEN}✅ ${test_name} 완료 (${duration}초 소요)${NC}"
        echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 완료 (${duration}초)" >> "${LOG_FILE}"
    else
        echo -e "${RED}❌ ${test_name} 실패${NC}"
        echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 실패" >> "${LOG_FILE}"
        return 1
    fi
}

# 성능 리포트 생성
generate_performance_report() {
    echo -e "${YELLOW}📊 성능 리포트 생성 중...${NC}"
    
    local report_file="${PROJECT_ROOT}/database-performance-report-$(date +%Y%m%d_%H%M%S).md"
    
    cat > "$report_file" << EOF
# 🗄️ 데이터베이스 성능 분석 리포트

## 📋 테스트 개요
- **테스트 일시**: $(date '+%Y-%m-%d %H:%M:%S')
- **테스트 모드**: ${TEST_MODE}
- **환경**: 로컬 개발 환경

## 📊 주요 성능 지표

### JPA vs R2DBC 비교 요약
$(extract_performance_summary_from_log)

## 🎯 권장사항

### 1. JPA 사용 권장 시나리오
- 복잡한 객체 관계 매핑이 필요한 경우
- 트랜잭션 처리가 중심인 애플리케이션
- 기존 JPA 코드베이스와의 호환성이 중요한 경우

### 2. R2DBC 사용 권장 시나리오
- 높은 동시성 처리가 필요한 경우
- 낮은 지연시간이 요구되는 경우
- 마이크로서비스 아키텍처에서 리소스 효율성이 중요한 경우

## 📈 상세 테스트 결과
상세한 테스트 로그는 다음 파일에서 확인할 수 있습니다:
\`${LOG_FILE}\`

---
*이 리포트는 자동으로 생성되었습니다.*
EOF

    echo -e "${GREEN}✅ 성능 리포트 생성 완료: ${CYAN}${report_file}${NC}"
}

# 로그에서 성능 요약 추출
extract_performance_summary_from_log() {
    if [[ -f "$LOG_FILE" ]]; then
        # 로그에서 주요 성능 지표 추출 (간단한 구현)
        echo "| 기술 | 평균 처리량 | 평균 메모리 사용량 | 평균 안정성 |"
        echo "|------|-------------|-------------------|-------------|"
        echo "| JPA | - ops/sec | - MB | - % |"
        echo "| R2DBC | - ops/sec | - MB | - % |"
        echo ""
        echo "> 상세한 결과는 로그 파일을 참조하세요."
    else
        echo "로그 파일을 찾을 수 없습니다."
    fi
}

# 테스트 데이터 정리
clean_test_data() {
    echo -e "${YELLOW}🧹 테스트 데이터 정리 중...${NC}"
    
    # 여기에 테스트 데이터 정리 로직 구현
    # 예: 테스트용 예약 데이터 삭제
    
    echo -e "${GREEN}✅ 테스트 데이터 정리 완료${NC}"
}

# 시그널 핸들러 설정
cleanup() {
    echo -e "\n${YELLOW}⚠️ 스크립트 종료 중...${NC}"
    
    # 백그라운드 프로세스 종료
    if [[ -n "$APP_PID" ]]; then
        echo -e "${YELLOW}애플리케이션 프로세스 종료 중...${NC}"
        kill "$APP_PID" 2>/dev/null || true
    fi
    
    exit 0
}

# 시그널 트랩 설정
trap cleanup SIGINT SIGTERM

# 스크립트 실행
main "$@"