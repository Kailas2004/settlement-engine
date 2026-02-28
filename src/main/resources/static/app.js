let autoRefreshInterval = null;
let dashboardStatsInterval = null;
let lastObservedLockAcquiredAt = null;
let lockRecentlyActiveUntil = 0;
let currentUser = null;
let currentRoles = [];

const GLOBAL_REFRESH_INTERVAL_MS = 10000;
const DASHBOARD_STATS_INTERVAL_MS = 100;
const LOCK_RECENTLY_ACTIVE_WINDOW_MS = 2000;
const TABLE_PAGE_SIZE = 10;

const tablePageState = {
    customers: 1,
    merchants: 1,
    transactions: 1,
    exceptions: 1,
    logs: 1
};

const tableDataCache = {
    customers: [],
    merchants: [],
    transactions: [],
    exceptions: [],
    logs: []
};

function hasRole(role) {
    return currentRoles.includes(role) || currentRoles.includes(`ROLE_${role}`);
}

function isAdminUser() {
    return hasRole("ADMIN");
}

function requireAdminAction(actionName) {
    if (isAdminUser()) return true;
    alert(`${actionName} is available only for admin users.`);
    return false;
}

function setElementVisible(id, visible) {
    const el = document.getElementById(id);
    if (!el) return;
    el.style.display = visible ? "" : "none";
}

function getPaginatedSlice(tableKey, data) {
    const totalItems = Array.isArray(data) ? data.length : 0;
    const totalPages = Math.max(1, Math.ceil(totalItems / TABLE_PAGE_SIZE));
    const currentPage = Math.min(
        Math.max(tablePageState[tableKey] || 1, 1),
        totalPages
    );

    tablePageState[tableKey] = currentPage;

    const startIndex = (currentPage - 1) * TABLE_PAGE_SIZE;
    const items = (data || []).slice(startIndex, startIndex + TABLE_PAGE_SIZE);
    const endIndex = startIndex + items.length;

    return {
        items,
        totalItems,
        totalPages,
        currentPage,
        startIndex,
        endIndex
    };
}

function renderPagination(containerId, tableKey, pageInfo) {
    const container = document.getElementById(containerId);
    if (!container) return;

    if (!pageInfo || pageInfo.totalItems === 0) {
        container.innerHTML = "";
        return;
    }

    const { currentPage, totalPages, totalItems, startIndex, endIndex } = pageInfo;
    const startDisplay = startIndex + 1;
    const endDisplay = endIndex;
    const prevDisabled = currentPage <= 1 ? "disabled" : "";
    const nextDisabled = currentPage >= totalPages ? "disabled" : "";

    container.innerHTML = `
        <div class="pagination-inner">
            <button class="page-btn" ${prevDisabled} onclick="setTablePage('${tableKey}', ${currentPage - 1})">Previous</button>
            <span class="page-meta">Page ${currentPage} of ${totalPages}</span>
            <span class="page-count">${startDisplay}-${endDisplay} of ${totalItems}</span>
            <button class="page-btn" ${nextDisabled} onclick="setTablePage('${tableKey}', ${currentPage + 1})">Next</button>
        </div>
    `;
}

function setTablePage(tableKey, nextPage) {
    tablePageState[tableKey] = nextPage;
    renderTableByKey(tableKey);
}

function renderTableByKey(tableKey) {
    switch (tableKey) {
        case "customers":
            renderCustomersTable();
            break;
        case "merchants":
            renderMerchantsTable();
            break;
        case "transactions":
            renderTransactionsTable();
            break;
        case "exceptions":
            renderExceptionsTable();
            break;
        case "logs":
            renderLogsTable();
            break;
        default:
            break;
    }
}

function showSection(id) {
    document.querySelectorAll(".section").forEach(s => s.classList.add("hidden"));

    const section = document.getElementById(id);
    if (section) {
        section.classList.remove("hidden");
    }

    document.querySelectorAll(".menu-btn[data-section]").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.section === id);
    });

    const sectionTitle = document.getElementById("sectionTitle");
    if (sectionTitle && section) {
        sectionTitle.innerText = section.dataset.title || "Settlement Engine";
    }

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

async function readErrorMessage(response) {
    try {
        const body = await response.text();
        if (!body) {
            return `HTTP ${response.status}`;
        }

        try {
            const parsed = JSON.parse(body);
            if (parsed && parsed.message) {
                return `HTTP ${response.status}: ${parsed.message}`;
            }
        } catch (_) {
            // keep raw body fallback
        }

        return `HTTP ${response.status}: ${body}`;
    } catch (_) {
        return `HTTP ${response.status}`;
    }
}

async function loadCurrentUser() {
    const res = await fetch("/api/auth/me");
    if (res.status === 401 || res.status === 403) {
        window.location.href = "/login.html";
        return;
    }
    if (!res.ok) {
        throw new Error(await readErrorMessage(res));
    }

    const data = await res.json();
    currentUser = data.username || null;
    currentRoles = Array.isArray(data.roles) ? data.roles : [];
}

function applyRoleAccess() {
    const isAdmin = isAdminUser();

    setElementVisible("customerForm", isAdmin);
    setElementVisible("merchantForm", isAdmin);
    setElementVisible("transactionForm", isAdmin);
    setElementVisible("reconciliationControls", isAdmin);
    setElementVisible("triggerSettlementBtn", isAdmin);

    const authInfo = document.getElementById("authInfo");
    if (authInfo) {
        const roleLabel = isAdmin ? "ADMIN" : "USER";
        authInfo.innerText = `${currentUser || "unknown"} (${roleLabel})`;
    }

    const brandRoleLabel = document.getElementById("brandRoleLabel");
    if (brandRoleLabel) {
        brandRoleLabel.innerText = isAdmin ? "System Admin" : "System User";
    }
}

async function refreshData() {
    await loadStats();
    await loadTransactions();
    await loadExceptionQueue();
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
    const res = await fetch(`/api/settlements/stats?t=${Date.now()}`, {
        cache: "no-store"
    });
    if (!res.ok) return;

    const s = await res.json();

    renderMetricCard(totalCard, "Total", Number(s.totalTransactions || 0).toLocaleString(), "total");
    renderMetricCard(capturedCard, "Captured", Number(s.captured || 0).toLocaleString(), "captured");
    renderMetricCard(processingCard, "Processing", Number(s.processing || 0).toLocaleString(), "processing");
    renderMetricCard(settledCard, "Settled", Number(s.settled || 0).toLocaleString(), "settled");
    renderMetricCard(failedCard, "Failed", Number(s.failed || 0).toLocaleString(), "failed");
    renderMetricCard(exceptionCard, "Exceptions", Number(s.exceptionQueued || 0).toLocaleString(), "exceptions");
    renderMetricCard(retryCard, "Avg Retry", Number(s.averageRetryCount).toFixed(2), "retry");

    updateAdvancedStats(s);
}

function renderMetricCard(cardEl, title, value, iconType) {
    if (!cardEl) return;
    cardEl.innerHTML = `
        <div class="card-head">
            <h3>${title}</h3>
            <span class="card-icon card-icon-${iconType}" aria-hidden="true">${metricIconSvg(iconType)}</span>
        </div>
        <p>${value}</p>
    `;
}

function metricIconSvg(iconType) {
    switch (iconType) {
        case "total":
            return `<svg viewBox="0 0 24 24"><path d="M5 19H19"/><path d="M8 16V10"/><path d="M12 16V7"/><path d="M16 16V12"/></svg>`;
        case "captured":
            return `<svg viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="12" rx="2"/><path d="M3 10H21"/><path d="M7 14H10"/></svg>`;
        case "processing":
            return `<svg viewBox="0 0 24 24"><path d="M13 3L6 14H12L11 21L18 10H12L13 3Z"/></svg>`;
        case "settled":
            return `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M8 12L11 15L16 9"/></svg>`;
        case "failed":
            return `<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 8V12"/><path d="M12 16H12.01"/></svg>`;
        case "exceptions":
            return `<svg viewBox="0 0 24 24"><path d="M12 3L22 21H2L12 3Z"/><path d="M12 9V13"/><path d="M12 17H12.01"/></svg>`;
        case "retry":
            return `<svg viewBox="0 0 24 24"><path d="M20 11A8 8 0 1 1 12 4"/><path d="M20 4V11H13"/></svg>`;
        default:
            return "";
    }
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
            <div class="activity-item warn">
                <strong>${stats.lockHeld ? "Redis Lock Active" : "Redis Lock Recently Active"}</strong>
                <small>Holder: ${stats.lockHolder || stats.lastLockHolder || "Unknown"}</small>
            </div>`;
    } else {
        html += `
            <div class="activity-item success">
                <strong>No Lock Held</strong>
            </div>`;
    }

    // Settlement queue state
    if (stats.processing > 0) {
        html += `
            <div class="activity-item warn">
                <strong>Settlement Running</strong>
                <small>${stats.processing} transaction(s) currently processing.</small>
            </div>`;
    } else if (stats.captured > 0) {
        html += `
            <div class="activity-item warn">
                <strong>Pending Settlements</strong>
                <small>${stats.captured} captured transaction(s) waiting.</small>
            </div>`;
    } else {
        html += `
            <div class="activity-item success">
                <strong>No Active or Pending Settlements</strong>
            </div>`;
    }

    // Last run info
    if (stats.lastRunTime) {
        html += `
            <div class="activity-item info">
                <strong>Last Run: ${stats.lastRunTime}</strong>
                <small>Processed: ${stats.lastProcessedCount || 0} | Source: ${stats.lastRunSource || "UNKNOWN"}</small>
            </div>`;
    }

    // Recent lock lifecycle details
    if (stats.lastLockAcquiredAt || stats.lastLockReleasedAt || stats.lastLockSkippedAt) {
        html += `
            <div class="activity-item neutral">
                <strong>Lock Lifecycle</strong>
                <small>Acquired: ${stats.lastLockAcquiredAt || "-"}</small>
                <br>
                <small>Released: ${stats.lastLockReleasedAt || "-"}</small>
                <br>
                <small>Source: ${stats.lastLockSource || "UNKNOWN"}</small>
                <br>
                <small>Skipped Trigger: ${stats.lastLockSkippedAt || "-"}</small>
                <br>
                <small>Skipped Source: ${stats.lastSkippedLockSource || "-"}</small>
            </div>`;
    }

    div.innerHTML = html;
}

/* ================= CUSTOMERS ================= */

async function createCustomer() {
    if (!requireAdminAction("Customer creation")) return;

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

    tableDataCache.customers = await res.json();
    renderCustomersTable();
}

function renderCustomersTable() {
    const table = document.getElementById("customersTable");
    if (!table) return;

    const pageInfo = getPaginatedSlice("customers", tableDataCache.customers);
    let html = `<tr><th>ID</th><th>Name</th><th>Email</th></tr>`;

    if (pageInfo.items.length === 0) {
        html += `<tr><td colspan="3">No customers found.</td></tr>`;
    }

    pageInfo.items.forEach(c => {
        html += `<tr>
            <td>${c.id}</td>
            <td>${c.name}</td>
            <td>${c.email}</td>
        </tr>`;
    });

    table.innerHTML = html;
    renderPagination("customersPagination", "customers", pageInfo);
}

/* ================= MERCHANTS ================= */

async function createMerchant() {
    if (!requireAdminAction("Merchant creation")) return;

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
    merchantCycle.value = "DAILY";
    loadMerchants();
}

async function loadMerchants() {
    const res = await fetch("/merchants");
    if (!res.ok) return;

    tableDataCache.merchants = await res.json();
    renderMerchantsTable();
}

function renderMerchantsTable() {
    const table = document.getElementById("merchantsTable");
    if (!table) return;

    const pageInfo = getPaginatedSlice("merchants", tableDataCache.merchants);
    let html = `<tr><th>ID</th><th>Name</th><th>Bank</th><th>Cycle</th></tr>`;

    if (pageInfo.items.length === 0) {
        html += `<tr><td colspan="4">No merchants found.</td></tr>`;
    }

    pageInfo.items.forEach(m => {
        html += `<tr>
            <td>${m.id}</td>
            <td>${m.name}</td>
            <td>${m.bankAccount}</td>
            <td>${m.settlementCycle}</td>
        </tr>`;
    });

    table.innerHTML = html;
    renderPagination("merchantsPagination", "merchants", pageInfo);
}

/* ================= TRANSACTIONS ================= */

async function createTransaction() {
    if (!requireAdminAction("Transaction creation")) return;

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

    tableDataCache.transactions = await res.json();
    renderTransactionsTable();
}

function renderTransactionsTable() {
    const table = document.getElementById("transactionsTable");
    if (!table) return;

    const pageInfo = getPaginatedSlice("transactions", tableDataCache.transactions);
    let html = `<tr>
        <th>ID</th><th>Amount</th><th>Status</th>
        <th>Retry</th><th>Created</th>
    </tr>`;

    if (pageInfo.items.length === 0) {
        html += `<tr><td colspan="5">No transactions found.</td></tr>`;
    }

    pageInfo.items.forEach(t => {
        html += `<tr>
            <td>${t.id}</td>
            <td>${t.amount}</td>
            <td class="status-${t.status.toLowerCase()}">${t.status}</td>
            <td>${t.retryCount} / ${t.maxRetries}</td>
            <td>${t.createdAt}</td>
        </tr>`;
    });

    table.innerHTML = html;
    renderPagination("transactionsPagination", "transactions", pageInfo);
}

/* ================= RECONCILIATION ================= */

async function runReconciliation() {
    if (!requireAdminAction("Reconciliation run")) return;

    const res = await fetch("/api/reconciliation/run", { method: "POST" });
    if (!res.ok) {
        const err = await readErrorMessage(res);
        alert("Failed to run reconciliation:\n" + err);
        return;
    }

    const data = await res.json();
    alert(`Reconciliation completed. Updated ${data.updatedTransactions || 0} transaction(s).`);
    await refreshData();
}

async function retryException(transactionId) {
    if (!requireAdminAction("Exception retry")) return;

    const res = await fetch(`/api/reconciliation/exceptions/${transactionId}/retry`, {
        method: "POST"
    });

    if (!res.ok) {
        const err = await res.text();
        alert("Retry failed:\n" + err);
        return;
    }

    await refreshData();
}

async function resolveException(transactionId) {
    if (!requireAdminAction("Exception resolve")) return;

    const note = prompt("Resolution note (optional):", "");
    if (note === null) return;

    const res = await fetch(`/api/reconciliation/exceptions/${transactionId}/resolve`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ note })
    });

    if (!res.ok) {
        const err = await res.text();
        alert("Resolve failed:\n" + err);
        return;
    }

    await refreshData();
}

async function loadExceptionQueue() {
    const res = await fetch("/api/reconciliation/exceptions");
    if (!res.ok) {
        console.error("Failed to load exception queue:", await readErrorMessage(res));
        return;
    }

    tableDataCache.exceptions = await res.json();
    renderExceptionsTable();
}

function renderExceptionsTable() {
    const table = document.getElementById("exceptionsTable");
    if (!table) return;

    const pageInfo = getPaginatedSlice("exceptions", tableDataCache.exceptions);
    const isAdmin = isAdminUser();

    let html = `<tr>
        <th>Transaction</th><th>Amount</th><th>Status</th>
        <th>Retry</th><th>Reason</th><th>Updated</th>${isAdmin ? "<th>Actions</th>" : ""}
    </tr>`;

    if (pageInfo.items.length === 0) {
        html += `<tr><td colspan="${isAdmin ? 7 : 6}">No exceptions in queue.</td></tr>`;
        table.innerHTML = html;
        renderPagination("exceptionsPagination", "exceptions", pageInfo);
        return;
    }

    pageInfo.items.forEach(item => {
        const reason = item.exceptionReason || "-";
        html += `<tr>
            <td>${item.transactionId}</td>
            <td>${item.amount}</td>
            <td class="status-${item.status.toLowerCase()}">${item.status}</td>
            <td>${item.retryCount} / ${item.maxRetries}</td>
            <td>${reason}</td>
            <td>${item.reconciliationUpdatedAt || "-"}</td>`;

        if (isAdmin) {
            html += `<td>
                <button onclick="retryException(${item.transactionId})">Retry</button>
                <button onclick="resolveException(${item.transactionId})">Resolve</button>
            </td>`;
        }

        html += `
        </tr>`;
    });

    table.innerHTML = html;
    renderPagination("exceptionsPagination", "exceptions", pageInfo);
}

/* ================= LOGS ================= */

async function loadLogs() {
    const res = await fetch("/logs");
    if (!res.ok) return;

    tableDataCache.logs = await res.json();
    renderLogsTable();
}

function renderLogsTable() {
    const table = document.getElementById("logsTable");
    if (!table) return;

    const pageInfo = getPaginatedSlice("logs", tableDataCache.logs);
    let html = `<tr>
        <th>ID</th><th>Transaction</th>
        <th>Attempt</th><th>Result</th><th>Message</th>
    </tr>`;

    if (pageInfo.items.length === 0) {
        html += `<tr><td colspan="5">No settlement logs found.</td></tr>`;
    }

    pageInfo.items.forEach(l => {
        html += `<tr>
            <td>${l.id}</td>
            <td>${l.transactionId || "-"}</td>
            <td>${l.attemptNumber}</td>
            <td>${l.result}</td>
            <td>${l.message}</td>
        </tr>`;
    });

    table.innerHTML = html;
    renderPagination("logsPagination", "logs", pageInfo);
}

/* ================= TRIGGER ================= */

async function triggerSettlement() {
    if (!requireAdminAction("Settlement trigger")) return;

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

function logout() {
    // Use a real form POST so browser follows Spring Security's logout redirect.
    const form = document.createElement("form");
    form.method = "POST";
    form.action = "/logout";
    document.body.appendChild(form);
    form.submit();
}

/* ================= INIT ================= */

async function initializeApp() {
    try {
        await loadCurrentUser();
        applyRoleAccess();
        await refreshData();
        startAutoRefresh();
        syncDashboardStatsRefresh();
    } catch (error) {
        console.error("App initialization failed:", error);
        alert("Unable to initialize the dashboard. Please refresh the page.");
    }
}

initializeApp();
