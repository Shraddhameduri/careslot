import type { ReactNode } from 'react';
import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { Header } from './components/ui';
import type { Role } from './types';
import AdminDashboard from './pages/AdminDashboard';
import Book from './pages/Book';
import Login from './pages/Login';
import ProviderDashboard from './pages/ProviderDashboard';
import SignupTenant from './pages/SignupTenant';
import TenantPicker from './pages/TenantPicker';

function RequireTenant({ children }: { children: ReactNode }) {
  const { tenant } = useAuth();
  if (!tenant) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user, tenant } = useAuth();
  if (!tenant) return <Navigate to="/" replace />;
  if (!user) return <Navigate to="/login" replace />;
  if (!roles.includes(user.role)) {
    // Role-aware fallback: send the user to their own home.
    const home =
      user.role === 'TENANT_ADMIN'
        ? '/admin'
        : user.role === 'PROVIDER'
          ? '/provider'
          : '/book';
    return <Navigate to={home} replace />;
  }
  return <>{children}</>;
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="main">{children}</main>
      <footer className="footer">CareSlot · multi-tenant appointment booking</footer>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Shell>
          <Routes>
            <Route path="/" element={<TenantPicker />} />
            <Route path="/signup-tenant" element={<SignupTenant />} />
            <Route path="/login" element={<Login />} />
            <Route
              path="/book"
              element={
                <RequireTenant>
                  <Book />
                </RequireTenant>
              }
            />
            <Route
              path="/provider"
              element={
                <RequireRole roles={['PROVIDER']}>
                  <ProviderDashboard />
                </RequireRole>
              }
            />
            <Route
              path="/admin"
              element={
                <RequireRole roles={['TENANT_ADMIN']}>
                  <AdminDashboard />
                </RequireRole>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Shell>
      </AuthProvider>
    </BrowserRouter>
  );
}
