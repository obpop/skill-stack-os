import type { NextConfig } from "next";

const deploymentId = process.env.DEPLOYMENT_VERSION?.trim();
const apiOrigin = (() => {
  try {
    const parsed = new URL(process.env.NEXT_PUBLIC_API_URL ?? "");
    return parsed.protocol === "http:" || parsed.protocol === "https:" ? parsed.origin : null;
  } catch {
    return null;
  }
})();
const connectSource = apiOrigin ? `'self' ${apiOrigin}` : "'self'";
// React uses eval() in development only, for debugging call stacks. Production keeps the strict policy.
const scriptSource = process.env.NODE_ENV === "development" ? "'self' 'unsafe-inline' 'unsafe-eval'" : "'self' 'unsafe-inline'";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  output: "standalone",
  agentRules: false,
  ...(deploymentId ? { deploymentId } : {}),
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: `default-src 'self'; base-uri 'self'; connect-src ${connectSource}; font-src 'self'; form-action 'self'; frame-ancestors 'none'; img-src 'self' data: blob:; object-src 'none'; script-src ${scriptSource}; style-src 'self' 'unsafe-inline'` },
          { key: "Permissions-Policy", value: "camera=(), geolocation=(), microphone=(), payment=(), usb=()" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
        ],
      },
    ];
  },
};

export default nextConfig;
