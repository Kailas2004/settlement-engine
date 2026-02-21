let autoRefreshInterval = null;
let dashboardStatsInterval = null;
let lastObservedLockAcquiredAt = null;
let lockRecentlyActiveUntil = 0;

const GLOBAL_REFRESH_INTERVAL_MS = 10000;
const DASHBOARD_STATS_INTERVAL_MS = 100;
const LOCK_RECENTLY_ACTIVE_WINDOW_MS = 2000;

function showSection(id) {
    document.querySelectorAll(".section").forEach(s => s.classList.add("hidden"));
    document.getElementById(id).classList.remove("hidden");
    syncDashboardStatsRefresh();
    refreshData();
}

function startAutoRefresh() {
    autoRefreshInterval = setInterval(refreshData, GLOBAL_REFRESH_INTERVAL_MS);
}

function isDashboardVisible() {
    const dashboard = document.getElementById("dashboard");
    return dashboard && !dashboard.classList.contains("hidden");
}

function stopDashboardStatsRefresh() {
    if (dashboardStatsInterval) {
        clearInterval(dashboardStatsInterval);
        dashboardStatsInterval = null;
    }
}

function startDashboardStatsRefresh() {
    stopDashboardStatsRefresh();

    // Keep lock/processing indicators responsive while dashboard is visible.
    dashboardStatsInterval = setInterval(() => {
        if (!isDashboardVisible()) return;
        loadStats().catch(() => {});
    }, DASHBOARD_STATS_INTERVAL_MS);
}

function syncDashboardStatsRefresh() {
    if (isDashboardVisible()) {
        startDashboardStatsRefresh();
    } else {
        stopDashboardStatsRefresh();
    }
}

function parseServerDateTime(value) {
    if (!value || typeof value !== "string") return NaN;

    // Spring LocalDateTime can include microseconds, which Date.parse may reject.
    const normalized = value.replace(/(\.\d{3})\d+$/, "$1");
    return Date.parse(normalized);
}

async function refreshData() {
    await loadStats();
    await loadTransactions();
    await loadLogs();
    await loadCustomers();
    await loadMerchants();

    const lastUpdated = document.getElementById("lastUpdated");
    if (lastUpdated) {
        lastUpdated.innerText =
            "Last Updated: " + new Date().toLocaleTimeString();
    }
}

/* ================= DASHBOARD ================= */

async function loadStats() {
    const res = await fetch("/api/settlements/stats");
    if (!res.ok) return;

    const s = await res.json();

    totalCard.innerHTML = `<h3>Total</h3><p>${s.totalTransactions}</p>`;
    capturedCard.innerHTML = `<h3>Captured</h3><p>${s.captured}</p>`;
    processingCard.innerHTML = `<h3>Processing</h3><p>${s.processing}</p>`;
    settledCard.innerHTML = `<h3>Settled</h3><p>${s.settled}</p>`;
    failedCard.innerHTML = `<h3>Failed</h3><p>${s.failed}</p>`;
    retryCard.innerHTML = `<h3>Avg Retry</h3><p>${Number(s.averageRetryCount).toFixed(2)}</p>`;

    updateAdvancedStats(s);
}

function updateAdvancedStats(stats) {
    const div = document.getElementById("settlementActivity");
    if (!div) return;

    let html = "";

    const acquiredAtMs = parseServerDateTime(stats.lastLockAcquiredAt);

    if (stats.lastLockAcquiredAt && stats.lastLockAcquiredAt !== lastObservedLockAcquiredAt) {
        lastObservedLockAcquiredAt = stats.lastLockAcquiredAt;
        if (!Number.isNaN(acquiredAtMs)) {
            lockRecentlyActiveUntil = Math.max(
                lockRecentlyActiveUntil,
                acquiredAtMs + LOCK_RECENTLY_ACTIVE_WINDOW_MS
            );
        }
    }

    const showActiveLock = stats.lockHeld || Date.now() < lockRecentlyActiveUntil;

    // Lock status
    if (showActiveLock) {
        html += `
            <div style="padding:10px;margin-bottom:8px;background:#fff3cd;color:#856404;border-radius:8px;">
                🔒 ${stats.lockHeld ? "Redis Lock Active" : "Redis Lock Recently Active"}
                <br>
                <small>Holder: ${stats.lockHolder || stats.lastLockHolder || "Unknown"}</small>
            </div>`;
    } else {
        html += `
            <div style="padding:10px;margin-bottom:8px;background:#e6fffa;color:#065f46;border-radius:8px;">
                🔓 No Lock Held
            </div>`;
    }

    // Settlement queue state
    if (stats.processing > 0) {
        html += `
            <div style="padding:10px;margin-bottom:8px;background:#fff3cd;color:#856404;border-radius:8px;">
                ⏳ Settlement Running — ${stats.processing} processing
            </div>`;
    } else if (stats.captured > 0) {
        html += `
            <div style="padding:10px;margin-bottom:8px;background:#fffbeb;color:#92400e;border-radius:8px;">
                🧾 Pending Settlements — ${stats.captured} captured
            </div>`;
    } else {
        html += `
            <div style="padding:10px;margin-bottom:8px;background:#e6fffa;color:#065f46;border-radius:8px;">
                ✅ No Active or Pending Settlements
            </div>`;
    }

    // Last run info
    if (stats.lastRunTime) {
        html += `
            <div style="padding:10px;background:#f0f9ff;color:#1e3a8a;border-radius:8px;">
                🕒 Last Run: ${stats.lastRunTime}
                <br>
                📦 Processed: ${stats.lastProcessedCount || 0}
                <br>
                <small>Source: ${stats.lastRunSource || "UNKNOWN"}</small>
            </div>`;
    }

    // Recent lock lifecycle details
    if (stats.lastLockAcquiredAt || stats.lastLockReleasedAt || stats.lastLockSkippedAt) {
        html += `
            <div style="padding:10px;margin-top:8px;background:#f8fafc;color:#334155;border-radius:8px;">
                🔐 Last Lock Acquired: ${stats.lastLockAcquiredAt || "-"}
                <br>
                🔓 Last Lock Released: ${stats.lastLockReleasedAt || "-"}
                <br>
                <small>Source: ${stats.lastLockSource || "UNKNOWN"}</small>
                <br>
                <small>Last Skipped Trigger: ${stats.lastLockSkippedAt || "-"}</small>
                <br>
                <small>Skipped Source: ${stats.lastSkippedLockSource || "-"}</small>
            </div>`;
    }

    div.innerHTML = html;
}

