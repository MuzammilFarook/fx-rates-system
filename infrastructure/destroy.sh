#!/bin/bash

# ============================================================================
# FX Rates System - Azure Infrastructure Cleanup Script
# ============================================================================
# This script deletes all Azure resources to stop costs
#
# Prerequisites:
#   - Azure CLI installed
#   - Logged in to Azure (az login)
#
# Usage:
#   ./destroy.sh [--yes]
#
# Options:
#   --yes    Skip confirmation prompt (use with caution!)
# ============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}======================================================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}======================================================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# ============================================================================
# Parse Arguments
# ============================================================================

SKIP_CONFIRMATION=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --yes|-y)
            SKIP_CONFIRMATION=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: ./destroy.sh [--yes]"
            exit 1
            ;;
    esac
done

# ============================================================================
# Prerequisites Check
# ============================================================================

print_header "🔍 Checking Prerequisites"

# Check Azure CLI
if ! command -v az &> /dev/null; then
    print_error "Azure CLI not found!"
    exit 1
fi
print_success "Azure CLI installed"

# Check if logged in
if ! az account show &> /dev/null; then
    print_error "Not logged in to Azure!"
    echo "Run: az login"
    exit 1
fi
print_success "Logged in to Azure"

echo ""

# ============================================================================
# Find Resource Group
# ============================================================================

print_header "🔍 Finding FX Rates Resources"

RESOURCE_GROUP="fexco-fx-rates-rg"

# Check if resource group exists
if ! az group exists --name "$RESOURCE_GROUP" | grep -q "true"; then
    print_warning "Resource group '$RESOURCE_GROUP' not found"
    echo ""
    echo "Looking for other FX Rates resource groups..."

    # List all resource groups with FX Rates tag
    RG_LIST=$(az group list --tag Project=FX-Rates-System --query "[].name" -o tsv)

    if [ -z "$RG_LIST" ]; then
        print_info "No FX Rates System resources found. Nothing to delete."
        exit 0
    fi

    echo ""
    echo "Found these resource groups:"
    echo "$RG_LIST"
    echo ""

    read -p "Enter resource group name to delete: " RESOURCE_GROUP

    if [ -z "$RESOURCE_GROUP" ]; then
        print_error "No resource group specified"
        exit 1
    fi
fi

print_success "Found resource group: $RESOURCE_GROUP"

echo ""

# ============================================================================
# Show Resources to be Deleted
# ============================================================================

print_header "📋 Resources to be Deleted"

echo ""
print_info "Listing resources in $RESOURCE_GROUP..."
echo ""

az resource list --resource-group "$RESOURCE_GROUP" --output table

echo ""

# ============================================================================
# Estimate Cost Savings
# ============================================================================

print_info "💰 This will stop all Azure charges for the FX Rates System"
print_info "   Estimated savings: ~\$12-15/month"

echo ""

# ============================================================================
# Confirmation
# ============================================================================

if [ "$SKIP_CONFIRMATION" = false ]; then
    print_warning "⚠️  WARNING: This will DELETE ALL resources in '$RESOURCE_GROUP'"
    print_warning "⚠️  This action CANNOT be undone!"
    echo ""

    read -p "Are you sure you want to proceed? (type 'yes' to confirm): " CONFIRMATION

    if [ "$CONFIRMATION" != "yes" ]; then
        print_info "Deletion cancelled"
        exit 0
    fi
fi

echo ""

# ============================================================================
# Delete Resource Group
# ============================================================================

print_header "🗑️  Deleting Resources"

echo ""
print_info "Deleting resource group: $RESOURCE_GROUP"
print_info "This may take 3-5 minutes..."
echo ""

# Delete with --no-wait for async deletion
az group delete \
    --name "$RESOURCE_GROUP" \
    --yes \
    --no-wait

print_success "Deletion initiated"

echo ""

# ============================================================================
# Cleanup Local Files
# ============================================================================

print_header "🧹 Cleaning Up Local Files"

if [ -f "../.env" ]; then
    print_info "Backing up .env to .env.backup"
    mv ../.env ../.env.backup
    print_success ".env backed up"
fi

echo ""

# ============================================================================
# Summary
# ============================================================================

print_header "✅ Cleanup Initiated!"

echo ""
echo "📊 What Happened:"
echo "  • Resource group deletion started (async)"
echo "  • All Azure resources will be deleted"
echo "  • Billing will stop once deletion completes"
echo ""

echo "⏱️  Deletion Timeline:"
echo "  • Initial deletion: Started now"
echo "  • Full cleanup: 3-5 minutes"
echo "  • Billing stops: As resources are deleted"
echo ""

echo "🔍 Monitor Deletion Progress:"
echo "   az group show --name $RESOURCE_GROUP"
echo ""

echo "📋 To Redeploy Later:"
echo "   cd infrastructure && ./deploy.sh"
echo ""

print_header "🎉 Done!"

echo ""
print_info "You can close this terminal. Deletion continues in background."
print_info "Verify completion in Azure Portal or with: az group list"
echo ""
