# B2B Enterprise Pivot Strategy: From "Voice Assistant" to "Que Automation Engine"

This document details the transformation of `que-mobile-sdk` into a robust, enterprise-grade B2B product. The goal is to sell **Reliability** and **Automation**, not "AI Features".

## 1. Product Positioning: "Appium on Steroids"

Shift the narrative from a Consumer Voice Assistant to an **Intelligent Test Automation Framework**.

*   **Current (Weak)**: "An AI that uses your phone for you." (Niche, scary to users, unstable).
*   **Target (Strong)**: "Self-healing end-to-end testing SDK that fixes broken scripts automatically using AI." (High value, solves huge Enterprise pain point).

## 2. Technical Roadmap for Enterprise Readiness

### Feature 1: "Headless" Operation
Enterprises run tests on server farms (Device Farms like AWS Device Farm, BrowserStack). They don't look at the screen.
*   **Action**: Create a `HeadlessService` that replaces `CosmicOverlayService`.
*   **Implementation**: Remove all UI overlays. The Agent runs in the background. If it needs to "speak" or "show progress," it sends a broadcast intent or writes to a log file instead of showing a Bubble.

### Feature 2: Structured Logging (The "Truth" Source)
Enterprises need to parse results programmatically.
*   **Action**: Implement `JsonLogger`.
*   **Format**:
    ```json
    {
      "timestamp": "2024-12-17T10:00:00Z",
      "level": "INFO",
      "step": 5,
      "event": "ACTION_EXECUTED",
      "details": {
        "action": "Click",
        "target": "login_button",
        "result": "SUCCESS"
      }
    }
    ```
*   **Benefit**: Allows integration with Splunk, Datadog, or custom dashboards.

### Feature 3: The "Script Recorder" (Low-Code Tool)
Enterprises want to "record" a golden path (e.g., Checkout Flow) and have the AI replay it.
*   **Action**: Build a simple "Recorder Mode" in `QueAccessibilityService`.
*   **Logic**:
    1.  User manually taps "Add to Cart".
    2.  SDK captures: `Activity: ProductPage`, `Element: "Add to Cart" (ID: btn_add)`.
    3.  Save as a "Goal" or "Hint" for the Agent.
    4.  When Replaying: If "btn_add" ID changes to "btn_new_add", the LLM uses the label "Add to Cart" to find it anyway (Self-Healing).

### Feature 4: ADB & CI/CD Control
QA Engineers use CI/CD (Jenkins, GitHub Actions). They need to start the agent via command line.
*   **Action**: Expose an exported BroadcastReceiver or Activity for ADB commands.
*   **Command**:
    ```bash
    adb shell am start-service -a com.que.START_AGENT \
      --es task "Login with user test@example.com" \
      --es mode "headless"
    ```

## 3. What to Fix (Immediate "De-Risking")

1.  **Remove Fragility**: The current `QueAccessibilityService` likely crashes if the UI tree is null. Wrap every `rootInActiveWindow` call in a `retry(3)` block.
2.  **Permission Stability**: In B2B, you use **MDM (Mobile Device Management)** or **ADB** to grant permissions once. Document strictly which ADB commands grant the necessary permissions permanently so the service doesn't get killed.
    *   `adb shell settings put secure enabled_accessibility_services ...`

## 4. Monetization Model (B2B)

1.  **SDK Licensing**: Sell the `.aar` implementation to App Development shops.
2.  **SaaS Platform**: "Que Cloud". They upload their APK, your device farm runs the AI Agent to "Monkey Test" it intelligently.
3.  **Seat Model**: $X/month per QA engineer using the "Self-Healing Script" tool.
