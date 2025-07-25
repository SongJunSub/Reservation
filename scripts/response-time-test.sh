#!/bin/bash

# API 응답 시간 비교 테스트 스크립트
# Usage: ./scripts/response-time-test.sh [mode]

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 로고 출력
echo -e "${CYAN}"
echo "  ____                                         _____ _                 "
echo " |  _ \ ___  ___ _ __   ___  _ __  ___  ___    |_   _(_)_ __ ___   ___  "
echo " | |_) / _ \/ __| '_ \ / _ \| '_ \/ __|/ _ \     | | | | '_ \` _ \ / _ \ "
echo " |  _ <  __/\__ \ |_) | (_) | | | \__ \  __/     | | | | | | | | |  __/"
echo " |_| \_\___||___/ .__/ \___/|_| |_|___/\___|     |_| |_|_| |_| |_|\___| "
echo "                |_|                                                    "
echo -e "${NC}"
echo -e "${CYAN}📊 API Response Time Comparison Tool${NC}"
echo "=============================================="

# 모드 확인
MODE=${1:-comparison}

# 도구 함수들
check_dependencies() {
    echo -e "${YELLOW}🔍 의존성 확인 중...${NC}"
    
    # jq 설치 확인
    if ! command -v jq &> /dev/null; then
        echo -e "${YELLOW}⚠️ jq가 설치되지 않았습니다. JSON 파싱에 제한이 있을 수 있습니다.${NC}"
    fi
    
    # curl 확인
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}❌ curl이 필요합니다.${NC}"
        exit 1
    fi
    
    # bc 확인 (계산용)
    if ! command -v bc &> /dev/null; then
        echo -e "${YELLOW}⚠️ bc가 설치되지 않았습니다. 계산 기능에 제한이 있을 수 있습니다.${NC}"
    fi
    
    echo -e "${GREEN}✅ 의존성 확인 완료${NC}"
}

start_application() {
    echo -e "${YELLOW}🚀 애플리케이션 시작 중...${NC}"
    
    # 기존 프로세스 확인 및 종료
    if pgrep -f "reservation" > /dev/null; then
        echo "기존 애플리케이션 프로세스를 종료합니다..."
        pkill -f "reservation" || true
        sleep 3
    fi
    
    # 애플리케이션 빌드
    echo "애플리케이션 빌드 중..."
    ./gradlew clean build -x test -q
    
    # 백그라운드에서 애플리케이션 실행
    ./gradlew bootRun > app.log 2>&1 &
    APP_PID=$!
    echo "Application PID: $APP_PID"
    
    # 애플리케이션 시작 대기
    echo "애플리케이션 시작 대기 중..."
    for i in {1..60}; do
        if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
            echo -e "${GREEN}✅ 애플리케이션 준비 완료${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
    done
    
    echo -e "${RED}❌ 애플리케이션 시작 실패${NC}"
    kill $APP_PID 2>/dev/null || true
    exit 1
}

