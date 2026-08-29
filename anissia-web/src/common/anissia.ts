import {DateFormat, ko} from "raon";

class Anissia {
    public isPureWeek(week: number | string): boolean {
        return [7, 8].indexOf(Number(week)) === -1;
    }

    public toKoWeek(week: number | string) {
        return (['일', '월', '화', '수', '목', '금', '토', '外', '新'])[Number(week)];
    }

    public toKo12Time(time: string) {
        if (time.localeCompare('11:59') > 0) {
            const hour = Number(time.substring(0, 2));
            return '오후 ' + ('0'+ (hour > 12 ? hour -12 : hour)).slice(-2) + time.substring(2);
        } else {
            return '오전 ' + time;
        }
    }

    public getSubjectPrefix(week: string, status: string, startDate: string, endDate: string) {
        const today = new DateFormat().format("yyyy-MM-dd");
        if (this.isPureWeek(Number(week))) {
            if (status === 'OFF') {
                return '결방';
            } else if (today.localeCompare(startDate) <= 0) {
                return startDate.substring(5);
            } else if (this.animeIsEnded(endDate)) {
                return '完';
            }
        }
        return '';
    }

    public animeIsEnded(endDate: string): boolean {
        const today = new DateFormat().format("yyyy-MM-dd");
        return endDate !== '' && today.localeCompare(endDate) >= 0;
    }

    public animePeriod(week: string, startDate: string, endDate: string): string {
        if (startDate !== '' || endDate !== '') {
            const sd = startDate.replace(/-99/g, '');
            const ed = endDate.replace(/-99/g, '');
            let rv = '';
            if (sd && ed) {
                rv = sd === ed ? sd : `${sd}  ~  ${ed}`;
            } else {
                rv = sd + ed;
                if (this.isPureWeek(Number(week)) && sd) {
                    rv += '  ~  방영중';
                }
            }
            return (`${rv} `
                .replace(/([\d]{4})-([\d]{2})-([\d]{2})/g, '$1. $2. $3.')
                .replace(/([\d]{4})-([\d]{2})/g, '$1. $2.')
                .replace(/([\d]{4}) /g, '$1.'))
                .trim();
        }
        return '';
    }

    public ymdOrDynamicAgo(inputDate: string|number): string {
        let dateFormat;
        if (typeof inputDate === 'string') {
            dateFormat = DateFormat.parseByFormat(inputDate, "yyyy-MM-ddTHH:mm:ss");
        } else if (typeof inputDate === 'number') {
            dateFormat = DateFormat.parseByUnixTime(inputDate);
        } else {
            return '';
        }
        const ago = dateFormat.ago;
        return ago.unitIndex < 4 ? ko.toDateAgo(ago) : dateFormat.format('yyyy-MM-dd');
    }
    
    public fieldToText(field: string): string {
        switch (field) {
            case 'subject' : return '제목';
            case 'originalSubject' : return '원제';
            case 'status' : return '상태';
            case 'week' : return '요일';
            case 'time' : return '시간';
            case 'genres' : return '장르';
            case 'x' : return 'X';
            case 'startDate' : return '시작일';
            case 'endDate' : return '종료일';
            case 'website' : return '웹사이트';
            case 'note': return '비고';
        }
        return field;
    }

    public deCombineAnimeDate(animeDate: string, callback: (type: string, year: string, month: string, date: string) => void) {
        if (!animeDate || (!(/^\d{4}-\d{2}-\d{2}$/).test(animeDate))) {
            callback('N/A', '', '', '');
        } else if (animeDate.endsWith('-99-99')) {
            callback('Y', animeDate.substring(0, 4), '', '');
        } else if (animeDate.endsWith('-99')) {
            callback('YM', animeDate.substring(0, 4), animeDate.substring(5, 7), '');
        } else {
            callback('YMD', animeDate.substring(0, 4), animeDate.substring(5, 7), animeDate.substring(8, 10));
        }
    }

    public combineAnimeDate(type: string, year: string, month: string, date: string): string {
        let ymd = '';
        if (type == 'YMD') {
            ymd = `${year}-${month}-${date}`;
        } else if (type == 'YM') {
            ymd = `${year}-${month}-99`;
        } else if (type == 'Y') {
            ymd = `${year}-99-99`;
        }
        return (/^\d{4}-\d{2}-\d{2}$/).test(ymd) ? ymd : '';
    }

    public checkAnimeDate(type: string, animeDate: string): boolean {
        if ((/^\d{4}-\d{2}-\d{2}$/).test(animeDate)) {
            return true;
        }
        return (animeDate == '' && type == 'N/A');
    }

    public animeStatusToText(status: string, pureWeek: boolean): string {
        switch (status) {
            case 'ON': return pureWeek ? '방영중' : `신작/기타`;
            case 'OFF': return '결방';
            case 'END': return '완결';
            case 'DEL': return '삭제대기';
            case 'REQ': return '등록요청';
        }
        return '';
    }

    public url(url: string, exactly: boolean = false): boolean {
        const path = location.pathname;
        return path == url || (!exactly && path.startsWith(`${url}/`));
    }

    public enHtml(text: string): string {
        const div = document.createElement('div');
        div.innerText = text;
        return div.innerHTML;
    }
}

export default new Anissia();