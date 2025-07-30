#!/bin/bash

# JVM Tuning and GC Performance Testing Automation Script
# Phase 3-1: JVM Tuning and Garbage Collection Optimization
# Created: $(date)

set -e  # Exit immediately if a command exits with a non-zero status

# =============================================================================
# Configuration Variables
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_ROOT/reports"
GRADLE_CMD="./gradlew"

# JVM Tuning Configuration
JVM_HEAP_SIZES=("1g" "2g" "4g" "8g")
GC_ALGORITHMS=("G1GC" "ParallelGC" "SerialGC")
GC_LOG_FILE="$LOG_DIR/gc-performance.log"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# =============================================================================
# Utility Functions
# =============================================================================

print_header() {
    echo -e "\n${PURPLE}================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}================================${NC}\n"
}

print_step() {
    echo -e "${BLUE}📋 Step: $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

check_prerequisites() {
    print_step "Checking JVM tuning prerequisites..."
    
    # Check if Java is available
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    
    # Check Java version
    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    print_info "Java Version: $java_version"
    
    # Check if JVM monitoring tools are available
    if command -v jstat &> /dev/null; then
        print_success "jstat available for GC monitoring"
    else
        print_warning "jstat not available. Some monitoring features may be limited."
    fi
    
    if command -v jmap &> /dev/null; then
        print_success "jmap available for heap analysis"
    else
        print_warning "jmap not available. Heap dump features may be limited."
    fi
    
    # Check if Gradle wrapper exists
    if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
        print_error "Gradle wrapper not found in project root"
        exit 1
    fi
    
    # Create reports directory
    mkdir -p "$LOG_DIR"
    
    print_success "Prerequisites check completed"
}

compile_project() {
    print_step "Compiling project for JVM tuning tests..."
    
    cd "$PROJECT_ROOT"
    
    if $GRADLE_CMD compileKotlin > /dev/null 2>&1; then
        print_success "Project compiled successfully"
    else
        print_error "Project compilation failed"
        exit 1
    fi
}

get_jvm_pid() {
    local app_name="reservation"
    local pid=$(jps -l | grep -i "$app_name" | awk '{print $1}' | head -1)
    
    if [ -n "$pid" ]; then
        echo "$pid"
    else
        echo ""
    fi
}

# =============================================================================
# GC Algorithm Performance Testing
# =============================================================================

test_gc_algorithms() {
    print_header "GC Algorithm Performance Comparison"
    
    local results_file="$LOG_DIR/gc-algorithm-comparison-$(date +%Y%m%d_%H%M%S).csv"
    
    echo "GC_Algorithm,Heap_Size,Test_Duration,GC_Count,GC_Time,Total_Time,Throughput,Avg_Pause,Max_Pause" > "$results_file"
    
    for heap_size in "${JVM_HEAP_SIZES[@]}"; do
        for gc_algo in "${GC_ALGORITHMS[@]}"; do
            print_step "Testing $gc_algo with heap size $heap_size"
            
            local gc_result=$(run_gc_test "$gc_algo" "$heap_size")
            echo "$gc_result" >> "$results_file"
            
            print_info "Result: $gc_result"
            
            # Wait between tests to avoid interference
            sleep 5
        done
    done
    
    print_success "GC algorithm comparison completed"
    print_info "Results saved to: $results_file"
    
    # Generate summary report
    generate_gc_comparison_report "$results_file"
}

run_gc_test() {
    local gc_algorithm="$1"
    local heap_size="$2"
    local test_duration=60  # 60 seconds test
    
    local jvm_flags=""
    case "$gc_algorithm" in
        "G1GC")
            jvm_flags="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=16m"
            ;;
        "ParallelGC")
            jvm_flags="-XX:+UseParallelGC -XX:ParallelGCThreads=4"
            ;;
        "SerialGC")
            jvm_flags="-XX:+UseSerialGC"
            ;;
        *)
            jvm_flags="-XX:+UseG1GC"
            ;;
    esac
    
    # Add common flags
    jvm_flags="$jvm_flags -Xms$heap_size -Xmx$heap_size"
    jvm_flags="$jvm_flags -XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
    jvm_flags="$jvm_flags -Xloggc:$GC_LOG_FILE"
    
    # Start application with specific JVM flags
    cd "$PROJECT_ROOT"
    
    local app_log="$LOG_DIR/app-${gc_algorithm}-${heap_size}-$(date +%Y%m%d_%H%M%S).log"
    
    # Run application in background
    JAVA_OPTS="$jvm_flags" timeout ${test_duration}s $GRADLE_CMD bootRun --args="gc-comparison" > "$app_log" 2>&1 &
    local app_pid=$!
    
    # Wait for application to start
    sleep 10
    
    # Get JVM process ID for monitoring
    local jvm_pid=$(get_jvm_pid)
    
    if [ -n "$jvm_pid" ]; then
        # Monitor GC during test
        monitor_gc_performance "$jvm_pid" "$gc_algorithm" "$heap_size" "$test_duration"
    else
        print_warning "Could not find JVM process for monitoring"
        # Wait for the test to complete
        wait $app_pid 2>/dev/null || true
        echo "${gc_algorithm},${heap_size},${test_duration},0,0,${test_duration}000,0,0,0"
    fi
}

