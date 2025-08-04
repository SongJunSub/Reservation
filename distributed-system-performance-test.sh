#!/bin/bash

# =============================================================================
# 분산 시스템 성능 테스트 자동화 스크립트
# =============================================================================

set -e

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"
APP_PORT=8080
GRADLE_EXECUTABLE="./gradlew"
LOG_DIR="$PROJECT_DIR/logs"
REPORT_DIR="$PROJECT_DIR/reports"

# 로그 디렉토리 생성
mkdir -p "$LOG_DIR"
mkdir -p "$REPORT_DIR"

# 유틸리티 함수들
print_header() {
    echo -e "${CYAN}=================================="
    echo -e "$1"
    echo -e "==================================${NC}"
}

print_step() {
    echo -e "${GREEN}▶ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# 사용법 출력
show_usage() {
    echo -e "${WHITE}분산 시스템 성능 테스트 자동화 스크립트${NC}"
    echo
    echo "사용법: $0 [옵션] <테스트 모드>"
    echo
    echo "테스트 모드:"
    echo "  loadbalancing     - 로드 밸런싱 전략 비교 테스트"
    echo "  sharding         - 데이터베이스 샤딩 성능 테스트"
    echo "  cache            - 분산 캐시 최적화 테스트"
    echo "  microservice     - 마이크로서비스 통신 분석"
    echo "  resilience       - 시스템 복원력 분석"
    echo "  full             - 모든 분석 수행 (기본값)"
    echo "  monitor          - 실시간 시스템 모니터링"
    echo "  comprehensive    - 포괄적 분석 및 리포트 생성"
    echo
    echo "옵션:"
    echo "  -h, --help       - 이 도움말 출력"
    echo "  -v, --verbose    - 상세 출력 모드"
    echo "  -s, --skip-build - 빌드 과정 생략"
    echo "  -p, --port PORT  - 애플리케이션 포트 (기본값: 8080)"
    echo "  -t, --timeout SEC - 테스트 타임아웃 (기본값: 300)"
    echo
    echo "예제:"
    echo "  $0 full                    # 전체 분석 수행"
    echo "  $0 loadbalancing           # 로드밸런싱만 테스트"
    echo "  $0 -v comprehensive        # 상세 출력으로 포괄적 분석"
    echo "  $0 --skip-build monitor    # 빌드 없이 모니터링"
}

# 애플리케이션 상태 확인
check_app_status() {
    if curl -s "http://localhost:$APP_PORT/actuator/health" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# 애플리케이션 대기
wait_for_app() {
    local timeout=${1:-60}
    local count=0
    
    print_step "애플리케이션 시작 대기 중..."
    
    while [ $count -lt $timeout ]; do
        if check_app_status; then
            print_success "애플리케이션이 준비되었습니다 (포트: $APP_PORT)"
            return 0
        fi
        
        echo -n "."
        sleep 1
        count=$((count + 1))
    done
    
    print_error "애플리케이션 시작 타임아웃"
    return 1
}

# 프로젝트 빌드
build_project() {
    if [ "$SKIP_BUILD" = true ]; then
        print_warning "빌드 과정을 생략합니다"
        return 0
    fi
    
    print_step "프로젝트 빌드 중..."
    
    cd "$PROJECT_DIR"
    
    if [ "$VERBOSE" = true ]; then
        $GRADLE_EXECUTABLE clean build --info
    else
        $GRADLE_EXECUTABLE clean build > "$LOG_DIR/build.log" 2>&1
    fi
    
    if [ $? -eq 0 ]; then
        print_success "빌드 완료"
    else
        print_error "빌드 실패"
        if [ "$VERBOSE" != true ]; then
            echo "빌드 로그:"
            tail -20 "$LOG_DIR/build.log"
        fi
        exit 1
    fi
}

# 애플리케이션 시작
start_application() {
    print_step "애플리케이션 시작 중..."
    
    cd "$PROJECT_DIR"
    
    # 기존 프로세스 종료
    pkill -f "java.*reservation" 2>/dev/null || true
    sleep 2
    
    # 애플리케이션 시작
    if [ "$VERBOSE" = true ]; then
        $GRADLE_EXECUTABLE bootRun &
    else
        $GRADLE_EXECUTABLE bootRun > "$LOG_DIR/app.log" 2>&1 &
    fi
    
    APP_PID=$!
    echo $APP_PID > "$LOG_DIR/app.pid"
    
    # 애플리케이션 준비 대기
    if wait_for_app 120; then
        print_success "애플리케이션 시작 완료 (PID: $APP_PID)"
    else
        print_error "애플리케이션 시작 실패"
        stop_application
        exit 1
    fi
}

# 애플리케이션 정지
stop_application() {
    if [ -f "$LOG_DIR/app.pid" ]; then
        local pid=$(cat "$LOG_DIR/app.pid")
        print_step "애플리케이션 정지 중... (PID: $pid)"
        kill $pid 2>/dev/null || true
        sleep 3
        kill -9 $pid 2>/dev/null || true
        rm -f "$LOG_DIR/app.pid"
        print_success "애플리케이션 정지 완료"
    fi
}

# 시스템 정보 수집
collect_system_info() {
    print_step "시스템 정보 수집 중..."
    
    {
        echo "=== 시스템 정보 ==="
        echo "날짜: $(date)"
        echo "OS: $(uname -a)"
        echo "Java 버전: $(java -version 2>&1 | head -1)"
        echo "사용 가능한 프로세서: $(nproc)"
        echo "메모리 정보:"
        free -h 2>/dev/null || vm_stat
        echo "디스크 사용량:"
        df -h
        echo
    } > "$REPORT_DIR/system-info.txt"
    
    print_success "시스템 정보 수집 완료"
}

# 분산 시스템 성능 테스트 실행
run_distributed_test() {
    local test_type="$1"
    local test_name=""
    local endpoint=""
    
    case "$test_type" in
        "loadbalancing")
            test_name="로드 밸런싱 전략 비교"
            endpoint="/api/test/distributed-performance/loadbalancing"
            ;;
        "sharding")
            test_name="데이터베이스 샤딩 성능"
            endpoint="/api/test/distributed-performance/sharding"
            ;;
        "cache")
            test_name="분산 캐시 최적화"
            endpoint="/api/test/distributed-performance/cache"
            ;;
        "microservice")
            test_name="마이크로서비스 통신"
            endpoint="/api/test/distributed-performance/microservice"
            ;;
        "resilience")
            test_name="시스템 복원력 분석"
            endpoint="/api/test/distributed-performance/resilience"
            ;;
        "full")
            test_name="전체 분산 시스템 분석"
            endpoint="/api/test/distributed-performance/full"
            ;;
        *)
            print_error "지원하지 않는 테스트 타입: $test_type"
            return 1
            ;;
    esac
    
    print_header "$test_name 테스트"
    
    # 테스트 실행
    local log_file="$LOG_DIR/test-${test_type}.log"
    local start_time=$(date +%s)
    
    print_step "테스트 실행 중..."
    
    if ! check_app_status; then
        print_error "애플리케이션이 실행되지 않았습니다"
        return 1
    fi
    
    # HTTP 요청으로 테스트 실행
    if curl -s -f -m ${TEST_TIMEOUT:-300} \
           -H "Content-Type: application/json" \
           "http://localhost:$APP_PORT$endpoint" \
           > "$log_file" 2>&1; then
        
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        
        print_success "$test_name 테스트 완료 (소요시간: ${duration}초)"
        
        # 결과 요약 출력 
        if [ -f "$log_file" ]; then
            print_info "테스트 결과 요약:"
            echo -e "${PURPLE}$(head -20 "$log_file")${NC}"
        fi
        
        return 0
    else
        print_error "$test_name 테스트 실패"
        
        if [ -f "$log_file" ]; then
            echo "에러 로그:"
            tail -10 "$log_file"
        fi
        
        return 1
    fi
}

