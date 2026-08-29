import {sessionStore} from "../sessionStore";
import Session from "../Session";
import sessionRemote from "./sessionRemote";
import LoginInfo from "../LoginInfo";
import Result from "../../../common/Result";
import {Router} from "vue-router";
import {cookies, Locate} from "raon";

class SessionService {

    public login(email: string, password: string, makeLoginToken: boolean): Promise<boolean> {
        return new Promise(resolve => {
            if (!(makeLoginToken && !confirm('정말로 자동로그인을 사용하시겠습니까?\n- 로그인정보가 현 기기에 저장됩니다.\n- 공공장소에서는 절대 사용하지 마십시오.'))) {
                sessionRemote.login(email, password, makeLoginToken)
                    .then(res => {
                        if (res.success) {
                            this.applyLoginInfo(res)
                        } else {
                            res.toastErrorMessage();
                        }
                        resolve(res.success);
                    });
            }
        });
    }

    public sync(): Promise<boolean> {
        return new Promise(resolve => {
            const user = sessionStore().user;
            if (user.isLogin) {
                if (user.needRenewSession) {
                    sessionRemote.updateDat().then(res => {
                        resolve(this.applyLoginInfo(res));
                    });
                } else {
                    resolve(true);
                }
            } else {
                let session = Session.assign(this.getDat())
                sessionStore().setUser(session);
                if (session.isLogin) {
                    resolve(true);
                } else {
                    this.tokenLogin().then(r => resolve(r));
                }
            }
        });
    }

    public logout() {
        this.deleteDat();
        localStorage.removeItem('auth-token');
        sessionStore().setUser(Session.notLogin());
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

    private tokenLogin(): Promise<boolean> {
        return new Promise(resolve => {
            const token = localStorage.getItem('auth-token');
            if (token) {
                sessionRemote.tokenLogin(token).then(res => {
                    if (this.applyLoginInfo(res)) {
                        resolve(true);
                    } else {
                        this.logout();
                        resolve(false);
                    }
                });
            } else {
                resolve(false);
            }
        });
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
            if (loginInfoResult.success) {
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
        }
        return result;
    }
}

export default new SessionService();
