import anissia from "../../common/anissia";

export default class AnimeAutocorrect {

    constructor(keyword: string, compactLine: string) {
        this.init(keyword, compactLine);
    }

    public key = 0;
    public word ='';
    public hl ='';

    public get lo(): string {
        return this.word.toLowerCase();
    }

    private init(keyword: string, line: string) {
        const k0 = keyword.trim().toLowerCase();
        const k1 = anissia.enHtml(k0);
        const k2 = anissia.enHtml(k0.length > 1 ? k0.substring(0, k0.length - 1) : '');

        this.key = Number(line.substring(0, line.indexOf(' ')));

        const w1 = this.word = anissia.enHtml(line.substring(line.indexOf(' ') + 1));
        const w2 = this.lo;

        if (k1 && w2.indexOf(k1) == 0) {
            this.hl = `<b>${w1.substring(0, k1.length)}</b>${w1.substring(k1.length)}`;
        } else if (k2 && w2.indexOf(k2) == 0) {
            this.hl = `<b>${w1.substring(0, k2.length)}</b>${w1.substring(k2.length)}`;
        } else {
            this.hl = w1;
        }
    }
}
