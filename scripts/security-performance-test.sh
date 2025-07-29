#!/bin/bash

# Security Performance Testing Automation Script
# Phase 2-4: Security Performance Overhead Analysis
# Created: $(date)

set -e  # Exit immediately if a command exits with a non-zero status

# =============================================================================
# Configuration Variables
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LOG_DIR="$PROJECT_ROOT/reports"
GRADLE_CMD="./gradlew"

# Test Configuration
REDIS_HOST="localhost"
REDIS_PORT="6379"
REDIS_PASSWORD=""

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
    print_step "Checking prerequisites..."
    
    # Check if Java is available
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    
    # Check if Gradle wrapper exists
    if [ ! -f "$PROJECT_ROOT/gradlew" ]; then
        print_error "Gradle wrapper not found in project root"
        exit 1
    fi
    
    # Check if Redis is available (optional)
    if command -v redis-cli &> /dev/null; then
        if redis-cli -h $REDIS_HOST -p $REDIS_PORT ping > /dev/null 2>&1; then
            print_success "Redis server is running"
        else
            print_warning "Redis server is not running. Some tests may be skipped."
        fi
    else
        print_warning "Redis CLI not found. Redis-related tests will be skipped."
    fi
    
    # Create reports directory
    mkdir -p "$LOG_DIR"
    
    print_success "Prerequisites check completed"
}

compile_project() {
    print_step "Compiling project..."
    
    cd "$PROJECT_ROOT"
    
    if $GRADLE_CMD compileKotlin > /dev/null 2>&1; then
        print_success "Project compiled successfully"
    else
        print_error "Project compilation failed"
        exit 1
    fi
}

setup_redis() {
    print_step "Setting up Redis for security performance tests..."
    
    if command -v redis-server &> /dev/null; then
        # Start Redis if not running
        if ! redis-cli -h $REDIS_HOST -p $REDIS_PORT ping > /dev/null 2>&1; then
            print_info "Starting Redis server..."
            redis-server --daemonize yes --port $REDIS_PORT
            sleep 2
        fi
        
        # Clear existing data
        redis-cli -h $REDIS_HOST -p $REDIS_PORT FLUSHALL > /dev/null
        print_success "Redis setup completed"
    else
        print_warning "Redis not available. Skipping Redis setup."
    fi
}

# =============================================================================
# Security Performance Test Functions
# =============================================================================

run_jwt_performance_test() {
    print_header "JWT Token Processing Performance Test"
    
    local output_file="$LOG_DIR/jwt-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running JWT performance analysis..."
    
    cd "$PROJECT_ROOT"
    
    timeout 300 $GRADLE_CMD bootRun --args="jwt" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress
    local counter=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 5
        counter=$((counter + 1))
        
        if [ $counter -gt 60 ]; then  # 5 minutes timeout
            print_error "JWT test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "JWT performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract key metrics from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}JWT Performance Summary:${NC}"
            grep -E "(Token Generation|Token Validation|Token Parsing|Overall JWT Performance)" "$output_file" | head -10
        fi
    else
        print_error "JWT performance test failed with exit code: $exit_code"
        return 1
    fi
}

run_crypto_performance_test() {
    print_header "Cryptographic Operations Performance Test"
    
    local output_file="$LOG_DIR/crypto-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running cryptographic performance analysis..."
    
    cd "$PROJECT_ROOT"
    
    timeout 300 $GRADLE_CMD bootRun --args="crypto" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress
    local counter=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 5
        counter=$((counter + 1))
        
        if [ $counter -gt 60 ]; then  # 5 minutes timeout
            print_error "Crypto test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "Crypto performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract key metrics from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}Crypto Performance Summary:${NC}"
            grep -E "(AES Encryption|AES Decryption|Hashing Algorithm|Password Encoding|Overall Crypto Score)" "$output_file" | head -10
        fi
    else
        print_error "Crypto performance test failed with exit code: $exit_code"
        return 1
    fi
}

