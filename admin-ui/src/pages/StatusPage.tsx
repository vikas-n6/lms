import { useStatus } from "../api/status";

function badgeClass(status: string): string {
  const base = "inline-block rounded px-2 py-0.5 text-xs font-medium ";
  if (status === "UP") return base + "bg-green-100 text-green-800";
  if (status === "DOWN") return base + "bg-red-100 text-red-800";
  return base + "bg-neutral-200 text-neutral-700";
}

export function StatusPage() {
  const { data, isLoading, isError } = useStatus();

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold">Service status</h1>
      {isLoading && <p className="text-sm text-neutral-500">Loading…</p>}
      {isError && <p className="text-sm text-red-700">Could not load /api/status.</p>}
      {data && (
        <table className="w-full max-w-lg overflow-hidden rounded border border-neutral-200 bg-white text-sm">
          <thead className="bg-neutral-100 text-left">
            <tr>
              <th className="px-4 py-2 font-medium">Service</th>
              <th className="px-4 py-2 font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {data.services.map((s) => (
              <tr key={s.name} className="border-t border-neutral-100">
                <td className="px-4 py-2">{s.name}</td>
                <td className="px-4 py-2">
                  <span className={badgeClass(s.status)}>{s.status}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
