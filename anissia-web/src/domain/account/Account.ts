import anissia from "../../common/anissia";

export class Account {
    public email = ""
    public name = "";
    public regTime = 0;
    public roles = [] as string[];

    public get rolesText(): string[] {
        if (this.roles.length == 0) {
            return ['일반회원'];
        }
        return this.roles;
    }

    public get regDtText(): string {
        return anissia.ymdOrDynamicAgo(this.regTime);
    }
}