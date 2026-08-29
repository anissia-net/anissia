import ajax from "../../../common/ajax";
import Result from "../../../common/Result";
import LoginInfo from "../LoginInfo";

const api = import.meta.env.VITE_API_PATH;

class SessionRemote {

    public login(email: string, password: string, makeLoginToken: boolean): Promise<Result<LoginInfo>> {
        return ajax.post(`${api}/session`, {email, password, makeLoginToken})
            .then(e => e.map(LoginInfo.assign));
    }

    public tokenLogin(absoluteToken: string): Promise<Result<LoginInfo>> {
        return ajax.post(`${api}/session/token`, {absoluteToken})
            .then(e => e.map(LoginInfo.assign));
    }

    public updateDat(): Promise<Result<LoginInfo>> {
        return ajax.put(`${api}/session`)
            .then(e => e.map(LoginInfo.assign))
    }
}

export default new SessionRemote();