monitor_gc_performance() {
    local pid="$1"
    local gc_algo="$2"
    local heap_size="$3"
    local duration="$4"
    
    local monitoring_log="$LOG_DIR/gc-monitoring-${gc_algo}-${heap_size}-$(date +%Y%m%d_%H%M%S).log"
    
    # Start GC monitoring
    local monitor_interval=5  # 5 seconds
    local iterations=$((duration / monitor_interval))
    
    echo "Timestamp,HeapUsed,HeapMax,GCCount,GCTime,YoungGCCount,OldGCCount" > "$monitoring_log"
    
    local initial_gc_count=0
    local initial_gc_time=0
    local start_time=$(date +%s)
    
    # Get initial GC statistics
    if command -v jstat &> /dev/null; then
        local initial_stats=$(jstat -gc "$pid" | tail -1)
        initial_gc_count=$(echo "$initial_stats" | awk '{print $13+$14}')  # YGC + FGC
        initial_gc_time=$(echo "$initial_stats" | awk '{print $15+$16}')   # YGCT + FGCT
    fi
    
    # Monitor for specified duration
    for ((i=1; i<=iterations; i++)); do
        if ! kill -0 "$pid" 2>/dev/null; then
            print_warning "Process $pid terminated during monitoring"
            break
        fi
        
        local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        
        if command -v jstat &> /dev/null; then
            local gc_stats=$(jstat -gc "$pid" | tail -1)
            local heap_stats=$(jstat -gccapacity "$pid" | tail -1)
            
            # Parse statistics
            local heap_used=$(echo "$gc_stats" | awk '{print ($3+$4+$6+$8)/1024}')  # Convert to MB
            local heap_max=$(echo "$heap_stats" | awk '{print $12/1024}')  # Max heap in MB
            local young_gc=$(echo "$gc_stats" | awk '{print $13}')
            local old_gc=$(echo "$gc_stats" | awk '{print $14}')
            local total_gc_count=$((young_gc + old_gc))
            local total_gc_time=$(echo "$gc_stats" | awk '{print $15+$16}')
            
            echo "$timestamp,$heap_used,$heap_max,$total_gc_count,$total_gc_time,$young_gc,$old_gc" >> "$monitoring_log"
        else
            # Fallback monitoring without jstat
            local heap_info=$(jcmd "$pid" VM.info 2>/dev/null | grep -i heap || echo "N/A")
            echo "$timestamp,N/A,N/A,N/A,N/A,N/A,N/A" >> "$monitoring_log"
        fi
        
        sleep "$monitor_interval"
    done
    
    local end_time=$(date +%s)
    local total_time=$((end_time - start_time))
    
    # Calculate final statistics
    local final_gc_count=0
    local final_gc_time=0
    
    if command -v jstat &> /dev/null && kill -0 "$pid" 2>/dev/null; then
        local final_stats=$(jstat -gc "$pid" | tail -1)
        final_gc_count=$(echo "$final_stats" | awk '{print $13+$14}')
        final_gc_time=$(echo "$final_stats" | awk '{print $15+$16}')
    fi
    
    local gc_count_diff=$((final_gc_count - initial_gc_count))
    local gc_time_diff=$(echo "$final_gc_time $initial_gc_time" | awk '{printf "%.0f", ($1-$2)*1000}')  # Convert to ms
    local throughput=$(echo "$total_time $gc_time_diff" | awk '{if($1>0) printf "%.2f", (($1*1000-$2)/($1*1000))*100; else print "0"}')
    
    local avg_pause="0"
    local max_pause="0"
    if [ "$gc_count_diff" -gt 0 ]; then
        avg_pause=$(echo "$gc_time_diff $gc_count_diff" | awk '{printf "%.2f", $1/$2}')
    fi
    
    # Parse GC log for max pause time if available
    if [ -f "$GC_LOG_FILE" ]; then
        max_pause=$(grep -oP '\d+\.\d+(?= secs\])' "$GC_LOG_FILE" 2>/dev/null | sort -n | tail -1 || echo "0")
        max_pause=$(echo "$max_pause" | awk '{printf "%.0f", $1*1000}')  # Convert to ms
    fi
    
    # Kill the application process
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
    
    echo "${gc_algo},${heap_size},${total_time},${gc_count_diff},${gc_time_diff},${total_time}000,${throughput},${avg_pause},${max_pause}"
}

# =============================================================================
# Heap Memory Tuning Tests
# =============================================================================

test_heap_tuning() {
    print_header "Heap Memory Tuning Analysis"
    
    local results_file="$LOG_DIR/heap-tuning-analysis-$(date +%Y%m%d_%H%M%S).csv"
    echo "Test_Type,Heap_Size,Young_Gen_Ratio,Memory_Used,GC_Count,GC_Time,Allocation_Rate,Memory_Efficiency" > "$results_file"
    
    # Test different heap sizes
    for heap_size in "${JVM_HEAP_SIZES[@]}"; do
        print_step "Testing heap size: $heap_size"
        
        # Test different Young Generation ratios
        local young_ratios=("2" "3" "4" "6")
        for ratio in "${young_ratios[@]}"; do
            local heap_result=$(run_heap_tuning_test "$heap_size" "$ratio")
            echo "$heap_result" >> "$results_file"
            
            print_info "Heap $heap_size, Young ratio 1:$ratio - Result: $heap_result"
            sleep 3
        done
    done
    
    print_success "Heap tuning analysis completed"
    print_info "Results saved to: $results_file"
    
    generate_heap_tuning_report "$results_file"
}

run_heap_tuning_test() {
    local heap_size="$1"
    local young_ratio="$2"
    local test_duration=45  # 45 seconds test
    
    local jvm_flags="-Xms$heap_size -Xmx$heap_size"
    jvm_flags="$jvm_flags -XX:+UseG1GC -XX:NewRatio=$young_ratio"
    jvm_flags="$jvm_flags -XX:+PrintGC -XX:+PrintGCDetails"
    
    cd "$PROJECT_ROOT"
    
    local app_log="$LOG_DIR/heap-tuning-${heap_size}-ratio${young_ratio}-$(date +%Y%m%d_%H%M%S).log"
    
    # Run heap tuning test
    JAVA_OPTS="$jvm_flags" timeout ${test_duration}s $GRADLE_CMD bootRun --args="heap-tuning" > "$app_log" 2>&1 &
    local app_pid=$!
    
    sleep 10  # Wait for startup
    
    local jvm_pid=$(get_jvm_pid)
    local memory_used="0"
    local gc_count="0"
    local gc_time="0"
    local allocation_rate="0"
    local memory_efficiency="0"
    
    if [ -n "$jvm_pid" ] && command -v jstat &> /dev/null; then
        # Monitor memory usage
        local start_stats=$(jstat -gc "$jvm_pid" | tail -1)
        local start_heap=$(echo "$start_stats" | awk '{print ($3+$4+$6+$8)/1024}')  # MB
        local start_gc_count=$(echo "$start_stats" | awk '{print $13+$14}')
        local start_gc_time=$(echo "$start_stats" | awk '{print $15+$16}')
        
        # Wait for test completion
        wait $app_pid 2>/dev/null || true
        
        # Get final statistics
        if kill -0 "$jvm_pid" 2>/dev/null; then
            local end_stats=$(jstat -gc "$jvm_pid" | tail -1)
            local end_heap=$(echo "$end_stats" | awk '{print ($3+$4+$6+$8)/1024}')
            local end_gc_count=$(echo "$end_stats" | awk '{print $13+$14}')
            local end_gc_time=$(echo "$end_stats" | awk '{print $15+$16}')
            
            memory_used=$(echo "$end_heap" | awk '{printf "%.2f", $1}')
            gc_count=$((end_gc_count - start_gc_count))
            gc_time=$(echo "$end_gc_time $start_gc_time" | awk '{printf "%.0f", ($1-$2)*1000}')
            allocation_rate=$(echo "$memory_used $test_duration" | awk '{if($2>0) printf "%.2f", $1/$2; else print "0"}')
            memory_efficiency=$(echo "$memory_used ${heap_size%g}" | awk '{if($2>0) printf "%.2f", ($1/($2*1024))*100; else print "0"}')
            
            kill "$jvm_pid" 2>/dev/null || true
        fi
    else
        wait $app_pid 2>/dev/null || true
    fi
    
    echo "heap_tuning,$heap_size,1:$young_ratio,$memory_used,$gc_count,$gc_time,$allocation_rate,$memory_efficiency"
}

