import AnimeCaption from "../AnimeCaption";
import PageData from "../../../common/PageData";
import AnimeAutocorrect from "../AnimeAutocorrect";
import Anime from "../Anime";
import ajax from "../../../common/ajax";
import Result from "../../../common/Result";
import {AnimeRankItem} from "../../rank/AnimeRankItem";

const api = import.meta.env.VITE_API_PATH;

class AnimeRemote {
    private cacheAutocorrect = new Map<string, {expires: number, promise: Promise<AnimeAutocorrect[]>}>();
    private genres: string[] = [];

    public getScheduleAnimeList(week: number|string): Promise<Anime[]> {
        // Public, always-fresh reads: no DAT preflight and no browser data cache.
        return ajax.fetch(`${api}/anime/schedule/${week}`, null, {cache: 'no-store'}, false)
            .then(e => e.orIgnore)
            .then(list => list.map(Anime.assign));
    }

    public getAnimeCaptionList(animeNo: string|number): Promise<AnimeCaption[]> {
        return ajax.fetch(`${api}/anime/caption/animeNo/${animeNo}`, null, {cache: 'no-store'}, false)
            .then(e => e.orIgnore)
            .then(list => list.map(AnimeCaption.assign));
    }

    public getAnimeList(page: number, query: string): Promise<PageData<Anime>> {
        return ajax.fetch(`${api}/anime/list/${page}?q=${encodeURIComponent(query)}`)
            .then(e => e.orIgnore)
            .then(data => PageData.cast(data, Anime.assign));
    }

    public getAnime(animeNo: number): Promise<Anime> {
        return ajax.fetch(`${api}/anime/animeNo/${animeNo}`)
            .then(e => e.orIgnore)
            .then(anime => {
            const node = Object.assign(new Anime(), anime);
            node.captions = anime.captions.map((caption: any) => Object.assign(new AnimeCaption(), caption));
            return node;
        });
    }

    public getAnimeListAutocorrect(query: string): Promise<AnimeAutocorrect[]> {
        query = query.trim();
        if (!query) {
            return Promise.resolve([]);
        }
        const cached = this.cacheAutocorrect.get(query);
        if (cached && cached.expires > Date.now()) {
            return cached.promise;
        }
        this.cacheAutocorrect.delete(query);
        const entry = {
            expires: Infinity,
            promise: ajax.fetch(`${api}/anime/autocorrect?q=${encodeURIComponent(query)}`)
                .then(e => e.orIgnore)
                .then(list => {
                    const result: AnimeAutocorrect[] = list.map((e: string) => new AnimeAutocorrect(query, e));
                    result.sort((a, b) => a.lo.localeCompare(b.lo));
                    entry.expires = Date.now() + 60_000;
                    return result;
                })
                .catch(error => {
                    if (this.cacheAutocorrect.get(query) === entry) {
                        this.cacheAutocorrect.delete(query);
                    }
                    throw error;
                })
        };
        this.cacheAutocorrect.set(query, entry);
        if (this.cacheAutocorrect.size > 64) {
            this.cacheAutocorrect.delete(this.cacheAutocorrect.keys().next().value!);
        }
        return entry.promise;
    }

    public getAdminScheduleAnimeList(week: number): Promise<Anime[]> {
        return ajax.fetch(`${api}/anime/schedule/${week}?useCache=false`)
            .then(e => e.orIgnore)
            .then(list => list.map(Anime.assign));
    }

    public getAdminCaptionList(state: number, page: number): Promise<PageData<any>> {
        return ajax.fetch(`${api}/anime/caption/myList/${state}/${page}`)
            .then(e => e.orIgnore)
            .then(data => PageData.cast(data, AnimeCaption.assign));
    }

    public deleteAdminCaption(animeNo: number): Promise<Result<any>> {
        return ajax.delete(`${api}/anime/caption/${animeNo}`);
    }

    public updateAdminCaption(caption: AnimeCaption): Promise<Result<any>> {
        return ajax.put(`${api}/anime/caption/${caption.animeNo}`, caption);
    }

    public addAdminCaption(animeNo: number): Promise<Result<any>> {
        return ajax.post(`${api}/anime/caption/${animeNo}`);
    }

    public getAdminAnimeDelist(): Promise<PageData<Anime>> {
        return ajax.fetch(`${api}/anime/delist`)
            .then(e => e.orIgnore)
            .then(data => PageData.cast(data, Anime.assign));
    }

    public addAdminAnime(anime: Anime): Promise<Result<any>> {
        return ajax.post(`${api}/anime`, anime).then(result => this.invalidateAutocorrect(result));
    }

    public updateAdminAnime(anime: Anime): Promise<Result<any>> {
        return ajax.put(`${api}/anime/${anime.animeNo}`, anime).then(result => this.invalidateAutocorrect(result));
    }

    public deleteAdminAnime(animeNo: number): Promise<Result<any>> {
        return ajax.delete(`${api}/anime/${animeNo}`).then(result => this.invalidateAutocorrect(result));
    }

    public recoverAdminAnime(agendaNo: number): Promise<Result<any>> {
        return ajax.post(`${api}/anime/recover/${agendaNo}`).then(result => this.invalidateAutocorrect(result));
    }

    private invalidateAutocorrect(result: Result<any>): Result<any> {
        if (result.code === 'ok') {
            this.cacheAutocorrect.clear();
        }
        return result;
    }

    public getCaptionRecent(): Promise<AnimeCaption[]> {
        return ajax.fetch(`${api}/anime/caption/recent`)
            .then(e => e.orIgnore)
            .then(list => list.map(AnimeCaption.assign));
    }

    public getCaptionRecentPage(page: number): Promise<PageData<AnimeCaption>> {
        return ajax.fetch(`${api}/anime/caption/recent/${page}`)
            .then(e => e.orIgnore)
            .then(list => PageData.cast(list, e => Object.assign(new AnimeCaption(), e)));
    }

    public getGenres(): Promise<string[]> {
        if (this.genres.length) {
            return Promise.resolve(this.genres);
        } else {
            return ajax.fetch(`${api}/anime/genres`)
                .then(e => e.orIgnore)
                .then(genres => this.genres = genres);
        }
    }

    public getRank(type: string): Promise<AnimeRankItem[]> {
        return ajax.fetch(`${api}/anime/rank/${type}`).then(e => e.orIgnore).then(list => {
            for (let i = list.length - 1 ; i >= 1 ; i--) {
                if (list[i - 1].rank === list[i].rank) {
                    list[i].rank = '-';
                }
            }
            if (list.length < 30) {
                list = list.concat([...new Array(30).keys()].map(e => ({animeNo: 0, rank: e+1})).slice(list.length));
            }
            return list.map((e: any) => Object.assign(new AnimeRankItem(), e));
        });
    }
}

export default new AnimeRemote()
