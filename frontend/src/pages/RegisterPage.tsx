import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import axios from "axios";
import {
  ShieldCheck,
  User,
  Mail,
  Lock,
  Building2,
  Eye,
  EyeOff,
  Loader2,
  AlertCircle,
  CheckCircle2,
} from "lucide-react";
import { useAuth } from "../context/AuthContext";

interface ApiErrorPayload {
  message?: string;
}

const STRONG_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export function RegisterPage() {
  const { register, isLoading } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [departmentId, setDepartmentId] = useState("1");
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  function validateForm(): string | null {
    const trimmedName = fullName.trim();
    const trimmedEmail = email.trim();
    const parsedDeptId = Number(departmentId);

    if (!trimmedName) {
      return "Please enter your full name.";
    }
    if (trimmedName.length > 100) {
      return "Full name must not exceed 100 characters.";
    }
    if (!trimmedEmail) {
      return "Please enter your email address.";
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      return "Please enter a valid email address.";
    }
    if (!STRONG_PASSWORD_REGEX.test(password)) {
      return "Password must be at least 8 characters and include uppercase, lowercase, a number, and a special character.";
    }
    if (!departmentId || Number.isNaN(parsedDeptId) || parsedDeptId < 1) {
      return "Please enter a valid positive Department ID (e.g., 1 or 2).";
    }
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSuccessMessage(null);

    const validationMessage = validateForm();
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }

    try {
      const created = await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        departmentId: Number(departmentId),
      });

      setSuccessMessage(
        `Account created for ${created.fullName} (${created.email}). Redirecting to sign in...`
      );
      setTimeout(() => {
        navigate("/login", { replace: true });
      }, 1200);
    } catch (err) {
      if (axios.isAxiosError<ApiErrorPayload>(err)) {
        const backendMessage = err.response?.data?.message;
        if (err.response?.status === 409) {
          setFormError(backendMessage || "An account with this email already exists.");
        } else if (err.response?.status === 400) {
          setFormError(backendMessage || "Please check all required registration fields.");
        } else if (!err.response) {
          setFormError("Unable to reach the server. Make sure the backend is running on port 8080.");
        } else {
          setFormError(backendMessage || "Registration failed. Please try again.");
        }
      } else {
        setFormError("Something unexpected went wrong while registering.");
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
            <p className="text-sm text-slate-500">Employee Account Registration</p>
          </div>
        </div>

        <h2 className="text-lg font-semibold text-slate-800 mb-1">Create your account</h2>
        <p className="text-sm text-slate-500 mb-6">
          New accounts start with Employee access mapped to your department cost center.
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

        {successMessage && (
          <div
            role="status"
            className="mb-5 flex items-start gap-2.5 rounded-lg border border-emerald-200 bg-emerald-50 p-3.5 text-sm text-emerald-700"
          >
            <CheckCircle2 className="h-4 w-4 mt-0.5 shrink-0 text-emerald-600" />
            <span>{successMessage}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <div>
            <label htmlFor="reg-fullname" className="block text-sm font-medium text-slate-700 mb-1">
              Full Name
            </label>
            <div className="relative">
              <User className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="reg-fullname"
                type="text"
                autoComplete="name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Anil Kumar"
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label htmlFor="reg-email" className="block text-sm font-medium text-slate-700 mb-1">
              Email Address
            </label>
            <div className="relative">
              <Mail className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="reg-email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="employee@procureshield.com"
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label htmlFor="reg-password" className="block text-sm font-medium text-slate-700 mb-1">
              Password
            </label>
            <div className="relative">
              <Lock className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="reg-password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Min 8 chars, Aa, 1, @"
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

          <div>
            <label htmlFor="reg-dept" className="block text-sm font-medium text-slate-700 mb-1">
              Department ID
            </label>
            <div className="relative">
              <Building2 className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                id="reg-dept"
                type="number"
                min={1}
                step={1}
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <p className="mt-1 text-xs text-slate-500">
              Example: <span className="font-medium">1</span> for Platform Engineering or{" "}
              <span className="font-medium">2</span> for Enterprise Security.
            </p>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full mt-2 inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-medium text-sm py-2.5 px-4 transition-colors cursor-pointer disabled:cursor-not-allowed"
          >
            {isLoading && <Loader2 className="h-4 w-4 animate-spin" />}
            {isLoading ? "Creating account..." : "Create Account"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-blue-600 hover:text-blue-700">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}