# =============================================================================
# JVM Flags Optimization Testing
# =============================================================================

test_jvm_flags_optimization() {
    print_header "JVM Flags Optimization Testing"
    
    local results_file="$LOG_DIR/jvm-flags-optimization-$(date +%Y%m%d_%H%M%S).csv"
    echo "Flag_Set,Test_Type,Execution_Time,Memory_Used,GC_Count,GC_Time,Throughput,Performance_Score" > "$results_file"
    
    # Define different JVM flag configurations
    declare -A flag_sets=(
        ["baseline"]="-Xms2g -Xmx2g"
        ["g1gc_optimized"]="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:G1HeapRegionSize=16m"
        ["parallel_optimized"]="-Xms2g -Xmx2g -XX:+UseParallelGC -XX:ParallelGCThreads=4 -XX:+UseParallelOldGC"
        ["performance_tuned"]="-Xms2g -Xmx2g -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+UseCompressedOops -server"
        ["memory_optimized"]="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=10 -XX:+ShrinkHeapInSteps"
    )
    
    for flag_name in "${!flag_sets[@]}"; do
        print_step "Testing JVM flags: $flag_name"
        
        local flags="${flag_sets[$flag_name]}"
        local test_types=("jvm-flags" "stress-test" "memory-leak")
        
        for test_type in "${test_types[@]}"; do
            local flag_result=$(run_jvm_flags_test "$flag_name" "$flags" "$test_type")
            echo "$flag_result" >> "$results_file"
            
            print_info "$flag_name ($test_type): $flag_result"
            sleep 5
        done
    done
    
    print_success "JVM flags optimization testing completed"
    print_info "Results saved to: $results_file"
    
    generate_jvm_flags_report "$results_file"
}

