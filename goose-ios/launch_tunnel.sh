#!/bin/bash
set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuration
PREFERRED_PORT=62998
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GOOSED_URL="https://github.com/michaelneale/goose-tunnel/releases/download/test/goosed"
TUNNEL_DIR="${HOME}/.goose-tunnel"
GOOSED_LOCAL_PATH="${TUNNEL_DIR}/goosed"
TUNNEL_REPO="https://github.com/michaelneale/lapstone-tunnel"
TUNNEL_CLIENT_PATH="${TUNNEL_DIR}/client.js"
WORKER_URL="https://cloudflare-tunnel-proxy.michael-neale.workers.dev"

# Function to find an available port starting from the preferred port
find_available_port() {
    local start_port=$1
    local max_attempts=100
    local port=$start_port

    for ((i=0; i<max_attempts; i++)); do
        if lsof -i:$port >/dev/null 2>&1; then
            echo -e "${YELLOW}Port $port is in use, trying next...${NC}" >&2
            ((port++))
        else
            echo $port
            return 0
        fi
    done

    echo -e "${RED}Error: Could not find an available port after $max_attempts attempts${NC}" >&2
    return 1
}

# Find an available port
echo -e "${BLUE}Checking for available port starting from $PREFERRED_PORT...${NC}"
PORT=$(find_available_port $PREFERRED_PORT)
if [ $? -ne 0 ]; then
    exit 1
fi

if [ $PORT -eq $PREFERRED_PORT ]; then
    echo -e "${GREEN}✓ Using preferred port $PORT${NC}"
else
    echo -e "${YELLOW}✓ Using available port $PORT (preferred $PREFERRED_PORT was in use)${NC}"
fi

# Function to download goosed binary
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
    
    # Ensure tunnel directory exists
    mkdir -p "$TUNNEL_DIR"
    
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
        sleep 1
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

# Check if qrencode is available for QR code generation, install if not
if ! command -v qrencode &> /dev/null; then
    echo -e "${YELLOW}qrencode not found, installing via Homebrew...${NC}"
    if command -v brew &> /dev/null; then
        brew install qrencode
        if ! command -v qrencode &> /dev/null; then
            echo -e "${RED}Error: Failed to install qrencode${NC}"
            exit 1
        fi
        echo -e "${GREEN}✓ qrencode installed successfully${NC}"
    else
        echo -e "${RED}Error: Homebrew not found and qrencode is not installed${NC}"
        echo -e "${YELLOW}Please install Homebrew first: https://brew.sh${NC}"
        echo -e "${YELLOW}Then install qrencode: brew install qrencode${NC}"
        exit 1
    fi
fi

# Function to setup tunnel client
setup_tunnel_client() {
    # Check if it's a proper git repository
    if [ ! -d "$TUNNEL_DIR/.git" ]; then
        # Directory might exist but not be a git repo, or not exist at all
        if [ -d "$TUNNEL_DIR" ]; then
            echo -e "${YELLOW}$TUNNEL_DIR exists but is not a git repository${NC}"
            echo -e "${YELLOW}Saving existing binary and re-cloning...${NC}"
            # Move the binary if it exists
            if [ -f "$TUNNEL_DIR/goosed" ]; then
                mv "$TUNNEL_DIR/goosed" /tmp/goosed.backup
            fi
            rm -rf "$TUNNEL_DIR"
        fi
        
        echo -e "${YELLOW}Cloning lapstone-tunnel repository...${NC}"
        if git clone "$TUNNEL_REPO" "$TUNNEL_DIR"; then
            echo -e "${GREEN}✓ Repository cloned successfully${NC}"
            # Restore the binary if we backed it up
            if [ -f /tmp/goosed.backup ]; then
                mv /tmp/goosed.backup "$TUNNEL_DIR/goosed"
                chmod +x "$TUNNEL_DIR/goosed"
            fi
        else
            echo -e "${RED}Error: Failed to clone tunnel repository${NC}"
            return 1
        fi
    else
        echo -e "${GREEN}✓ Tunnel repository already exists at $TUNNEL_DIR${NC}"
        # Optionally pull latest changes
        echo -e "${YELLOW}Updating tunnel client...${NC}"
        (cd "$TUNNEL_DIR" && git pull --quiet) || echo -e "${YELLOW}Note: Could not update repository (may be modified)${NC}"
    fi
    
    # Check if client.js exists
    if [ ! -f "$TUNNEL_CLIENT_PATH" ]; then
        echo -e "${RED}Error: client.js not found in tunnel repository${NC}"
        echo -e "${YELLOW}Try removing $TUNNEL_DIR and run the script again${NC}"
        return 1
    fi
    
    # Install npm dependencies if needed
    if [ ! -d "$TUNNEL_DIR/node_modules" ]; then
        echo -e "${YELLOW}Installing tunnel client dependencies...${NC}"
        (cd "$TUNNEL_DIR" && npm install)
        if [ $? -ne 0 ]; then
            echo -e "${RED}Error: Failed to install npm dependencies${NC}"
            return 1
        fi
        echo -e "${GREEN}✓ Dependencies installed${NC}"
    fi
    
    return 0
}

