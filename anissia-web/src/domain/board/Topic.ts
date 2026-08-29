import anissia from "../../common/anissia";
import {Post} from "./Post";

export class Topic {
    public topicNo = 91;
    public topic = "";
    public name = "";
    public postCount = 0;
    public fixed = false;
    public regTime = 0;
    public posts = [] as Post[];

    public get regDtText(): string {
        return anissia.ymdOrDynamicAgo(this.regTime);
    }

    public static assign(e: any) {
        const item = Object.assign(new Topic(), e);
        if (e.posts) {
            item.posts = e.posts.map((f: any) => Object.assign(new Post(item), f));
        }
        return item;
    }
}