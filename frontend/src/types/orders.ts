export type PurchaseOrderStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "ISSUED"
  | "COMPLETED";

export interface PurchaseOrderLineItem {
  productId: number;
  productName: string;
  seatQuantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface PurchaseOrderAuditEntry {
  id?: number;
  actorName: string;
  actorRole?: string | null;
  previousStatus?: PurchaseOrderStatus | null;
  newStatus: PurchaseOrderStatus;
  comments?: string | null;
  timestamp: string;
}

export interface PurchaseOrderSummary {
  poId?: number;
  id?: number;
  poNumber: string;
  status: PurchaseOrderStatus;
  totalAmount: number;
  highestAiRiskScore?: number | null;
  requiresEscalation?: boolean;
  requesterId?: number;
  requesterName?: string;
  departmentId?: number;
  departmentName?: string;
  contractEndDate?: string;
  justification?: string;
  createdAt?: string;
}

export interface PurchaseOrderDetail extends PurchaseOrderSummary {
  items?: PurchaseOrderLineItem[];
  auditLogs?: PurchaseOrderAuditEntry[];
  transactionRef?: string | null;
  settledAt?: string | null;
}

export interface UpdateOrderStatusPayload {
  newStatus: "APPROVED" | "REJECTED" | "ISSUED";
  comments?: string;
}

export interface SettleOrderResponse {
  paymentId?: number;
  poId?: number;
  poNumber: string;
  transactionRef: string;
  amountPaid: number;
  poStatus?: PurchaseOrderStatus;
  paidAt?: string;
}
