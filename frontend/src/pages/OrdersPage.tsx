import { useEffect, useState } from "react";
import axios from "axios";
import {
  FileSpreadsheet,
  CheckCircle2,
  XCircle,
  Send,
  Receipt,
  Lock,
  Loader2,
  AlertCircle,
  ShieldAlert,
  X,
} from "lucide-react";
import { apiClient } from "../api/apiClient";
import { AppLayout } from "../components/AppLayout";
import { useAuth } from "../context/AuthContext";
import type {
  PurchaseOrderAuditEntry,
  PurchaseOrderDetail,
  PurchaseOrderStatus,
  PurchaseOrderSummary,
  SettleOrderResponse,
} from "../types/orders";

interface OrdersListPayload {
  orders?: PurchaseOrderSummary[];
  items?: PurchaseOrderSummary[];
  content?: PurchaseOrderSummary[];
}

interface ApiErrorPayload {
  message?: string;
}

interface ToastMessage {
  type: "success" | "error";
  text: string;
}

function getOrderId(order: { poId?: number; id?: number }): number {
  return order.poId ?? order.id ?? 0;
}

function getStatusBadgeClass(status: PurchaseOrderStatus): string {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    case "APPROVED":
    case "ISSUED":
      return "bg-blue-50 text-blue-700 border-blue-200";
    case "PENDING_APPROVAL":
      return "bg-amber-50 text-amber-800 border-amber-200";
    case "REJECTED":
      return "bg-red-50 text-red-700 border-red-200";
    default:
      return "bg-slate-100 text-slate-700 border-slate-200";
  }
}

function extractApiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorPayload>(err)) {
    return err.response?.data?.message || fallback;
  }
  return fallback;
}

