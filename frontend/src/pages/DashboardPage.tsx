import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { ShieldCheck, LogOut, Wallet, Loader2, AlertCircle } from "lucide-react";
import { apiClient } from "../api/apiClient";
import { useAuth } from "../context/AuthContext";

interface DepartmentBudgetSnapshot {
  id: number;
  name: string;
  costCenterCode: string;
  annualBudget: number;
  remainingBudget: number;
  reservedBudget: number;
  spentBudget: number;
}

interface UserBudgetResponse {
  userId: number;
  email: string;
  fullName: string;
  role: string;
  department: DepartmentBudgetSnapshot | null;
}

interface ApiErrorPayload {
  message?: string;
}

export function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [budgetData, setBudgetData] = useState<UserBudgetResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadBudget() {
      setLoading(true);
      setError(null);
      try {
        const res = await apiClient.get<UserBudgetResponse>("/users/me/budget");
        if (active) {
          setBudgetData(res.data);
        }
      } catch (err) {
        if (!active) return;
        if (axios.isAxiosError<ApiErrorPayload>(err)) {
          setError(
            err.response?.data?.message ||
              "Could not load your budget snapshot. Make sure the backend is running."
          );
        } else {
          setError("Something unexpected went wrong while loading dashboard data.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadBudget();
    return () => {
      active = false;
    };
  }, []);

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-blue-600 text-white flex items-center justify-center">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-base font-bold text-slate-900">ProcureShield AI</h1>
            <p className="text-xs text-slate-500">
              {user?.fullName ?? "User"} · {user?.role ?? ""}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-sm font-medium px-3.5 py-2 cursor-pointer"
        >
          <LogOut className="h-4 w-4" />
          Sign Out
        </button>
      </header>

      <main className="max-w-4xl mx-auto p-6">
        <h2 className="text-xl font-semibold text-slate-900 mb-4">Overview</h2>

        {loading && (
          <div className="bg-white rounded-xl border border-slate-200 p-8 flex items-center justify-center gap-2 text-slate-600">
            <Loader2 className="h-5 w-5 animate-spin text-blue-600" />
            <span>Loading your department budget...</span>
          </div>
        )}

        {!loading && error && (
          <div
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 p-4 flex items-start gap-2.5 text-sm text-red-700"
          >
            <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-600" />
            <span>{error}</span>
          </div>
        )}

        {!loading && !error && budgetData && (
          <div className="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
            <div className="flex items-center gap-2 text-slate-800 font-semibold">
              <Wallet className="h-5 w-5 text-blue-600" />
              <span>
                Department Cost Center:{" "}
                {budgetData.department
                  ? `${budgetData.department.name} (${budgetData.department.costCenterCode})`
                  : "Unassigned"}
              </span>
            </div>

            {budgetData.department ? (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 pt-2">
                <div className="rounded-lg bg-slate-50 p-3.5 border border-slate-100">
                  <p className="text-xs text-slate-500">Annual Budget</p>
                  <p className="text-lg font-bold text-slate-900 mt-1">
                    ${budgetData.department.annualBudget.toLocaleString()}
                  </p>
                </div>
                <div className="rounded-lg bg-emerald-50 p-3.5 border border-emerald-100">
                  <p className="text-xs text-emerald-700">Remaining</p>
                  <p className="text-lg font-bold text-emerald-800 mt-1">
                    ${budgetData.department.remainingBudget.toLocaleString()}
                  </p>
                </div>
                <div className="rounded-lg bg-amber-50 p-3.5 border border-amber-100">
                  <p className="text-xs text-amber-700">Reserved</p>
                  <p className="text-lg font-bold text-amber-800 mt-1">
                    ${budgetData.department.reservedBudget.toLocaleString()}
                  </p>
                </div>
                <div className="rounded-lg bg-slate-50 p-3.5 border border-slate-100">
                  <p className="text-xs text-slate-500">Spent</p>
                  <p className="text-lg font-bold text-slate-900 mt-1">
                    ${budgetData.department.spentBudget.toLocaleString()}
                  </p>
                </div>
              </div>
            ) : (
              <p className="text-sm text-slate-500">
                No department cost center is currently linked to your account.
              </p>
            )}
          </div>
        )}
      </main>
    </div>
  );
}