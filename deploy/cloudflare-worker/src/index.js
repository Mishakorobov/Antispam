/**
 * Cloudflare Worker: AntiSpam Crowdsourcing Reports Ingestion Bridge
 *
 * Provides a zero-cost serverless ingestion endpoint for user spam/survey complaints
 * submitted from Android clients in Autonomous Serverless mode.
 *
 * Validates report payloads and dispatches batches to GitHub Actions (repository_dispatch)
 * to queue updates for weekly database recompilation.
 */

const ALLOWED_CATEGORIES = new Set([
  "SPAM",
  "SURVEY",
  "FINANCIAL",
  "SPOOFING",
  "SILENT",
  "SURVEY_VCIOM",
  "VIP_MODE",
  "FOREIGN_PLUS7",
]);

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Device-Hash",
};

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...CORS_HEADERS,
    },
  });
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: CORS_HEADERS,
      });
    }

    // Healthcheck & metadata
    if (url.pathname === "/" || url.pathname === "/health") {
      return jsonResponse({
        status: "healthy",
        service: "antispam-report-bridge",
        version: "1.0.0",
        serverless: true,
        dispatches_enabled: Boolean(env.GITHUB_TOKEN),
        timestamp: new Date().toISOString(),
      });
    }

    // Report batch ingestion endpoint
    if (
      (url.pathname === "/api/reports/batch" || url.pathname === "/reports/batch") &&
      request.method === "POST"
    ) {
      return handleBatchReports(request, env, ctx);
    }

    return jsonResponse({ error: "Endpoint not found" }, 404);
  },
};

async function handleBatchReports(request, env, ctx) {
  let body;
  try {
    body = await request.json();
  } catch (err) {
    return jsonResponse(
      { error: "Invalid JSON format in request body" },
      400
    );
  }

  const { device_hash, reports, pow_nonce, pow_timestamp } = body || {};

  // 1. Validate device hash
  if (
    !device_hash ||
    typeof device_hash !== "string" ||
    device_hash.length < 8 ||
    device_hash.length > 128
  ) {
    return jsonResponse(
      { error: "Invalid or missing 'device_hash' (string, 8-128 chars)" },
      400
    );
  }

  // 2. Validate reports array
  if (!Array.isArray(reports) || reports.length === 0) {
    return jsonResponse(
      { error: "Missing or empty 'reports' array" },
      400
    );
  }

  if (reports.length > 100) {
    return jsonResponse(
      { error: "Batch size exceeds maximum limit of 100 reports" },
      400
    );
  }

  // 3. Sanitize and validate each item
  const validReports = [];
  const errors = [];

  for (let i = 0; i < reports.length; i++) {
    const item = reports[i];
    if (!item || typeof item !== "object") {
      errors.push(`Item ${i}: must be an object`);
      continue;
    }

    const rawPhone = String(item.phone_number || "").replace(/\D/g, "");
    if (rawPhone.length < 10 || rawPhone.length > 15) {
      errors.push(`Item ${i}: invalid phone_number length (${rawPhone.length} digits)`);
      continue;
    }

    const category = String(item.category || "SPAM").toUpperCase();
    if (!ALLOWED_CATEGORIES.has(category)) {
      errors.push(`Item ${i}: invalid category '${category}'`);
      continue;
    }

    const duration = Number.isInteger(item.call_duration_seconds)
      ? Math.max(0, item.call_duration_seconds)
      : 0;

    validReports.push({
      phone_number: rawPhone,
      category,
      call_duration_seconds: duration,
    });
  }

  if (validReports.length === 0) {
    return jsonResponse(
      { error: "All items in batch were invalid", details: errors },
      400
    );
  }

  // 3.5. Phase 11.2: Validate Micro-Proof-of-Work (Anti-Botnet Proxy-Farm Shield)
  const isPowProvided = pow_nonce !== undefined && pow_timestamp !== undefined;
  const isPowRequired = env.REQUIRE_POW === "true" || env.REQUIRE_POW === true;

  if (isPowRequired && !isPowProvided) {
    return jsonResponse(
      { error: "Missing required Proof-of-Work fields ('pow_nonce', 'pow_timestamp')" },
      400
    );
  }

  if (isPowProvided) {
    const firstPhone = validReports[0]?.phone_number || "";
    const isValidPoW = await verifyProofOfWork(firstPhone, device_hash, pow_timestamp, pow_nonce);
    if (!isValidPoW) {
      return jsonResponse(
        { error: "Invalid or expired Proof-of-Work challenge solution" },
        400
      );
    }
  }

  // 4. Trigger GitHub Actions repository_dispatch if GITHUB_TOKEN is available
  let dispatched = false;
  let dispatchError = null;

  if (env.GITHUB_TOKEN) {
    const owner = env.GITHUB_OWNER || "Mishakorobov";
    const repo = env.GITHUB_REPO || "Antispam";
    const eventType = env.EVENT_TYPE || "crowdsource_reports";

    const ghUrl = `https://api.github.com/repos/${owner}/${repo}/dispatches`;
    const payload = {
      event_type: eventType,
      client_payload: {
        source: "cloudflare_worker_bridge",
        device_hash,
        timestamp: new Date().toISOString(),
        reports_count: validReports.length,
        reports: validReports,
      },
    };

    try {
      const ghResponse = await fetch(ghUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.GITHUB_TOKEN}`,
          Accept: "application/vnd.github.v3+json",
          "User-Agent": "AntiSpam-Cloudflare-Worker-Bridge",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (ghResponse.status === 204 || ghResponse.ok) {
        dispatched = true;
      } else {
        const ghErrText = await ghResponse.text();
        dispatchError = `GitHub API returned ${ghResponse.status}: ${ghErrText}`;
        console.error("GitHub dispatch failed:", dispatchError);
      }
    } catch (e) {
      dispatchError = e.message;
      console.error("Error dispatching to GitHub:", e);
    }
  }

  return jsonResponse({
    status: "success",
    accepted_count: validReports.length,
    validation_warnings: errors.length > 0 ? errors : undefined,
    dispatched,
    dispatch_error: dispatchError || undefined,
    message: "Reports accepted successfully.",
  });
}

/**
 * Phase 11.2: Verifies Micro-Proof-of-Work (PoW) solution.
 * Enforces 16 bits of leading zero bytes ("0000...") and +-10 minute timestamp freshness.
 */
async function verifyProofOfWork(phoneNumber, deviceHash, timestamp, nonce) {
  if (typeof nonce !== "number" && typeof nonce !== "string") return false;
  const ts = Number(timestamp);
  if (!Number.isFinite(ts)) return false;

  const nowSeconds = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSeconds - ts) > 600) {
    return false; // Skewed or expired timestamp
  }

  const cleanPhone = String(phoneNumber || "").replace(/\D/g, "");
  const challenge = `${cleanPhone}:${deviceHash}:${ts}:${nonce}`;
  const msgUint8 = new TextEncoder().encode(challenge);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  const hashArray = new Uint8Array(hashBuffer);

  return hashArray[0] === 0 && hashArray[1] === 0;
}
