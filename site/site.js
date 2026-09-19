const copyDefault = { zh: "复制", en: "Copy" };
const copyDone = { zh: "已复制", en: "Copied" };
const copyFail = { zh: "复制失败", en: "Copy failed" };

const meta = {
  zh: {
    title: "Kross — 组织可自托管的云端电脑 Agent",
    description:
      "Kross 给每位成员一台远程电脑：长期工作区、独立 Worker、自己的文件和记忆。合上笔记本，任务还在跑。跑在你自己的 Docker 或 k3s 上。",
    ogDescription: "每位成员一台远程电脑。整理资料、写出产物。不操作用户自己的电脑。",
    locale: "zh_CN",
    toggleLabel: "切换为英文",
  },
  en: {
    title: "Kross — self-hosted cloud computer agent",
    description:
      "Kross gives each member a remote computer: a long-lived workspace, an isolated Worker, and their own files and memory. Close the laptop. The job keeps running. Runs on your Docker or k3s.",
    ogDescription: "One remote computer per member. It does not operate the user's personal computer.",
    locale: "en_US",
    toggleLabel: "Switch to Chinese",
  },
};

const en = {
  eyebrow: "Self-hosted / one computer each / MIT",
  heroA: "Close the laptop.",
  heroB: "The job keeps running.",
  stamp: "Lid closed. Machine still on.",
  lede: "Kross is a self-hosted cloud computer agent for organizations. Each member gets a remote computer: a long-lived workspace, an isolated Worker, and their own files and memory. Describe the work in a browser. The agent writes artifacts on that machine. It does not operate the user's personal computer.",
  installCta: "Install locally",
  start: "Get started",
  docsNav: "Docs",
  installTitle: "Start it on one machine",
  installLead: "Clone the repo first. Needs Docker Engine and Compose v2. The first registered account becomes the platform super administrator.",
  copy: copyDefault.en,
  workbench: "Workbench",
  admin: "Admin",
  whoTitle: "Who it is for",
  who1: "Teams that want the agent on their own infrastructure, not a vendor-held workspace.",
  who2: "Organizations that need tenants, roles, enterprise login, and per-person isolation.",
  who3: "Knowledge work that produces briefs, plans, tables, and notes — not repository edits.",
  notTitle: "What it is not",
  not1: "Not a coding agent. No terminal UI, no local CLI.",
  not2: "It does not operate the user's own computer. Closing a laptop does not stop the job.",
  not3: "Members do not paste API keys, pick models, or choose permission profiles.",
  incTitle: "Included",
  inc1t: "One computer per member",
  inc1d: "An independent Worker and durable /work, not a session on a shared disk.",
  inc2t: "Tenants and roles",
  inc2d: "Platform super admin, organization admin, and members. Optional enterprise OIDC.",
  inc3t: "Confirm only the outside world",
  inc3d: "Workspace reads and writes run without prompts. External systems require a clear confirmation.",
  inc4t: "Two deployments",
  inc4d: "Docker Compose on one host, or k3s with Helm. Cluster install still needs a real-environment check.",
  flowTitle: "The browser talks only to the control plane",
  flow1: "Members describe the desired result in the workbench",
  flow2: "The Java control plane owns identity, conversations, models, and Worker lifecycle",
  flow3: "Each member's remote Worker does the work",
  flow4: "Artifacts land in /work; conversations live in PostgreSQL",
  license: "MIT License",
  fine: "Same category as ChatGPT Work, Grok Bot, and Cursor Cloud Agent. The computer runs on your machines.",
};

const zhKeys = {};

function currentLang() {
  return localStorage.getItem("kross-lang") === "en" ? "en" : "zh";
}

function setMeta(name, content, attr) {
  const selector = attr === "property" ? `meta[property="${name}"]` : `meta[name="${name}"]`;
  const node = document.querySelector(selector);
  if (node) {
    node.setAttribute("content", content);
  }
}

function applyLang(lang) {
  document.documentElement.lang = lang === "en" ? "en" : "zh-CN";
  document.querySelectorAll("[data-i18n]").forEach((node) => {
    const key = node.getAttribute("data-i18n");
    if (lang === "en") {
      if (!zhKeys[key]) {
        zhKeys[key] = node.textContent.trim();
      }
      if (en[key]) {
        node.textContent = en[key];
      }
    } else if (zhKeys[key]) {
      node.textContent = zhKeys[key];
    }
  });
  const toggle = document.querySelector("[data-lang-toggle]");
  if (toggle) {
    toggle.textContent = lang === "en" ? "中文" : "EN";
    toggle.setAttribute("aria-label", meta[lang].toggleLabel);
  }
  const pack = meta[lang];
  document.title = pack.title;
  setMeta("description", pack.description);
  setMeta("og:title", pack.title, "property");
  setMeta("og:description", pack.ogDescription, "property");
  setMeta("og:locale", pack.locale, "property");
}

async function copyText(value) {
  try {
    await navigator.clipboard.writeText(value);
    return true;
  } catch {
    const area = document.createElement("textarea");
    area.value = value;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.left = "-9999px";
    document.body.appendChild(area);
    area.select();
    try {
      return document.execCommand("copy");
    } catch {
      return false;
    } finally {
      area.remove();
    }
  }
}

document.querySelector("[data-lang-toggle]")?.addEventListener("click", () => {
  const next = currentLang() === "en" ? "zh" : "en";
  localStorage.setItem("kross-lang", next);
  applyLang(next);
});

document.querySelectorAll("[data-copy]").forEach((button) => {
  button.addEventListener("click", async () => {
    const value = button.getAttribute("data-copy") ?? "";
    const lang = currentLang();
    const ok = await copyText(value);
    button.textContent = ok ? copyDone[lang] : copyFail[lang];
    button.classList.toggle("is-done", ok);
    button.classList.toggle("is-fail", !ok);
    window.setTimeout(() => {
      button.textContent = copyDefault[lang];
      button.classList.remove("is-done", "is-fail");
    }, 1600);
  });
});

applyLang(currentLang());
