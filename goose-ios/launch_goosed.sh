#!/bin/bash
set -e

# Configuration
PORT=62996
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GOOSED_URL="https://github.com/michaelneale/goose-tunnel/releases/download/test/goosed"
GOOSED_LOCAL_PATH="${SCRIPT_DIR}/goosed"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

SECRET="test"

# Function to download goosed binary with LOUD notification
download_goosed() {
    echo ""
    echo -e "${BOLD}${MAGENTA}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${MAGENTA}║                                                                       ║${NC}"
    echo -e "${BOLD}${MAGENTA}║                   🚀  DOWNLOADING GOOSED BINARY  🚀                   ║${NC}"
    echo -e "${BOLD}${MAGENTA}║                                                                       ║${NC}"
    echo -e "${BOLD}${MAGENTA}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BOLD}${CYAN}⬇️  Fetching from: ${GOOSED_URL}${NC}"
    echo -e "${BOLD}${CYAN}📦 Saving to: ${GOOSED_LOCAL_PATH}${NC}"
    echo ""
    
    if curl -L -o "$GOOSED_LOCAL_PATH" "$GOOSED_URL"; then
        chmod +x "$GOOSED_LOCAL_PATH"
        echo ""
        echo -e "${BOLD}${GREEN}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BOLD}${GREEN}║                                                                       ║${NC}"
        echo -e "${BOLD}${GREEN}║                  ✅  DOWNLOAD SUCCESSFUL!  ✅                         ║${NC}"
        echo -e "${BOLD}${GREEN}║                                                                       ║${NC}"
        echo -e "${BOLD}${GREEN}║              goosed binary is now available locally!                  ║${NC}"
        echo -e "${BOLD}${GREEN}║                                                                       ║${NC}"
        echo -e "${BOLD}${GREEN}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
        echo ""
        sleep 1  # Pause so user can see the message
        return 0
    else
        echo ""
        echo -e "${BOLD}${RED}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BOLD}${RED}║                                                                       ║${NC}"
        echo -e "${BOLD}${RED}║                    ❌  DOWNLOAD FAILED!  ❌                           ║${NC}"
        echo -e "${BOLD}${RED}║                                                                       ║${NC}"
        echo -e "${BOLD}${RED}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
        echo ""
        return 1
    fi
}

# Determine which goosed to use
GOOSED_CMD=""
if command -v goosed &> /dev/null; then
    # Found in PATH
    GOOSED_CMD="goosed"
    echo ""
    echo -e "${BOLD}${BLUE}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${BLUE}║                                                                       ║${NC}"
    echo -e "${BOLD}${BLUE}║                   📍  USING GOOSED FROM PATH  📍                      ║${NC}"
    echo -e "${BOLD}${BLUE}║                                                                       ║${NC}"
    echo -e "${BOLD}${BLUE}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BOLD}${CYAN}📂 Location: $(which goosed)${NC}"
    echo ""
elif [ -f "$GOOSED_LOCAL_PATH" ]; then
    # Found locally
    GOOSED_CMD="$GOOSED_LOCAL_PATH"
    echo ""
    echo -e "${BOLD}${YELLOW}╔═══════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}${YELLOW}║                                                                       ║${NC}"
    echo -e "${BOLD}${YELLOW}║                  📦  USING LOCAL GOOSED BINARY  📦                    ║${NC}"
    echo -e "${BOLD}${YELLOW}║                                                                       ║${NC}"
    echo -e "${BOLD}${YELLOW}╚═══════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BOLD}${CYAN}📂 Location: $GOOSED_LOCAL_PATH${NC}"
    echo ""
else
    # Not found anywhere - download it
    if download_goosed; then
        GOOSED_CMD="$GOOSED_LOCAL_PATH"
    else
        echo -e "${RED}Error: Failed to download goosed${NC}"
        echo -e "${YELLOW}Please manually download from: ${GOOSED_URL}${NC}"
        echo -e "${YELLOW}Or add goose/target/release to your PATH${NC}"
        exit 1
    fi
fi

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Shutting down...${NC}"
    if [ ! -z "$GOOSED_PID" ]; then
        echo "Stopping goosed (PID: $GOOSED_PID)"
        kill $GOOSED_PID 2>/dev/null || true
    fi
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# Start goosed in the background
echo -e "${GREEN}Starting goosed on port ${PORT}...${NC}"
export GOOSE_PORT=$PORT
export GOOSE_SERVER__SECRET_KEY="$SECRET"
$GOOSED_CMD agent &
GOOSED_PID=$!

# Wait for goosed to be ready
echo "Waiting for goosed to start..."
for i in {1..30}; do
    if curl -s "http://localhost:${PORT}/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Goosed is running (PID: $GOOSED_PID)${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}Error: goosed failed to start${NC}"
        exit 1
    fi
    sleep 0.5
done

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                     Connection Information                         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}URL:${NC}        http://localhost:${PORT}"
echo -e "${GREEN}Secret Key:${NC} $SECRET"
echo ""
echo -e "${GREEN}✓ Goosed is running!${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop the server${NC}"
echo ""

# Keep the script running
wait
