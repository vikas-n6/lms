// Format a Money value (minor units + ISO 4217 code) using Intl.NumberFormat.
// The currency's fraction digits come from Intl, never hard-coded (docs/domain-rules.md).
export function formatMoney(minorUnits: number, currencyCode: string, locale?: string): string {
  const format = new Intl.NumberFormat(locale ?? "en-IN", {
    style: "currency",
    currency: currencyCode,
  });
  const fractionDigits = format.resolvedOptions().maximumFractionDigits ?? 2;
  const major = minorUnits / 10 ** fractionDigits;
  return format.format(major);
}
