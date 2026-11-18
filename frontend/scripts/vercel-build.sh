
#!/usr/bin/env bash

set -euo pipefail

# Move to the frontend directory
cd "$(dirname "$0")/.."

echo "Starting WorkSync production build..."

# Install the required .NET SDK
DOTNET_DIR="$PWD/.dotnet"
DOTNET="$DOTNET_DIR/dotnet"

if [[ ! -x "$DOTNET" ]] || ! "$DOTNET" --list-sdks | grep -q '^10\.0\.403 '; then
    echo "Installing .NET SDK 10.0.403..."

    mkdir -p "$DOTNET_DIR"

    curl -fsSL https://dot.net/v1/dotnet-install.sh \
        -o /tmp/dotnet-install.sh

    bash /tmp/dotnet-install.sh \
        --version 10.0.403 \
        --install-dir "$DOTNET_DIR"
fi

export DOTNET_ROOT="$DOTNET_DIR"
export PATH="$DOTNET_DIR:$PATH"

echo "Using .NET SDK:"
dotnet --version

# Read and validate the production API URL
if [[ -z "${WORKSYNC_API_BASE_URL:-}" ]]; then
    echo "ERROR: WORKSYNC_API_BASE_URL environment variable is missing."
    exit 1
fi

if [[ ! "$WORKSYNC_API_BASE_URL" =~ ^https?://[^/[:space:]]+(/[^[:space:]]*)?$ ]]; then
    echo "ERROR: WORKSYNC_API_BASE_URL must be a valid HTTP or HTTPS URL."
    exit 1
fi

echo "Production API URL validated."

# Publish the Blazor WebAssembly application
echo "Publishing WorkSync frontend..."

dotnet publish Worksync.Frontend.csproj \
    -c Release \
    -o publish

# Verify the published application
if [[ ! -f "publish/wwwroot/index.html" ]]; then
    echo "ERROR: Published index.html was not found."
    exit 1
fi

if [[ ! -d "publish/wwwroot/_framework" ]]; then
    echo "ERROR: Published _framework directory was not found."
    exit 1
fi

echo "Published Blazor files verified."

# Update only the published production configuration
export WORKSYNC_API_BASE_URL

python3 - <<'PY'
import json
import os
from pathlib import Path

config_path = Path("publish/wwwroot/appsettings.json")

if not config_path.is_file():
    raise SystemExit(
        f"ERROR: Published configuration not found: {config_path}"
    )

with config_path.open("r", encoding="utf-8") as file:
    config = json.load(file)

config["ApiBaseUrl"] = os.environ["WORKSYNC_API_BASE_URL"]

with config_path.open("w", encoding="utf-8") as file:
    json.dump(config, file, indent=2)
    file.write("\n")

print(f"Production API URL written to {config_path}")
PY

echo "WorkSync production build completed successfully!"
