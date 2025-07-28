#!/bin/bash

# 대용량 데이터 처리 성능 테스트 스크립트
# JPA vs R2DBC를 대규모 데이터셋에서 실제 성능 비교

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
    echo "║                    📊 대용량 데이터 처리 성능 분석 도구                       ║"
    echo "║                                                                              ║"
    echo "║              JPA vs R2DBC 대규모 데이터셋 실무 성능 비교                     ║"
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
    echo "  full           - 전체 대용량 데이터 처리 테스트 (기본값)"
    echo "  retrieval      - 대용량 데이터 조회 성능만 테스트"
    echo "  paging         - 페이징 전략 성능 비교"
    echo "  modification   - Insert/Update 성능 테스트"
    echo "  export         - 데이터 Export/Import 성능"
    echo "  index          - 인덱스 효과 분석"
    echo "  memory         - 메모리 사용 패턴 분석"
    echo ""
    echo -e "${CYAN}옵션:${NC}"
    echo "  --build        - 애플리케이션을 빌드한 후 테스트 실행"
    echo "  --clean        - 테스트 후 생성된 데이터 정리"
    echo "  --report       - 상세 리포트 생성"
    echo "  --data-size N  - 테스트 데이터 크기 지정 (기본: 100000)"
    echo "  --help         - 이 도움말 출력"
    echo ""
    echo -e "${CYAN}예제:${NC}"
    echo "  $0 full --build                    # 빌드 후 전체 테스트"
    echo "  $0 paging --report                 # 페이징 테스트 및 리포트 생성"
    echo "  $0 retrieval --data-size 50000     # 5만개 데이터로 조회 테스트"
}

# 설정
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_CMD="./gradlew"
MAIN_CLASS="com.example.reservation.ReservationApplication"
TEST_MODE="full"
BUILD_APP=false
CLEAN_DATA=false
GENERATE_REPORT=false
DATA_SIZE=100000
LOG_FILE="${PROJECT_ROOT}/large-data-processing-$(date +%Y%m%d_%H%M%S).log"

# 인자 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        full|retrieval|paging|modification|export|index|memory)
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
        --data-size)
            DATA_SIZE="$2"
            shift 2
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
    
    echo -e "${YELLOW}📊 대용량 데이터 처리 성능 테스트 시작${NC}"
    echo -e "테스트 모드: ${GREEN}${TEST_MODE}${NC}"
    echo -e "데이터 크기: ${GREEN}${DATA_SIZE}${NC}개"
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
            run_full_large_data_test
            ;;
        "retrieval")
            run_retrieval_test
            ;;
        "paging")
            run_paging_test
            ;;
        "modification")
            run_modification_test
            ;;
        "export")
            run_export_test
            ;;
        "index")
            run_index_test
            ;;
        "memory")
            run_memory_test
            ;;
    esac

    # 리포트 생성 (옵션)
    if [[ "$GENERATE_REPORT" == true ]]; then
        generate_large_data_report
    fi

    # 데이터 정리 (옵션)
    if [[ "$CLEAN_DATA" == true ]]; then
        clean_test_data
    fi

    echo -e "\n${GREEN}✅ 대용량 데이터 처리 성능 테스트 완료${NC}"
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

# 전체 대용량 데이터 처리 테스트 실행
run_full_large_data_test() {
    echo -e "${PURPLE}🚀 전체 대용량 데이터 처리 테스트 실행${NC}"
    echo -e "이 테스트는 완료까지 약 20-30분 소요됩니다..."
    echo ""
    
    run_large_data_test_command "--large-data-processing" "전체 대용량 데이터 처리 테스트"
}

# 대용량 데이터 조회 테스트
run_retrieval_test() {
    echo -e "${PURPLE}📋 대용량 데이터 조회 성능 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=retrieval" "대용량 데이터 조회 테스트"
}

# 페이징 전략 테스트
run_paging_test() {
    echo -e "${PURPLE}📄 페이징 전략 성능 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=paging" "페이징 전략 테스트"
}

# Insert/Update 테스트
run_modification_test() {
    echo -e "${PURPLE}⚡ 대용량 데이터 Insert/Update 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=modification" "Insert/Update 테스트"
}

# Export/Import 테스트
run_export_test() {
    echo -e "${PURPLE}📤📥 데이터 Export/Import 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=export" "Export/Import 테스트"
}

# 인덱스 효과 테스트
run_index_test() {
    echo -e "${PURPLE}🔍 인덱스 효과 분석 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=index" "인덱스 효과 테스트"
}

# 메모리 사용 패턴 테스트
run_memory_test() {
    echo -e "${PURPLE}💾 메모리 사용 패턴 분석 테스트 실행${NC}"
    
    run_large_data_test_command "--large-data-processing --mode=memory" "메모리 사용 패턴 테스트"
}

