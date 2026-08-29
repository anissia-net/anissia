import anissia from "../../common/anissia";

export default class AnimeCaption {

    public animeNo: number = 0;
    public episode: string = '';
    public name: string = '';
    public subject: string = '';
    public updDt: string = '';
    public website: string = '';

    public get episodeText(): string {
        if (this.website == '') {
            return '준비중';
        }
        return this.episode === '0' ? '단편' : (this.episode + '화');
    }

    public get updDtText(): string {
        return anissia.ymdOrDynamicAgo(this.updDt);
    }

    public static assign(data: any): AnimeCaption {
        return Object.assign(new AnimeCaption(), data);
    }
}