run_protocol_performance_test() {
    print_header "HTTPS vs HTTP Protocol Performance Test"
    
    local output_file="$LOG_DIR/protocol-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running protocol performance comparison..."
    
    cd "$PROJECT_ROOT"
    
    timeout 300 $GRADLE_CMD bootRun --args="protocol" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress
    local counter=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 5
        counter=$((counter + 1))
        
        if [ $counter -gt 60 ]; then  # 5 minutes timeout
            print_error "Protocol test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "Protocol performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract key metrics from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}Protocol Performance Summary:${NC}"
            grep -E "(HTTP Performance|HTTPS Performance|Performance Difference|Security Overhead)" "$output_file" | head -10
        fi
    else
        print_error "Protocol performance test failed with exit code: $exit_code"
        return 1
    fi
}

run_rate_limiting_test() {
    print_header "Rate Limiting Performance Impact Test"
    
    local output_file="$LOG_DIR/ratelimit-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running rate limiting impact analysis..."
    
    cd "$PROJECT_ROOT"
    
    timeout 300 $GRADLE_CMD bootRun --args="ratelimit" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress
    local counter=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 5
        counter=$((counter + 1))
        
        if [ $counter -gt 60 ]; then  # 5 minutes timeout
            print_error "Rate limiting test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "Rate limiting performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract key metrics from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}Rate Limiting Performance Summary:${NC}"
            grep -E "(Rate Limiting Performance|Throughput Impact|Memory Impact|Latency Increase)" "$output_file" | head -10
        fi
    else
        print_error "Rate limiting performance test failed with exit code: $exit_code"
        return 1
    fi
}

run_filter_chain_test() {
    print_header "Security Filter Chain Optimization Test"
    
    local output_file="$LOG_DIR/filter-chain-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running security filter chain analysis..."
    
    cd "$PROJECT_ROOT"
    
    timeout 300 $GRADLE_CMD bootRun --args="filter-chain" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress
    local counter=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 5
        counter=$((counter + 1))
        
        if [ $counter -gt 60 ]; then  # 5 minutes timeout
            print_error "Filter chain test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "Filter chain performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract key metrics from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}Filter Chain Performance Summary:${NC}"
            grep -E "(Filter Chain|Processing Time|Throughput|Memory Overhead)" "$output_file" | head -15
        fi
    else
        print_error "Filter chain performance test failed with exit code: $exit_code"
        return 1
    fi
}

run_comprehensive_test() {
    print_header "Comprehensive Security Performance Analysis"
    
    local output_file="$LOG_DIR/comprehensive-security-performance-$(date +%Y%m%d_%H%M%S).log"
    
    print_step "Running comprehensive security performance analysis..."
    print_info "This may take 10-15 minutes to complete all tests..."
    
    cd "$PROJECT_ROOT"
    
    timeout 900 $GRADLE_CMD bootRun --args="comprehensive" > "$output_file" 2>&1 &
    local pid=$!
    
    # Monitor the test progress with more frequent updates
    local counter=0
    local last_size=0
    while kill -0 $pid 2>/dev/null; do
        echo -n "."
        sleep 10
        counter=$((counter + 1))
        
        # Show progress by checking log file size
        if [ -f "$output_file" ]; then
            local current_size=$(wc -l < "$output_file" 2>/dev/null || echo 0)
            if [ $current_size -gt $last_size ]; then
                echo -e "\n${CYAN}Progress: $current_size lines of output generated${NC}"
                last_size=$current_size
            fi
        fi
        
        if [ $counter -gt 90 ]; then  # 15 minutes timeout
            print_error "Comprehensive test timed out"
            kill $pid 2>/dev/null
            return 1
        fi
    done
    
    wait $pid
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "Comprehensive security performance test completed"
        print_info "Results saved to: $output_file"
        
        # Extract summary from log
        if [ -f "$output_file" ]; then
            echo -e "\n${CYAN}Comprehensive Analysis Summary:${NC}"
            grep -E "(Overall Security Performance Score|COMPREHENSIVE)" "$output_file" | tail -5
        fi
        
        # Find and display CSV report location
        local csv_report=$(find "$LOG_DIR" -name "security-performance-report-*.csv" -newer "$output_file" 2>/dev/null | head -1)
        if [ -n "$csv_report" ]; then
            print_success "Detailed CSV report generated: $csv_report"
        fi
    else
        print_error "Comprehensive security performance test failed with exit code: $exit_code"
        return 1
    fi
}