# 대용량 데이터 테스트 명령 실행
run_large_data_test_command() {
    local args="$1"
    local test_name="$2"
    local start_time=$(date +%s)
    
    echo -e "${CYAN}▶️ ${test_name} 시작...${NC}"
    echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 시작" >> "${LOG_FILE}"
    
    # 시스템 리소스 모니터링 시작
    monitor_system_resources &
    local monitor_pid=$!
    
    # 테스트 실행
    if $GRADLE_CMD bootRun --args="$args --data-size=$DATA_SIZE" >> "${LOG_FILE}" 2>&1; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        # 모니터링 중단
        kill $monitor_pid 2>/dev/null || true
        
        echo -e "${GREEN}✅ ${test_name} 완료 (${duration}초 소요)${NC}"
        echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 완료 (${duration}초)" >> "${LOG_FILE}"
        
        # 성능 요약 출력
        print_performance_summary "$test_name" "$duration"
    else
        # 모니터링 중단
        kill $monitor_pid 2>/dev/null || true
        
        echo -e "${RED}❌ ${test_name} 실패${NC}"
        echo "$(date '+%Y-%m-%d %H:%M:%S') - ${test_name} 실패" >> "${LOG_FILE}"
        return 1
    fi
}

# 시스템 리소스 모니터링
monitor_system_resources() {
    local resource_log="${PROJECT_ROOT}/system-resources-$(date +%Y%m%d_%H%M%S).log"
    
    echo "시간,CPU사용률(%),메모리사용률(%),디스크I/O(KB/s)" > "$resource_log"
    
    while true; do
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}' || echo "0")
        local memory_usage=$(free | grep Mem | awk '{printf("%.1f", $3/$2 * 100.0)}' || echo "0")
        local disk_io=$(iostat -d 1 1 2>/dev/null | tail -n +4 | awk '{sum+=$4} END {print sum}' || echo "0")
        
        echo "$timestamp,$cpu_usage,$memory_usage,$disk_io" >> "$resource_log"
        sleep 5
    done
}

# 성능 요약 출력
print_performance_summary() {
    local test_name="$1"
    local duration="$2"
    
    echo -e "${CYAN}📊 ${test_name} 성능 요약:${NC}"
    echo "  총 실행 시간: ${duration}초"
    echo "  데이터 크기: ${DATA_SIZE}개"
    
    if [[ $duration -gt 0 ]]; then
        local throughput=$((DATA_SIZE / duration))
        echo "  처리율: ${throughput} records/sec"
        
        # 성능 등급 평가
        if [[ $throughput -gt 5000 ]]; then
            echo "  성능 등급: ${GREEN}A+ (매우 우수)${NC}"
        elif [[ $throughput -gt 2000 ]]; then
            echo "  성능 등급: ${GREEN}A (우수)${NC}"
        elif [[ $throughput -gt 1000 ]]; then
            echo "  성능 등급: ${YELLOW}B (양호)${NC}"
        elif [[ $throughput -gt 500 ]]; then
            echo "  성능 등급: ${YELLOW}C (보통)${NC}"
        else
            echo "  성능 등급: ${RED}D (개선 필요)${NC}"
        fi
    fi
    echo ""
}

