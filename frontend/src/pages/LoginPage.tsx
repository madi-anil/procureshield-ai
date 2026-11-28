import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { ShieldCheck, Mail, Lock, Eye, EyeOff, Loader2, AlertCircle } from "lucide-react";
import { useAuth } from "../context/AuthContext";

interface ApiErrorPayload {
  message?: string;
}

export function LoginPage() {
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  function validateForm(): string | null {
    const trimmedEmail = email.trim();
    if (!trimmedEmail) {
      return "Please enter your email address.";
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return "Please enter a valid email address.";
    }
    if (!password) {
      return "Please enter your password.";
    }
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const validationMessage = validateForm();
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }

    try {
      await login({
        email: email.trim(),
        password,
      });
      navigate("/dashboard", { replace: true });
    } catch (err) {
      if (axios.isAxiosError<ApiErrorPayload>(err)) {
        const backendMessage = err.response?.data?.message;
        if (err.response?.status === 401) {
          setFormError(backendMessage || "Invalid email or password.");
        } else if (err.response?.status === 400) {
          setFormError(backendMessage || "Please check your login details.");
        } else if (!err.response) {
          setFormError("Unable to reach the server. Make sure the backend is running on port 8080.");
        } else {
          setFormError(backendMessage || "Sign-in failed. Please try again.");
        }
      } else {
        setFormError("Something unexpected went wrong while signing in.");
      }
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="h-11 w-11 rounded-xl bg-blue-600 text-white flex items-center justify-center">
            <ShieldCheck className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-900">ProcureShield AI</h1>
            <p className="text-sm text-slate-500">Enterprise Procurement Portal</p>
          </div>
        </div>

        <h2 className="text-lg font-semibold text-slate-800 mb-1">Sign in to your account</h2>
        <p className="text-sm text-slate-500 mb-6">
          Enter your corporate credentials to access budgets, catalog, and requisitions.
        </p>

        {formError && (
          <div
            role="alert"
            className="mb-5 flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50 p-3.5 text-sm text-red-700"
          >
            <AlertCircle className="h-4 w-4 mt-0.5 shrink-0 text-red-600" />
            <span>{formError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="login-email" className="block text-sm font-medium text-slate-700 mb-1">
              Email Address
            </label>
            <div className="relative">
              <Mail className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@procureshield.com"
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label htmlFor="login-password" className="block text-sm font-medium text-slate-700 mb-1">
              Password
            </label>
            <div className="relative">
              <Lock className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="login-password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full pl-9 pr-10 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                className="text-slate-400 hover:text-slate-600 absolute right-3 top-1/2 -translate-y-1/2"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full mt-2 inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-medium text-sm py-2.5 px-4 transition-colors cursor-pointer disabled:cursor-not-allowed"
          >
            {isLoading && <Loader2 className="h-4 w-4 animate-spin" />}
            {isLoading ? "Signing in..." : "Sign In"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          New employee?{" "}
          <Link to="/register" className="font-medium text-blue-600 hover:text-blue-700">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
}