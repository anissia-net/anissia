export class AnimeRankItem {
    public animeNo = 0;
    public diff = 0;
    public exist = true;
    public hit = 0;
    public rank = 0;
    public subject = '';

    public get isDiff(): boolean {
        return this.diff != 0;
    }

    public get isDiffUp(): boolean {
        return this.diff > 0;
    }

    public get isDiffAbs(): number {
        return Math.abs(this.diff);
    }
}