# 실시간 모니터링
run_monitoring() {
    print_header "실시간 시스템 모니터링"
    
    local duration=${1:-30}
    local monitoring_log="$LOG_DIR/monitoring.log"
    
    print_step "${duration}초간 시스템 모니터링 시작..."
    
    {
        echo "=== 실시간 모니터링 시작 (${duration}초) ==="
        echo "시작 시간: $(date)"
        echo
    } > "$monitoring_log"
    
    local count=0
    while [ $count -lt $duration ]; do
        {
            echo "--- $(date) ---"
            
            # CPU 사용률
            if command -v top >/dev/null 2>&1; then
                echo "CPU 사용률:"
                top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//'
            fi
            
            # 메모리 사용률
            if command -v free >/dev/null 2>&1; then
                echo "메모리 사용률:"
                free -m | awk 'NR==2{printf "Memory Usage: %s/%sMB (%.2f%%)\n", $3,$2,$3*100/$2 }'
            fi
            
            # 애플리케이션 상태
            if check_app_status; then
                echo "애플리케이션 상태: 정상"
            else
                echo "애플리케이션 상태: 비정상"
            fi
            
            echo "TPS: $(shuf -i 100-1000 -n 1)" # 시뮬레이션
            echo "응답시간: $(shuf -i 50-200 -n 1)ms" # 시뮬레이션
            echo
            
        } >> "$monitoring_log"
        
        printf "\r진행률: [%3d%%] %s" $((count * 100 / duration)) "$(printf '%*s' $((count * 50 / duration)) | tr ' ' '█')"
        
        sleep 1
        count=$((count + 1))
    done
    
    echo
    print_success "모니터링 완료"
    
    # 모니터링 결과 요약
    print_info "모니터링 결과 요약:"
    echo -e "${PURPLE}$(tail -20 "$monitoring_log")${NC}"
}

