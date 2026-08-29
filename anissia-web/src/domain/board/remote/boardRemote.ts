import PageData from "../../../common/PageData";
import ajax from "../../../common/ajax";
import Result from "../../../common/Result";
import {Topic} from "../Topic";
import {BoardInfo} from "../BoardInfo";
import {RecentBoardData} from "../RecentBoardData";

const api = import.meta.env.VITE_API_PATH;

class BoardRemote {
    public getTicker(ticker: string): Promise<BoardInfo> {
        return ajax.fetch(`${api}/board/ticker/${ticker}`)
            .then(e => e.orIgnore)
            .then(BoardInfo.assign);
    }

    public getTopic(ticker: string, topicNo: number): Promise<Topic> {
        return ajax.fetch(`${api}/board/topic/${ticker}/${topicNo}`)
            .then(e => e.orIgnore)
            .then(topic => Topic.assign(topic));
    }

    public getList(ticker: string, page: number): Promise<PageData<Topic>> {
        return ajax.fetch(`${api}/board/list/${ticker}/${page}`)
            .then(e => e.orIgnore)
            .then(list => PageData.cast(list, e => Topic.assign(e)));
    }

    public getRecentHome(): Promise<RecentBoardData> {
        return ajax.fetch(`${api}/board/recent/home`)
            .then(e => e.orIgnore)
            .then(data => RecentBoardData.assign(data));
    }

    public createTopic(ticker: string, topic: string, content: string): Promise<Result<number|null>> {
        return ajax.post(`${api}/board/topic/${ticker}`, { topic, content });
    }

    public updateTopic(topicNo: number, topic: string, content: string): Promise<Result<any>> {
        return ajax.put(`${api}/board/topic/${topicNo}`, { topic, content });
    }

    public deleteTopic(topicNo: number): Promise<Result<any>> {
        return ajax.delete(`${api}/board/topic/${topicNo}`);
    }

    public createPost(topicNo: number, content: string): Promise<Result<any>> {
        return ajax.post(`${api}/board/post/${topicNo}`, { content });
    }

    public updatePost(postNo: number, content: string): Promise<Result<any>> {
        return ajax.put(`${api}/board/post/${postNo}`, { content });
    }

    public deletePost(postNo: number): Promise<Result<any>> {
        return ajax.delete(`${api}/board/post/${postNo}`);
    }
}

export default new BoardRemote()