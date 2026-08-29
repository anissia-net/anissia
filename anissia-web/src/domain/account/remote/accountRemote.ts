import ajax from "../../../common/ajax";
import Result from "../../../common/Result";
import {Account} from "../Account";

const api = import.meta.env.VITE_API_PATH;

class AccountRemote {

    public sendRecoverAuthMail(email: string, name: string): Promise<Result<any>> {
        return ajax.post(`${api}/account/recover`, {email, name});
    }

    public validationRecoverAuthMail(absoluteToken: string): Promise<Result<any>> {
        return ajax.put(`${api}/account/recover`, {absoluteToken});
    }

    public recoverPassword(absoluteToken: string, password: string, passwordConfirm: string): Promise<Result<any>> {
        if (password !== passwordConfirm) {
            return Promise.resolve(new Result('fail', '암호가 서로 일치하지 않습니다.'));
        }
        return ajax.put(`${api}/account/recover/password`, {absoluteToken, password});
    }

    public sendRegisterAuthMail(email: string, password: string, passwordConfirm: string, name: string): Promise<Result<any>> {
        if (password !== passwordConfirm) {
            return Promise.resolve(new Result('fail', '암호가 서로 일치하지 않습니다.'));
        }
        return ajax.post(`${api}/account/register`, {email, password, name});
    }

    public validationRegister(absoluteToken: string): Promise<Result<any>> {
        return ajax.put(`${api}/account/register`, {absoluteToken});
    }

    public getAccount(): Promise<Account> {
        return ajax.fetch(`${api}/account/user`).then(e => Object.assign(new Account(), e));
    }

    public updateUserPassword(oldPassword: string, newPassword: string, newPasswordConfirm: string): Promise<Result<any>> {
        if (newPassword !== newPasswordConfirm) {
            return Promise.resolve(new Result('fail', '새 암호가 서로 일치하지 않습니다.'));
        }
        return ajax.put(`${api}/account/user/password`, {oldPassword, newPassword});
    }

    public updateUserName(name: string, password: string): Promise<Result<any>> {
        return ajax.put(`${api}/account/user/name`, {name, password});
    }

    public withdraw(password: string): Promise<Result<any>> {
        return ajax.delete(`${api}/account/user`, {password});
    }
}

export default new AccountRemote()