# 대용량 데이터 리포트 생성
generate_large_data_report() {
    echo -e "${YELLOW}📊 대용량 데이터 처리 성능 리포트 생성 중...${NC}"
    
    local report_file="${PROJECT_ROOT}/large-data-processing-report-$(date +%Y%m%d_%H%M%S).md"
    
    cat > "$report_file" << EOF
# 📊 대용량 데이터 처리 성능 분석 리포트

## 📋 테스트 개요
- **테스트 일시**: $(date '+%Y-%m-%d %H:%M:%S')
- **테스트 모드**: ${TEST_MODE}
- **데이터 크기**: ${DATA_SIZE}개
- **환경**: 로컬 개발 환경

## 🎯 테스트 목적
대용량 데이터 처리 시나리오에서 JPA와 R2DBC의 성능 특성을 비교하고,
실무 환경에서 최적의 기술 선택을 위한 객관적 데이터를 제공합니다.

## 📊 주요 성능 지표

### 1. 대용량 조회 성능
$(extract_retrieval_performance_from_log)

### 2. 페이징 전략 비교
$(extract_paging_performance_from_log)

### 3. Insert/Update 성능
$(extract_modification_performance_from_log)

### 4. Export/Import 성능
$(extract_export_performance_from_log)

### 5. 인덱스 효과 분석
$(extract_index_performance_from_log)

### 6. 메모리 사용 패턴
$(extract_memory_performance_from_log)

## 🎯 권장사항

### 🏆 JPA 사용 권장 시나리오
- **복잡한 트랜잭션 처리**: 다중 테이블 조인 및 복잡한 비즈니스 로직
- **배치 처리 시스템**: 대량 데이터의 일괄 Insert/Update
- **레거시 시스템 호환**: 기존 JPA 코드베이스와의 호환성
- **개발팀 숙련도**: JPA 경험이 풍부한 팀

### 🏆 R2DBC 사용 권장 시나리오
- **실시간 데이터 스트리밍**: 대용량 데이터의 실시간 처리
- **높은 동시성**: 많은 수의 동시 사용자 지원
- **메모리 효율성**: 제한된 메모리 환경에서의 처리
- **마이크로서비스**: 리액티브 아키텍처 기반 시스템

### 📈 성능 최적화 전략

#### JPA 최적화
- 배치 처리를 위한 \`batch_size\` 설정
- 지연 로딩 vs 즉시 로딩 전략 선택
- 2차 캐시 활용
- N+1 문제 해결을 위한 Fetch Join

#### R2DBC 최적화
- 백프레셔 전략 적용
- 커넥션 풀 크기 조정
- 스트리밍 처리 활용
- Flux vs Mono 적절한 선택

## 📈 상세 테스트 결과
상세한 테스트 로그는 다음 파일에서 확인할 수 있습니다:
\`${LOG_FILE}\`

## 🔧 테스트 환경 정보
- **JVM 버전**: $(java -version 2>&1 | head -n 1)
- **시스템 메모리**: $(free -h | grep '^Mem:' | awk '{print $2}' || echo "Unknown")
- **CPU 코어**: $(nproc || echo "Unknown")개
- **테스트 데이터베이스**: H2 (인메모리)

---
*이 리포트는 자동으로 생성되었습니다.*
EOF

    echo -e "${GREEN}✅ 대용량 데이터 처리 성능 리포트 생성 완료: ${CYAN}${report_file}${NC}"
}

# 로그에서 성능 데이터 추출 함수들
extract_retrieval_performance_from_log() {
    if [[ -f "$LOG_FILE" ]]; then
        echo "| 기술 | 10K 조회 | 50K 조회 | 100K 조회 | 메모리 사용량 |"
        echo "|------|----------|----------|-----------|-------------|"
        echo "| JPA | - ms | - ms | - ms | - MB |"
        echo "| R2DBC | - ms | - ms | - ms | - MB |"
        echo ""
        echo "> 상세한 결과는 로그 파일을 참조하세요."
    else
        echo "로그 파일을 찾을 수 없습니다."
    fi
}

extract_paging_performance_from_log() {
    echo "| 전략 | 처리율 | 메모리 효율성 | 권장 사용 사례 |"
    echo "|------|--------|---------------|----------------|"
    echo "| Offset | - rps | - % | 소규모 데이터 |"
    echo "| Cursor | - rps | - % | 대용량 데이터 |"
    echo "| Streaming | - rps | - % | 실시간 처리 |"
}

extract_modification_performance_from_log() {
    echo "| 작업 | JPA 성능 | R2DBC 성능 | 성능 차이 |"
    echo "|------|----------|------------|-----------|"
    echo "| Insert | - rps | - rps | - % |"
    echo "| Update | - rps | - rps | - % |"
}

extract_export_performance_from_log() {
    echo "| 형식 | Export 속도 | Import 속도 | 파일 크기 |"
    echo "|------|-------------|-------------|-----------|"
    echo "| CSV | - MB/s | - MB/s | - MB |"
    echo "| JSON | - MB/s | - MB/s | - MB |"
}

extract_index_performance_from_log() {
    echo "| 검색 유형 | 인덱스 있음 | 인덱스 없음 | 성능 개선 |"
    echo "|-----------|-------------|-------------|-----------|"
    echo "| 단일 컬럼 | - ms | - ms | - 배 |"
    echo "| 복합 조건 | - ms | - ms | - 배 |"
}

extract_memory_performance_from_log() {
    echo "| 배치 크기 | 평균 메모리 | 최대 메모리 | GC 빈도 |"
    echo "|-----------|-------------|-------------|---------|"
    echo "| 작은 배치 | - MB | - MB | - 회 |"
    echo "| 큰 배치 | - MB | - MB | - 회 |"
}

# 테스트 데이터 정리
clean_test_data() {
    echo -e "${YELLOW}🧹 테스트 데이터 정리 중...${NC}"
    
    # HTTP 요청으로 데이터 정리 API 호출
    if curl -s -X DELETE "http://localhost:8080/api/test/cleanup" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ 테스트 데이터 정리 완료${NC}"
    else
        echo -e "${YELLOW}⚠️ 테스트 데이터 정리 API가 사용할 수 없거나 이미 정리되었습니다${NC}"
    fi
    
    # 임시 파일 정리
    find "${PROJECT_ROOT}" -name "export_*.csv" -o -name "export_*.json" -delete 2>/dev/null || true
    find "${PROJECT_ROOT}" -name "system-resources-*.log" -delete 2>/dev/null || true
    
    echo -e "${GREEN}✅ 임시 파일 정리 완료${NC}"
}

# 시그널 핸들러 설정
cleanup() {
    echo -e "\n${YELLOW}⚠️ 스크립트 종료 중...${NC}"
    
    # 백그라운드 프로세스 종료
    if [[ -n "$APP_PID" ]]; then
        echo -e "${YELLOW}애플리케이션 프로세스 종료 중...${NC}"
        kill "$APP_PID" 2>/dev/null || true
    fi
    
    # 리소스 모니터링 프로세스 종료
    pkill -f "monitor_system_resources" 2>/dev/null || true
    
    exit 0
}

# 시그널 트랩 설정
trap cleanup SIGINT SIGTERM

# 스크립트 실행
main "$@"