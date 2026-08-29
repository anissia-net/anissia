import {TranslatorApplyPoll} from "./TranslatorApplyPoll";
import anissia from "../../common/anissia";
import {DateFormat} from "raon";

export class TranslatorApply {
    public applyNo = 0;
    public name = "";
    public polls = [] as TranslatorApplyPoll[];
    public regTime = 0;
    public result = "";
    public status = "";
    public website = "";

    public get regDtText(): string {
        return anissia.ymdOrDynamicAgo(this.regTime);
    }

    public get regDtFullText(): string {
        return DateFormat.parseByUnixTime(this.regTime).format("yyyy-MM-dd HH:mm:ss");
    }

    public get resultText(): string {
        switch (this.result) {
            case 'ACT': return'진행';
            case 'PASS': return'수리';
            case 'FAIL': return'반려';
        }
        return this.result;
    }

    public static assign(obj: any): TranslatorApply {
        const view = Object.assign(new TranslatorApply(), obj);
        if (view.polls) {
            view.polls = obj.polls.map(TranslatorApplyPoll.assign);
        }
        return view;
    }
}
