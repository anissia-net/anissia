import anissia from "../../common/anissia";

export class TranslatorApplyPoll {
    public comment = "";
    public name = "";
    public no = 0;
    public regTime = 0;
    public vote = 0;

    public get regDtText(): string {
        return anissia.ymdOrDynamicAgo(this.regTime);
    }

    public static assign(obj: any): TranslatorApplyPoll {
        return Object.assign(new TranslatorApplyPoll(), obj);
    }
}
