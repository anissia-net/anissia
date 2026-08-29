import Result from "../../../common/Result";
import ajax from "../../../common/ajax";
import PageData from "../../../common/PageData";
import {TranslatorApply} from "../TranslatorApply";

const api = import.meta.env.VITE_API_PATH;

class TranslatorRemote {
    public getApplyList(page: number): Promise<PageData<TranslatorApply>> {
        return ajax.fetch(`${api}/translator/apply/list/${page}`)
            .then(e => e.orIgnore)
            .then(data => PageData.cast(data, TranslatorApply.assign));
    }

    public getApply(applyNo: number): Promise<TranslatorApply> {
        return ajax.fetch(`${api}/translator/apply/${applyNo}`)
            .then(e => e.orIgnore)
            .then(TranslatorApply.assign);
    }

    public addApply(website: string): Promise<Result<number>> {
        return ajax.post(`${api}/translator/apply`, {website});
    }

    public addApplyPoll(applyNo: number, point: string, comment: string): Promise<Result<any>> {
        return ajax.post(`${api}/translator/apply/${applyNo}/poll`, {point, comment});
    }

    public getAdminTranslatorApplyCount(): Promise<number> {
        return ajax.fetch(`${api}/translator/apply/count`)
            .then(e => e.orIgnore);
    }
}

export default new TranslatorRemote();