import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import {
  Search,
  ShieldAlert,
  Bookmark,
  ShoppingCart,
  FileCheck2,
  X,
  Loader2,
  AlertCircle,
  CheckCircle2,
} from "lucide-react";
import { apiClient } from "../api/apiClient";
import { AppLayout } from "../components/AppLayout";
import type {
  ProductItem,
  ProductListResponse,
  RiskLevel,
  RiskScorecardResponse,
} from "../types/catalog";

interface ApiErrorPayload {
  message?: string;
}

interface ToastMessage {
  type: "success" | "error";
  text: string;
}

function getProductId(item: ProductItem): number {
  return item.productId ?? item.id ?? 0;
}

function getRiskBadgeStyle(riskLevel: RiskLevel | null, score: number | null): string {
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

export function CatalogPage() {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  const [searchTerm, setSearchTerm] = useState<string>("");
  const [debouncedSearch, setDebouncedSearch] = useState<string>("");
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [maxRiskScore, setMaxRiskScore] = useState<string>("");

  const [bookmarkedIds, setBookmarkedIds] = useState<Set<number>>(() => new Set<number>());
  const [busyProductId, setBusyProductId] = useState<number | null>(null);

  const [scorecardOpen, setScorecardOpen] = useState<boolean>(false);
  const [scorecardLoading, setScorecardLoading] = useState<boolean>(false);
  const [scorecardError, setScorecardError] = useState<string | null>(null);
  const [activeScorecard, setActiveScorecard] = useState<RiskScorecardResponse | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(searchTerm.trim().toLowerCase());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchTerm]);

  useEffect(() => {
    let active = true;

    async function fetchCatalog() {
      setLoading(true);
      setError(null);
      try {
        const params: Record<string, string | number> = { page: 0, size: 20 };
        if (selectedCategory !== "ALL") {
          params.category = selectedCategory;
        }
        if (maxRiskScore.trim() !== "") {
          const parsedScore = Number(maxRiskScore);
          if (!Number.isNaN(parsedScore) && parsedScore >= 1 && parsedScore <= 100) {
            params.maxRiskScore = parsedScore;
          }
        }

        const res = await apiClient.get<ProductListResponse>("/products", { params });
        if (active) {
          setProducts(res.data.items ?? []);
        }
      } catch (err) {
        if (!active) return;
        setError(extractApiMessage(err, "Failed to load vendor catalog."));
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void fetchCatalog();
    return () => {
      active = false;
    };
  }, [selectedCategory, maxRiskScore]);

  const categories = useMemo<string[]>(() => {
    const set = new Set<string>(["ALL"]);
    products.forEach((product) => {
      if (product.category) {
        set.add(product.category);
      }
    });
    return Array.from(set);
  }, [products]);

  const filteredProducts = useMemo<ProductItem[]>(() => {
    if (!debouncedSearch) return products;
    return products.filter(
      (item) =>
        item.productName.toLowerCase().includes(debouncedSearch) ||
        item.vendorName.toLowerCase().includes(debouncedSearch) ||
        item.category.toLowerCase().includes(debouncedSearch)
    );
  }, [products, debouncedSearch]);

  async function handleOpenScorecard(product: ProductItem) {
    const id = getProductId(product);
    if (!id) return;

    setScorecardOpen(true);
    setScorecardLoading(true);
    setScorecardError(null);
    setActiveScorecard(null);

    try {
      const res = await apiClient.get<RiskScorecardResponse>(`/products/${id}/risk-scorecard`);
      setActiveScorecard(res.data);
    } catch (err) {
      setScorecardError(
        extractApiMessage(err, "Risk scorecard is unavailable for this product.")
      );
    } finally {
      setScorecardLoading(false);
    }
  }

  async function handleBookmark(product: ProductItem) {
    const id = getProductId(product);
    if (!id) return;

    setBusyProductId(id);
    setToast(null);
    try {
      await apiClient.post(`/wishlist/${id}`, {
        notes: `Shortlisted ${product.productName} from catalog`,
      });
      setBookmarkedIds((prev) => {
        const next = new Set<number>(prev);
        next.add(id);
        return next;
      });
      setToast({
        type: "success",
        text: `${product.productName} added to your Vendor Shortlist.`,
      });
    } catch (err) {
      if (axios.isAxiosError<ApiErrorPayload>(err) && err.response?.status === 409) {
        setBookmarkedIds((prev) => {
          const next = new Set<number>(prev);
          next.add(id);
          return next;
        });
        setToast({
          type: "success",
          text: `${product.productName} is already in your shortlist.`,
        });
      } else {
        setToast({
          type: "error",
          text: extractApiMessage(err, "Could not bookmark vendor."),
        });
      }
    } finally {
      setBusyProductId(null);
    }
  }

  async function handleAddToCart(product: ProductItem) {
    const id = getProductId(product);
    if (!id || product.auditStatus !== "READY") return;

    setBusyProductId(id);
    setToast(null);
    try {
      await apiClient.post("/cart/items", {
        productId: id,
        seatQuantity: 1,
      });
      setToast({
        type: "success",
        text: `Added 1 seat of ${product.productName} to your requisition cart.`,
      });
    } catch (err) {
      setToast({
        type: "error",
        text: extractApiMessage(err, "Unable to add seat to requisition cart."),
      });
    } finally {
      setBusyProductId(null);
    }
  }

  const flaggedClauses: string[] = activeScorecard?.scorecard?.flaggedClauses ?? [];

  return (
    <AppLayout>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">Enterprise SaaS Catalog</h1>
            <p className="text-sm text-slate-500">
              Review AI contract risk scores, inspect SLA scorecards, and shortlist or add seats.
            </p>
          </div>
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

        {/* Filter Bar */}
        <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="relative md:col-span-2">
              <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search by SaaS product, vendor, or category..."
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <input
                type="number"
                min={1}
                max={100}
                value={maxRiskScore}
                onChange={(e) => setMaxRiskScore(e.target.value)}
                placeholder="Max AI Risk Score (1–100, optional)"
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            {categories.map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setSelectedCategory(cat)}
                className={`px-3 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
                  selectedCategory === cat
                    ? "bg-blue-600 text-white"
                    : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                }`}
              >
                {cat === "ALL" ? "All Categories" : cat}
              </button>
            ))}
          </div>
        </div>

        {/* Catalog Grid */}
        {loading && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading SaaS catalog...</span>
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

        {!loading && !error && filteredProducts.length === 0 && (
          <div className="bg-white rounded-xl border border-slate-200 p-10 text-center text-sm text-slate-500">
            No matching SaaS products found. Try clearing your search or risk score filter.
          </div>
        )}

        {!loading && !error && filteredProducts.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredProducts.map((item, index) => {
              const id = getProductId(item);
              const isReady = item.auditStatus === "READY";
              const isBookmarked = bookmarkedIds.has(id);
              const isBusy = busyProductId === id;

              return (
                <div
                  key={`${item.productName}-${id || index}`}
                  className="bg-white rounded-xl border border-slate-200 p-5 flex flex-col justify-between gap-4 shadow-2xs"
                >
                  <div className="space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-xs font-medium text-blue-600">{item.category}</p>
                        <h2 className="text-base font-bold text-slate-900 mt-0.5">
                          {item.productName}
                        </h2>
                        <p className="text-xs text-slate-500">{item.vendorName}</p>
                      </div>

                      <span
                        className={`text-xs font-semibold px-2.5 py-1 rounded-full border shrink-0 ${getRiskBadgeStyle(
                          item.aiRiskLevel,
                          item.aiRiskScore
                        )}`}
                      >
                        {item.aiRiskScore !== null
                          ? `Score ${item.aiRiskScore} · ${item.aiRiskLevel ?? ""}`
                          : item.auditStatus}
                      </span>
                    </div>

                    {item.description && (
                      <p className="text-xs text-slate-600 line-clamp-2">{item.description}</p>
                    )}

                    <div className="flex items-baseline justify-between pt-1 border-t border-slate-100">
                      <span className="text-lg font-bold text-slate-900">
                        ${item.pricePerSeat.toLocaleString()}
                      </span>
                      <span className="text-xs text-slate-500">
                        per seat / {item.billingCycle ?? "ANNUAL"}
                      </span>
                    </div>
                  </div>

                  <div className="space-y-2 pt-2 border-t border-slate-100">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => void handleOpenScorecard(item)}
                        className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium py-2 px-3 cursor-pointer"
                      >
                        <FileCheck2 className="h-3.5 w-3.5 text-blue-600" />
                        <span>Risk Scorecard</span>
                      </button>

                      <button
                        type="button"
                        disabled={isBusy}
                        onClick={() => void handleBookmark(item)}
                        title="Bookmark to Vendor Shortlist"
                        className={`inline-flex items-center justify-center rounded-lg border px-3 py-2 text-xs font-medium transition-colors cursor-pointer ${
                          isBookmarked
                            ? "border-blue-200 bg-blue-50 text-blue-700"
                            : "border-slate-300 bg-white hover:bg-slate-50 text-slate-700"
                        }`}
                      >
                        <Bookmark className="h-3.5 w-3.5" />
                      </button>
                    </div>

                    <button
                      type="button"
                      disabled={!isReady || isBusy}
                      title={
                        !isReady
                          ? "SLA risk audit not complete"
                          : `Add ${item.productName} to requisition cart`
                      }
                      onClick={() => void handleAddToCart(item)}
                      className="w-full inline-flex items-center justify-center gap-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-500 text-white text-xs font-medium py-2 px-3 transition-colors cursor-pointer disabled:cursor-not-allowed"
                    >
                      {isBusy ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <ShoppingCart className="h-3.5 w-3.5" />
                      )}
                      <span>{isReady ? "Add to Requisition" : "Audit Pending"}</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* AI Risk Scorecard Modal */}
        {scorecardOpen && (
          <div className="fixed inset-0 z-40 bg-slate-900/40 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl border border-slate-200 max-w-lg w-full p-6 shadow-xl space-y-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ShieldAlert className="h-5 w-5 text-blue-600" />
                  <h3 className="text-lg font-bold text-slate-900">AI Contract Risk Scorecard</h3>
                </div>
                <button
                  type="button"
                  onClick={() => setScorecardOpen(false)}
                  aria-label="Close scorecard modal"
                  className="text-slate-400 hover:text-slate-700 cursor-pointer"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {scorecardLoading && (
                <div className="py-8 flex items-center justify-center gap-2 text-sm text-slate-600">
                  <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
                  <span>Fetching compliance scorecard...</span>
                </div>
              )}

              {!scorecardLoading && scorecardError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3.5 text-sm text-red-700">
                  {scorecardError}
                </div>
              )}

              {!scorecardLoading && !scorecardError && activeScorecard && (
                <div className="space-y-3 text-sm">
                  <div className="flex items-center justify-between bg-slate-50 rounded-lg p-3 border border-slate-200">
                    <div>
                      <p className="font-semibold text-slate-900">
                        {activeScorecard.productName}
                      </p>
                      <p className="text-xs text-slate-500">{activeScorecard.vendorName}</p>
                    </div>
                    <span
                      className={`text-xs font-semibold px-2.5 py-1 rounded-full border ${getRiskBadgeStyle(
                        activeScorecard.aiRiskLevel,
                        activeScorecard.aiRiskScore
                      )}`}
                    >
                      {activeScorecard.aiRiskScore ?? "N/A"} · {activeScorecard.aiRiskLevel ?? ""}
                    </span>
                  </div>

                  {activeScorecard.scorecard?.auditorSummary && (
                    <p className="text-slate-700">{activeScorecard.scorecard.auditorSummary}</p>
                  )}

                  {flaggedClauses.length > 0 && (
                    <div>
                      <p className="font-semibold text-slate-800 mb-1">Flagged Clauses</p>
                      <ul className="list-disc pl-5 space-y-1 text-xs text-slate-600">
                        {flaggedClauses.map((clause: string, idx: number) => (
                          <li key={idx}>{clause}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}

              <div className="pt-2 flex justify-end">
                <button
                  type="button"
                  onClick={() => setScorecardOpen(false)}
                  className="rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-medium px-4 py-2 cursor-pointer"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppLayout>
  );
}