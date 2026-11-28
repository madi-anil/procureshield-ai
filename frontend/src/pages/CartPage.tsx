import { useEffect, useState } from "react";
import axios from "axios";
import {
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  AlertTriangle,
  AlertCircle,
  CheckCircle2,
  Loader2,
  ShieldAlert,
  ArrowRight,
  X,
} from "lucide-react";
import { apiClient } from "../api/apiClient";
import { AppLayout } from "../components/AppLayout";
import type { CartLineItem, CartSummary, CheckoutOrderResponse } from "../types/cart";

interface ApiErrorPayload {
  message?: string;
}

interface ToastMessage {
  type: "success" | "error";
  text: string;
}

function getItemUnitPrice(item: CartLineItem): number {
  return item.unitPrice ?? item.pricePerSeat ?? 0;
}

function getItemSubtotal(item: CartLineItem): number {
  if (typeof item.subtotal === "number") return item.subtotal;
  return getItemUnitPrice(item) * item.seatQuantity;
}

function getRiskBadgeClass(score: number | null, riskLevel?: string | null): string {
  const normalized = riskLevel?.toUpperCase();
  if (normalized === "LOW" || (score !== null && score >= 80)) {
    return "bg-emerald-50 text-emerald-700 border-emerald-200";
  }
  if (normalized === "MEDIUM" || (score !== null && score >= 60)) {
    return "bg-amber-50 text-amber-700 border-amber-200";
  }
  if (normalized === "HIGH" || (score !== null && score < 60)) {
    return "bg-red-50 text-red-700 border-red-200";
  }
  return "bg-slate-100 text-slate-600 border-slate-200";
}

function extractApiMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorPayload>(err)) {
    return err.response?.data?.message || fallback;
  }
  return fallback;
}

function getDefaultFutureDate(): string {
  const nextYear = new Date();
  nextYear.setFullYear(nextYear.getFullYear() + 1);
  return nextYear.toISOString().slice(0, 10);
}

