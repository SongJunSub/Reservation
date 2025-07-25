#!/bin/bash

# 성능 벤치마크 실행 스크립트
# Usage: ./scripts/benchmark.sh [test-type]

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 로고 출력
echo -e "${BLUE}"
echo "  ____                  _                          "
echo " |  _ \ ___  ___  ___ _ __| |__   ___ _ __   ___ ___  "
echo " | |_) / _ \/ __|/ _ \ '__| '_ \ / _ \ '_ \ / __/ _ \ "
echo " |  _ <  __/\__ \  __/ |  | |_) |  __/ | | | (_|  __/"
echo " |_| \_\___||___/\___|_|  |_.__/ \___|_| |_|\___\___|"
echo -e "${NC}"
echo -e "${BLUE}🚀 Reservation System Performance Benchmark${NC}"
echo "=============================================="

# 테스트 타입 확인
TEST_TYPE=${1:-all}

# Java 버전 확인
echo -e "${YELLOW}📋 시스템 정보${NC}"
echo "Java Version: $(java -version 2>&1 | head -n 1)"
echo "Kotlin Version: $(kotlin -version 2>&1 | grep -o 'Kotlin/[0-9.]*')"
echo "Available CPU Cores: $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 'Unknown')"
echo "Available Memory: $(free -h 2>/dev/null | grep '^Mem:' | awk '{print $2}' || echo 'Unknown')"
echo ""

