#!/usr/bin/env bash
set -euo pipefail

# Install the `kmpfire` CLI from GitHub Releases (project: kmpfire_cli).
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/dungngminh/kmpfire_cli/main/install.sh | bash
#   curl -fsSL ... | bash -s -- v0.1.0

REPO="dungngminh/kmpfire_cli"
INSTALL_DIR="${KMPFIRE_INSTALL_DIR:-$HOME/.local/lib/kmpfire}"
BIN_DIR="${KMPFIRE_BIN_DIR:-$HOME/.local/bin}"
BIN_LINK="$BIN_DIR/kmpfire"
BINARY_NAME="kmpfire"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

info() { echo -e "${CYAN}[info]${RESET} $*" >&2; }
success() { echo -e "${GREEN}[✔]${RESET} $*" >&2; }
warn() { echo -e "${YELLOW}[warn]${RESET} $*" >&2; }
error() { echo -e "${RED}[✘]${RESET} $*" >&2; exit 1; }

detect_os() {
  local os
  os="$(uname -s)"
  case "$os" in
    Linux*) echo "linux" ;;
    Darwin*) echo "macos" ;;
    *) error "Unsupported operating system: $os (supported: macOS, Linux)" ;;
  esac
}

detect_arch() {
  local arch
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) echo "x64" ;;
    aarch64|arm64) echo "arm64" ;;
    *) error "Unsupported architecture: $arch" ;;
  esac
}

resolve_version() {
  local version="${1:-latest}"

  if [ "$version" = "latest" ]; then
    info "Resolving latest release..."
    version=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
      | grep -m 1 '"tag_name":' \
      | cut -d '"' -f 4)

    if [ -z "$version" ]; then
      error "Could not determine the latest release. Check https://github.com/$REPO/releases"
    fi
  fi

  echo "$version"
}

install() {
  local os="$1"
  local arch="$2"
  local version="$3"

  local asset_name="${BINARY_NAME}-${os}-${arch}.tar.gz"
  local download_url="https://github.com/$REPO/releases/download/$version/$asset_name"

  info "Detected: ${BOLD}${os}${RESET} / ${BOLD}${arch}${RESET}"
  info "Version: ${BOLD}${version}${RESET}"
  info "Downloading ${BOLD}${asset_name}${RESET}..."

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT

  local tmp_file="${tmp_dir}/${asset_name}"
  if ! curl -fsSL -o "$tmp_file" "$download_url"; then
    error "Download failed. URL: $download_url\nMake sure release $version exists with asset $asset_name."
  fi

  info "Extracting..."
  tar -xzf "$tmp_file" -C "$tmp_dir" || error "Failed to extract $tmp_file"

  local extracted_file="${tmp_dir}/${BINARY_NAME}-${os}-${arch}"
  if [ ! -f "$extracted_file" ]; then
    # Fallback: single file named kmpfire inside archive
    if [ -f "${tmp_dir}/${BINARY_NAME}" ]; then
      extracted_file="${tmp_dir}/${BINARY_NAME}"
    else
      error "Could not find expected binary '${BINARY_NAME}-${os}-${arch}' after extraction."
    fi
  fi

  mkdir -p "$INSTALL_DIR" "$BIN_DIR"
  local target_path="${INSTALL_DIR}/${BINARY_NAME}"
  mv "$extracted_file" "$target_path"
  chmod +x "$target_path"

  ln -sfn "$target_path" "$BIN_LINK"
  success "Installed to ${BOLD}${target_path}${RESET}"
  success "Linked ${BOLD}${BIN_LINK}${RESET}"
}

configure_path() {
  local path_entry="$BIN_DIR"
  local export_line="export PATH=\"$path_entry:\$PATH\""

  if echo "$PATH" | tr ':' '\n' | grep -qx "$path_entry"; then
    info "PATH already contains ${BOLD}${path_entry}${RESET}"
    return
  fi

  local configured=false
  local shell_name
  shell_name="$(basename "${SHELL:-/bin/bash}")"

  local bashrc="$HOME/.bashrc"
  if [ -f "$bashrc" ] || [ "$shell_name" = "bash" ]; then
    if ! grep -qF "$path_entry" "$bashrc" 2>/dev/null; then
      {
        echo ""
        echo "# kmpfire"
        echo "$export_line"
      } >> "$bashrc"
      success "Added to ${BOLD}~/.bashrc${RESET}"
      configured=true
    fi
  fi

  local zshrc="$HOME/.zshrc"
  if [ -f "$zshrc" ] || [ "$shell_name" = "zsh" ]; then
    if ! grep -qF "$path_entry" "$zshrc" 2>/dev/null; then
      {
        echo ""
        echo "# kmpfire"
        echo "$export_line"
      } >> "$zshrc"
      success "Added to ${BOLD}~/.zshrc${RESET}"
      configured=true
    fi
  fi

  local fish_config="$HOME/.config/fish/config.fish"
  if [ -f "$fish_config" ] || [ "$shell_name" = "fish" ]; then
    if ! grep -qF "$path_entry" "$fish_config" 2>/dev/null; then
      mkdir -p "$(dirname "$fish_config")"
      {
        echo ""
        echo "# kmpfire"
        echo "fish_add_path $path_entry"
      } >> "$fish_config"
      success "Added to ${BOLD}~/.config/fish/config.fish${RESET}"
      configured=true
    fi
  fi

  if [ "$configured" = false ]; then
    warn "Could not auto-update shell config."
    echo -e "  Add to your PATH manually:"
    echo -e "  ${CYAN}${export_line}${RESET}"
  fi
}

main() {
  local os arch version

  os="$(detect_os)"
  arch="$(detect_arch)"
  version="$(resolve_version "${1:-latest}")"

  install "$os" "$arch" "$version"
  configure_path

  echo ""
  echo -e "${GREEN}${BOLD}Installation completed!${RESET}"
  echo ""
  echo -e "  Restart your terminal or run:"
  echo -e "    ${CYAN}source ~/.bashrc${RESET}  or  ${CYAN}source ~/.zshrc${RESET}"
  echo ""
  echo -e "  Then verify with:"
  echo -e "    ${CYAN}kmpfire --version${RESET}"
  echo ""
}

main "$@"