# =============================================================================
# Performance Monitoring Functions
# =============================================================================

monitor_system_resources() {
    print_step "Monitoring system resources during tests..."
    
    local monitoring_log="$LOG_DIR/system-resources-$(date +%Y%m%d_%H%M%S).log"
    
    # Start resource monitoring in background
    {
        echo "Timestamp,CPU%,Memory%,Disk%,Network"
        while true; do
            local timestamp=$(date "+%Y-%m-%d %H:%M:%S")
            local cpu_usage=$(top -l 1 -n 0 2>/dev/null | grep "CPU usage" | awk '{print $3}' | sed 's/%//' || echo "0")
            local memory_usage=$(top -l 1 -n 0 2>/dev/null | grep "PhysMem" | awk '{print $2}' | sed 's/M//' || echo "0")
            local disk_usage=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//' || echo "0")
            
            echo "$timestamp,$cpu_usage,$memory_usage,$disk_usage,0"
            sleep 30
        done
    } > "$monitoring_log" &
    
    local monitor_pid=$!
    echo $monitor_pid > "$LOG_DIR/monitor.pid"
    
    print_info "Resource monitoring started (PID: $monitor_pid)"
    print_info "Monitoring log: $monitoring_log"
}

stop_monitoring() {
    if [ -f "$LOG_DIR/monitor.pid" ]; then
        local monitor_pid=$(cat "$LOG_DIR/monitor.pid")
        if kill -0 $monitor_pid 2>/dev/null; then
            kill $monitor_pid 2>/dev/null
            print_info "Resource monitoring stopped"
        fi
        rm -f "$LOG_DIR/monitor.pid"
    fi
}

# =============================================================================
# Report Generation Functions
# =============================================================================

