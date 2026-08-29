import anissia from "../../common/anissia";
import AnimeCaption from "./AnimeCaption";

export default class Anime {

    public animeNo = 0;
    public captionCount = 0;
    public endDate ='';
    public genres = '';
    public originalSubject = '';
    public startDate = '';
    public status = 'ON';
    public subject = '';
    public time = '00:00';
    public x = '';
    public website = '';
    public note = '';
    public week = '8';
    public captions: AnimeCaption[] = [];

    public tags: string[] = [];

    public agendaNo = 0;

    public editGenresOpen = false;
    public editGenres = [] as string[];
    public editStartDateType = 'N/A';
    public editStartDateYear = '';
    public editStartDateMonth = '';
    public editStartDateDate = '';
    public editEndDateType = 'N/A';
    public editEndDateYear = '';
    public editEndDateMonth = '';
    public editEndDateDate = '';

    public bindEdit(): Anime {
        this.editGenres = this.genres.split(",").filter(e => e);
        anissia.deCombineAnimeDate(this.startDate, (type, year, month, date) => {
            this.editStartDateType = type;
            this.editStartDateYear = year;
            this.editStartDateMonth = month;
            this.editStartDateDate = date;
        });
        anissia.deCombineAnimeDate(this.endDate, (type, year, month, date) => {
            this.editEndDateType = type;
            this.editEndDateYear = year;
            this.editEndDateMonth = month;
            this.editEndDateDate = date;
        });
        return this;
    }

    public applyEdit(): Anime {
        this.genres = this.editGenres.join(',');
        this.time = this.week != '7' ? this.time : '00:00';
        this.startDate = anissia.combineAnimeDate(this.editStartDateType, this.editStartDateYear, this.editStartDateMonth, this.editStartDateDate);
        this.endDate = anissia.combineAnimeDate(this.editEndDateType, this.editEndDateYear, this.editEndDateMonth, this.editEndDateDate);
        return this;
    }

    public get statusText(): string {
        switch (this.status) {
            case 'ON': return this.pureWeek ? '방영중' : `신작/기타`;
            case 'OFF': return '결방';
            case 'END': return '완결';
            case 'DEL': return '삭제대기';
            case 'REQ': return '등록요청';
        }
        return '';
    }

    public get pureWeek(): boolean {
        return anissia.isPureWeek(this.week);
    }

    public get timeText(): string {
        return this.pureWeek ? anissia.toKo12Time(this.time) : '';
    }

    public get weekText(): string {
        return anissia.toKoWeek(this.week);
    }

    public get subjectPrefix(): string {
        return anissia.getSubjectPrefix(this.week + '', this.status, this.startDate, this.endDate);
    }

    public get scheduleTime(): string {
        return this.time = this.time != '' ? this.time.replace(/-99/g, '') : '-';
    }

    public get period(): string {
        return anissia.animePeriod(this.week + '', this.startDate, this.endDate);
    }

    public static assign(data: any): Anime {
        const anime = Object.assign(new Anime(), data);

        const tags = [anime.period];
        if (!anime.pureWeek) {
            tags.push(anime.weekText);
        } else if (['ON', 'OFF'].indexOf(anime.status) != -1 && anime.week) {
            tags.push("매주 (" + anime.weekText + ") " + anime.timeText);
            if (anime.status == 'OFF') {
                tags.push('금주결방');
            }
        }
        anime.tags = tags.filter(e => (e || '').trim());

        return anime;
    }
}
