import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  clearAuth,
  clearTenant,
  loadTenant,
  loadUser,
  login as apiLogin,
  logout as apiLogout,
  refreshTokens as apiRefreshTokens,
  register as apiRegister,
  saveAuth,
  saveTenant,
  signupTenant as apiSignupTenant,
  type TenantRef,
} from './api';
import type { AuthResponse, SignupTenantResponse, Tokens, User } from './types';

interface AuthContextValue {
  user: User | null;
  tenant: TenantRef | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<AuthResponse>;
  register: (email: string, password: string, fullName: string) => Promise<AuthResponse>;
  signupTenant: (payload: {
    tenantName: string;
    adminName: string;
    adminEmail: string;
    password: string;
  }) => Promise<SignupTenantResponse>;
  refreshSession: () => Promise<boolean>;
  logout: () => Promise<void>;
  setTenantRef: (t: TenantRef | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => loadUser());
  const [tenant, setTenant] = useState<TenantRef | null>(() => loadTenant());

  const setTenantRef = useCallback((t: TenantRef | null) => {
    setTenant(t);
    if (t) saveTenant(t);
    else clearTenant();
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiLogin({ email, password });
    saveAuth(res, res.user);
    setUser(res.user);
    return res;
  }, []);

  const register = useCallback(async (email: string, password: string, fullName: string) => {
    const res = await apiRegister({ email, password, fullName });
    saveAuth(res, res.user);
    setUser(res.user);
    return res;
  }, []);

  const signupTenant = useCallback(
    async (payload: {
      tenantName: string;
      adminName: string;
      adminEmail: string;
      password: string;
    }) => {
      const res = await apiSignupTenant(payload);
      setTenantRef({ id: res.tenantId, slug: res.slug, name: payload.tenantName });
      saveAuth(res, res.user);
      setUser(res.user);
      return res;
    },
    [setTenantRef]
  );

  const refreshSession = useCallback(async (): Promise<boolean> => {
    const stored = localStorage.getItem('careslot_refresh');
    if (!stored) return false;
    try {
      const tokens: Tokens = await apiRefreshTokens(stored);
      const current = loadUser();
      if (!current) return false;
      saveAuth(tokens, current);
      return true;
    } catch {
      clearAuth();
      setUser(null);
      return false;
    }
  }, []);

  const logout = useCallback(async () => {
    const stored = localStorage.getItem('careslot_refresh');
    await apiLogout(stored ?? '');
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      tenant,
      isAuthenticated: !!user,
      login,
      register,
      signupTenant,
      refreshSession,
      logout,
      setTenantRef,
    }),
    [user, tenant, login, register, signupTenant, refreshSession, logout, setTenantRef]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