/* ================= CUSTOMERS ================= */

async function createCustomer() {
    const name = customerName.value.trim();
    const email = customerEmail.value.trim();

    const res = await fetch("/customers", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, email })
    });

    if (!res.ok) {
        const err = await res.text();
        alert("Customer creation failed:\n" + err);
        return;
    }

    customerName.value = "";
    customerEmail.value = "";
    loadCustomers();
}

async function loadCustomers() {
    const res = await fetch("/customers");
    if (!res.ok) return;

    const data = await res.json();

    let html = `<tr><th>ID</th><th>Name</th><th>Email</th></tr>`;
    data.forEach(c => {
        html += `<tr>
            <td>${c.id}</td>
            <td>${c.name}</td>
            <td>${c.email}</td>
        </tr>`;
    });

    customersTable.innerHTML = html;
}

/* ================= MERCHANTS ================= */

async function createMerchant() {
    const name = merchantName.value.trim();
    const bankAccount = merchantBank.value.trim();
    const settlementCycle = merchantCycle.value.trim();

    const res = await fetch("/merchants", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, bankAccount, settlementCycle })
    });

    if (!res.ok) {
        const err = await res.text();
        alert("Merchant creation failed:\n" + err);
        return;
    }

    merchantName.value = "";
    merchantBank.value = "";
    merchantCycle.value = "";
    loadMerchants();
}

async function loadMerchants() {
    const res = await fetch("/merchants");
    if (!res.ok) return;

    const data = await res.json();

    let html = `<tr><th>ID</th><th>Name</th><th>Bank</th><th>Cycle</th></tr>`;
    data.forEach(m => {
        html += `<tr>
            <td>${m.id}</td>
            <td>${m.name}</td>
            <td>${m.bankAccount}</td>
            <td>${m.settlementCycle}</td>
        </tr>`;
    });

    merchantsTable.innerHTML = html;
}

/* ================= TRANSACTIONS ================= */

async function createTransaction() {
    const cId = parseInt(customerId.value);
    const mId = parseInt(merchantId.value);
    const amt = parseFloat(amount.value);

    if (!cId || !mId || !amt) {
        alert("Please enter valid customer ID, merchant ID, and amount.");
        return;
    }

    const res = await fetch(
        `/transactions?customerId=${cId}&merchantId=${mId}&amount=${amt}`,
        { method: "POST" }
    );

    if (!res.ok) {
        const err = await res.text();
        alert("Transaction creation failed:\n" + err);
        return;
    }

    amount.value = "";
    loadTransactions();
}

async function loadTransactions() {
    const res = await fetch("/transactions");
    if (!res.ok) return;

    const data = await res.json();

    let html = `<tr>
        <th>ID</th><th>Amount</th><th>Status</th>
        <th>Retry</th><th>Created</th>
    </tr>`;

    data.forEach(t => {
        html += `<tr>
            <td>${t.id}</td>
            <td>${t.amount}</td>
            <td class="status-${t.status.toLowerCase()}">${t.status}</td>
            <td>${t.retryCount} / ${t.maxRetries}</td>
            <td>${t.createdAt}</td>
        </tr>`;
    });

    transactionsTable.innerHTML = html;
}

/* ================= LOGS ================= */

async function loadLogs() {
    const res = await fetch("/logs");
    if (!res.ok) return;

    const data = await res.json();

    let html = `<tr>
        <th>ID</th><th>Transaction</th>
        <th>Attempt</th><th>Result</th><th>Message</th>
    </tr>`;

    data.forEach(l => {
        html += `<tr>
            <td>${l.id}</td>
            <td>${l.transactionId || "-"}</td>
            <td>${l.attemptNumber}</td>
            <td>${l.result}</td>
            <td>${l.message}</td>
        </tr>`;
    });

    logsTable.innerHTML = html;
}

/* ================= TRIGGER ================= */

async function triggerSettlement() {
    if (!confirm("Trigger settlement now?")) return;

    // Fast polling while trigger is in flight lets the UI show lock acquire/release.
    const lockPollInterval = setInterval(loadStats, 300);
    let res;
    try {
        res = await fetch("/settlement/trigger", { method: "POST" });
    } finally {
        clearInterval(lockPollInterval);
    }

    if (!res.ok) {
        alert("Failed to trigger settlement");
        return;
    }

    await loadStats();
    refreshData();
}

/* ================= INIT ================= */

refreshData();
startAutoRefresh();
syncDashboardStatsRefresh();