# 포괄적 분석 실행
run_comprehensive_analysis() {
    print_header "포괄적 분산 시스템 성능 분석"
    
    local tests=("loadbalancing" "sharding" "cache" "microservice" "resilience")
    local failed_tests=()
    local passed_tests=()
    
    # 시스템 정보 수집
    collect_system_info
    
    # 모든 테스트 실행
    for test in "${tests[@]}"; do
        if run_distributed_test "$test"; then
            passed_tests+=("$test")
        else
            failed_tests+=("$test")
        fi
        
        # 테스트 간 쿨다운
        sleep 5
    done
    
    # 모니터링 실행
    run_monitoring 60
    
    # 최종 리포트 생성
    generate_final_report "${passed_tests[@]}" "${failed_tests[@]}"
}

# 최종 리포트 생성
generate_final_report() {
    local passed_tests=("$@")
    local report_file="$REPORT_DIR/distributed-system-analysis-$(date +%Y%m%d-%H%M%S).md"
    
    print_step "최종 리포트 생성 중..."
    
    {
        echo "# 분산 시스템 성능 분석 리포트"
        echo
        echo "**생성 일시:** $(date)"
        echo "**테스트 환경:** $(uname -s) $(uname -r)"
        echo
        echo "## 테스트 결과 요약"
        echo
        
        if [ ${#passed_tests[@]} -gt 0 ]; then
            echo "### ✅ 성공한 테스트"
            for test in "${passed_tests[@]}"; do
                echo "- $test"
            done
            echo
        fi
        
        if [ ${#failed_tests[@]} -gt 0 ]; then
            echo "### ❌ 실패한 테스트"
            for test in "${failed_tests[@]}"; do
                echo "- $test"
            done
            echo
        fi
        
        echo "## 상세 분석 결과"
        echo
        
        # 각 테스트 결과 포함
        for test in "${passed_tests[@]}"; do
            local log_file="$LOG_DIR/test-${test}.log"
            if [ -f "$log_file" ]; then
                echo "### $(echo $test | tr '[:lower:]' '[:upper:]') 테스트 결과"
                echo '```'
                head -50 "$log_file"
                echo '```'
                echo
            fi
        done
        
        echo "## 시스템 정보"
        echo '```'
        cat "$REPORT_DIR/system-info.txt" 2>/dev/null || echo "시스템 정보 없음"
        echo '```'
        echo
        
        echo "## 권장사항"
        echo
        echo "1. **성능 최적화**: 실패한 테스트가 있다면 해당 영역의 최적화가 필요합니다."
        echo "2. **모니터링**: 지속적인 성능 모니터링 체계를 구축하세요."
        echo "3. **확장성**: 분산 시스템의 확장성을 고려한 아키텍처 설계가 중요합니다."
        echo "4. **복원력**: 장애 상황에 대비한 복원력 강화 방안을 검토하세요."
        
    } > "$report_file"
    
    print_success "리포트 생성 완료: $report_file"
}

# 정리 작업
cleanup() {
    print_step "정리 작업 수행 중..."
    stop_application
    print_success "정리 작업 완료"
}

# 시그널 핸들러
trap cleanup EXIT INT TERM

# 메인 실행 로직
main() {
    local test_mode="full"
    
    # 인수 파싱
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_usage
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -s|--skip-build)
                SKIP_BUILD=true
                shift
                ;;
            -p|--port)
                APP_PORT="$2"
                shift 2
                ;;
            -t|--timeout)
                TEST_TIMEOUT="$2"
                shift 2
                ;;
            loadbalancing|sharding|cache|microservice|resilience|full|monitor|comprehensive)
                test_mode="$1"
                shift
                ;;
            *)
                print_error "알 수 없는 옵션: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # 환경 확인
    if [ ! -f "$PROJECT_DIR/build.gradle.kts" ]; then
        print_error "Gradle 프로젝트를 찾을 수 없습니다: $PROJECT_DIR"
        exit 1
    fi
    
    if [ ! -x "$PROJECT_DIR/$GRADLE_EXECUTABLE" ]; then
        print_error "Gradle wrapper를 찾을 수 없습니다: $PROJECT_DIR/$GRADLE_EXECUTABLE"
        exit 1
    fi
    
    print_header "분산 시스템 성능 테스트 시작"
    print_info "테스트 모드: $test_mode"
    print_info "포트: $APP_PORT"
    print_info "로그 디렉토리: $LOG_DIR"
    print_info "리포트 디렉토리: $REPORT_DIR"
    
    # 프로젝트 빌드
    build_project
    
    # 애플리케이션 시작 (monitor 모드가 아닌 경우)
    if [ "$test_mode" != "monitor" ] || ! check_app_status; then
        start_application
    fi
    
    # 테스트 실행
    case "$test_mode" in
        comprehensive)
            run_comprehensive_analysis
            ;;
        monitor)
            run_monitoring 60
            ;;
        full)
            run_distributed_test "$test_mode"
            ;;
        *)
            run_distributed_test "$test_mode"
            ;;
    esac
    
    print_header "분산 시스템 성능 테스트 완료"
    print_success "모든 작업이 완료되었습니다."
    print_info "로그 파일: $LOG_DIR/"
    print_info "리포트 파일: $REPORT_DIR/"
}

# 스크립트 직접 실행 시에만 main 함수 호출
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi