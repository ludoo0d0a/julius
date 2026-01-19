#!/bin/bash
# Helper script to run GeminiRealApiTests.testListModels on desktop
# Displays the list of models output in the console

cd "$(dirname "$0")"

echo "Running GeminiRealApiTests.testListModels..."
echo ""

# Run desktop tests and filter output to show only testListModels results
./gradlew :shared:desktopTestGeminiListModels --rerun-tasks --continue 2>&1 | \
  tee /tmp/gemini-test-output.txt

echo ""
echo "============================================================"
echo "Searching for Gemini testListModels output..."
echo "============================================================"

# Extract testListModels output
if grep -q "GeminiListModelsTest\|GeminiRealApiTests.*testListModels\|✅.*Gemini.*ListModels\|ListModels Response" /tmp/gemini-test-output.txt; then
    echo ""
    echo "Found testListModels output:"
    echo ""
    grep -A 200 "GeminiListModelsTest\|GeminiRealApiTests.*testListModels\|✅.*Gemini.*ListModels\|ListModels Response" /tmp/gemini-test-output.txt | \
      head -100
else
    echo ""
    echo "⚠️  testListModels output not found in test results"
    echo ""
    echo "Checking if Gemini tests ran:"
    grep -i "gemini\|testListModels" /tmp/gemini-test-output.txt | head -10
    echo ""
    echo "Full output saved to: /tmp/gemini-test-output.txt"
    echo "Run: cat /tmp/gemini-test-output.txt | grep -i gemini"
fi