export function CartPage() {
  const [cart, setCart] = useState<CartSummary>({
    items: [],
    totalEstimatedCost: 0,
    checkoutBlocked: false,
    highRiskWarning: false,
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [busyItemId, setBusyItemId] = useState<number | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  const [justification, setJustification] = useState<string>("");
  const [contractEndDate, setContractEndDate] = useState<string>(getDefaultFutureDate);
  const [submittingCheckout, setSubmittingCheckout] = useState<boolean>(false);
  const [createdOrder, setCreatedOrder] = useState<CheckoutOrderResponse | null>(null);

  async function loadCart() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<CartSummary>("/cart");
      setCart({
        ...res.data,
        items: res.data.items ?? [],
        totalEstimatedCost: res.data.totalEstimatedCost ?? 0,
      });
    } catch (err) {
      setError(extractApiMessage(err, "Could not load your requisition cart."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadCart();
  }, []);

  async function handleUpdateSeats(item: CartLineItem, nextSeats: number) {
    if (nextSeats < 1) return;
    setBusyItemId(item.itemId);
    setToast(null);
    setCreatedOrder(null);
    try {
      const res = await apiClient.post<CartSummary>("/cart/items", {
        productId: item.productId,
        seatQuantity: nextSeats,
      });
      if (res.data && Array.isArray(res.data.items)) {
        setCart({
          ...res.data,
          totalEstimatedCost: res.data.totalEstimatedCost ?? 0,
        });
      } else {
        await loadCart();
      }
      setToast({
        type: "success",
        text: `Updated ${item.productName} to ${nextSeats} seat${nextSeats === 1 ? "" : "s"}.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Could not update seat quantity."),
      });
    } finally {
      setBusyItemId(null);
    }
  }

  async function handleRemoveItem(item: CartLineItem) {
    setBusyItemId(item.itemId);
    setToast(null);
    setCreatedOrder(null);
    try {
      const res = await apiClient.delete<CartSummary | undefined>(`/cart/items/${item.itemId}`);
      if (res.data && Array.isArray(res.data.items)) {
        setCart({
          ...res.data,
          totalEstimatedCost: res.data.totalEstimatedCost ?? 0,
        });
      } else {
        await loadCart();
      }
      setToast({
        type: "success",
        text: `Removed ${item.productName} from your cart.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Could not remove line item."),
      });
    } finally {
      setBusyItemId(null);
    }
  }

  async function handleCheckout(e: React.FormEvent) {
    e.preventDefault();
    if (cart.items.length === 0 || cart.checkoutBlocked) return;

    const trimmedJustification = justification.trim();
    if (!trimmedJustification) {
      setToast({
        type: "error",
        text: "Please enter a business justification before submitting the requisition.",
      });
      return;
    }

    setSubmittingCheckout(true);
    setToast(null);
    try {
      const res = await apiClient.post<CheckoutOrderResponse>("/orders/checkout", {
        justification: trimmedJustification,
        contractEndDate,
      });
      setCreatedOrder(res.data);
      setJustification("");
      await loadCart();
      setToast({
        type: "success",
        text: `Created Purchase Order ${res.data.poNumber} (${res.data.status}).`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Checkout failed. Please review cart items and dates."),
      });
    } finally {
      setSubmittingCheckout(false);
    }
  }

  const hasHighRiskItem =
    Boolean(cart.highRiskWarning) ||
    cart.items.some((item) => item.aiRiskScore !== null && item.aiRiskScore < 60);

  const highRiskCount = cart.items.filter(
    (item) => item.aiRiskScore !== null && item.aiRiskScore < 60
  ).length;

  const isCheckoutDisabled =
    loading ||
    submittingCheckout ||
    cart.items.length === 0 ||
    Boolean(cart.checkoutBlocked) ||
    busyItemId !== null;

  return (
    <AppLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Requisition Cart</h1>
          <p className="text-sm text-slate-500">
            Adjust seat counts, review AI contract risk warnings, and convert your requisition into a Purchase Order.
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

        {createdOrder && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900 space-y-1">
            <p className="font-semibold">
              Purchase Order {createdOrder.poNumber} submitted ({createdOrder.status})
            </p>
            <p className="text-xs text-emerald-800">
              Total Amount: ${Number(createdOrder.totalAmount ?? 0).toLocaleString()}
              {createdOrder.requiresEscalation ? " · Escalation required for approval" : ""}
            </p>
          </div>
        )}

        {hasHighRiskItem && (
          <div className="rounded-xl border border-rose-300 bg-rose-50 p-4 flex items-start gap-3 text-rose-800">
            <ShieldAlert className="h-5 w-5 shrink-0 text-rose-600 mt-0.5" />
            <div className="text-sm">
              <p className="font-semibold">
                {cart.warningMessage ||
                  `High AI Contract Risk Detected: ${highRiskCount} item${
                    highRiskCount === 1 ? "" : "s"
                  } in your requisition has an AI Risk Score below 60.`}
              </p>
              <p className="text-xs text-rose-700 mt-0.5">
                Your approver will see this AI risk score during Purchase Order review.
              </p>
            </div>
          </div>
        )}

        {cart.checkoutBlocked && (
          <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 flex items-start gap-3 text-amber-900">
            <AlertTriangle className="h-5 w-5 shrink-0 text-amber-600 mt-0.5" />
            <div className="text-sm">
              <p className="font-semibold">Checkout temporarily blocked</p>
              <p className="text-xs text-amber-800 mt-0.5">
                One or more items in your cart is inactive or awaiting a completed SLA audit. Remove or update affected items before checking out.
              </p>
            </div>
          </div>
        )}

        {loading && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading requisition cart...</span>
          </div>
        )}

        {!loading && error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-center gap-2.5 text-sm text-red-700"
          >
            <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
            <span>{error}</span>
          </div>
        )}

        {!loading && !error && cart.items.length === 0 && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 text-center space-y-2">
            <ShoppingCart className="h-8 w-8 text-slate-400 mx-auto" />
            <p className="text-sm font-medium text-slate-700">Your requisition cart is empty</p>
            <p className="text-xs text-slate-500">
              Add audited SaaS seats from the Vendor Catalog or Vendor Shortlist to prepare a Purchase Order.
            </p>
          </div>
        )}

        {!loading && !error && cart.items.length > 0 && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
            <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
              {cart.items.map((item) => {
                const isBusy = busyItemId === item.itemId;
                const unitPrice = getItemUnitPrice(item);
                const subtotal = getItemSubtotal(item);
                const notReady = item.auditStatus && item.auditStatus !== "READY";

                return (
                  <div
                    key={item.itemId}
                    className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
                  >
                    <div className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-semibold text-slate-900">{item.productName}</h2>
                        <span
                          className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${getRiskBadgeClass(
                            item.aiRiskScore,
                            item.aiRiskLevel
                          )}`}
                        >
                          {item.aiRiskScore !== null
                            ? `${item.aiRiskScore} · ${item.aiRiskLevel ?? ""}`
                            : "Pending Audit"}
                        </span>
                        {notReady && (
                          <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-amber-100 text-amber-800">
                            {item.auditStatus}
                          </span>
                        )}
                      </div>
                      {item.vendorName && (
                        <p className="text-xs text-slate-500">{item.vendorName}</p>
                      )}
                      <p className="text-xs text-slate-600">
                        Unit Price: ${unitPrice.toLocaleString()} / seat
                      </p>
                    </div>

                    <div className="flex items-center justify-between sm:justify-end gap-4">
                      <div className="inline-flex items-center rounded-lg border border-slate-300 bg-slate-50">
                        <button
                          type="button"
                          disabled={isBusy || item.seatQuantity <= 1}
                          onClick={() => void handleUpdateSeats(item, item.seatQuantity - 1)}
                          aria-label={`Decrease seats for ${item.productName}`}
                          className="p-1.5 text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 rounded-l-lg cursor-pointer"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                        <span className="px-3 text-sm font-semibold text-slate-900">
                          {item.seatQuantity}
                        </span>
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleUpdateSeats(item, item.seatQuantity + 1)}
                          aria-label={`Increase seats for ${item.productName}`}
                          className="p-1.5 text-slate-700 hover:bg-slate-200/70 disabled:opacity-40 rounded-r-lg cursor-pointer"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      <div className="text-right min-w-[84px]">
                        <p className="text-sm font-bold text-slate-900">
                          ${subtotal.toLocaleString()}
                        </p>
                      </div>

                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => void handleRemoveItem(item)}
                        aria-label={`Remove ${item.productName}`}
                        className="p-2 rounded-lg border border-slate-300 text-slate-600 hover:bg-red-50 hover:border-red-200 hover:text-red-600 disabled:opacity-40 cursor-pointer"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>

            <form
              onSubmit={(e) => void handleCheckout(e)}
              className="bg-white rounded-xl border border-slate-200 p-5 space-y-4"
            >
              <h2 className="text-base font-bold text-slate-900">Requisition & Checkout</h2>

              <div className="space-y-2 text-sm border-b border-slate-100 pb-4">
                <div className="flex justify-between text-slate-600">
                  <span>Line Items</span>
                  <span>{cart.itemCount ?? cart.items.length}</span>
                </div>
                {typeof cart.departmentRemainingBudget === "number" && (
                  <div className="flex justify-between text-slate-600">
                    <span>Department Remaining Budget</span>
                    <span>${cart.departmentRemainingBudget.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-slate-900 text-base pt-1">
                  <span>Total Estimated Cost</span>
                  <span>${Number(cart.totalEstimatedCost ?? 0).toLocaleString()}</span>
                </div>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="contract-end-date"
                  className="block text-xs font-semibold uppercase tracking-wider text-slate-600"
                >
                  Contract End Date
                </label>
                <input
                  id="contract-end-date"
                  type="date"
                  required
                  value={contractEndDate}
                  onChange={(e) => setContractEndDate(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="requisition-justification"
                  className="block text-xs font-semibold uppercase tracking-wider text-slate-600"
                >
                  Business Justification
                </label>
                <textarea
                  id="requisition-justification"
                  rows={3}
                  required
                  placeholder="Describe why these seats are needed and how they align with team goals..."
                  value={justification}
                  onChange={(e) => setJustification(e.target.value)}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none"
                />
              </div>

              <button
                type="submit"
                disabled={isCheckoutDisabled}
                className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white text-sm font-semibold px-4 py-2.5 transition-colors cursor-pointer"
              >
                {submittingCheckout ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Submitting Order...</span>
                  </>
                ) : (
                  <>
                    <span>Submit Requisition</span>
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </button>
            </form>
          </div>
        )}
      </div>
    </AppLayout>
  );
}