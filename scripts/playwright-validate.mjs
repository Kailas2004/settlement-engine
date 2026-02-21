import fs from "fs/promises";
import path from "path";
import { chromium } from "playwright";

const BASE_URL = process.env.BASE_URL || "http://localhost:8080";
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR || process.cwd();

const screenshots = [
  "step1_homepage.png",
  "step2_customer_created.png",
  "step3_merchant_created.png",
  "step4_transaction_captured.png",
  "step5_transaction_settled.png",
  "step6_lock_status.png",
  "step7_logs.png",
  "step8_idempotency.png",
];

const results = [];
const uiInconsistencies = [];
const stateInconsistencies = [];
const dialogs = [];
const pageErrors = [];
const responseErrors = [];

let customerId = null;
let merchantId = null;
let transactionId = null;

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
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
  await wait(250);
}

async function takeShot(page, filename) {
  const output = path.join(SCREENSHOT_DIR, filename);
  await page.screenshot({ path: output, fullPage: true });
  return output;
}

async function getRows(page, tableId) {
  return page.$$eval(`#${tableId} tr`, (trs) =>
    trs.slice(1).map((tr) =>
      Array.from(tr.querySelectorAll("td")).map((td) =>
        (td.textContent || "").trim()
      )
    )
  );
}

function parseTransactionRows(rows) {
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

function parseLogRows(rows) {
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

async function gotoSection(page, buttonName, sectionId) {
  await page.getByRole("button", { name: buttonName, exact: true }).click();
  await wait(300);
  const isHidden = await page.$eval(
    `#${sectionId}`,
    (el) => el.classList.contains("hidden")
  );
  if (isHidden) {
    throw new Error(`Section '${sectionId}' did not become visible.`);
  }
}

async function findCustomerRow(page, name, email) {
  const rows = await getRows(page, "customersTable");
  return rows.find((r) => r[1] === name && r[2] === email);
}

async function findMerchantRow(page, name, bankAccount, cycle) {
  const rows = await getRows(page, "merchantsTable");
  return rows.find((r) => r[1] === name && r[2] === bankAccount && r[3] === cycle);
}

async function getTransactionStatus(page, id) {
  const rows = parseTransactionRows(await getRows(page, "transactionsTable"));
  const tx = rows.find((r) => r.id === id);
  return tx ? tx.status : null;
}

async function withStep(page, stepNumber, title, screenshotName, fn) {
  const step = {
    step: stepNumber,
    title,
    screenshot: screenshotName,
    pass: false,
    details: "",
  };

  try {
    await fn();
    step.pass = true;
    step.details = "Assertions passed.";
  } catch (error) {
    step.pass = false;
    step.details = error?.message || String(error);
  }

  try {
    const shotPath = await takeShot(page, screenshotName);
    step.screenshotPath = shotPath;
  } catch (error) {
    step.pass = false;
    step.details = `${step.details} Screenshot failed: ${error?.message || String(error)}`.trim();
  }

  results.push(step);
}

async function run() {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL: BASE_URL });
  const page = await context.newPage();

  page.on("dialog", async (dialog) => {
    dialogs.push({ type: dialog.type(), message: dialog.message() });
    await dialog.accept();
  });
  page.on("pageerror", (error) => {
    pageErrors.push(error.message || String(error));
  });
  page.on("response", (response) => {
    try {
      const url = response.url();
      if (url.startsWith(BASE_URL) && response.status() >= 400) {
        responseErrors.push(`${response.status()} ${url}`);
      }
    } catch {
      // Ignore response parsing failures.
    }
  });

  await withStep(
    page,
    1,
    "Load Application",
    "step1_homepage.png",
    async () => {
      await page.goto("/", { waitUntil: "domcontentloaded", timeout: 30000 });
      await page.waitForSelector("#dashboard");
      await wait(1200);

      const dashboardHidden = await page.$eval(
        "#dashboard",
        (el) => el.classList.contains("hidden")
      );
      if (dashboardHidden) {
        throw new Error("Dashboard section is hidden after page load.");
      }

      const title = await page.title();
      if (!title.includes("Settlement Engine")) {
        throw new Error(`Unexpected page title: '${title}'`);
      }

      if (pageErrors.length > 0) {
        throw new Error(`JavaScript errors detected: ${pageErrors.join(" | ")}`);
      }

      if (responseErrors.length > 0) {
        throw new Error(`HTTP errors detected: ${responseErrors.join(" | ")}`);
      }
    }
  );

  await withStep(
    page,
    2,
    "Create Customer",
    "step2_customer_created.png",
    async () => {
      await gotoSection(page, "Customers", "customers");
      await invokeLoaders(page, ["loadCustomers"]);

      await page.fill("#customerName", "Test User");
      await page.fill("#customerEmail", "testuser1@example.com");
      await page.locator("#customers button", { hasText: "Create" }).click();

      const timeoutAt = Date.now() + 15000;
      let customerRow = null;
      while (Date.now() < timeoutAt) {
        await invokeLoaders(page, ["loadCustomers"]);
        customerRow = await findCustomerRow(page, "Test User", "testuser1@example.com");
        if (customerRow) break;
        await wait(500);
      }

      if (!customerRow) {
        const dialogFailure = dialogs.find((d) =>
          d.message.toLowerCase().includes("customer creation failed")
        );
        if (dialogFailure) {
          throw new Error(`Customer creation failed: ${dialogFailure.message}`);
        }
        throw new Error("Customer row not found in customers table.");
      }

      customerId = Number.parseInt(customerRow[0], 10);
      if (!Number.isFinite(customerId)) {
        throw new Error("Customer ID could not be parsed from table row.");
      }
    }
  );

  await withStep(
    page,
    3,
    "Create Merchant",
    "step3_merchant_created.png",
    async () => {
      await gotoSection(page, "Merchants", "merchants");
      await invokeLoaders(page, ["loadMerchants"]);

      await page.fill("#merchantName", "Test Merchant");
      await page.fill("#merchantBank", "123456789");
      await page.fill("#merchantCycle", "DAILY");
      await page.locator("#merchants button", { hasText: "Create" }).click();

      const timeoutAt = Date.now() + 15000;
      let merchantRow = null;
      while (Date.now() < timeoutAt) {
        await invokeLoaders(page, ["loadMerchants"]);
        merchantRow = await findMerchantRow(
          page,
          "Test Merchant",
          "123456789",
          "DAILY"
        );
        if (merchantRow) break;
        await wait(500);
      }

      if (!merchantRow) {
        const dialogFailure = dialogs.find((d) =>
          d.message.toLowerCase().includes("merchant creation failed")
        );
        if (dialogFailure) {
          throw new Error(`Merchant creation failed: ${dialogFailure.message}`);
        }
        throw new Error("Merchant row not found in merchants table.");
      }

      merchantId = Number.parseInt(merchantRow[0], 10);
      if (!Number.isFinite(merchantId)) {
        throw new Error("Merchant ID could not be parsed from table row.");
      }
    }
  );

  await withStep(
    page,
    4,
    "Create Transaction",
    "step4_transaction_captured.png",
    async () => {
      await gotoSection(page, "Transactions", "transactions");
      await invokeLoaders(page, ["loadTransactions"]);

      const beforeRows = parseTransactionRows(await getRows(page, "transactionsTable"));
      const previousMaxId = beforeRows.length
        ? Math.max(...beforeRows.map((r) => r.id))
        : 0;

      await page.fill("#customerId", String(customerId));
      await page.fill("#merchantId", String(merchantId));
      await page.fill("#amount", "1000");
      await page.locator("#transactions button", { hasText: "Create" }).click();

      const timeoutAt = Date.now() + 20000;
      let created = null;
      while (Date.now() < timeoutAt) {
        await invokeLoaders(page, ["loadTransactions"]);
        const rows = parseTransactionRows(await getRows(page, "transactionsTable"));
        created = rows
          .filter((r) => r.id > previousMaxId && r.amount.startsWith("1000"))
          .sort((a, b) => b.id - a.id)[0];
        if (created) break;
        await wait(500);
      }

      if (!created) {
        throw new Error("New transaction was not found after submission.");
      }

      transactionId = created.id;
      if (created.status !== "CAPTURED") {
        throw new Error(
          `Expected new transaction status CAPTURED, found '${created.status}'.`
        );
      }
    }
  );

  await withStep(
    page,
    5,
    "Trigger Settlement",
    "step5_transaction_settled.png",
    async () => {
      await gotoSection(page, "Transactions", "transactions");
      await invokeLoaders(page, ["loadTransactions"]);

      const startStatus = await getTransactionStatus(page, transactionId);
      if (startStatus !== "CAPTURED") {
        throw new Error(
          `Expected starting status CAPTURED, found '${startStatus || "missing"}'.`
        );
      }

      await page.getByRole("button", { name: "Trigger Settlement", exact: true }).click();

      const timeoutAt = Date.now() + 120000;
      const seenStates = new Set(["CAPTURED"]);
      while (Date.now() < timeoutAt) {
        await invokeLoaders(page, ["loadTransactions", "loadStats"]);
        const status = await getTransactionStatus(page, transactionId);
        if (!status) {
          throw new Error(`Transaction ${transactionId} disappeared from table.`);
        }
        seenStates.add(status);
        if (status === "SETTLED") break;
        await wait(1000);
      }

      const finalStatus = await getTransactionStatus(page, transactionId);
      if (finalStatus !== "SETTLED") {
        throw new Error(
          `Expected final status SETTLED, found '${finalStatus || "missing"}'.`
        );
      }

      if (!seenStates.has("PROCESSING")) {
        throw new Error(
          "Did not observe PROCESSING state in UI during CAPTURED -> SETTLED transition."
        );
      }
    }
  );

  await withStep(
    page,
    6,
    "Verify Lock Indicator",
    "step6_lock_status.png",
    async () => {
      await gotoSection(page, "Dashboard", "dashboard");
      await invokeLoaders(page, ["loadStats"]);

      for (let i = 0; i < 3; i += 1) {
        await page.getByRole("button", { name: "Trigger Settlement", exact: true }).click();
        await wait(300);
      }

      const timeoutAt = Date.now() + 70000;
      let sawLockActive = false;
      let sawLockReleased = false;
      let multipleHolderSignal = false;

      while (Date.now() < timeoutAt) {
        await invokeLoaders(page, ["loadStats"]);
        const text = await page.locator("#settlementActivity").innerText();

        if (text.includes("Redis Lock Active")) {
          sawLockActive = true;
        }
        if (text.includes("No Lock Held")) {
          sawLockReleased = true;
        }

        const holderOccurrences = (text.match(/Holder:/g) || []).length;
        if (holderOccurrences > 1) {
          multipleHolderSignal = true;
        }

        if (sawLockActive && sawLockReleased) {
          break;
        }

        await wait(1000);
      }

      if (multipleHolderSignal) {
        throw new Error("UI displayed multiple lock holders simultaneously.");
      }
      if (!sawLockActive || !sawLockReleased) {
        throw new Error(
          "Did not observe both lock acquired and lock released states in dashboard."
        );
      }
    }
  );

  await withStep(page, 7, "Verify Logs", "step7_logs.png", async () => {
    await gotoSection(page, "Settlement Logs", "logs");
    await invokeLoaders(page, ["loadLogs"]);

    const logs = parseLogRows(await getRows(page, "logsTable")).filter(
      (l) => l.transactionId === transactionId
    );

    if (logs.length === 0) {
      throw new Error(`No settlement logs found for transaction ${transactionId}.`);
    }

    const attemptOne = logs.find((l) => l.attemptNumber === "1");
    if (!attemptOne) {
      throw new Error(`No attemptNumber=1 log found for transaction ${transactionId}.`);
    }
    if (attemptOne.result !== "SETTLED") {
      throw new Error(
        `Expected attemptNumber=1 result SETTLED, found '${attemptOne.result}'.`
      );
    }
  });

  await withStep(page, 8, "Idempotency Test", "step8_idempotency.png", async () => {
    await gotoSection(page, "Settlement Logs", "logs");
    await invokeLoaders(page, ["loadLogs"]);

    const beforeLogs = parseLogRows(await getRows(page, "logsTable")).filter(
      (l) => l.transactionId === transactionId
    );
    const beforeCount = beforeLogs.length;

    await page.getByRole("button", { name: "Trigger Settlement", exact: true }).click();
    await wait(1500);
    await invokeLoaders(page, ["loadTransactions", "loadLogs", "loadStats"]);

    await gotoSection(page, "Transactions", "transactions");
    await invokeLoaders(page, ["loadTransactions"]);
    const status = await getTransactionStatus(page, transactionId);
    if (status !== "SETTLED") {
      throw new Error(`Transaction moved from SETTLED to '${status || "missing"}'.`);
    }

    await gotoSection(page, "Settlement Logs", "logs");
    await invokeLoaders(page, ["loadLogs"]);
    const afterLogs = parseLogRows(await getRows(page, "logsTable")).filter(
      (l) => l.transactionId === transactionId
    );

    if (afterLogs.length !== beforeCount) {
      throw new Error(
        `Duplicate settlement detected for settled transaction. Log count before=${beforeCount}, after=${afterLogs.length}.`
      );
    }
  });

  if (pageErrors.length > 0) {
    uiInconsistencies.push(`JavaScript errors captured: ${pageErrors.join(" | ")}`);
  }
  if (responseErrors.length > 0) {
    uiInconsistencies.push(`HTTP errors captured: ${responseErrors.join(" | ")}`);
  }

  const failedSteps = results.filter((r) => !r.pass);
  for (const step of failedSteps) {
    stateInconsistencies.push(`Step ${step.step} (${step.title}): ${step.details}`);
  }

  await browser.close();

  const report = {
    baseUrl: BASE_URL,
    customerId,
    merchantId,
    transactionId,
    results,
    uiInconsistencies,
    stateInconsistencies,
    screenshots: screenshots.map((name) => path.join(SCREENSHOT_DIR, name)),
    dialogs,
  };

  console.log("VALIDATION_RESULT_START");
  console.log(JSON.stringify(report, null, 2));
  console.log("VALIDATION_RESULT_END");
}

run().catch((error) => {
  console.log("VALIDATION_RESULT_START");
  console.log(
    JSON.stringify(
      {
        fatalError: error?.message || String(error),
        results,
        screenshots: screenshots.map((name) => path.join(SCREENSHOT_DIR, name)),
      },
      null,
      2
    )
  );
  console.log("VALIDATION_RESULT_END");
  process.exitCode = 1;
});
