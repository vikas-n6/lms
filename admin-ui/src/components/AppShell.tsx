import { type ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { BusinessDateWidget } from "./BusinessDateWidget";

const navItems = [
  { to: "/", label: "Status" },
  { to: "/customers", label: "Customers" },
  { to: "/applications", label: "Applications" },
  { to: "/review-queue", label: "Review queue" },
  { to: "/decisions", label: "Decision explainer" },
  { to: "/loans", label: "Loans" },
  { to: "/ledger", label: "Ledger" },
  { to: "/simulators", label: "Simulators" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { username, logout } = useAuth();
  return (
    <div className="min-h-screen flex bg-neutral-50 text-neutral-900">
      <aside className="w-56 shrink-0 border-r border-neutral-200 bg-white p-4">
        <div className="mb-6 text-lg font-semibold">LMS Admin</div>
        <nav className="flex flex-col gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) =>
                `rounded px-3 py-2 text-sm ${isActive ? "bg-neutral-900 text-white" : "hover:bg-neutral-100"}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex-1 flex flex-col">
        <header className="flex items-center justify-between border-b border-neutral-200 bg-white px-6 py-3">
          <div className="flex items-center gap-3 text-sm text-neutral-500">
            <span>Back office</span>
            <BusinessDateWidget />
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-neutral-700">{username}</span>
            <button
              onClick={logout}
              className="rounded border border-neutral-300 px-3 py-1 hover:bg-neutral-100"
            >
              Log out
            </button>
          </div>
        </header>
        <main className="p-6">{children}</main>
      </div>
    </div>
  );
}
