import Session from "../session/Session";

export class BoardInfo {
    public name = '';
    public ticker = '';
    public writePostRoles = ["ROOT"] as string[];
    public writeTopicRoles = ["ROOT"] as string[];
    public phTopic = '';

    public canWriteTopic(user: Session): boolean {
        return user.isLogin && user.hasRole(...this.writeTopicRoles);
    }

    public canWritePost(user: Session): boolean {
        return user.isLogin && user.hasRole(...this.writePostRoles);
    }

    public static assign(e: any): BoardInfo {
        return Object.assign(new BoardInfo(), e);
    }
}
