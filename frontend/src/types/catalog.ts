export type AuditStatus =
  | "PENDING"
  | "AUDITING"
  | "READY"
  | "FAILED"
  | "MANUAL_REVIEW_REQUIRED";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type BillingCycle = "MONTHLY" | "QUARTERLY" | "ANNUAL";

export interface ProductItem {
  id?: number;
  productId?: number;
  vendorName: string;
  productName: string;
  category: string;
  description?: string;
  pricePerSeat: number;
  billingCycle?: BillingCycle;
  auditStatus: AuditStatus;
  aiRiskScore: number | null;
  aiRiskLevel: RiskLevel | null;
  renewalNoticeDays?: number | null;
  isActive?: boolean;
}

export interface ProductListResponse {
  page: number;
  size: number;
  totalElements: number;
  items: ProductItem[];
}

export interface RiskScorecardDetails {
  overallScore: number;
  riskLevel: RiskLevel;
  rubricVersion?: string;
  autoRenewalNoticeDays?: number;
  autoRenewalTermMonths?: number;
  vendorLiabilityCap?: string;
  dataResidencyCompliant?: boolean;
  uncappedLiabilityDetected?: boolean;
  flaggedClauses?: string[];
  auditorSummary?: string;
}

export interface RiskScorecardResponse {
  productId: number;
  vendorName: string;
  productName: string;
  auditStatus: AuditStatus;
  aiRiskScore: number | null;
  aiRiskLevel: RiskLevel | null;
  scorecard: RiskScorecardDetails | null;
}

export interface WishlistItem {
  wishlistItemId?: number;
  id?: number;
  productId: number;
  productName: string;
  vendorName?: string;
  category?: string;
  pricePerSeat?: number;
  aiRiskScore: number | null;
  aiRiskLevel?: RiskLevel | null;
  notes?: string;
  createdAt?: string;
}