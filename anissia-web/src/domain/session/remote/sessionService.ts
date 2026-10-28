import {sessionStore} from "../sessionStore";
import Session from "../Session";
import sessionRemote from "./sessionRemote";
import LoginInfo from "../LoginInfo";
import Result from "../../../common/Result";
import {Router} from "vue-router";
import {cookies, Locate} from "raon";
import {clearPushDevice} from '../../push/pushDevice';
import toast from '../../../common/toast';

class SessionService {
    private logoutRequest: Promise<void> | null = null;

    public async login(email: string, password: string, makeLoginToken: boolean): Promise<boolean> {
        if (makeLoginToken && !confirm('정말로 자동로그인을 사용하시겠습니까?\n- 로그인정보가 현 기기에 저장됩니다.\n- 공공장소에서는 절대 사용하지 마십시오.')) return false;
        if (this.logoutRequest) await this.logoutRequest;
        const res = await sessionRemote.login(email, password, makeLoginToken);
        if (res?.success) return this.applyLoginInfo(res);
        res?.toastErrorMessage();
        return false;
    }

    public async sync(): Promise<boolean> {
        if (this.logoutRequest) return this.logoutRequest.then(() => false);
        // Cookies are shared between tabs. Always read them before trusting the
        // in-memory user, otherwise an old tab could revoke a new account's push.
        const dat = this.getDat();
        const user = Session.assign(dat);
        sessionStore().setUser(user);
        if (!user.isLogin) return this.tokenLogin();
        if (!user.needRenewSession) return true;
        const result = await sessionRemote.updateDat();
        if (this.logoutRequest || this.getDat() !== dat) return Session.assign(this.getDat()).isLogin;
        const success = this.applyLoginInfo(result);
        if (!success) await this.logout();
        return success;
    }

    public logout(): Promise<void> {
        if (this.logoutRequest) return this.logoutRequest;
        const dat = this.getDat();
        const owner = sessionStore().user.email;
        sessionStore().setUser(Session.notLogin());
        this.logoutRequest = clearPushDevice(dat)
            .catch(error => toast.error(error instanceof Error ? error.message : '브라우저 알림 해제를 확인해 주세요.'))
            .finally(() => {
                // A delayed logout must not erase a newer sign-in in another tab.
                if (this.getDat() === dat || (owner && Session.assign(this.getDat()).email === owner)) {
                    this.deleteDat();
                    localStorage.removeItem('auth-token');
                    sessionStore().setUser(Session.notLogin());
                }
                this.logoutRequest = null;
            });
        return this.logoutRequest;
    }

    public amendPathBySession(path: string, router: Router, next: Function = () => {}) {
        const user = sessionStore().user;
        const isLogin = user.isLogin;
        const isAdmin = user.isAdmin;

        if (isLogin) {
            if (path.startsWith('/login') || path.startsWith('/register') || path.startsWith('/recover')) {
                const path = new Locate().getParameter('path', '');
                router.push(path && path.startsWith('/') ? path : '/');
            } else if ((path.startsWith('/admin')) && !isAdmin) {
                router.push(`/`);
            } else {
                next();
            }
        } else {
            if (path.startsWith('/account') || path.startsWith('/admin')) {
                router.push(`/login?path=${encodeURIComponent(path)}`);
            } else {
                next();
            }
        }
    }

    private async tokenLogin(): Promise<boolean> {
        const token = localStorage.getItem('auth-token');
        const dat = this.getDat();
        if (!token) return false;
        const result = await sessionRemote.tokenLogin(token);
        if (this.logoutRequest || this.getDat() !== dat || localStorage.getItem('auth-token') !== token) return Session.assign(this.getDat()).isLogin;
        const success = this.applyLoginInfo(result);
        if (!success) await this.logout();
        return success;
    }

    public getDat(): string {
        return cookies.get('dat');
    }

    private deleteDat() {
        cookies.del('dat');
    }

    private applyLoginInfo(loginInfoResult: Result<LoginInfo>): boolean {
        let result = false;
        try {
            if (loginInfoResult?.success) {
                const data = loginInfoResult.data!!;
                if (data.dat) {
                    cookies.set('dat', data.dat);
                }
                if (data.token) {
                    localStorage.setItem('auth-token', data.token);
                }
                const session = Session.assign(this.getDat())
                sessionStore().setUser(session);
                result = true;
            }
        } catch (e) { }
        if (!result) {
            this.deleteDat();
            sessionStore().setUser(Session.notLogin());
        }
        return result;
    }
}

export default new SessionService();
