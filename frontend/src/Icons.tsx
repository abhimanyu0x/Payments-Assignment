import type { SVGProps } from "react";
type Props = SVGProps<SVGSVGElement> & { size?: number };
function icon(path: string) {
  return function Icon({ size = 20, ...props }: Props) {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        {...props}
      >
        <path d={path} />
      </svg>
    );
  };
}
export const ArrowUpRight = icon("M7 17 17 7M7 7h10v10");
export const Check = icon("m5 12 4 4L19 6");
export const ChevronRight = icon("m9 5 7 7-7 7");
export const CircleDot = icon(
  "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M13 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0",
);
export const CreditCard = icon("M3 5h18v14H3zM3 10h18M6 15h4");
export const FileText = icon("M14 3H5v18h14V8zM14 3v5h5M8 12h8M8 16h6");
export const KeyRound = icon(
  "M15 9a5 5 0 1 1 5 5 5 5 0 0 1-5-5M16 13l-9 9H3v-4l4-4h4l2-2",
);
export const Layers = icon(
  "m12 3 10 6-10 6L2 9zm-10 11 10 6 10-6M2 18l10 6 10-6",
);
export const Plus = icon("M12 5v14M5 12h14");
export const RefreshCw = icon(
  "M20 8a8 8 0 0 0-14-3L3 8M3 3v5h5M4 16a8 8 0 0 0 14 3l3-3m0 5v-5h-5",
);
export const Send = icon("m22 2-7 20-4-9-9-4zM22 2 11 13");
export const Users = icon(
  "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-4",
);
export const X = icon("m6 6 12 12M6 18 18 6");
