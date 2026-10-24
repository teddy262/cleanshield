#!/usr/bin/env bash
# Adapts CleanFrame's Samsung-specific wording and controls for the Nvidia Shield.
# Usage: patch-ui.sh <path to the copied CleanFrame app folder>
set -e
DIR="$1"
F="$DIR/main.js"
test -f "$F" || { echo "main.js not found in $DIR"; exit 1; }

# The Art Mode control is a Samsung Frame feature: remove it from the Home controls.
sed -i "s/function utils() { return \['art', 'apps', 'settings'\]; }/function utils() { return ['apps', 'settings']; }/" "$F"
grep -q "function utils() { return \['apps', 'settings'\]; }" "$F" || { echo "Could not remove the Art Mode control"; exit 1; }

# Samsung wording -> Shield wording.
sed -i "s/Open Samsung Home?/Open the Shield home screen?/" "$F"
sed -i "s/Samsung Home'\]\]/Shield Home']]/" "$F"
sed -i "s/perform('Samsung Home'/perform('Shield Home'/g" "$F"
sed -i "s/label: 'Samsung Home'/label: 'Shield Home'/" "$F"
sed -i "s/Open Samsung’s Home screen/Open the stock Shield home screen/" "$F"
sed -i "s/label: 'TV settings'/label: 'Shield settings'/" "$F"
sed -i "s/perform('TV settings'/perform('Shield settings'/" "$F"
sed -i "s/Home and TV settings/Home and Shield settings/" "$F"
sed -i "s/label: 'Samsung', value/label: 'Shield', value/" "$F"
sed -i "s/samsung: 'Samsung' }/samsung: 'Shield' }/" "$F"

echo "Remaining mentions of Samsung (informational):"
grep -n "Samsung" "$F" | cut -c1-140 || true
echo "Patched $F"