# 단일 엔드포인트 응답시간 측정
measure_endpoint_response_time() {
    local endpoint=$1
    local name=$2
    local requests=${3:-100}
    
    echo -e "${BLUE}📊 $name 응답시간 측정 ($requests 요청)${NC}"
    
    local total_time=0
    local success_count=0
    local min_time=9999999
    local max_time=0
    local times=()
    
    # 테스트 데이터
    local test_data='{
        "guestName": "Response Time Test Guest",
        "roomNumber": "Room 101",
        "checkInDate": "2024-12-25",
        "checkOutDate": "2024-12-27",
        "totalAmount": 250.0
    }'
    
    echo "진행률: "
    for i in $(seq 1 $requests); do
        # 진행률 표시
        if [ $((i % 10)) -eq 0 ]; then
            echo -n "$i "
        elif [ $((i % 5)) -eq 0 ]; then
            echo -n "."
        fi
        
        # 요청 실행 및 시간 측정
        start_time=$(date +%s%3N)
        response=$(curl -w "%{http_code}:%{time_total}" -o /dev/null2>/dev/null \
                      -H "Content-Type: application/json" \
                      -d "$test_data" \
                      -s "http://localhost:8080$endpoint" 2>/dev/null || echo "000:0")
        end_time=$(date +%s%3N)
        
        # 응답 파싱
        http_code=$(echo $response | cut -d':' -f1)
        response_time_sec=$(echo $response | cut -d':' -f2)
        response_time_ms=$(echo "$response_time_sec * 1000" | bc -l 2>/dev/null || echo "0")
        response_time_ms=${response_time_ms%.*} # 소수점 제거
        
        # 성공한 요청만 계산
        if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            success_count=$((success_count + 1))
            total_time=$((total_time + response_time_ms))
            times+=($response_time_ms)
            
            # 최소/최대 시간 업데이트
            if [ $response_time_ms -lt $min_time ]; then
                min_time=$response_time_ms
            fi
            if [ $response_time_ms -gt $max_time ]; then
                max_time=$response_time_ms
            fi
        fi
        
        # 요청 간 짧은 지연
        sleep 0.01
    done
    
    echo "" # 줄바꿈
    
    # 결과 계산
    if [ $success_count -gt 0 ]; then
        local avg_time=$((total_time / success_count))
        local success_rate=$((success_count * 100 / requests))
        
        # 백분위수 계산 (간단한 근사치)
        if command -v bc &> /dev/null && [ ${#times[@]} -gt 0 ]; then
            # 배열 정렬
            IFS=$'\n' sorted_times=($(sort -n <<<"${times[*]}"))
            unset IFS
            
            local p50_index=$((${#sorted_times[@]} * 50 / 100))
            local p95_index=$((${#sorted_times[@]} * 95 / 100))
            local p99_index=$((${#sorted_times[@]} * 99 / 100))
            
            local p50=${sorted_times[$p50_index]:-$avg_time}
            local p95=${sorted_times[$p95_index]:-$avg_time}
            local p99=${sorted_times[$p99_index]:-$max_time}
        else
            local p50=$avg_time
            local p95=$max_time
            local p99=$max_time
        fi
        
        echo "결과:"
        echo "  성공률: $success_rate% ($success_count/$requests)"
        echo "  평균 응답시간: ${avg_time}ms"
        echo "  최소 응답시간: ${min_time}ms"
        echo "  최대 응답시간: ${max_time}ms"
        echo "  P50 (중간값): ${p50}ms"
        echo "  P95: ${p95}ms"
        echo "  P99: ${p99}ms"
        
        # 성능 등급
        local grade="C"
        if [ $avg_time -lt 50 ]; then
            grade="A+"
        elif [ $avg_time -lt 100 ]; then
            grade="A"
        elif [ $avg_time -lt 200 ]; then
            grade="B"
        fi
        
        echo "  성능 등급: $grade"
        
        # 전역 변수에 결과 저장 (비교용)
        declare -g "${name// /_}_avg_time=$avg_time"
        declare -g "${name// /_}_success_rate=$success_rate"
        declare -g "${name// /_}_grade=$grade"
        
    else
        echo -e "${RED}❌ 모든 요청이 실패했습니다.${NC}"
        declare -g "${name// /_}_avg_time=9999"
        declare -g "${name// /_}_success_rate=0"
        declare -g "${name// /_}_grade=F"
    fi
    
    echo ""
}

# 동시 요청 테스트
concurrent_request_test() {
    local endpoint=$1
    local name=$2
    local total_requests=${3:-100}
    local concurrent_users=${4:-10}
    
    echo -e "${BLUE}🔥 $name 동시 요청 테스트${NC}"
    echo "설정: $total_requests 요청, $concurrent_users 동시 사용자"
    
    local test_data='{
        "guestName": "Concurrent Test Guest",
        "roomNumber": "Room 202",
        "checkInDate": "2024-12-26",
        "checkOutDate": "2024-12-28",
        "totalAmount": 300.0
    }'
    
    # 임시 파일로 결과 수집
    local temp_dir=$(mktemp -d)
    local start_time=$(date +%s)
    
    # 동시 요청 실행
    for i in $(seq 1 $concurrent_users); do
        {
            local user_requests=$((total_requests / concurrent_users))
            local success=0
            local total_time=0
            
            for j in $(seq 1 $user_requests); do
                response_time=$(curl -w "%{time_total}" -o /dev/null -s \
                               -H "Content-Type: application/json" \
                               -d "$test_data" \
                               "http://localhost:8080$endpoint" 2>/dev/null || echo "0")
                
                if [ "$?" -eq 0 ]; then
                    success=$((success + 1))
                    time_ms=$(echo "$response_time * 1000" | bc -l 2>/dev/null | cut -d'.' -f1)
                    total_time=$((total_time + time_ms))
                fi
                
                sleep 0.01
            done
            
            echo "$success $total_time" > "$temp_dir/user_$i.result"
        } &
    done
    
    # 모든 백그라운드 작업 완료 대기
    wait
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - start_time))
    
    # 결과 집계
    local total_success=0
    local total_response_time=0
    
    for result_file in "$temp_dir"/*.result; do
        if [ -f "$result_file" ]; then
            read success time < "$result_file"
            total_success=$((total_success + success))
            total_response_time=$((total_response_time + time))
        fi
    done
    
    # 임시 디렉토리 정리
    rm -rf "$temp_dir"
    
    if [ $total_success -gt 0 ]; then
        local avg_response_time=$((total_response_time / total_success))
        local success_rate=$((total_success * 100 / total_requests))
        local throughput=$((total_success / total_duration))
        
        echo "동시 요청 결과:"
        echo "  총 실행 시간: ${total_duration}초"
        echo "  성공률: $success_rate% ($total_success/$total_requests)"
        echo "  평균 응답시간: ${avg_response_time}ms"
        echo "  처리량: ${throughput} RPS"
        
        # 확장성 평가
        if [ -n "${!name// /_}_avg_time" ]; then
            local single_avg=${name// /_}_avg_time
            local scalability=$((avg_response_time * 100 / ${!single_avg}))
            echo "  확장성 지수: ${scalability}% (단일 요청 대비)"
        fi
    else
        echo -e "${RED}❌ 동시 요청 테스트 실패${NC}"
    fi
    
    echo ""
}

# 비교 분석 출력
print_comparison_analysis() {
    echo -e "${CYAN}📈 종합 비교 분석${NC}"
    echo "=" * 60
    
    # 성능 순위
    echo "🏆 응답시간 순위:"
    declare -a endpoints=(
        "Kotlin_MVC:$Kotlin_MVC_avg_time"
        "Kotlin_WebFlux:$Kotlin_WebFlux_avg_time"
        "Java_MVC:$Java_MVC_avg_time"
        "Java_WebFlux:$Java_WebFlux_avg_time"
    )
    
    # 정렬 (간단한 버블 정렬)
    for endpoint in "${endpoints[@]}"; do
        if [ -n "${endpoint}" ]; then
            name=$(echo $endpoint | cut -d':' -f1)
            time=$(echo $endpoint | cut -d':' -f2)
            echo "  $name: ${time}ms"
        fi
    done
    
    echo ""
    echo "🔍 기술별 비교:"
    
    # MVC vs WebFlux
    if [ -n "$Kotlin_MVC_avg_time" ] && [ -n "$Kotlin_WebFlux_avg_time" ]; then
        if [ $Kotlin_MVC_avg_time -lt $Kotlin_WebFlux_avg_time ]; then
            local diff=$((Kotlin_WebFlux_avg_time - Kotlin_MVC_avg_time))
            local percentage=$((diff * 100 / Kotlin_WebFlux_avg_time))
            echo "  📊 MVC vs WebFlux (Kotlin): MVC가 ${percentage}% 빠름"
        else
            local diff=$((Kotlin_MVC_avg_time - Kotlin_WebFlux_avg_time))
            local percentage=$((diff * 100 / Kotlin_MVC_avg_time))
            echo "  📊 MVC vs WebFlux (Kotlin): WebFlux가 ${percentage}% 빠름"
        fi
    fi
    
    # Kotlin vs Java
    if [ -n "$Kotlin_MVC_avg_time" ] && [ -n "$Java_MVC_avg_time" ]; then
        if [ $Kotlin_MVC_avg_time -lt $Java_MVC_avg_time ]; then
            local diff=$((Java_MVC_avg_time - Kotlin_MVC_avg_time))
            local percentage=$((diff * 100 / Java_MVC_avg_time))
            echo "  🔤 Kotlin vs Java (MVC): Kotlin이 ${percentage}% 빠름"
        else
            local diff=$((Kotlin_MVC_avg_time - Java_MVC_avg_time))
            local percentage=$((diff * 100 / Kotlin_MVC_avg_time))
            echo "  🔤 Kotlin vs Java (MVC): Java가 ${percentage}% 빠름"
        fi
    fi
    
    echo ""
    echo "💡 권장사항:"
    echo "  - 높은 동시성이 필요한 경우: WebFlux 권장"
    echo "  - 단순한 CRUD 작업: MVC 권장"  
    echo "  - 개발 생산성 중시: Kotlin 권장"
    echo "  - 성능 최적화 중시: 상황에 따라 선택"
}

# 실시간 모니터링 모드
real_time_monitoring() {
    echo -e "${CYAN}📊 실시간 성능 모니터링 시작${NC}"
    
    # 백그라운드에서 애플리케이션 실행
    if ! pgrep -f "reservation" > /dev/null; then
        start_application
    fi
    
    # 실시간 모니터링 실행
    ./gradlew bootRun --args="--real-time-monitor" &
    MONITOR_PID=$!
    
    echo "실시간 모니터링이 시작되었습니다."
    echo "Ctrl+C를 눌러 모니터링을 중단하세요."
    
    # 신호 처리
    trap 'kill $MONITOR_PID 2>/dev/null; kill $APP_PID 2>/dev/null; exit 0' SIGINT SIGTERM
    
    wait $MONITOR_PID
}

# 메인 실행 로직
main() {
    check_dependencies
    
    case $MODE in
        "comparison")
            start_application
            
            echo -e "${BLUE}🎯 API 응답시간 비교 테스트 시작${NC}"
            echo ""
            
            # 각 엔드포인트 테스트
            measure_endpoint_response_time "/api/reservations" "Kotlin MVC" 50
            measure_endpoint_response_time "/api/webflux/reservations" "Kotlin WebFlux" 50
            measure_endpoint_response_time "/api/java/reservations" "Java MVC" 50
            measure_endpoint_response_time "/api/webflux-java/reservations" "Java WebFlux" 50
            
            # 비교 분석
            print_comparison_analysis
            
            # 동시 요청 테스트 (선택적)
            echo -e "${YELLOW}동시 요청 테스트를 실행하시겠습니까? (y/N): ${NC}"
            read -t 10 -n 1 response
            echo ""
            if [[ $response =~ ^[Yy]$ ]]; then
                concurrent_request_test "/api/reservations" "Kotlin MVC" 100 10
                concurrent_request_test "/api/webflux/reservations" "Kotlin WebFlux" 100 10
            fi
            
            # 애플리케이션 종료
            kill $APP_PID 2>/dev/null || true
            ;;
            
        "monitor")
            real_time_monitoring
            ;;
            
        "quick")
            start_application
            
            echo -e "${BLUE}⚡ 빠른 응답시간 체크${NC}"
            measure_endpoint_response_time "/api/reservations" "Kotlin MVC" 10
            measure_endpoint_response_time "/api/webflux/reservations" "Kotlin WebFlux" 10
            
            kill $APP_PID 2>/dev/null || true
            ;;
            
        *)
            echo -e "${RED}❌ 알 수 없는 모드: $MODE${NC}"
            echo ""
            echo "사용법: $0 [mode]"
            echo "  mode:"
            echo "    comparison  - 전체 비교 테스트 (기본값)"
            echo "    monitor     - 실시간 모니터링"
            echo "    quick       - 빠른 테스트"
            exit 1
            ;;
    esac
    
    echo ""
    echo -e "${GREEN}🎉 테스트 완료!${NC}"
}

# 스크립트 실행
main "$@"