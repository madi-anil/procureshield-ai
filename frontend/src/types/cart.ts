import type { RiskLevel } from "./catalog";

export interface CartLineItem {
  itemId: number;
  productId: number;
  productName: string;
  vendorName?: string | null;
  seatQuantity: number;
  unitPrice?: number;
  pricePerSeat?: number;
  subtotal?: number;
  aiRiskScore: number | null;
  aiRiskLevel?: RiskLevel | string | null;
  auditStatus?: string | null;
}

export interface CartSummary {
  cartId?: number;
  itemCount?: number;
  checkoutBlocked?: boolean;
  totalEstimatedCost: number;
  highRiskWarning?: boolean;
  warningMessage?: string | null;
  departmentRemainingBudget?: number | null;
  items: CartLineItem[];
}

export interface AddCartItemPayload {
  productId: number;
  seatQuantity: number;
}

export interface CheckoutOrderPayload {
  justification: string;
  contractEndDate: string;
}

export interface CheckoutOrderResponse {
  poId: number;
  poNumber: string;
  status: string;
  totalAmount: number;
  highestAiRiskScore?: number | null;
  requiresEscalation?: boolean;
  contractEndDate?: string;
}