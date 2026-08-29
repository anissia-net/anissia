import {SiteMemberTag} from "./SiteMemberTag";

export interface SiteMember {
    name: string;
    date: string;
    tags: SiteMemberTag[];
}
