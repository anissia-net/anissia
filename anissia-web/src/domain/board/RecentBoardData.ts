import {Topic} from "./Topic";

export class RecentBoardData {
    public notice: Topic[] = [];
    public inquiry: Topic[] = [];

    public static assign(data: any) {
        const item = new RecentBoardData();
        item.notice = data.notice.map((e: any) => Object.assign(new Topic(), e));
        item.inquiry = data.inquiry.map((e: any) => Object.assign(new Topic(), e));
        return item;
    }
}