export function OrdersPage() {
  const { user } = useAuth();
  const [orders, setOrders] = useState<PurchaseOrderSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<PurchaseOrderDetail | null>(null);
  const [loadingList, setLoadingList] = useState<boolean>(true);
  const [loadingDetail, setLoadingDetail] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  const [comments, setComments] = useState<string>("");
  const [riskAcknowledged, setRiskAcknowledged] = useState<boolean>(false);
  const [actionBusy, setActionBusy] = useState<boolean>(false);

  async function loadOrderDetail(poId: number) {
    setLoadingDetail(true);
    try {
      const res = await apiClient.get<PurchaseOrderDetail>(`/orders/${poId}`);
      setDetail(res.data);
      setRiskAcknowledged(false);
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Could not load purchase order details."),
      });
    } finally {
      setLoadingDetail(false);
    }
  }

  async function loadOrders(preferredId?: number | null) {
    setLoadingList(true);
    setError(null);
    try {
      const res = await apiClient.get<OrdersListPayload | PurchaseOrderSummary[]>("/orders");
      const list = Array.isArray(res.data)
        ? res.data
        : (res.data.orders ?? res.data.items ?? res.data.content ?? []);
      setOrders(list);

      const targetId =
        preferredId !== undefined && preferredId !== null
          ? preferredId
          : selectedId ?? (list.length > 0 ? getOrderId(list[0]) : null);

      if (targetId) {
        setSelectedId(targetId);
        await loadOrderDetail(targetId);
      } else {
        setDetail(null);
      }
    } catch (err) {
      setError(extractApiMessage(err, "Could not load purchase orders."));
    } finally {
      setLoadingList(false);
    }
  }

  useEffect(() => {
    void loadOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleSelectOrder(poId: number) {
    setSelectedId(poId);
    setComments("");
    await loadOrderDetail(poId);
  }

  const activeOrder = detail ?? orders.find((o) => getOrderId(o) === selectedId) ?? null;
  const activeOrderId = activeOrder ? getOrderId(activeOrder) : 0;

  const role = user?.role ?? "";
  const isDeptHead = role === "ROLE_DEPARTMENT_HEAD";
  const isProcurementAdmin = role === "ROLE_PROCUREMENT_ADMIN";

  const isRequester =
    Boolean(user?.email && activeOrder?.requesterName && user.email.toLowerCase().startsWith(activeOrder.requesterName.toLowerCase().split(" ")[0]?.toLowerCase() ?? "")) ||
    false;

  const hasHighRisk =
    typeof activeOrder?.highestAiRiskScore === "number" &&
    activeOrder.highestAiRiskScore < 60;

  const requiresEscalation = Boolean(activeOrder?.requiresEscalation);

  const canApprove =
    activeOrder?.status === "PENDING_APPROVAL" &&
    !isRequester &&
    ((isDeptHead && !requiresEscalation) || isProcurementAdmin) &&
    (!hasHighRisk || riskAcknowledged);

  const canReject =
    activeOrder?.status === "PENDING_APPROVAL" &&
    (isRequester || isDeptHead || isProcurementAdmin);

  const canIssue =
    activeOrder?.status === "APPROVED" &&
    !isRequester &&
    isProcurementAdmin;

  const canSettle =
    activeOrder?.status === "ISSUED" &&
    !isRequester &&
    isProcurementAdmin;

  async function handleStatusTransition(newStatus: "APPROVED" | "REJECTED" | "ISSUED") {
    if (!activeOrderId) return;

    const trimmedComments = comments.trim();
    if (newStatus === "REJECTED" && !trimmedComments) {
      setToast({
        type: "error",
        text: "Comments are required when rejecting or withdrawing a purchase order.",
      });
      return;
    }

    setActionBusy(true);
    setToast(null);
    try {
      await apiClient.patch(`/orders/${activeOrderId}/status`, {
        newStatus,
        comments: trimmedComments || undefined,
      });
      setComments("");
      await loadOrders(activeOrderId);
      setToast({
        type: "success",
        text: `Purchase order ${activeOrder?.poNumber ?? ""} moved to ${newStatus}.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, `Failed to transition order to ${newStatus}.`),
      });
    } finally {
      setActionBusy(false);
    }
  }

  async function handleSettleInvoice() {
    if (!activeOrderId) return;
    setActionBusy(true);
    setToast(null);
    try {
      const res = await apiClient.post<SettleOrderResponse>(`/payments/settle/${activeOrderId}`);
      await loadOrders(activeOrderId);
      setToast({
        type: "success",
        text: `Settled ${activeOrder?.poNumber ?? ""} (Ref: ${res.data.transactionRef}).`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Failed to settle purchase order against budget."),
      });
    } finally {
      setActionBusy(false);
    }
  }

  const auditLogs: PurchaseOrderAuditEntry[] = detail?.auditLogs ?? [];

  return (
    <AppLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Purchase Orders & Settlement</h1>
          <p className="text-sm text-slate-500">
            Track requisition approvals, inspect immutable audit trails, and settle issued vendor invoices.
          </p>
        </div>

        {toast && (
          <div
            role="status"
            className={`rounded-xl border p-3.5 flex items-center justify-between gap-3 text-sm ${
              toast.type === "success"
                ? "bg-emerald-50 border-emerald-200 text-emerald-800"
                : "bg-red-50 border-red-200 text-red-800"
            }`}
          >
            <div className="flex items-center gap-2">
              {toast.type === "success" ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
              ) : (
                <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
              )}
              <span>{toast.text}</span>
            </div>
            <button
              type="button"
              onClick={() => setToast(null)}
              aria-label="Dismiss notification"
              className="text-slate-500 hover:text-slate-800 cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {loadingList && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading purchase orders...</span>
          </div>
        )}

        {!loadingList && error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-center gap-2.5 text-sm text-red-700"
          >
            <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
            <span>{error}</span>
          </div>
        )}

        {!loadingList && !error && orders.length === 0 && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 text-center space-y-2">
            <FileSpreadsheet className="h-8 w-8 text-slate-400 mx-auto" />
            <p className="text-sm font-medium text-slate-700">No purchase orders yet</p>
            <p className="text-xs text-slate-500">
              Submit a requisition from the Requisition Cart to create a Purchase Order for approval.
            </p>
          </div>
        )}

        {!loadingList && !error && orders.length > 0 && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Orders List */}
            <div className="lg:col-span-5 bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
              {orders.map((order) => {
                const id = getOrderId(order);
                const isSelected = id === activeOrderId;

                return (
                  <button
                    key={id || order.poNumber}
                    type="button"
                    onClick={() => void handleSelectOrder(id)}
                    className={`w-full text-left p-4 transition-colors cursor-pointer ${
                      isSelected ? "bg-blue-50/70" : "hover:bg-slate-50"
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-sm text-slate-900">
                        {order.poNumber}
                      </span>
                      <span
                        className={`text-[11px] font-semibold px-2 py-0.5 rounded-full border ${getStatusBadgeClass(
                          order.status
                        )}`}
                      >
                        {order.status}
                      </span>
                    </div>
                    <div className="mt-1 flex items-center justify-between text-xs text-slate-500">
                      <span>{order.departmentName ?? "Department"}</span>
                      <span className="font-semibold text-slate-800">
                        ${Number(order.totalAmount ?? 0).toLocaleString()}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Selected Order Detail, Actions & Audit Trail */}
            <div className="lg:col-span-7 space-y-5">
              {loadingDetail ? (
                <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
                  <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
                  <span>Loading order details...</span>
                </div>
              ) : activeOrder ? (
                <>
                  <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
                    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 pb-4">
                      <div>
                        <div className="flex items-center gap-2.5">
                          <h2 className="text-lg font-bold text-slate-900">
                            {activeOrder.poNumber}
                          </h2>
                          <span
                            className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${getStatusBadgeClass(
                              activeOrder.status
                            )}`}
                          >
                            {activeOrder.status}
                          </span>
                        </div>
                        <p className="text-xs text-slate-500 mt-1">
                          {activeOrder.requesterName ? `Requested by ${activeOrder.requesterName} · ` : ""}
                          {activeOrder.departmentName ?? ""}
                        </p>
                      </div>

                      <div className="text-right">
                        <p className="text-xs text-slate-500">Total Amount</p>
                        <p className="text-lg font-bold text-slate-900">
                          ${Number(activeOrder.totalAmount ?? 0).toLocaleString()}
                        </p>
                      </div>
                    </div>

                    {requiresEscalation && (
                      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
                        Escalation threshold exceeded: approval requires a{" "}
                        <span className="font-semibold">ROLE_PROCUREMENT_ADMIN</span>.
                      </div>
                    )}

                    {hasHighRisk && (
                      <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 flex items-start gap-2.5 text-xs text-rose-800">
                        <ShieldAlert className="h-4 w-4 shrink-0 text-rose-600 mt-0.5" />
                        <div className="space-y-2">
                          <p className="font-semibold">
                            High AI Risk Score Detected ({activeOrder.highestAiRiskScore}/100 &lt; 60).
                          </p>
                          {activeOrder.status === "PENDING_APPROVAL" && (
                            <label className="flex items-center gap-2 cursor-pointer">
                              <input
                                type="checkbox"
                                checked={riskAcknowledged}
                                onChange={(e) => setRiskAcknowledged(e.target.checked)}
                                className="rounded border-rose-300"
                              />
                              <span>I acknowledge the high SLA risk on this requisition.</span>
                            </label>
                          )}
                        </div>
                      </div>
                    )}

                    {activeOrder.justification && (
                      <div className="text-xs text-slate-600 bg-slate-50 rounded-lg p-3">
                        <span className="font-semibold text-slate-800">Justification: </span>
                        {activeOrder.justification}
                      </div>
                    )}

                    {detail?.items && detail.items.length > 0 && (
                      <div className="border border-slate-200 rounded-lg overflow-hidden">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-slate-50 border-b border-slate-200 text-slate-500 uppercase">
                            <tr>
                              <th className="py-2 px-3">Product</th>
                              <th className="py-2 px-3">Seats</th>
                              <th className="py-2 px-3">Unit Price</th>
                              <th className="py-2 px-3 text-right">Subtotal</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100">
                            {detail.items.map((line, i) => (
                              <tr key={`${line.productId}-${i}`}>
                                <td className="py-2 px-3 font-medium text-slate-900">
                                  {line.productName}
                                </td>
                                <td className="py-2 px-3 text-slate-600">{line.seatQuantity}</td>
                                <td className="py-2 px-3 text-slate-600">
                                  ${Number(line.unitPrice ?? 0).toLocaleString()}
                                </td>
                                <td className="py-2 px-3 text-right font-semibold text-slate-900">
                                  ${Number(line.subtotal ?? 0).toLocaleString()}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}

                    {/* Workflow Action Controls */}
                    {(activeOrder.status === "PENDING_APPROVAL" ||
                      activeOrder.status === "APPROVED" ||
                      activeOrder.status === "ISSUED") && (
                      <div className="pt-2 space-y-3 border-t border-slate-100">
                        {activeOrder.status === "PENDING_APPROVAL" && (
                          <div>
                            <label
                              htmlFor="order-action-comments"
                              className="block text-xs font-semibold text-slate-600 mb-1"
                            >
                              Approval / Rejection Comments (required for rejection)
                            </label>
                            <input
                              id="order-action-comments"
                              type="text"
                              value={comments}
                              onChange={(e) => setComments(e.target.value)}
                              placeholder="Add review notes or reason for rejection..."
                              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none"
                            />
                          </div>
                        )}

                        <div className="flex flex-wrap items-center gap-2.5">
                          {activeOrder.status === "PENDING_APPROVAL" && (
                            <>
                              <button
                                type="button"
                                disabled={!canApprove || actionBusy}
                                title={
                                  requiresEscalation && isDeptHead
                                    ? "Requires Procurement Admin approval"
                                    : undefined
                                }
                                onClick={() => void handleStatusTransition("APPROVED")}
                                className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-200 disabled:text-slate-400 text-white text-xs font-semibold px-3.5 py-2 transition-colors cursor-pointer"
                              >
                                <CheckCircle2 className="h-4 w-4" />
                                <span>Approve</span>
                              </button>

                              <button
                                type="button"
                                disabled={!canReject || actionBusy}
                                onClick={() => void handleStatusTransition("REJECTED")}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white hover:bg-red-50 disabled:opacity-40 text-red-700 text-xs font-semibold px-3.5 py-2 transition-colors cursor-pointer"
                              >
                                <XCircle className="h-4 w-4" />
                                <span>{isRequester ? "Withdraw" : "Reject"}</span>
                              </button>
                            </>
                          )}

                          {activeOrder.status === "APPROVED" && (
                            <button
                              type="button"
                              disabled={!canIssue || actionBusy}
                              onClick={() => void handleStatusTransition("ISSUED")}
                              className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400 text-white text-xs font-semibold px-3.5 py-2 transition-colors cursor-pointer"
                            >
                              <Send className="h-4 w-4" />
                              <span>Mark as Issued</span>
                            </button>
                          )}

                          {activeOrder.status === "ISSUED" && (
                            <button
                              type="button"
                              disabled={!canSettle || actionBusy}
                              onClick={() => void handleSettleInvoice()}
                              className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 disabled:bg-slate-200 disabled:text-slate-400 text-white text-xs font-semibold px-3.5 py-2 transition-colors cursor-pointer"
                            >
                              <Receipt className="h-4 w-4" />
                              <span>Settle & Deduct Budget</span>
                            </button>
                          )}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Immutable Audit Timeline */}
                  <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-3">
                    <div className="flex items-center gap-2">
                      <Lock className="h-4 w-4 text-slate-500" />
                      <h3 className="text-sm font-bold text-slate-900">
                        Immutable PO Audit Log
                      </h3>
                    </div>

                    {auditLogs.length === 0 ? (
                      <p className="text-xs text-slate-500">
                        No audit entries recorded yet for this order.
                      </p>
                    ) : (
                      <ul className="space-y-3 border-l-2 border-slate-200 ml-2 pl-4">
                        {auditLogs.map((entry, idx) => (
                          <li key={entry.id ?? idx} className="text-xs space-y-0.5">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="font-semibold text-slate-900">
                                {entry.previousStatus ? `${entry.previousStatus} → ` : ""}
                                {entry.newStatus}
                              </span>
                              <span className="text-slate-500">by {entry.actorName}</span>
                            </div>
                            {entry.comments && (
                              <p className="text-slate-600">{entry.comments}</p>
                            )}
                            <p className="text-[11px] text-slate-400">{entry.timestamp}</p>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </>
              ) : null}
            </div>
          </div>
        )}
      </div>
    </AppLayout>
  );
}