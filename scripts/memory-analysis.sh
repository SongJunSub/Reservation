#!/bin/bash

# 메모리 분석 및 프로파일링 스크립트
# Usage: ./scripts/memory-analysis.sh [mode]

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# 로고 출력
echo -e "${PURPLE}"
echo "  __  __                                  "
echo " |  \/  | ___ _ __ ___   ___  _ __ _   _   "
echo " | |\/| |/ _ \ '_ \` _ \ / _ \| '__| | | |  "
echo " | |  | |  __/ | | | | | (_) | |  | |_| |  "
echo " |_|  |_|\___|_| |_| |_|\___/|_|   \__, |  "
echo "                                  |___/   "
echo "     _                _           _       "
echo "    / \   _ __   __ _| |_   _ ___(_)___   "
echo "   / _ \ | '_ \ / _\` | | | | / __| / __|  "
echo "  / ___ \| | | | (_| | | |_| \__ \ \__ \  "
echo " /_/   \_\_| |_|\__,_|_|\__, |___/_|___/  "
echo "                       |___/             "
echo -e "${NC}"
echo -e "${PURPLE}🧠 Memory Analysis & Profiling Tool${NC}"
echo "=============================================="

# 모드 확인
MODE=${1:-profile}

# 유틸리티 함수들
check_java_tools() {
    echo -e "${YELLOW}🔍 Java 분석 도구 확인 중...${NC}"
    
    # jstat 확인
    if command -v jstat &> /dev/null; then
        JSTAT_AVAILABLE=true
        echo "  ✅ jstat 사용 가능"
    else
        JSTAT_AVAILABLE=false
        echo "  ❌ jstat 사용 불가"
    fi
    
    # jmap 확인
    if command -v jmap &> /dev/null; then
        JMAP_AVAILABLE=true
        echo "  ✅ jmap 사용 가능"
    else
        JMAP_AVAILABLE=false
        echo "  ❌ jmap 사용 불가"
    fi
    
    # jcmd 확인
    if command -v jcmd &> /dev/null; then
        JCMD_AVAILABLE=true
        echo "  ✅ jcmd 사용 가능"
    else
        JCMD_AVAILABLE=false
        echo "  ❌ jcmd 사용 불가"
    fi
    
    echo ""
}

start_application_with_memory_options() {
    echo -e "${YELLOW}🚀 메모리 분석 옵션으로 애플리케이션 시작 중...${NC}"
    
    # 기존 프로세스 종료
    if pgrep -f "reservation" > /dev/null; then
        echo "기존 애플리케이션 프로세스를 종료합니다..."
        pkill -f "reservation" || true
        sleep 3
    fi
    
    # 메모리 분석용 JVM 옵션
    MEMORY_JVM_OPTS=(
        "-Xmx1g"                              # 최대 힙 크기
        "-Xms512m"                            # 초기 힙 크기
        "-XX:+UseG1GC"                        # G1 GC 사용
        "-XX:+PrintGCDetails"                 # GC 상세 로그
        "-XX:+PrintGCTimeStamps"              # GC 타임스탬프
        "-XX:+PrintGCApplicationStoppedTime"  # GC 중단 시간
        "-XX:+HeapDumpOnOutOfMemoryError"     # OOM 시 힙 덤프
        "-XX:HeapDumpPath=./heap-dumps/"      # 힙 덤프 경로
        "-XX:+PrintStringDeduplicationStatistics" # 문자열 중복 제거 통계
        "-Xloggc:gc.log"                      # GC 로그 파일
    )
    
    # 힙 덤프 디렉토리 생성
    mkdir -p heap-dumps
    
    # 애플리케이션 빌드
    echo "애플리케이션 빌드 중..."
    ./gradlew clean build -x test -q
    
    # JVM 옵션을 환경변수로 설정
    export JAVA_OPTS="${MEMORY_JVM_OPTS[*]}"
    
    # 백그라운드에서 애플리케이션 실행
    ./gradlew bootRun > app-memory.log 2>&1 &
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

# 실시간 메모리 모니터링
real_time_memory_monitoring() {
    echo -e "${CYAN}📊 실시간 메모리 모니터링 시작${NC}"
    
    if [ "$JSTAT_AVAILABLE" = false ]; then
        echo -e "${YELLOW}⚠️ jstat을 사용할 수 없어 제한된 모니터링만 가능합니다.${NC}"
        return
    fi
    
    echo "프로세스 ID: $APP_PID"
    echo "모니터링 시작... (Ctrl+C로 중단)"
    echo ""
    
    # 헤더 출력
    printf "%-8s %-10s %-10s %-10s %-8s %-8s %-8s %-8s\n" \
           "TIME" "HEAP_USED" "HEAP_MAX" "HEAP_UTIL" "GC_COUNT" "GC_TIME" "THREADS" "CPU%"
    echo "------------------------------------------------------------------------"
    
    # 이전 GC 통계를 저장할 변수
    PREV_YGC=0
    PREV_FGC=0
    PREV_YGCT=0.0
    PREV_FGCT=0.0
    
    while true; do
        if ! kill -0 $APP_PID 2>/dev/null; then
            echo -e "${RED}애플리케이션이 종료되었습니다.${NC}"
            break
        fi
        
        # jstat으로 메모리 정보 수집
        if [ "$JSTAT_AVAILABLE" = true ]; then
            JSTAT_OUTPUT=$(jstat -gc $APP_PID 2>/dev/null || echo "")
            
            if [ -n "$JSTAT_OUTPUT" ]; then
                # jstat 출력 파싱 (헤더 제외)
                JSTAT_DATA=$(echo "$JSTAT_OUTPUT" | tail -n 1)
                
                # jstat 필드 파싱 (S0C S1C S0U S1U EC EU OC OU MC MU CCSC CCSU YGC YGCT FGC FGCT GCT)
                read -r S0C S1C S0U S1U EC EU OC OU MC MU CCSC CCSU YGC YGCT FGC FGCT GCT <<< "$JSTAT_DATA"
                
                # 힙 사용량 계산 (KB 단위)
                HEAP_USED=$(echo "scale=0; ($S0U + $S1U + $EU + $OU) / 1024" | bc 2>/dev/null || echo "0")
                HEAP_MAX=$(echo "scale=0; ($S0C + $S1C + $EC + $OC) / 1024" | bc 2>/dev/null || echo "1")
                HEAP_UTIL=$(echo "scale=1; $HEAP_USED * 100 / $HEAP_MAX" | bc 2>/dev/null || echo "0")
                
                # GC 증가량 계산
                GC_COUNT_DELTA=$((YGC - PREV_YGC + FGC - PREV_FGC))
                GC_TIME_DELTA=$(echo "scale=3; ($YGCT - $PREV_YGCT) + ($FGCT - $PREV_FGCT)" | bc 2>/dev/null || echo "0")
                
                PREV_YGC=$YGC
                PREV_FGC=$FGC
                PREV_YGCT=$YGCT
                PREV_FGCT=$FGCT
            else
                HEAP_USED="N/A"
                HEAP_MAX="N/A"
                HEAP_UTIL="N/A"
                GC_COUNT_DELTA="N/A"
                GC_TIME_DELTA="N/A"
            fi
        fi
        
        # 시스템 정보 수집
        if command -v ps &> /dev/null; then
            PS_OUTPUT=$(ps -p $APP_PID -o pid,pcpu,nlwp --no-headers 2>/dev/null || echo "")
            if [ -n "$PS_OUTPUT" ]; then
                read -r PID CPU_PERCENT THREAD_COUNT <<< "$PS_OUTPUT"
            else
                CPU_PERCENT="N/A"
                THREAD_COUNT="N/A"
            fi
        else
            CPU_PERCENT="N/A"
            THREAD_COUNT="N/A"
        fi
        
        # 현재 시간
        CURRENT_TIME=$(date '+%H:%M:%S')
        
        # 결과 출력
        printf "%-8s %-10s %-10s %-8s%% %-8s %-8s %-8s %-8s%%\n" \
               "$CURRENT_TIME" "${HEAP_USED}MB" "${HEAP_MAX}MB" "$HEAP_UTIL" \
               "$GC_COUNT_DELTA" "$GC_TIME_DELTA" "$THREAD_COUNT" "$CPU_PERCENT"
        
        sleep 5
    done
}

# 메모리 힙 덤프 생성
generate_heap_dump() {
    echo -e "${BLUE}📸 메모리 힙 덤프 생성 중...${NC}"
    
    if [ "$JMAP_AVAILABLE" = false ]; then
        echo -e "${YELLOW}⚠️ jmap을 사용할 수 없어 힙 덤프를 생성할 수 없습니다.${NC}"
        return
    fi
    
    local dump_file="heap-dumps/heap-dump-$(date +%Y%m%d-%H%M%S).hprof"
    
    echo "힙 덤프 생성 중... (시간이 오래 걸릴 수 있습니다)"
    if jmap -dump:format=b,file="$dump_file" $APP_PID; then
        echo -e "${GREEN}✅ 힙 덤프 생성 완료: $dump_file${NC}"
        
        # 힙 덤프 파일 크기 표시
        if [ -f "$dump_file" ]; then
            local file_size=$(du -h "$dump_file" | cut -f1)
            echo "힙 덤프 파일 크기: $file_size"
        fi
        
        echo ""
        echo "힙 덤프 분석 도구:"
        echo "  - Eclipse MAT (Memory Analyzer Tool)"
        echo "  - VisualVM"
        echo "  - JProfiler"
        echo "  - jhat (기본 제공, 간단한 분석용)"
        
    else
        echo -e "${RED}❌ 힙 덤프 생성 실패${NC}"
    fi
}

# GC 분석
analyze_gc_performance() {
    echo -e "${GREEN}🗑️ GC 성능 분석${NC}"
    
    if [ ! -f "gc.log" ]; then
        echo -e "${YELLOW}⚠️ GC 로그 파일이 없습니다.${NC}"
        return
    fi
    
    echo "GC 로그 분석 결과:"
    echo "-" * 40
    
    # GC 로그 기본 통계
    local total_gc_events=$(grep -c "GC(" gc.log 2>/dev/null || echo "0")
    local young_gc_events=$(grep -c "GC(.*) Pause Young" gc.log 2>/dev/null || echo "0")
    local mixed_gc_events=$(grep -c "GC(.*) Pause Mixed" gc.log 2>/dev/null || echo "0")
    local full_gc_events=$(grep -c "GC(.*) Pause Full" gc.log 2>/dev/null || echo "0")
    
    echo "GC 이벤트 통계:"
    echo "  총 GC 이벤트: $total_gc_events"
    echo "  Young GC: $young_gc_events"
    echo "  Mixed GC: $mixed_gc_events"
    echo "  Full GC: $full_gc_events"
    
    # GC 시간 분석
    if command -v awk &> /dev/null; then
        local avg_pause_time=$(awk '/Pause/ {sum+=$NF; count++} END {if(count>0) print sum/count; else print 0}' gc.log 2>/dev/null || echo "0")
        local max_pause_time=$(awk '/Pause/ {if($NF>max) max=$NF} END {print max+0}' gc.log 2>/dev/null || echo "0")
        
        echo ""
        echo "GC 성능:"
        echo "  평균 일시정지 시간: ${avg_pause_time}ms"
        echo "  최대 일시정지 시간: ${max_pause_time}ms"
        
        # GC 성능 평가
        if (( $(echo "$avg_pause_time < 10" | bc -l 2>/dev/null || echo "0") )); then
            echo "  성능 평가: 우수 (< 10ms)"
        elif (( $(echo "$avg_pause_time < 50" | bc -l 2>/dev/null || echo "0") )); then
            echo "  성능 평가: 양호 (< 50ms)"
        elif (( $(echo "$avg_pause_time < 100" | bc -l 2>/dev/null || echo "0") )); then
            echo "  성능 평가: 보통 (< 100ms)"
        else
            echo "  성능 평가: 개선 필요 (>= 100ms)"
        fi
    fi
    
    echo ""
    echo "GC 로그 파일 위치: gc.log"
    echo "상세 분석을 위해 GCViewer 또는 GCPlot 사용을 권장합니다."
}

# 메모리 프로파일링 실행
run_memory_profiling() {
    echo -e "${CYAN}🔬 메모리 프로파일링 실행${NC}"
    
    # 내장 메모리 프로파일러 실행
    ./gradlew bootRun --args="--memory-profiling" &
    PROFILER_PID=$!
    
    echo "메모리 프로파일링이 시작되었습니다."
    echo "프로파일링 완료까지 약 5-10분 소요됩니다..."
    
    wait $PROFILER_PID
    
    echo -e "${GREEN}✅ 메모리 프로파일링 완료${NC}"
}

# 메모리 누수 감지 실행
run_memory_leak_detection() {
    echo -e "${RED}🕵️ 메모리 누수 감지 실행${NC}"
    
    # 내장 메모리 누수 감지기 실행
    ./gradlew bootRun --args="--memory-leak-detection" &
    DETECTOR_PID=$!
    
    echo "메모리 누수 감지가 시작되었습니다."
    echo "감지 완료까지 약 3-5분 소요됩니다..."
    
    wait $DETECTOR_PID
    
    echo -e "${GREEN}✅ 메모리 누수 감지 완료${NC}"
}

# 종합 메모리 분석
comprehensive_memory_analysis() {
    echo -e "${PURPLE}🧠 종합 메모리 분석 시작${NC}"
    
    start_application_with_memory_options
    
    echo ""
    echo "=== 분석 단계 ==="
    echo "1. 실시간 모니터링 (60초)"
    echo "2. 힙 덤프 생성"
    echo "3. GC 성능 분석"
    echo "4. 메모리 프로파일링"
    echo "5. 메모리 누수 감지"
    echo ""
    
    # 1. 실시간 모니터링 (제한된 시간)
    echo -e "${CYAN}1단계: 실시간 모니터링${NC}"
    timeout 60s bash -c "real_time_memory_monitoring" || echo "모니터링 시간 완료"
    
    echo ""
    
    # 2. 힙 덤프 생성
    echo -e "${CYAN}2단계: 힙 덤프 생성${NC}"
    generate_heap_dump
    
    echo ""
    
    # 3. GC 분석
    echo -e "${CYAN}3단계: GC 성능 분석${NC}"
    analyze_gc_performance
    
    echo ""
    
    # 애플리케이션 종료
    kill $APP_PID 2>/dev/null || true
    wait $APP_PID 2>/dev/null || true
    
    # 4. 메모리 프로파일링
    echo -e "${CYAN}4단계: 메모리 프로파일링${NC}"
    run_memory_profiling
    
    echo ""
    
    # 5. 메모리 누수 감지
    echo -e "${CYAN}5단계: 메모리 누수 감지${NC}"
    run_memory_leak_detection
    
    echo ""
    echo -e "${GREEN}🎉 종합 메모리 분석 완료!${NC}"
    
    # 결과 요약
    print_analysis_summary
}

# 분석 결과 요약
print_analysis_summary() {
    echo ""
    echo -e "${PURPLE}📋 분석 결과 요약${NC}"
    echo "=" * 50
    
    echo "생성된 파일들:"
    [ -f "gc.log" ] && echo "  - GC 로그: gc.log"
    [ -f "app-memory.log" ] && echo "  - 애플리케이션 로그: app-memory.log"
    
    if [ -d "heap-dumps" ] && [ "$(ls -A heap-dumps)" ]; then
        echo "  - 힙 덤프: heap-dumps/"
        ls -la heap-dumps/ | tail -n +2 | while read -r line; do
            echo "    └─ $line"
        done
    fi
    
    echo ""
    echo "추가 분석 도구 추천:"
    echo "  - Eclipse MAT: 힙 덤프 분석"
    echo "  - VisualVM: 통합 프로파일링"
    echo "  - GCViewer: GC 로그 시각화"
    echo "  - JProfiler: 상용 프로파일러"
    
    echo ""
    echo "온라인 도구:"
    echo "  - GCPlot: https://gcplot.com"
    echo "  - Eclipse MAT: https://www.eclipse.org/mat/"
}

# 메인 실행 로직
main() {
    check_java_tools
    
    case $MODE in
        "profile")
            start_application_with_memory_options
            run_memory_profiling
            kill $APP_PID 2>/dev/null || true
            ;;
            
        "leak")
            run_memory_leak_detection
            ;;
            
        "monitor")
            start_application_with_memory_options
            trap 'kill $APP_PID 2>/dev/null; exit 0' SIGINT SIGTERM
            real_time_memory_monitoring
            ;;
            
        "heapdump")
            start_application_with_memory_options
            generate_heap_dump
            kill $APP_PID 2>/dev/null || true
            ;;
            
        "gc")
            start_application_with_memory_options
            sleep 30  # 충분한 GC 데이터 수집을 위한 대기
            analyze_gc_performance
            kill $APP_PID 2>/dev/null || true
            ;;
            
        "comprehensive")
            comprehensive_memory_analysis
            ;;
            
        *)
            echo -e "${RED}❌ 알 수 없는 모드: $MODE${NC}"
            echo ""
            echo "사용법: $0 [mode]"
            echo "  mode:"
            echo "    profile       - 메모리 프로파일링 실행"
            echo "    leak          - 메모리 누수 감지"
            echo "    monitor       - 실시간 메모리 모니터링"
            echo "    heapdump      - 힙 덤프 생성"
            echo "    gc            - GC 성능 분석"
            echo "    comprehensive - 종합 분석 (기본값)"
            exit 1
            ;;
    esac
    
    echo ""
    echo -e "${GREEN}🎉 분석 완료!${NC}"
}

# 스크립트 실행
main "$@"