run_jvm_flags_test() {
    local flag_name="$1"
    local jvm_flags="$2"
    local test_type="$3"
    local test_duration=60
    
    cd "$PROJECT_ROOT"
    
    local app_log="$LOG_DIR/jvm-flags-${flag_name}-${test_type}-$(date +%Y%m%d_%H%M%S).log"
    
    # Run test with specific flags
    JAVA_OPTS="$jvm_flags" timeout ${test_duration}s $GRADLE_CMD bootRun --args="$test_type" > "$app_log" 2>&1 &
    local app_pid=$!
    
    sleep 10  # Wait for startup
    
    local jvm_pid=$(get_jvm_pid)
    local execution_time="$test_duration"
    local memory_used="0"
    local gc_count="0"
    local gc_time="0"
    local throughput="0"
    local performance_score="0"
    
    if [ -n "$jvm_pid" ] && command -v jstat &> /dev/null; then
        local start_time=$(date +%s)
        local start_stats=$(jstat -gc "$jvm_pid" | tail -1)
        local start_gc_count=$(echo "$start_stats" | awk '{print $13+$14}')
        local start_gc_time=$(echo "$start_stats" | awk '{print $15+$16}')
        
        # Wait for test completion
        wait $app_pid 2>/dev/null || true
        
        local end_time=$(date +%s)
        execution_time=$((end_time - start_time))
        
        if kill -0 "$jvm_pid" 2>/dev/null; then
            local end_stats=$(jstat -gc "$jvm_pid" | tail -1)
            local end_gc_count=$(echo "$end_stats" | awk '{print $13+$14}')
            local end_gc_time=$(echo "$end_stats" | awk '{print $15+$16}')
            
            memory_used=$(echo "$end_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')  # MB
            gc_count=$((end_gc_count - start_gc_count))
            gc_time=$(echo "$end_gc_time $start_gc_time" | awk '{printf "%.0f", ($1-$2)*1000}')  # ms
            
            # Calculate throughput and performance score
            if [ "$execution_time" -gt 0 ]; then
                throughput=$(echo "$execution_time $gc_time" | awk '{printf "%.2f", (($1*1000-$2)/($1*1000))*100}')
                performance_score=$(echo "$throughput $gc_count $memory_used" | awk '{printf "%.2f", $1 - ($2*0.1) - ($3*0.01)}')
            fi
            
            kill "$jvm_pid" 2>/dev/null || true
        fi
    else
        wait $app_pid 2>/dev/null || true
    fi
    
    echo "$flag_name,$test_type,$execution_time,$memory_used,$gc_count,$gc_time,$throughput,$performance_score"
}

# =============================================================================
# Memory Leak Detection Testing
# =============================================================================

test_memory_leak_detection() {
    print_header "Memory Leak Detection Testing"
    
    local results_file="$LOG_DIR/memory-leak-detection-$(date +%Y%m%d_%H%M%S).csv"
    echo "Test_Type,Duration,Initial_Memory,Final_Memory,Memory_Growth,Leak_Detected,Risk_Level" > "$results_file"
    
    local leak_tests=("collection-leak" "listener-leak" "thread-local-leak" "cache-leak")
    
    for leak_test in "${leak_tests[@]}"; do
        print_step "Running memory leak test: $leak_test"
        
        local leak_result=$(run_memory_leak_test "$leak_test")
        echo "$leak_result" >> "$results_file"
        
        print_info "$leak_test result: $leak_result"
        sleep 10  # Allow memory to stabilize between tests
    done
    
    print_success "Memory leak detection testing completed"
    print_info "Results saved to: $results_file"
    
    generate_memory_leak_report "$results_file"
}

run_memory_leak_test() {
    local leak_type="$1"
    local test_duration=120  # 2 minutes to observe memory growth
    
    local jvm_flags="-Xms1g -Xmx4g -XX:+UseG1GC"
    jvm_flags="$jvm_flags -XX:+PrintGC -XX:+PrintGCDetails"
    
    cd "$PROJECT_ROOT"
    
    local app_log="$LOG_DIR/memory-leak-${leak_type}-$(date +%Y%m%d_%H%M%S).log"
    
    # Run memory leak test
    JAVA_OPTS="$jvm_flags" timeout ${test_duration}s $GRADLE_CMD bootRun --args="memory-leak" > "$app_log" 2>&1 &
    local app_pid=$!
    
    sleep 15  # Wait for application startup
    
    local jvm_pid=$(get_jvm_pid)
    local initial_memory="0"
    local final_memory="0"
    local memory_growth="0"
    local leak_detected="false"
    local risk_level="LOW"
    
    if [ -n "$jvm_pid" ] && command -v jstat &> /dev/null; then
        # Record initial memory usage
        local initial_stats=$(jstat -gc "$jvm_pid" | tail -1)
        initial_memory=$(echo "$initial_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')  # MB
        
        # Monitor memory growth over time
        local memory_samples=()
        local sample_interval=10  # 10 seconds
        local samples=$((test_duration / sample_interval - 1))
        
        for ((i=1; i<=samples; i++)); do
            if ! kill -0 "$jvm_pid" 2>/dev/null; then
                break
            fi
            
            sleep "$sample_interval"
            
            local current_stats=$(jstat -gc "$jvm_pid" | tail -1)
            local current_memory=$(echo "$current_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')
            memory_samples+=("$current_memory")
        done
        
        # Wait for test completion
        wait $app_pid 2>/dev/null || true
        
        # Record final memory usage
        if kill -0 "$jvm_pid" 2>/dev/null; then
            local final_stats=$(jstat -gc "$jvm_pid" | tail -1)
            final_memory=$(echo "$final_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')
            kill "$jvm_pid" 2>/dev/null || true
        else
            final_memory="${memory_samples[-1]:-$initial_memory}"
        fi
        
        # Analyze memory growth pattern
        memory_growth=$(echo "$final_memory $initial_memory" | awk '{printf "%.2f", $1-$2}')
        
        # Detect potential memory leak
        if (( $(echo "$memory_growth > 100" | bc -l) )); then  # More than 100MB growth
            leak_detected="true"
            risk_level="HIGH"
        elif (( $(echo "$memory_growth > 50" | bc -l) )); then  # More than 50MB growth
            leak_detected="true"  
            risk_level="MEDIUM"
        elif (( $(echo "$memory_growth > 20" | bc -l) )); then  # More than 20MB growth
            leak_detected="possible"
            risk_level="LOW"
        fi
        
        # Additional check: consistent upward trend
        if [ "${#memory_samples[@]}" -gt 5 ]; then
            local trend_positive=0
            for ((i=1; i<${#memory_samples[@]}; i++)); do
                if (( $(echo "${memory_samples[i]} > ${memory_samples[i-1]}" | bc -l) )); then
                    ((trend_positive++))
                fi
            done
            
            local trend_ratio=$((trend_positive * 100 / (${#memory_samples[@]} - 1)))
            if [ "$trend_ratio" -gt 70 ]; then  # 70% of samples show growth
                leak_detected="true"
                if [ "$risk_level" = "LOW" ]; then
                    risk_level="MEDIUM"
                fi
            fi
        fi
    else
        wait $app_pid 2>/dev/null || true
    fi
    
    echo "$leak_type,$test_duration,$initial_memory,$final_memory,$memory_growth,$leak_detected,$risk_level"
}

# =============================================================================
# GC Log Analysis
# =============================================================================

analyze_gc_logs() {
    print_header "GC Log Analysis and Visualization"
    
    local gc_analysis_file="$LOG_DIR/gc-log-analysis-$(date +%Y%m%d_%H%M%S).txt"
    
    print_step "Analyzing existing GC logs..."
    
    if [ -f "$GC_LOG_FILE" ]; then
        {
            echo "GC Log Analysis Report"
            echo "Generated: $(date)"
            echo "=========================="
            echo
            
            # Basic statistics
            echo "GC Log Statistics:"
            echo "------------------"
            local total_gc_events=$(grep -c "GC(" "$GC_LOG_FILE" 2>/dev/null || echo "0")
            local young_gc_events=$(grep -c "GC(.*) Pause Young" "$GC_LOG_FILE" 2>/dev/null || echo "0") 
            local mixed_gc_events=$(grep -c "GC(.*) Pause Mixed" "$GC_LOG_FILE" 2>/dev/null || echo "0")
            local full_gc_events=$(grep -c "GC(.*) Pause Full" "$GC_LOG_FILE" 2>/dev/null || echo "0")
            
            echo "Total GC Events: $total_gc_events"
            echo "Young GC Events: $young_gc_events"
            echo "Mixed GC Events: $mixed_gc_events"
            echo "Full GC Events: $full_gc_events"
            echo
            
            # Pause time analysis
            echo "Pause Time Analysis:"
            echo "-------------------"
            if command -v grep &> /dev/null && command -v awk &> /dev/null; then
                local pause_times=$(grep -oP '\d+\.\d+(?=ms\])' "$GC_LOG_FILE" 2>/dev/null || echo "")
                if [ -n "$pause_times" ]; then
                    local min_pause=$(echo "$pause_times" | sort -n | head -1)
                    local max_pause=$(echo "$pause_times" | sort -n | tail -1)
                    local avg_pause=$(echo "$pause_times" | awk '{sum+=$1; count++} END {if(count>0) printf "%.2f", sum/count; else print "0"}')
                    
                    echo "Min Pause Time: ${min_pause}ms"
                    echo "Max Pause Time: ${max_pause}ms"
                    echo "Average Pause Time: ${avg_pause}ms"
                else
                    echo "No pause time data found in log"
                fi
            fi
            echo
            
            # Memory usage patterns
            echo "Memory Usage Patterns:"
            echo "----------------------"
            local heap_before=$(grep -oP 'before \K\d+[KMG]' "$GC_LOG_FILE" 2>/dev/null | tail -5)
            local heap_after=$(grep -oP 'after \K\d+[KMG]' "$GC_LOG_FILE" 2>/dev/null | tail -5)
            
            if [ -n "$heap_before" ] && [ -n "$heap_after" ]; then
                echo "Recent heap usage (before -> after GC):"
                paste <(echo "$heap_before") <(echo "$heap_after") | while read before after; do
                    echo "  $before -> $after"
                done
            else
                echo "No detailed heap usage data found"
            fi
            echo
            
            # Recommendations
            echo "Optimization Recommendations:"
            echo "----------------------------"
            if [ "$total_gc_events" -gt 100 ]; then
                echo "• High GC frequency detected. Consider increasing heap size."
            fi
            
            if [ "$full_gc_events" -gt 5 ]; then
                echo "• Multiple Full GC events detected. Review memory allocation patterns."
            fi
            
            if command -v awk &> /dev/null && [ -n "$pause_times" ]; then
                local high_pause_count=$(echo "$pause_times" | awk '$1 > 100 {count++} END {print count+0}')
                if [ "$high_pause_count" -gt 10 ]; then
                    echo "• High pause times detected. Consider G1GC with lower MaxGCPauseMillis."
                fi
            fi
            
            echo "• Enable detailed GC logging for better analysis: -XX:+PrintGCDetails"
            echo "• Consider using GC analysis tools like GCViewer or GarbageFirst"
            
        } > "$gc_analysis_file"
        
        print_success "GC log analysis completed"
        print_info "Analysis saved to: $gc_analysis_file"
        
        # Display summary
        echo -e "\n${CYAN}GC Log Analysis Summary:${NC}"
        head -20 "$gc_analysis_file" | tail -10
        
    else
        print_warning "No GC log file found at: $GC_LOG_FILE"
        print_info "Run other tests first to generate GC logs"
    fi
}

# =============================================================================
# Memory Stress Testing
# =============================================================================

run_memory_stress_tests() {
    print_header "Memory Stress Testing"
    
    local results_file="$LOG_DIR/memory-stress-test-$(date +%Y%m%d_%H%M%S).csv"
    echo "Stress_Type,Duration,Peak_Memory,GC_Count,GC_Time,Recovery_Time,Stability_Score" > "$results_file"
    
    local stress_tests=("gradual-pressure" "burst-allocation" "sustained-pressure" "memory-recovery")
    
    for stress_test in "${stress_tests[@]}"; do
        print_step "Running memory stress test: $stress_test"
        
        local stress_result=$(run_single_memory_stress_test "$stress_test")
        echo "$stress_result" >> "$results_file"
        
        print_info "$stress_test result: $stress_result"
        
        # Allow system to recover between tests
        print_info "Allowing system recovery..."
        sleep 15
    done
    
    print_success "Memory stress testing completed"
    print_info "Results saved to: $results_file"
    
    generate_memory_stress_report "$results_file"
}

run_single_memory_stress_test() {
    local stress_type="$1"
    local test_duration=90  # 90 seconds
    
    local jvm_flags="-Xms1g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    jvm_flags="$jvm_flags -XX:+PrintGC -XX:+PrintGCDetails"
    
    cd "$PROJECT_ROOT"
    
    local app_log="$LOG_DIR/memory-stress-${stress_type}-$(date +%Y%m%d_%H%M%S).log"
    
    # Run memory stress test
    JAVA_OPTS="$jvm_flags" timeout ${test_duration}s $GRADLE_CMD bootRun --args="stress-test" > "$app_log" 2>&1 &
    local app_pid=$!
    
    sleep 10  # Wait for startup
    
    local jvm_pid=$(get_jvm_pid)
    local peak_memory="0"
    local gc_count="0"
    local gc_time="0"
    local recovery_time="0"
    local stability_score="0"
    
    if [ -n "$jvm_pid" ] && command -v jstat &> /dev/null; then
        local start_stats=$(jstat -gc "$jvm_pid" | tail -1)
        local start_gc_count=$(echo "$start_stats" | awk '{print $13+$14}')
        local start_gc_time=$(echo "$start_stats" | awk '{print $15+$16}')
        
        # Monitor memory usage throughout the test
        local memory_readings=()
        local monitor_interval=5
        local monitors=$((test_duration / monitor_interval))
        
        for ((i=1; i<=monitors; i++)); do
            if ! kill -0 "$jvm_pid" 2>/dev/null; then
                break
            fi
            
            sleep "$monitor_interval"
            
            local current_stats=$(jstat -gc "$jvm_pid" | tail -1)
            local current_memory=$(echo "$current_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')
            memory_readings+=("$current_memory")
            
            # Track peak memory
            if (( $(echo "$current_memory > $peak_memory" | bc -l) )); then
                peak_memory="$current_memory"
            fi
        done
        
        # Wait for test completion
        wait $app_pid 2>/dev/null || true
        
        # Get final statistics
        if kill -0 "$jvm_pid" 2>/dev/null; then
            local end_stats=$(jstat -gc "$jvm_pid" | tail -1)
            local end_gc_count=$(echo "$end_stats" | awk '{print $13+$14}')
            local end_gc_time=$(echo "$end_stats" | awk '{print $15+$16}')
            
            gc_count=$((end_gc_count - start_gc_count))
            gc_time=$(echo "$end_gc_time $start_gc_time" | awk '{printf "%.0f", ($1-$2)*1000}')
            
            # Measure recovery time (how quickly memory normalizes after stress)
            local recovery_start=$(date +%s)
            local final_memory=$(echo "$end_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')
            local target_memory=$(echo "$peak_memory * 0.6" | bc -l)  # 60% of peak as recovery target
            
            # Wait up to 30 seconds for memory to recover
            for ((j=1; j<=6; j++)); do
                sleep 5
                if kill -0 "$jvm_pid" 2>/dev/null; then
                    local recovery_stats=$(jstat -gc "$jvm_pid" | tail -1)
                    local recovery_memory=$(echo "$recovery_stats" | awk '{printf "%.2f", ($3+$4+$6+$8)/1024}')
                    
                    if (( $(echo "$recovery_memory < $target_memory" | bc -l) )); then
                        recovery_time=$(($(date +%s) - recovery_start))
                        break
                    fi
                fi
            done
            
            if [ "$recovery_time" -eq 0 ]; then
                recovery_time=30  # Max recovery time if not achieved
            fi
            
            # Calculate stability score based on memory variance
            if [ "${#memory_readings[@]}" -gt 3 ]; then
                local mean_memory=$(printf '%s\n' "${memory_readings[@]}" | awk '{sum+=$1} END {printf "%.2f", sum/NR}')
                local variance=0
                for reading in "${memory_readings[@]}"; do
                    variance=$(echo "$variance ($reading - $mean_memory)^2" | bc -l)
                done
                variance=$(echo "$variance / ${#memory_readings[@]}" | bc -l)
                local std_dev=$(echo "sqrt($variance)" | bc -l)
                
                # Lower standard deviation = higher stability (scale 0-100)
                stability_score=$(echo "100 - ($std_dev / $mean_memory * 100)" | bc -l | awk '{printf "%.2f", ($1 > 0) ? $1 : 0}')
            fi
            
            kill "$jvm_pid" 2>/dev/null || true
        fi
    else
        wait $app_pid 2>/dev/null || true
    fi
    
    echo "$stress_type,$test_duration,$peak_memory,$gc_count,$gc_time,$recovery_time,$stability_score"
}

# =============================================================================
# Report Generation Functions
# =============================================================================

generate_gc_comparison_report() {
    local results_file="$1"
    local report_file="${results_file%.csv}_report.md"
    
    {
        echo "# GC Algorithm Performance Comparison Report"
        echo "Generated: $(date)"
        echo
        echo "## Summary"
        echo
        
        # Find best performing algorithm
        local best_algo=$(tail -n +2 "$results_file" | sort -t',' -k7 -nr | head -1 | cut -d',' -f1)
        local best_heap=$(tail -n +2 "$results_file" | sort -t',' -k7 -nr | head -1 | cut -d',' -f2)
        local best_throughput=$(tail -n +2 "$results_file" | sort -t',' -k7 -nr | head -1 | cut -d',' -f7)
        
        echo "**Best Performing Configuration:**"
        echo "- Algorithm: $best_algo"
        echo "- Heap Size: $best_heap"
        echo "- Throughput: $best_throughput%"
        echo
        
        echo "## Detailed Results"
        echo
        echo "| Algorithm | Heap Size | Duration | GC Count | GC Time | Throughput | Avg Pause | Max Pause |"
        echo "|-----------|-----------|----------|----------|---------|------------|-----------|-----------|"
        
        tail -n +2 "$results_file" | while IFS=',' read -r algo heap duration gc_count gc_time total_time throughput avg_pause max_pause; do
            echo "| $algo | $heap | ${duration}s | $gc_count | ${gc_time}ms | $throughput% | ${avg_pause}ms | ${max_pause}ms |"
        done
        
        echo
        echo "## Recommendations"
        echo
        echo "Based on the test results:"
        echo "1. **For Low Latency**: Choose configuration with lowest average pause time"
        echo "2. **For High Throughput**: Choose configuration with highest throughput percentage"
        echo "3. **For Balanced Performance**: Consider G1GC with appropriate heap sizing"
        echo "4. **Memory Constrained**: Use smaller heap sizes with efficient GC algorithms"
        
    } > "$report_file"
    
    print_success "GC comparison report generated: $report_file"
}

generate_heap_tuning_report() {
    local results_file="$1"
    local report_file="${results_file%.csv}_report.md"
    
    {
        echo "# Heap Memory Tuning Analysis Report"
        echo "Generated: $(date)"
        echo
        echo "## Optimization Results"
        echo
        
        # Find most memory efficient configuration
        local best_config=$(tail -n +2 "$results_file" | sort -t',' -k8 -nr | head -1)
        local best_heap=$(echo "$best_config" | cut -d',' -f2)
        local best_ratio=$(echo "$best_config" | cut -d',' -f3)
        local best_efficiency=$(echo "$best_config" | cut -d',' -f8)
        
        echo "**Most Memory Efficient Configuration:**"
        echo "- Heap Size: $best_heap"
        echo "- Young:Old Ratio: $best_ratio"
        echo "- Memory Efficiency: $best_efficiency%"
        echo
        
        echo "## Configuration Analysis"
        echo
        echo "| Heap Size | Young Ratio | Memory Used | GC Count | GC Time | Allocation Rate | Efficiency |"
        echo "|-----------|-------------|-------------|----------|---------|-----------------|------------|"
        
        tail -n +2 "$results_file" | while IFS=',' read -r test_type heap ratio memory gc_count gc_time alloc_rate efficiency; do
            echo "| $heap | $ratio | ${memory}MB | $gc_count | ${gc_time}ms | ${alloc_rate}MB/s | $efficiency% |"
        done
        
        echo
        echo "## Tuning Recommendations"
        echo
        echo "1. **Heap Sizing**: Balance between memory availability and GC overhead"
        echo "2. **Generation Ratios**: Adjust based on object lifetime patterns"
        echo "3. **Allocation Patterns**: Monitor allocation rates for optimal heap sizing"
        echo "4. **GC Frequency**: Higher ratios may reduce GC frequency but increase pause times"
        
    } > "$report_file"
    
    print_success "Heap tuning report generated: $report_file"
}

generate_jvm_flags_report() {
    local results_file="$1"
    local report_file="${results_file%.csv}_report.md"
    
    {
        echo "# JVM Flags Optimization Report"
        echo "Generated: $(date)"
        echo
        echo "## Performance Comparison"
        echo
        
        # Find best performing flag set
        local best_flags=$(tail -n +2 "$results_file" | sort -t',' -k8 -nr | head -1 | cut -d',' -f1)
        local best_score=$(tail -n +2 "$results_file" | sort -t',' -k8 -nr | head -1 | cut -d',' -f8)
        
        echo "**Best Performing Flag Set:** $best_flags (Score: $best_score)"
        echo
        
        echo "## Detailed Results"
        echo
        echo "| Flag Set | Test Type | Exec Time | Memory | GC Count | GC Time | Throughput | Score |"
        echo "|----------|-----------|-----------|--------|----------|---------|------------|-------|"
        
        tail -n +2 "$results_file" | while IFS=',' read -r flags test_type exec_time memory gc_count gc_time throughput score; do
            echo "| $flags | $test_type | ${exec_time}s | ${memory}MB | $gc_count | ${gc_time}ms | $throughput% | $score |"
        done
        
        echo
        echo "## Flag Set Analysis"
        echo
        echo "- **baseline**: Default JVM settings for comparison"
        echo "- **g1gc_optimized**: G1GC with pause time targets"
        echo "- **parallel_optimized**: Parallel GC with thread optimization"
        echo "- **performance_tuned**: Additional performance optimizations"
        echo "- **memory_optimized**: Focus on memory efficiency"
        
    } > "$report_file"
    
    print_success "JVM flags report generated: $report_file"
}

generate_memory_leak_report() {
    local results_file="$1"
    local report_file="${results_file%.csv}_report.md"
    
    {
        echo "# Memory Leak Detection Report"
        echo "Generated: $(date)"
        echo
        echo "## Leak Detection Summary"
        echo
        
        local high_risk_count=$(tail -n +2 "$results_file" | grep -c "HIGH" || echo "0")
        local medium_risk_count=$(tail -n +2 "$results_file" | grep -c "MEDIUM" || echo "0")
        local low_risk_count=$(tail -n +2 "$results_file" | grep -c "LOW" || echo "0")
        
        echo "**Risk Assessment:**"
        echo "- High Risk Leaks: $high_risk_count"
        echo "- Medium Risk Leaks: $medium_risk_count"
        echo "- Low Risk Leaks: $low_risk_count"
        echo
        
        echo "## Detailed Analysis"
        echo
        echo "| Test Type | Duration | Initial Memory | Final Memory | Growth | Leak Detected | Risk Level |"
        echo "|-----------|----------|----------------|--------------|--------|---------------|------------|"
        
        tail -n +2 "$results_file" | while IFS=',' read -r test_type duration initial final growth detected risk; do
            echo "| $test_type | ${duration}s | ${initial}MB | ${final}MB | ${growth}MB | $detected | $risk |"
        done
        
        echo
        echo "## Leak Prevention Recommendations"
        echo
        echo "1. **Collection Management**: Explicitly clear collections when no longer needed"
        echo "2. **Listener Cleanup**: Always remove event listeners in cleanup methods"
        echo "3. **ThreadLocal Usage**: Call ThreadLocal.remove() after use"
        echo "4. **Cache Management**: Implement proper cache eviction policies"
        echo "5. **Regular Monitoring**: Use heap dumps and profiling tools regularly"
        
    } > "$report_file"
    
    print_success "Memory leak report generated: $report_file"
}

generate_memory_stress_report() {
    local results_file="$1"
    local report_file="${results_file%.csv}_report.md"
    
    {
        echo "# Memory Stress Testing Report"
        echo "Generated: $(date)"
        echo
        echo "## Stress Test Results"
        echo
        
        # Find most stable configuration
        local most_stable=$(tail -n +2 "$results_file" | sort -t',' -k7 -nr | head -1)
        local stable_type=$(echo "$most_stable" | cut -d',' -f1)
        local stable_score=$(echo "$most_stable" | cut -d',' -f7)
        
        echo "**Most Stable Under Stress:** $stable_type (Stability Score: $stable_score)"
        echo
        
        echo "## Performance Under Stress"
        echo
        echo "| Stress Type | Duration | Peak Memory | GC Count | GC Time | Recovery Time | Stability |"
        echo "|-------------|----------|-------------|----------|---------|---------------|-----------|"
        
        tail -n +2 "$results_file" | while IFS=',' read -r stress_type duration peak gc_count gc_time recovery stability; do
            echo "| $stress_type | ${duration}s | ${peak}MB | $gc_count | ${gc_time}ms | ${recovery}s | $stability |"
        done
        
        echo
        echo "## Stress Test Analysis"
        echo
        echo "- **gradual-pressure**: Progressive memory allocation"
        echo "- **burst-allocation**: Rapid memory allocation spikes"
        echo "- **sustained-pressure**: Continuous memory pressure"
        echo "- **memory-recovery**: Recovery efficiency after stress"
        echo
        echo "## Optimization Recommendations"
        echo
        echo "1. **GC Tuning**: Adjust GC parameters based on stress patterns"
        echo "2. **Heap Sizing**: Ensure adequate heap space for peak loads"
        echo "3. **Application Design**: Implement backpressure for memory-intensive operations"
        echo "4. **Monitoring**: Set up alerts for memory pressure conditions"
        
    } > "$report_file"
    
    print_success "Memory stress report generated: $report_file"
}

# =============================================================================
# Comprehensive JVM Analysis
# =============================================================================

run_comprehensive_jvm_analysis() {
    print_header "Comprehensive JVM Performance Analysis"
    
    print_info "This comprehensive analysis will take approximately 30-45 minutes..."
    print_info "Tests will run sequentially to avoid interference"
    
    local start_time=$(date +%s)
    local comprehensive_report="$LOG_DIR/comprehensive-jvm-analysis-$(date +%Y%m%d_%H%M%S).md"
    
    # Initialize comprehensive report
    {
        echo "# Comprehensive JVM Performance Analysis Report"
        echo "Generated: $(date)"
        echo "Analysis Duration: TBD"
        echo
        echo "## Executive Summary"
        echo "This report provides a complete analysis of JVM performance across multiple dimensions:"
        echo "- Garbage Collection Algorithm Performance"
        echo "- Heap Memory Tuning Optimization"
        echo "- JVM Flags Impact Analysis"
        echo "- Memory Leak Detection"
        echo "- Memory Stress Testing"
        echo "- GC Log Analysis"
        echo
    } > "$comprehensive_report"
    
    # Run all test suites
    print_step "Phase 1: GC Algorithm Performance Testing"
    test_gc_algorithms
    
    print_step "Phase 2: Heap Memory Tuning Analysis"
    test_heap_tuning
    
    print_step "Phase 3: JVM Flags Optimization"
    test_jvm_flags_optimization
    
    print_step "Phase 4: Memory Leak Detection"
    test_memory_leak_detection
    
    print_step "Phase 5: Memory Stress Testing"
    run_memory_stress_tests
    
    print_step "Phase 6: GC Log Analysis"
    analyze_gc_logs
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - start_time))
    local duration_formatted=$(printf "%02d:%02d:%02d" $((total_duration/3600)) $((total_duration%3600/60)) $((total_duration%60)))
    
    # Update comprehensive report with summary
    {
        echo
        echo "## Analysis Summary"
        echo "**Total Analysis Duration:** $duration_formatted"
        echo "**Tests Completed:** 6 test suites"
        echo "**Reports Generated:** Multiple detailed reports available"
        echo
        echo "## Key Findings"
        echo "Detailed findings are available in individual test reports:"
        
        # List generated reports
        find "$LOG_DIR" -name "*report*.md" -newer "$comprehensive_report" | while read -r report; do
            local report_name=$(basename "$report")
            echo "- $report_name"
        done
        
        echo
        echo "## Overall Recommendations"
        echo "1. **Monitor Continuously**: Implement ongoing JVM performance monitoring"
        echo "2. **Tune Incrementally**: Make gradual adjustments based on workload patterns"
        echo "3. **Test Thoroughly**: Validate changes in staging environments"
        echo "4. **Document Changes**: Keep track of JVM tuning modifications"
        echo "5. **Regular Analysis**: Repeat this analysis quarterly or after major changes"
        
    } >> "$comprehensive_report"
    
    print_success "Comprehensive JVM analysis completed in $duration_formatted"
    print_info "Comprehensive report: $comprehensive_report"
    
    # Display summary statistics
    echo -e "\n${CYAN}Analysis Statistics:${NC}"
    echo "  • Duration: $duration_formatted"
    echo "  • Reports Generated: $(find "$LOG_DIR" -name "*$(date +%Y%m%d)*" -type f | wc -l)"
    echo "  • Total Log Files: $(find "$LOG_DIR" -name "*.log" -newer "$comprehensive_report" | wc -l)"
    echo "  • Data Files: $(find "$LOG_DIR" -name "*.csv" -newer "$comprehensive_report" | wc -l)"
}

# =============================================================================
# System Resource Monitoring
# =============================================================================

monitor_system_resources() {
    print_step "Starting system resource monitoring..."
    
    local monitoring_log="$LOG_DIR/system-resources-$(date +%Y%m%d_%H%M%S).csv"
    local monitoring_duration=300  # 5 minutes
    local interval=10  # 10 seconds
    
    echo "Timestamp,CPU_Usage,Memory_Usage,Free_Memory,Swap_Usage,Load_Average,Disk_IO" > "$monitoring_log"
    
    {
        local end_time=$(($(date +%s) + monitoring_duration))
        
        while [ $(date +%s) -lt $end_time ]; do
            local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
            
            # Get system statistics
            local cpu_usage=$(top -l 1 -n 0 2>/dev/null | grep "CPU usage" | awk '{print $3}' | sed 's/%//' || echo "0")
            local memory_info=$(vm_stat 2>/dev/null | head -4)
            local load_avg=$(uptime | awk -F'load averages:' '{print $2}' | awk '{print $1}' | sed 's/,//' || echo "0")
            
            # Parse memory information (macOS specific)
            if [ "$(uname)" = "Darwin" ]; then
                local page_size=4096
                local free_pages=$(echo "$memory_info" | grep "Pages free" | awk '{print $3}' | sed 's/\.//' || echo "0")
                local active_pages=$(echo "$memory_info" | grep "Pages active" | awk '{print $3}' | sed 's/\.//' || echo "0")
                local inactive_pages=$(echo "$memory_info" | grep "Pages inactive" | awk '{print $3}' | sed 's/\.//' || echo "0")
                local wired_pages=$(echo "$memory_info" | grep "Pages wired down" | awk '{print $4}' | sed 's/\.//' || echo "0")
                
                local free_memory=$(( free_pages * page_size / 1024 / 1024 ))  # MB
                local used_memory=$(( (active_pages + inactive_pages + wired_pages) * page_size / 1024 / 1024 ))  # MB
                local total_memory=$(( (free_pages + active_pages + inactive_pages + wired_pages) * page_size / 1024 / 1024 ))  # MB
                local memory_usage=$(echo "$used_memory $total_memory" | awk '{if($2>0) printf "%.1f", ($1/$2)*100; else print "0"}')
            else
                # Linux fallback
                local memory_usage=$(free | grep Mem | awk '{printf "%.1f", ($3/$2)*100}' || echo "0")
                local free_memory=$(free -m | grep Mem | awk '{print $4}' || echo "0")
            fi
            
            local swap_usage=$(sysctl vm.swapusage 2>/dev/null | awk '{print $7}' | sed 's/%//' || echo "0")
            local disk_io="N/A"  # Disk I/O monitoring would require additional tools
            
            echo "$timestamp,$cpu_usage,$memory_usage,$free_memory,$swap_usage,$load_avg,$disk_io" >> "$monitoring_log"
            
            sleep $interval
        done
    } &
    
    local monitor_pid=$!
    echo $monitor_pid > "$LOG_DIR/monitor.pid"
    
    print_info "System monitoring started (PID: $monitor_pid)"
    print_info "Monitoring log: $monitoring_log"
    print_info "Duration: $monitoring_duration seconds"
}

stop_system_monitoring() {
    if [ -f "$LOG_DIR/monitor.pid" ]; then
        local monitor_pid=$(cat "$LOG_DIR/monitor.pid")
        if kill -0 $monitor_pid 2>/dev/null; then
            kill $monitor_pid 2>/dev/null
            print_info "System monitoring stopped (PID: $monitor_pid)"
        fi
        rm -f "$LOG_DIR/monitor.pid"
    else
        print_warning "No monitoring process found"
    fi
}

# =============================================================================
# Cleanup Functions
# =============================================================================

cleanup_resources() {
    print_step "Cleaning up resources..."
    
    # Stop any running monitoring
    stop_system_monitoring
    
    # Kill any remaining JVM processes from tests
    local jvm_pids=$(jps -l | grep -i reservation | awk '{print $1}')
    if [ -n "$jvm_pids" ]; then
        echo "$jvm_pids" | while read -r pid; do
            print_info "Stopping JVM process: $pid"
            kill $pid 2>/dev/null || true
        done
        
        # Wait for processes to terminate
        sleep 5
        
        # Force kill if still running
        echo "$jvm_pids" | while read -r pid; do
            if kill -0 $pid 2>/dev/null; then
                print_warning "Force killing JVM process: $pid"
                kill -9 $pid 2>/dev/null || true
            fi
        done
    fi
    
    # Clean up temporary files
    rm -f "$LOG_DIR"/*.tmp 2>/dev/null || true
    rm -f "$LOG_DIR"/monitor.pid 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# =============================================================================
# Main Execution Functions
# =============================================================================

show_usage() {
    echo "JVM Tuning and GC Performance Testing Script"
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Options:"
    echo "  gc-comparison      Compare GC algorithm performance"
    echo "  heap-tuning        Analyze heap memory tuning strategies"
    echo "  jvm-flags          Test JVM flags optimization"
    echo "  memory-leak        Detect memory leaks"
    echo "  gc-logs            Analyze GC logs"
    echo "  stress-test        Run memory stress tests"
    echo "  monitor            Start system resource monitoring"
    echo "  stop-monitor       Stop system resource monitoring"
    echo "  comprehensive      Run all JVM analysis tests"
    echo "  cleanup            Clean up resources and processes"
    echo "  help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 comprehensive      # Run complete JVM analysis"
    echo "  $0 gc-comparison      # Test GC algorithms only"
    echo "  $0 heap-tuning        # Analyze heap tuning only"
}

main() {
    local test_mode="${1:-comprehensive}"
    
    # Set trap for cleanup
    trap cleanup_resources EXIT
    
    case "$test_mode" in
        "gc-comparison")
            print_header "JVM Tuning - GC Algorithm Comparison"
            check_prerequisites
            compile_project
            monitor_system_resources
            test_gc_algorithms
            ;;
        "heap-tuning")
            print_header "JVM Tuning - Heap Memory Analysis"
            check_prerequisites
            compile_project
            monitor_system_resources
            test_heap_tuning
            ;;
        "jvm-flags")
            print_header "JVM Tuning - Flags Optimization"
            check_prerequisites
            compile_project
            monitor_system_resources
            test_jvm_flags_optimization
            ;;
        "memory-leak")
            print_header "JVM Tuning - Memory Leak Detection"
            check_prerequisites
            compile_project
            monitor_system_resources
            test_memory_leak_detection
            ;;
        "gc-logs")
            print_header "JVM Tuning - GC Log Analysis"
            analyze_gc_logs
            ;;
        "stress-test")
            print_header "JVM Tuning - Memory Stress Testing"
            check_prerequisites
            compile_project
            monitor_system_resources
            run_memory_stress_tests
            ;;
        "monitor")
            monitor_system_resources
            print_info "Monitoring started. Use '$0 stop-monitor' to stop."
            ;;
        "stop-monitor")
            stop_system_monitoring
            ;;
        "comprehensive")
            print_header "Comprehensive JVM Tuning Analysis"
            check_prerequisites
            compile_project
            monitor_system_resources
            run_comprehensive_jvm_analysis
            ;;
        "cleanup")
            cleanup_resources
            ;;
        "help"|"-h"|"--help")
            show_usage
            ;;
        *)
            print_error "Unknown option: $test_mode"
            show_usage
            exit 1
            ;;
    esac
    
    print_success "JVM tuning analysis completed successfully!"
}

# =============================================================================
# Script Entry Point
# =============================================================================

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi