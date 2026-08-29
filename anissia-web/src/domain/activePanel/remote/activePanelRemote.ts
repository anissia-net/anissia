import PageData from "../../../common/PageData";
import ajax from "../../../common/ajax";
import Result from "../../../common/Result";
import ActivePanelListItem from "../ActivePanelListItem";

const api = import.meta.env.VITE_API_PATH;

class ActivePanelRemote {
    public getList(page: number, mode: string): Promise<PageData<ActivePanelListItem>> {
        return ajax.fetch(`${api}/active-panel/list/${page}?mode=${mode}`)
            .then(e => e.orIgnore)
            .then(data => PageData.cast(data, e => Object.assign(new ActivePanelListItem(), e)));
    }

    public doCommand(query: string): Promise<Result<void>> {
        return ajax.post(`${api}/active-panel/command`, {query: query.trim()});
    }
}

export default new ActivePanelRemote()