generate_performance_summary() {
    print_header "Generating Performance Summary Report"
    
    local summary_file="$LOG_DIR/security-performance-summary-$(date +%Y%m%d_%H%M%S).html"
    
    cat > "$summary_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>Security Performance Analysis Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .header { background-color: #f4f4f4; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border-left: 4px solid #007cba; }
        .metric { background-color: #f9f9f9; padding: 10px; margin: 10px 0; border-radius: 3px; }
        .success { color: #28a745; }
        .warning { color: #ffc107; }
        .error { color: #dc3545; }
        table { width: 100%; border-collapse: collapse; margin: 15px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🔐 Security Performance Analysis Report</h1>
        <p>Generated on: $(date)</p>
        <p>Project: Reservation System Security Performance Overhead Analysis</p>
    </div>

    <div class="section">
        <h2>📊 Test Results Summary</h2>
        <table>
            <tr><th>Test Category</th><th>Status</th><th>Duration</th><th>Key Metrics</th></tr>
EOF

    # Add test results to HTML report
    local test_categories=("JWT" "Crypto" "Protocol" "RateLimit" "FilterChain")
    
    for category in "${test_categories[@]}"; do
        local log_file=$(find "$LOG_DIR" -name "*${category,,}*performance*.log" -type f | head -1)
        if [ -f "$log_file" ]; then
            local status="<span class='success'>✅ Completed</span>"
            local duration=$(stat -f%Sm -t "%H:%M:%S" "$log_file" 2>/dev/null || echo "Unknown")
            local key_metric="See detailed log"
            
            echo "            <tr><td>$category</td><td>$status</td><td>$duration</td><td>$key_metric</td></tr>" >> "$summary_file"
        else
            echo "            <tr><td>$category</td><td><span class='error'>❌ Not Run</span></td><td>-</td><td>-</td></tr>" >> "$summary_file"
        fi
    done

    cat >> "$summary_file" << EOF
        </table>
    </div>

    <div class="section">
        <h2>📈 Performance Insights</h2>
        <div class="metric">
            <h3>JWT Token Processing</h3>
            <p>Measures the overhead of JWT token generation, validation, and parsing operations.</p>
        </div>
        
        <div class="metric">
            <h3>Cryptographic Operations</h3>
            <p>Analyzes the performance impact of encryption, decryption, hashing, and password encoding.</p>
        </div>
        
        <div class="metric">
            <h3>Protocol Comparison</h3>
            <p>Compares HTTPS vs HTTP performance to quantify security protocol overhead.</p>
        </div>
        
        <div class="metric">
            <h3>Rate Limiting Impact</h3>
            <p>Evaluates the performance cost of implementing rate limiting mechanisms.</p>
        </div>
        
        <div class="metric">
            <h3>Security Filter Chain</h3>
            <p>Analyzes the cumulative performance impact of security filter chains.</p>
        </div>
    </div>

    <div class="section">
        <h2>📋 Generated Reports</h2>
        <ul>
EOF

    # List all generated report files
    find "$LOG_DIR" -name "*performance*" -type f -newer "$LOG_DIR" 2>/dev/null | while read -r file; do
        local filename=$(basename "$file")
        echo "            <li><a href=\"file://$file\">$filename</a></li>" >> "$summary_file"
    done

    cat >> "$summary_file" << EOF
        </ul>
    </div>

    <div class="section">
        <h2>🔧 Recommendations</h2>
        <ul>
            <li>Review JWT token expiration times to balance security and performance</li>
            <li>Consider hardware acceleration for cryptographic operations</li>
            <li>Implement connection pooling and keep-alive for HTTPS</li>
            <li>Use distributed rate limiting with Redis for better scalability</li>
            <li>Optimize security filter chain order for early rejection</li>
            <li>Monitor security performance metrics in production</li>
        </ul>
    </div>
</body>
</html>
EOF

    print_success "Performance summary report generated: $summary_file"
}

cleanup_resources() {
    print_step "Cleaning up resources..."
    
    # Stop monitoring
    stop_monitoring
    
    # Clean up any remaining processes
    pkill -f "SecurityPerformanceAnalyzer" 2>/dev/null || true
    
    # Remove temporary files
    rm -f "$LOG_DIR"/*.tmp 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# =============================================================================
# Main Execution Functions
# =============================================================================

show_usage() {
    echo "Security Performance Testing Script"
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Options:"
    echo "  jwt          Run JWT token processing performance test"
    echo "  crypto       Run cryptographic operations performance test"
    echo "  protocol     Run HTTPS vs HTTP protocol comparison test"
    echo "  ratelimit    Run rate limiting performance impact test"
    echo "  filter-chain Run security filter chain optimization test"
    echo "  comprehensive Run all security performance tests"
    echo "  monitor      Start system resource monitoring"
    echo "  stop-monitor Stop system resource monitoring"
    echo "  report       Generate performance summary report"
    echo "  help         Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 comprehensive    # Run all tests"
    echo "  $0 jwt             # Run only JWT performance test"
    echo "  $0 report          # Generate summary report"
}

main() {
    local test_mode="${1:-comprehensive}"
    
    # Set trap for cleanup
    trap cleanup_resources EXIT
    
    case "$test_mode" in
        "jwt")
            print_header "Security Performance Testing - JWT Analysis"
            check_prerequisites
            compile_project
            setup_redis
            monitor_system_resources
            run_jwt_performance_test
            ;;
        "crypto")
            print_header "Security Performance Testing - Cryptographic Analysis"
            check_prerequisites
            compile_project
            monitor_system_resources
            run_crypto_performance_test
            ;;
        "protocol")
            print_header "Security Performance Testing - Protocol Analysis"
            check_prerequisites
            compile_project
            monitor_system_resources
            run_protocol_performance_test
            ;;
        "ratelimit")
            print_header "Security Performance Testing - Rate Limiting Analysis"
            check_prerequisites
            compile_project
            setup_redis
            monitor_system_resources
            run_rate_limiting_test
            ;;
        "filter-chain")
            print_header "Security Performance Testing - Filter Chain Analysis"
            check_prerequisites
            compile_project
            monitor_system_resources
            run_filter_chain_test
            ;;
        "comprehensive")
            print_header "Comprehensive Security Performance Testing"
            check_prerequisites
            compile_project
            setup_redis
            monitor_system_resources
            run_comprehensive_test
            generate_performance_summary
            ;;
        "monitor")
            monitor_system_resources
            print_info "Monitoring started. Use '$0 stop-monitor' to stop."
            ;;
        "stop-monitor")
            stop_monitoring
            ;;
        "report")
            generate_performance_summary
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
    
    print_success "Security performance testing completed successfully!"
}

# =============================================================================
# Script Entry Point
# =============================================================================

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi