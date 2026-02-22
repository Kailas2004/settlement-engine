import fs from "fs/promises";
import path from "path";
import { chromium } from "playwright";

const BASE_URL = process.env.BASE_URL || "https://settlement-engine-production.up.railway.app";
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || "admin";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "admin123";
const USER_USERNAME = process.env.USER_USERNAME || "user";
const USER_PASSWORD = process.env.USER_PASSWORD || "user123";
const REPORT_DIR = process.env.REPORT_DIR || path.resolve("docs/screenshots/role-validation");

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const nowTag = Date.now().toString();

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function screenshot(page, outputDir, name) {
  const out = path.join(outputDir, name);
  await page.screenshot({ path: out, fullPage: true });
  return out;
}

async function gotoSection(page, buttonName, sectionId) {
  await page.getByRole("button", { name: buttonName, exact: true }).click();
  await wait(250);
  const hidden = await page.$eval(`#${sectionId}`, (el) => el.classList.contains("hidden"));
  if (hidden) {
    throw new Error(`Section ${sectionId} did not open`);
  }
}

async function invokeLoaders(page, loaderNames) {
  await page.evaluate(async (names) => {
    for (const name of names) {
      const fn = window[name];
      if (typeof fn === "function") {
        await fn();
      }
    }
  }, loaderNames);
  await wait(150);
}

async function tableRows(page, tableId) {
  return page.$$eval(`#${tableId} tr`, (trs) =>
    trs.slice(1).map((tr) =>
      Array.from(tr.querySelectorAll("td")).map((td) => (td.textContent || "").trim())
    )
  );
}

function parseTransactions(rows) {
  return rows
    .filter((r) => r.length >= 5)
    .map((r) => ({
      id: Number.parseInt(r[0], 10),
      amount: r[1],
      status: r[2],
      retry: r[3],
      createdAt: r[4],
    }))
    .filter((r) => Number.isFinite(r.id));
}

function parseLogs(rows) {
  return rows
    .filter((r) => r.length >= 5)
    .map((r) => ({
      id: Number.parseInt(r[0], 10),
      transactionId: Number.parseInt(r[1], 10),
      attemptNumber: r[2],
      result: r[3],
      message: r[4],
    }))
    .filter((r) => Number.isFinite(r.id));
}

async function login(page, username, password) {
  await page.goto("/login", { waitUntil: "domcontentloaded", timeout: 30000 });
  await page.fill('input[name="username"]', username);
  await page.fill('input[name="password"]', password);
  await Promise.all([
    page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 }),
    page.locator('button[type="submit"]').click(),
  ]);
}

async function logout(page) {
  await Promise.all([
    page.waitForURL((url) => url.pathname === "/login", { timeout: 15000 }),
    page.click("#logoutBtn"),
  ]);
}