# 애플리케이션 빌드
echo -e "${YELLOW}🔨 애플리케이션 빌드 중...${NC}"
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 빌드 실패${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 빌드 완료${NC}"
echo ""

# 테스트 함수들
run_internal_benchmark() {
    echo -e "${YELLOW}📊 내부 벤치마크 실행 중...${NC}"
    ./gradlew bootRun --args="--benchmark" &
    APP_PID=$!
    
    # 애플리케이션 시작 대기
    sleep 10
    
    # 벤치마크 완료 대기
    wait $APP_PID
    
    echo -e "${GREEN}✅ 내부 벤치마크 완료${NC}"
}

run_load_test() {
    echo -e "${YELLOW}🎯 부하 테스트 실행 중...${NC}"
    
    # 애플리케이션 백그라운드 실행
    ./gradlew bootRun &
    APP_PID=$!
    
    # 애플리케이션 시작 대기
    echo "애플리케이션 시작 대기 중..."
    sleep 20
    
    # 헬스 체크
    for i in {1..30}; do
        if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
            echo -e "${GREEN}✅ 애플리케이션 준비 완료${NC}"
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "${RED}❌ 애플리케이션 시작 실패${NC}"
            kill $APP_PID 2>/dev/null || true
            exit 1
        fi
        sleep 2
    done
    
    # 부하 테스트 실행
    SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun &
    LOADTEST_PID=$!
    
    # 부하 테스트 완료 대기
    wait $LOADTEST_PID
    
    # 애플리케이션 종료
    kill $APP_PID 2>/dev/null || true
    wait $APP_PID 2>/dev/null || true
    
    echo -e "${GREEN}✅ 부하 테스트 완료${NC}"
}

run_curl_benchmark() {
    echo -e "${YELLOW}🌐 cURL 벤치마크 실행 중...${NC}"
    
    # 애플리케이션 백그라운드 실행
    ./gradlew bootRun &
    APP_PID=$!
    
    # 애플리케이션 시작 대기
    sleep 20
    
    # 헬스 체크
    for i in {1..30}; do
        if curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "${RED}❌ 애플리케이션 시작 실패${NC}"
            kill $APP_PID 2>/dev/null || true
            exit 1
        fi
        sleep 2
    done
    
    # 테스트 데이터 준비
    TEST_DATA='{
        "guestName": "Benchmark Guest",
        "roomNumber": "Room 101",
        "checkInDate": "2024-12-25",
        "checkOutDate": "2024-12-27",
        "totalAmount": 250.0
    }'
    
    echo -e "${BLUE}🔄 각 엔드포인트별 성능 테스트${NC}"
    echo ""
    
    # MVC 엔드포인트 테스트
    echo "1️⃣ Kotlin MVC API 테스트:"
    curl -w "응답시간: %{time_total}초 | HTTP 상태: %{http_code} | 크기: %{size_download}바이트\n" \
         -o /dev/null -s \
         -H "Content-Type: application/json" \
         -d "$TEST_DATA" \
         http://localhost:8080/api/reservations
    
    # WebFlux 엔드포인트 테스트
    echo "2️⃣ Kotlin WebFlux API 테스트:"
    curl -w "응답시간: %{time_total}초 | HTTP 상태: %{http_code} | 크기: %{size_download}바이트\n" \
         -o /dev/null -s \
         -H "Content-Type: application/json" \
         -d "$TEST_DATA" \
         http://localhost:8080/api/webflux/reservations
    
    # Java MVC 엔드포인트 테스트
    echo "3️⃣ Java MVC API 테스트:"
    curl -w "응답시간: %{time_total}초 | HTTP 상태: %{http_code} | 크기: %{size_download}바이트\n" \
         -o /dev/null -s \
         -H "Content-Type: application/json" \
         -d "$TEST_DATA" \
         http://localhost:8080/api/java/reservations
    
    # Java WebFlux 엔드포인트 테스트
    echo "4️⃣ Java WebFlux API 테스트:"
    curl -w "응답시간: %{time_total}초 | HTTP 상태: %{http_code} | 크기: %{size_download}바이트\n" \
         -o /dev/null -s \
         -H "Content-Type: application/json" \
         -d "$TEST_DATA" \
         http://localhost:8080/api/webflux-java/reservations
    
    echo ""
    
    # 연속 요청 테스트 (간단한 부하 테스트)
    echo -e "${BLUE}🔥 연속 요청 테스트 (100회)${NC}"
    
    endpoints=(
        "api/reservations:Kotlin-MVC"
        "api/webflux/reservations:Kotlin-WebFlux"
        "api/java/reservations:Java-MVC"
        "api/webflux-java/reservations:Java-WebFlux"
    )
    
    for endpoint_info in "${endpoints[@]}"; do
        IFS=':' read -r endpoint name <<< "$endpoint_info"
        echo ""
        echo "📊 $name 연속 요청 테스트:"
        
        start_time=$(date +%s.%N)
        success_count=0
        
        for i in {1..100}; do
            response=$(curl -w "%{http_code}" -o /dev/null -s \
                      -H "Content-Type: application/json" \
                      -d "$TEST_DATA" \
                      "http://localhost:8080/$endpoint")
            
            if [ "$response" = "201" ] || [ "$response" = "200" ]; then
                ((success_count++))
            fi
            
            # 진행률 표시 (매 20회마다)
            if [ $((i % 20)) -eq 0 ]; then
                echo -n "."
            fi
        done
        
        end_time=$(date +%s.%N)
        total_time=$(echo "$end_time - $start_time" | bc)
        requests_per_second=$(echo "scale=2; 100 / $total_time" | bc)
        success_rate=$(echo "scale=2; $success_count * 100 / 100" | bc)
        
        echo ""
        echo "  성공률: $success_rate% ($success_count/100)"
        echo "  총 시간: ${total_time}초"
        echo "  초당 요청: $requests_per_second RPS"
    done
    
    # 애플리케이션 종료
    kill $APP_PID 2>/dev/null || true
    wait $APP_PID 2>/dev/null || true
    
    echo ""
    echo -e "${GREEN}✅ cURL 벤치마크 완료${NC}"
}

# 메인 실행 로직
case $TEST_TYPE in
    "internal")
        run_internal_benchmark
        ;;
    "load")
        run_load_test
        ;;
    "curl")
        run_curl_benchmark
        ;;
    "all")
        echo -e "${BLUE}🎯 전체 벤치마크 실행${NC}"
        echo ""
        run_internal_benchmark
        echo ""
        run_curl_benchmark
        ;;
    *)
        echo -e "${RED}❌ 알 수 없는 테스트 타입: $TEST_TYPE${NC}"
        echo ""
        echo "사용법: $0 [test-type]"
        echo "  test-type:"
        echo "    internal  - 내부 벤치마크만 실행"
        echo "    load      - 부하 테스트만 실행"
        echo "    curl      - cURL 벤치마크만 실행"
        echo "    all       - 모든 테스트 실행 (기본값)"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}🎉 벤치마크 완료!${NC}"
echo -e "${BLUE}💡 자세한 성능 분석은 애플리케이션 로그를 확인하세요.${NC}"