# Setup tunnel client
if ! setup_tunnel_client; then
    echo -e "${RED}Error: Failed to setup tunnel client${NC}"
    exit 1
fi

# Generate a deterministic agent ID based on hostname/username
# This ensures the same tunnel URL each time the script is run
MACHINE_ID=$(echo -n "$(hostname)-$(whoami)" | openssl dgst -sha256 | cut -d' ' -f2 | cut -c1-16)
AGENT_ID="goose-${MACHINE_ID}"
SECRET="tunnel_$(openssl rand -hex 16)"

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║              Goose Cloudflare Tunnel Remote Access                 ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Shutting down...${NC}"
    if [ ! -z "$GOOSED_PID" ]; then
        echo "Stopping goosed (PID: $GOOSED_PID)"
        kill $GOOSED_PID 2>/dev/null || true
    fi
    if [ ! -z "$TUNNEL_PID" ]; then
        echo "Stopping tunnel client (PID: $TUNNEL_PID)"
        kill $TUNNEL_PID 2>/dev/null || true
    fi
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# Start goosed in the background
echo -e "${GREEN}Starting goosed on port ${PORT}...${NC}"
export GOOSE_PORT=$PORT
export GOOSE_SERVER__SECRET_KEY="$SECRET"
$GOOSED_CMD agent > /dev/null 2>&1 &
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

# Start the cloudflare tunnel client
echo -e "${GREEN}Starting Cloudflare tunnel client...${NC}"
echo -e "${CYAN}Agent ID: ${AGENT_ID}${NC}"

# Start tunnel client in background and capture its output
node "$TUNNEL_CLIENT_PATH" "$WORKER_URL" "$AGENT_ID" "http://localhost:$PORT" > /tmp/tunnel_output.log 2>&1 &
TUNNEL_PID=$!

# Wait for tunnel to be established and capture the public URL
echo "Waiting for tunnel to establish..."
TUNNEL_URL=""
for i in {1..30}; do
    if [ -f /tmp/tunnel_output.log ]; then
        # Extract the public URL from the log
        TUNNEL_URL=$(grep "Public URL:" /tmp/tunnel_output.log | tail -1 | sed 's/.*Public URL: //' | tr -d '\n\r')
        if [ ! -z "$TUNNEL_URL" ]; then
            echo -e "${GREEN}✓ Tunnel established (PID: $TUNNEL_PID)${NC}"
            break
        fi
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}Error: Tunnel failed to establish${NC}"
        cat /tmp/tunnel_output.log
        exit 1
    fi
    sleep 0.5
done

# Create the configuration JSON for the QR code
CONFIG_JSON="{\"url\":\"${TUNNEL_URL}\",\"secret\":\"${SECRET}\"}"

# URL encode the config JSON
URL_ENCODED_CONFIG=$(printf %s "$CONFIG_JSON" | jq -sRr @uri)

# Create the app URL for deep linking
APP_URL="goosechat://configure?data=${URL_ENCODED_CONFIG}"

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                     Connection Information                         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}Tunnel URL:${NC}    $TUNNEL_URL"
echo -e "${GREEN}Agent ID:${NC}      $AGENT_ID"
echo -e "${GREEN}Secret Key:${NC}    $SECRET"
echo -e "${GREEN}Local Port:${NC}    $PORT"
echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                          QR Code (Scan Me!)                        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Generate and display QR code in terminal
qrencode -t ANSIUTF8 "$APP_URL"

echo ""
echo -e "${BLUE}────────────────────────────────────────────────────────────────────${NC}"
echo -e "${YELLOW}App URL:${NC} $APP_URL"
echo -e "${BLUE}────────────────────────────────────────────────────────────────────${NC}"
echo ""
echo -e "${GREEN}✓ Everything is running!${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop the server and tunnel${NC}"
echo ""
echo -e "${CYAN}Note: This uses a public shared tunnel service - best effort only!${NC}"
echo -e "${CYAN}For production use, consider deploying your own tunnel service.${NC}"
echo ""

# Keep the script running
wait
