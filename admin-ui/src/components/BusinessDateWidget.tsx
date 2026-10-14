import { useAdvanceBusinessDate, useBusinessDate } from "../api/businessDate";

/**
 * Header widget showing the simulated business date, with a one-day advance. The backing endpoint is
 * dev-profile only at the gateway, so in other environments the query errors and the widget hides.
 */
export function BusinessDateWidget() {
  const { data, isError } = useBusinessDate();
  const advance = useAdvanceBusinessDate();

  if (isError || !data) return null;

  return (
    <div className="flex items-center gap-2 rounded border border-dashed border-amber-300 bg-amber-50 px-3 py-1 text-xs">
      <span className="text-amber-700">Business date</span>
      <span className="font-mono font-medium">{data.businessDate || "today"}</span>
      <button
        onClick={() => advance.mutate(1)}
        className="rounded border border-amber-300 px-2 py-0.5 hover:bg-amber-100"
      >
        +1 day
      </button>
    </div>
  );
}
