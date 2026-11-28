import { useEffect, useState } from "react";
import axios from "axios";
import {
  BookmarkCheck,
  Trash2,
  ShoppingCart,
  Loader2,
  AlertCircle,
  CheckCircle2,
  X,
} from "lucide-react";
import { apiClient } from "../api/apiClient";
import { AppLayout } from "../components/AppLayout";
import type { RiskLevel, WishlistItem } from "../types/catalog";

interface WishlistListPayload {
  items?: WishlistItem[];
}

interface ApiErrorPayload {
  message?: string;
}

interface ToastMessage {
  type: "success" | "error";
  text: string;
}

function getWishlistItemId(item: WishlistItem): number {
  return item.wishlistItemId ?? item.id ?? 0;
}

function getRiskBadgeStyle(riskLevel: RiskLevel | null | undefined, score: number | null): string {
  if (riskLevel === "LOW" || (score !== null && score >= 80)) {
    return "bg-emerald-50 text-emerald-700 border-emerald-200";
  }
  if (riskLevel === "MEDIUM" || (score !== null && score >= 60)) {
    return "bg-amber-50 text-amber-700 border-amber-200";
  }
  if (riskLevel === "HIGH" || (score !== null && score < 60)) {
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

export function ShortlistPage() {
  const [items, setItems] = useState<WishlistItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  async function loadShortlist() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<WishlistListPayload | WishlistItem[]>("/wishlist");
      const list = Array.isArray(res.data) ? res.data : (res.data.items ?? []);
      setItems(list);
    } catch (err) {
      setError(extractApiMessage(err, "Could not load your vendor shortlist."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadShortlist();
  }, []);

  async function handleRemove(item: WishlistItem) {
    const id = getWishlistItemId(item);
    if (!id) return;

    setBusyId(id);
    setToast(null);
    try {
      await apiClient.delete(`/wishlist/${id}`);
      setItems((prev) => prev.filter((row) => getWishlistItemId(row) !== id));
      setToast({
        type: "success",
        text: `Removed ${item.productName} from your shortlist.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Failed to remove shortlist item."),
      });
    } finally {
      setBusyId(null);
    }
  }

  async function handleAddSeatToCart(item: WishlistItem) {
    const id = getWishlistItemId(item);
    setBusyId(id || -1);
    setToast(null);
    try {
      await apiClient.post("/cart/items", {
        productId: item.productId,
        seatQuantity: 1,
      });
      setToast({
        type: "success",
        text: `Added 1 seat of ${item.productName} to your requisition cart.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Could not add seat to requisition cart."),
      });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AppLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Vendor Shortlist & Comparison</h1>
          <p className="text-sm text-slate-500">
            Compare shortlisted SaaS tools, review AI risk scores, and add seats into your requisition cart.
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

        {loading && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading shortlisted vendors...</span>
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

        {!loading && !error && items.length === 0 && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 text-center space-y-2">
            <BookmarkCheck className="h-8 w-8 text-slate-400 mx-auto" />
            <p className="text-sm font-medium text-slate-700">Your shortlist is empty</p>
            <p className="text-xs text-slate-500">
              Bookmark SaaS products from the Vendor Catalog to compare pricing and AI risk scores here.
            </p>
          </div>
        )}

        {!loading && !error && items.length > 0 && (
          <div className="bg-white rounded-xl border border-slate-200 overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <th className="py-3 px-4">Product & Vendor</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Seat Price</th>
                  <th className="py-3 px-4">AI Risk</th>
                  <th className="py-3 px-4">Notes</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((item, idx) => {
                  const id = getWishlistItemId(item);
                  const isBusy = busyId === (id || -1);

                  return (
                    <tr key={`${item.productId}-${id || idx}`} className="hover:bg-slate-50/70">
                      <td className="py-3.5 px-4">
                        <p className="font-semibold text-slate-900">{item.productName}</p>
                        {item.vendorName && (
                          <p className="text-xs text-slate-500">{item.vendorName}</p>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-slate-600">{item.category ?? "—"}</td>
                      <td className="py-3.5 px-4 font-medium text-slate-900">
                        {item.pricePerSeat !== undefined
                          ? `$${item.pricePerSeat.toLocaleString()}`
                          : "—"}
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${getRiskBadgeStyle(
                            item.aiRiskLevel,
                            item.aiRiskScore
                          )}`}
                        >
                          {item.aiRiskScore !== null
                            ? `${item.aiRiskScore} · ${item.aiRiskLevel ?? ""}`
                            : "Pending"}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-xs text-slate-600 max-w-xs">
                        {item.notes || "—"}
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            disabled={isBusy}
                            onClick={() => void handleAddSeatToCart(item)}
                            className="inline-flex items-center gap-1 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white text-xs font-medium px-3 py-1.5 cursor-pointer"
                          >
                            <ShoppingCart className="h-3.5 w-3.5" />
                            <span>+1 Seat</span>
                          </button>
                          <button
                            type="button"
                            disabled={isBusy || !id}
                            onClick={() => void handleRemove(item)}
                            aria-label={`Remove ${item.productName}`}
                            className="inline-flex items-center justify-center rounded-lg border border-slate-300 hover:bg-red-50 hover:border-red-200 hover:text-red-600 disabled:opacity-40 text-slate-600 p-1.5 cursor-pointer"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppLayout>
  );
}