async function runAdminValidation(outputDir) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();

  const report = {
    role: "ADMIN",
    username: ADMIN_USERNAME,
    pass: true,
    checks: [],
    screenshots: [],
    details: {},
  };

  page.on("dialog", async (dialog) => {
    await dialog.accept();
  });

  function passCheck(name, details) {
    report.checks.push({ name, pass: true, details });
  }

  function failCheck(name, error) {
    report.pass = false;
    report.checks.push({ name, pass: false, details: error?.message || String(error) });
  }

  const run = async (name, fn, shotName) => {
    try {
      await fn();
      passCheck(name, "Passed");
    } catch (error) {
      failCheck(name, error);
    }
    if (shotName) {
      const shot = await screenshot(page, outputDir, shotName);
      report.screenshots.push(shot);
    }
  };

  const uniq = nowTag;
  const customerName = `Role Test Customer ${uniq}`;
  const customerEmail = `role.admin.${uniq}@example.com`;
  const merchantName = `Role Test Merchant ${uniq}`;
  const bankAccount = `ACC${uniq.slice(-8)}`;
  let customerId = null;
  let merchantId = null;
  let transactionId = null;

  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  await run("Admin login + dashboard visible", async () => {
    await page.waitForSelector("#dashboard", { timeout: 10000 });
    await page.waitForFunction(() => {
      const el = document.getElementById("authInfo");
      return !!el && !!el.textContent && el.textContent.trim().length > 0;
    });
    const authInfo = (await page.locator("#authInfo").innerText()).toLowerCase();
    if (!authInfo.includes("admin")) {
      throw new Error(`Unexpected auth info: ${authInfo}`);
    }
  }, `admin_${uniq}_step1_dashboard.png`);

  await run("Admin creates customer", async () => {
    await gotoSection(page, "Customers", "customers");
    await invokeLoaders(page, ["loadCustomers"]);
    await page.fill("#customerName", customerName);
    await page.fill("#customerEmail", customerEmail);
    await page.locator("#customerForm button", { hasText: "Create" }).click();

    const timeoutAt = Date.now() + 15000;
    let row;
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadCustomers"]);
      const rows = await tableRows(page, "customersTable");
      row = rows.find((r) => r[1] === customerName && r[2] === customerEmail);
      if (row) break;
      await wait(250);
    }
    if (!row) throw new Error("Created customer not found");
    customerId = Number.parseInt(row[0], 10);
    if (!Number.isFinite(customerId)) throw new Error("Customer ID parse failed");
  }, `admin_${uniq}_step2_customer.png`);

  await run("Admin creates merchant", async () => {
    await gotoSection(page, "Merchants", "merchants");
    await invokeLoaders(page, ["loadMerchants"]);
    await page.fill("#merchantName", merchantName);
    await page.fill("#merchantBank", bankAccount);
    await page.fill("#merchantCycle", "DAILY");
    await page.locator("#merchantForm button", { hasText: "Create" }).click();

    const timeoutAt = Date.now() + 15000;
    let row;
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadMerchants"]);
      const rows = await tableRows(page, "merchantsTable");
      row = rows.find((r) => r[1] === merchantName && r[2] === bankAccount && r[3] === "DAILY");
      if (row) break;
      await wait(250);
    }
    if (!row) throw new Error("Created merchant not found");
    merchantId = Number.parseInt(row[0], 10);
    if (!Number.isFinite(merchantId)) throw new Error("Merchant ID parse failed");
  }, `admin_${uniq}_step3_merchant.png`);

  await run("Admin creates transaction", async () => {
    await gotoSection(page, "Transactions", "transactions");
    await invokeLoaders(page, ["loadTransactions"]);

    const beforeRows = parseTransactions(await tableRows(page, "transactionsTable"));
    const previousMax = beforeRows.length ? Math.max(...beforeRows.map((r) => r.id)) : 0;

    await page.fill("#customerId", String(customerId));
    await page.fill("#merchantId", String(merchantId));
    await page.fill("#amount", "1000");
    await page.locator("#transactionForm button", { hasText: "Create" }).click();

    const timeoutAt = Date.now() + 20000;
    let created;
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadTransactions"]);
      const rows = parseTransactions(await tableRows(page, "transactionsTable"));
      created = rows
        .filter((r) => r.id > previousMax && r.amount.startsWith("1000"))
        .sort((a, b) => b.id - a.id)[0];
      if (created) break;
      await wait(300);
    }

    if (!created) throw new Error("Created transaction not found");
    if (created.status !== "CAPTURED") {
      throw new Error(`Expected CAPTURED, got ${created.status}`);
    }
    transactionId = created.id;
  }, `admin_${uniq}_step4_transaction_created.png`);

  await run("Admin triggers settlement + state transition", async () => {
    await gotoSection(page, "Transactions", "transactions");
    await invokeLoaders(page, ["loadTransactions"]);

    let current = parseTransactions(await tableRows(page, "transactionsTable")).find((r) => r.id === transactionId);
    if (!current) throw new Error(`Transaction ${transactionId} missing before trigger`);

    await page.getByRole("button", { name: "Trigger Settlement", exact: true }).click();

    const seen = new Set([current.status]);
    const timeoutAt = Date.now() + 120000;
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadTransactions", "loadStats"]);
      current = parseTransactions(await tableRows(page, "transactionsTable")).find((r) => r.id === transactionId);
      if (!current) throw new Error(`Transaction ${transactionId} missing during polling`);
      seen.add(current.status);

      if (current.status === "SETTLED" || current.status === "FAILED") {
        break;
      }
      await wait(300);
    }

    if (current.status !== "SETTLED" && current.status !== "FAILED") {
      throw new Error(`Expected terminal status SETTLED/FAILED, got ${current.status}`);
    }

    report.details.transactionTerminalStatus = current.status;
    report.details.transactionSeenStates = Array.from(seen);
  }, `admin_${uniq}_step5_after_settlement.png`);

  await run("Admin lock indicator behavior", async () => {
    // Ensure at least one CAPTURED transaction exists so lock is held long enough for UI polling.
    const preloadStatus = await page.evaluate(async ({ cId, mId }) => {
      const res = await fetch(`/transactions?customerId=${cId}&merchantId=${mId}&amount=2500`, {
        method: "POST",
      });
      return res.status;
    }, { cId: customerId, mId: merchantId });
    if (preloadStatus !== 200) {
      throw new Error(`Failed to preload transaction for lock test. HTTP ${preloadStatus}`);
    }

    await gotoSection(page, "Dashboard", "dashboard");
    await invokeLoaders(page, ["loadStats"]);

    for (let i = 0; i < 3; i += 1) {
      await page.getByRole("button", { name: "Trigger Settlement", exact: true }).click();
      await wait(200);
    }

    let sawActive = false;
    let sawReleased = false;
    let multipleHolder = false;

    const timeoutAt = Date.now() + 60000;
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadStats"]);
      const text = await page.locator("#settlementActivity").innerText();
      if (text.includes("Redis Lock Active") || text.includes("Redis Lock Recently Active")) {
        sawActive = true;
      }
      if (text.includes("No Lock Held")) {
        sawReleased = true;
      }
      if ((text.match(/Holder:/g) || []).length > 1) {
        multipleHolder = true;
      }
      if (sawActive && sawReleased) break;
      await wait(250);
    }

    if (multipleHolder) throw new Error("Multiple lock holders shown at once");
    if (!sawActive || !sawReleased) {
      throw new Error(`Expected both lock active and released states (active=${sawActive}, released=${sawReleased})`);
    }
  }, `admin_${uniq}_step6_lock_indicator.png`);

  await run("Admin sees settlement logs for transaction", async () => {
    await gotoSection(page, "Settlement Logs", "logs");
    await invokeLoaders(page, ["loadLogs"]);

    const timeoutAt = Date.now() + 30000;
    let txLogs = [];
    while (Date.now() < timeoutAt) {
      await invokeLoaders(page, ["loadLogs"]);
      const logs = parseLogs(await tableRows(page, "logsTable"));
      txLogs = logs.filter((l) => l.transactionId === transactionId);
      if (txLogs.length > 0) break;
      await wait(250);
    }

    if (txLogs.length === 0) throw new Error(`No logs found for transaction ${transactionId}`);
    report.details.logCountForTransaction = txLogs.length;
  }, `admin_${uniq}_step7_logs.png`);

  await run("Admin logout", async () => {
    await logout(page);
    if (!page.url().includes("/login")) {
      throw new Error(`Expected to land on /login after logout, got ${page.url()}`);
    }

    const body = await page.locator("body").innerText();
    if (body.toLowerCase().includes("whitelabel")) {
      throw new Error("Whitelabel page shown after logout");
    }
  }, `admin_${uniq}_step8_logout.png`);

  report.details.customerId = customerId;
  report.details.merchantId = merchantId;
  report.details.transactionId = transactionId;

  await browser.close();
  return report;
}

async function runUserValidation(outputDir) {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();

  const report = {
    role: "USER",
    username: USER_USERNAME,
    pass: true,
    checks: [],
    screenshots: [],
    details: {},
  };

  page.on("dialog", async (dialog) => {
    await dialog.accept();
  });

  function passCheck(name, details) {
    report.checks.push({ name, pass: true, details });
  }

  function failCheck(name, error) {
    report.pass = false;
    report.checks.push({ name, pass: false, details: error?.message || String(error) });
  }

  const run = async (name, fn, shotName) => {
    try {
      await fn();
      passCheck(name, "Passed");
    } catch (error) {
      failCheck(name, error);
    }
    if (shotName) {
      const shot = await screenshot(page, outputDir, shotName);
      report.screenshots.push(shot);
    }
  };

  await login(page, USER_USERNAME, USER_PASSWORD);

  await run("User login + dashboard visible", async () => {
    await page.waitForSelector("#dashboard", { timeout: 10000 });
    await page.waitForFunction(() => {
      const el = document.getElementById("authInfo");
      return !!el && !!el.textContent && el.textContent.trim().length > 0;
    });
    const authInfo = (await page.locator("#authInfo").innerText()).toLowerCase();
    if (!authInfo.includes("user")) {
      throw new Error(`Unexpected auth info: ${authInfo}`);
    }
  }, `user_${nowTag}_step1_dashboard.png`);

  await run("User cannot access admin controls in UI", async () => {
    const customerFormVisible = await page.$eval("#customerForm", (el) => getComputedStyle(el).display !== "none");
    const merchantFormVisible = await page.$eval("#merchantForm", (el) => getComputedStyle(el).display !== "none");
    const transactionFormVisible = await page.$eval("#transactionForm", (el) => getComputedStyle(el).display !== "none");
    const reconciliationControlsVisible = await page.$eval("#reconciliationControls", (el) => getComputedStyle(el).display !== "none");
    const triggerButtonVisible = await page.$eval("#triggerSettlementBtn", (el) => getComputedStyle(el).display !== "none");

    if (customerFormVisible || merchantFormVisible || transactionFormVisible || reconciliationControlsVisible || triggerButtonVisible) {
      throw new Error("One or more admin-only controls are visible to USER role");
    }
  }, `user_${nowTag}_step2_admin_controls_hidden.png`);

  await run("User can view read-only sections", async () => {
    await gotoSection(page, "Customers", "customers");
    await invokeLoaders(page, ["loadCustomers"]);
    await gotoSection(page, "Merchants", "merchants");
    await invokeLoaders(page, ["loadMerchants"]);
    await gotoSection(page, "Transactions", "transactions");
    await invokeLoaders(page, ["loadTransactions"]);
    await gotoSection(page, "Reconciliation", "reconciliation");
    await invokeLoaders(page, ["loadExceptionQueue"]);
    await gotoSection(page, "Settlement Logs", "logs");
    await invokeLoaders(page, ["loadLogs"]);
  }, `user_${nowTag}_step3_read_only_sections.png`);

  await run("User write APIs blocked by backend", async () => {
    const status = await page.evaluate(async () => {
      const res = await fetch("/customers", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: "blocked", email: "blocked@example.com" }),
      });
      return res.status;
    });

    if (status !== 403) {
      throw new Error(`Expected POST /customers to return 403 for USER, got ${status}`);
    }

    const triggerStatus = await page.evaluate(async () => {
      const res = await fetch("/settlement/trigger", { method: "POST" });
      return res.status;
    });

    if (triggerStatus !== 403) {
      throw new Error(`Expected POST /settlement/trigger to return 403 for USER, got ${triggerStatus}`);
    }
  }, `user_${nowTag}_step4_backend_forbidden.png`);

  await run("User logout", async () => {
    await logout(page);
    if (!page.url().includes("/login")) {
      throw new Error(`Expected to land on /login after logout, got ${page.url()}`);
    }

    const body = await page.locator("body").innerText();
    if (body.toLowerCase().includes("whitelabel")) {
      throw new Error("Whitelabel page shown after user logout");
    }
  }, `user_${nowTag}_step5_logout.png`);

  await browser.close();
  return report;
}

async function run() {
  await ensureDir(REPORT_DIR);

  const admin = await runAdminValidation(REPORT_DIR);
  const user = await runUserValidation(REPORT_DIR);

  const finalReport = {
    baseUrl: BASE_URL,
    generatedAt: new Date().toISOString(),
    overallPass: admin.pass && user.pass,
    admin,
    user,
  };

  const jsonPath = path.join(REPORT_DIR, `role-validation-${nowTag}.json`);
  await fs.writeFile(jsonPath, JSON.stringify(finalReport, null, 2), "utf8");

  console.log("ROLE_VALIDATION_REPORT_START");
  console.log(JSON.stringify({ reportFile: jsonPath, ...finalReport }, null, 2));
  console.log("ROLE_VALIDATION_REPORT_END");

  if (!finalReport.overallPass) {
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.log("ROLE_VALIDATION_REPORT_START");
  console.log(JSON.stringify({ fatalError: error?.message || String(error) }, null, 2));
  console.log("ROLE_VALIDATION_REPORT_END");
  process.exitCode = 1;
});
